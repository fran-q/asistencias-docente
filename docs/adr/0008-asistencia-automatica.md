# ADR-0008: Modelo de asistencia automática

**Estado**: Aceptada
**Fecha**: 2026-05-29
**Decisor**: Francisco Quiroga (fran-q)

## Contexto

Sprint 5 cierra el flujo end-to-end de asistencia: tomar la identificación facial que armamos en Sprint 4 y conectarla con los horarios de las comisiones para marcar asistencia automáticamente. El esquema de BD (tabla `asistencias`, `asistencias_manuales`, `motivos_carga_manual`, `justificaciones_ausencia`) ya estaba diseñado en V001 — Sprint 5 sólo le pone la lógica encima y la UI.

Hay decisiones que conviene cerrar:

1. **Cuándo se considera PRESENTE vs TARDE**.
2. **Cómo se generan las marcas AUSENTE** (ya no hay nadie para "no marcar").
3. **Idempotencia** del pase: el loop continuo del navegador manda un frame por segundo; no podemos crear una fila por cada uno.
4. **Conversión de la distancia LBPH a una "confianza" 0-1** para guardar.
5. **Flujo de carga manual y justificación**: catálogo de motivos, qué se puede justificar.

## Decisiones

### 1. PRESENTE vs TARDE: tolerancia previa por horario

Cada `Horario` tiene su propia `tolerancia_min` (Sprint 2). El criterio es:

- `hora_actual ∈ [hora_inicio - tolerancia, hora_inicio]` → **PRESENTE**.
- `hora_actual > hora_inicio` (y `≤ hora_fin`) → **TARDE** (se guarda la hora exacta).
- Fuera de la ventana `[hora_inicio - tolerancia, hora_fin]` → "no hay clase ahora" (no se marca).

Esto encaja con el pedido explícito del cliente: *"Desde que pasa el horario de ingreso cualquier marca es TARDE; la tolerancia debe ser ajustable; el admin decide después si lo convierte a AUSENTE"*. Como cada horario ya tenía su propia tolerancia desde Sprint 2, no hizo falta agregar una global.

**Alternativa descartada**: una tolerancia única global por institución. Más simple pero menos flexible (algunos turnos podrían tener más margen que otros).

### 2. AUSENTE: modelo híbrido — job programado + cálculo al listar

> **Actualizado post-cierre** (auditoría del dossier de defensa): la versión
> original de esta decisión era "calculada al listar, no persistida". Se
> evolucionó a un **modelo híbrido** al implementarse el job programado que
> la entrevista de requerimientos había dejado abierto.

**Capa 1 — Job programado (`GeneradorAusenciasService`)**: un `@Scheduled`
(cron configurable, default cada 30 minutos) recorre las instituciones
activas y, por cada horario del día ya terminado (`hora_fin` pasada,
vigencia válida, docente activo) sin marca para su docente asignado,
**persiste** la fila `AUSENTE`. Convenciones:
- `metodo = AUTOMATICO` (generada por el sistema sin intervención humana;
  `MANUAL` queda reservado para cargas hechas por una persona). Sin modelo
  facial ni confianza — el CHECK de V001 lo permite.
- `hora_registrada = hora_fin` del horario (momento en que la ausencia se
  volvió definitiva).
- **Multi-tenant sin request**: los hilos del scheduler no pasan por
  `TenantInterceptor`; el job setea `TenantContext` por institución (y lo
  limpia en `finally`) y todas sus queries llevan `institucionId` explícito.
- **Idempotencia**: verificación previa + el UNIQUE `(docente, horario,
  fecha)` resuelven la carrera con un pase facial simultáneo a favor de la
  marca real.

**Capa 2 — Cálculo al vuelo (`listarDelDia`)**: se mantiene como ventana de
gracia. Entre que termina la clase y corre el próximo job, el listado sigue
mostrando la fila AUSENTE virtual. Cuando el job la persiste, la fila real
reemplaza naturalmente a la virtual (la clave docente:horario queda cubierta).

**Qué habilita la persistencia**: justificar la ausencia directamente
(RF-25/26, sin el paso intermedio de carga manual), inclusión en reportes,
y base para alertas del dashboard (RF-37).

### 2.bis Desempate de horario ante ambigüedad (RF-18)

> **Agregado post-cierre**: cierra el segundo tema `[ABIERTO]` de la entrevista.

La cámara está en **secretaría, no en el aula**: el sistema no observa la clase, la
**deduce** cruzando la hora del registro con los horarios. Con un solo horario en ventana
el caso es trivial, pero hay dos escenarios ambiguos reales:

- **Consecutivos**: el docente da 18-20 y 20-22 y se presenta 19:55 — con la tolerancia,
  ambas ventanas lo contienen.
- **Solapados**: el docente está asignado a dos comisiones a la misma hora. La validación
  de superposición de `HorarioService` es **por comisión**, así que este caso no está
  prohibido a nivel de datos.

La implementación previa tomaba `findFirst()` sobre la lista: dependía del orden de la
query, era **arbitraria y no reproducible**. El criterio formalizado, en orden:

1. **Preferir horarios sin marca previa.** Si el docente ya registró la clase de las 18,
   la marca nueva corresponde a la siguiente — resuelve el caso consecutivo de la forma
   más natural. *Salvaguarda*: si todos los candidatos ya están marcados, no se filtra,
   para que el flujo siga devolviendo "ya estaba marcado" (idempotencia) en vez de
   "no hay clase".
2. **Hora de inicio más cercana** al momento del registro. A las 19:55, la clase que
   arranca 20:00 está a 5 minutos y la que arrancó 18:00 a 115.
3. **Menor id** ante empate exacto, para que la decisión sea **determinista**.

Si ningún horario contiene el momento actual, **no se registra**: el caso se deriva a
carga manual (RF-22 a RF-24), coherente con la política de no marcar ante duda. Cada
ambigüedad detectada se loguea (`RF-18 ambigüedad` / `RF-18 desempate`) para trazabilidad.

Cubierto por 4 tests: consecutivos con marca previa, consecutivos sin marcas, solapados
con mismo inicio, y preservación de la idempotencia.

### 3. Idempotencia del pase automático

El UNIQUE de BD garantiza que no haya dos filas para el mismo `(docente_id, horario_id, fecha)`. Pero confiar sólo en la BD obliga a manejar la `DataIntegrityViolationException` para cada loop del navegador. Lo respetamos también a nivel aplicación:

```java
Optional<Asistencia> existente = asistenciaRepository
    .findByDocenteIdAndHorarioIdAndFecha(docenteId, horarioId, fecha);
if (existente.isPresent()) return ResultadoMarca.yaEstaba(existente.get());
```

La respuesta al cliente distingue `creada` / `yaEstaba` / `sinClase` para que la UI muestre el mensaje correcto sin reintentar.

### 4. Confianza derivada de la distancia LBPH

La tabla `asistencias.confianza` está pensada como `DECIMAL(5,4)` entre 0 y 1, donde **mayor = más confiable** (semántica habitual). LBPH devuelve **distancia**, donde **menor = mejor**. Conversión:

```java
score = max(0, 1 - distancia / umbral)
```

`umbral` es el mismo `app.biometria.umbral-confianza` que se usa para decidir reconocer/no reconocer. Si la distancia está en el umbral, el score es 0; si la distancia es 0, el score es 1. Lineal por simplicidad.

**Alternativa descartada**: persistir la distancia bruta. Más fiel al algoritmo pero rompe la semántica esperada de la columna y obliga a la UI a interpretarla al revés.

### 5. Carga manual y justificación

- **Carga manual** (`AsistenciaService.marcarManual`): el admin elige docente, horario, fecha, hora, estado (PRESENTE/TARDE/AUSENTE), motivo del catálogo `motivos_carga_manual`, y detalle adicional opcional. Crea fila en `asistencias` + fila 1:1 en `asistencias_manuales`. Falla si ya existe marca para el mismo `(docente, horario, fecha)`.

- **Justificación** (`AsistenciaService.justificarAusencia`): sólo aplica a asistencias persistidas con estado `AUSENTE`. Para justificar una "ausencia calculada" del listado, primero hay que cargarla manualmente como AUSENTE y después justificarla.

  **Por qué no se permite justificar directamente sobre una ausencia calculada**: el constraint `ck_asistencias_metodo_modelo` exige que toda fila en `asistencias` tenga método válido y `metodo == MANUAL` requiere fila en `asistencias_manuales`. Saltarse el flujo de carga manual implicaría inventar un motivo del catálogo de oficio (probablemente "OTRO") que tendría poca información. Es más limpio que el admin reconozca primero la ausencia y después la justifique.

## Consecuencias

**Positivas**:
- Cumple RF-17 a RF-26: registro automático + carga manual + justificación.
- Sin cron jobs: el listado siempre refleja el estado actual.
- Idempotencia con tres-niveles (BD UNIQUE + service + UI muestra "ya estaba marcado") — el loop del navegador no genera ruido.
- La tolerancia por horario respeta la realidad de cada turno.

**Negativas**:
- Cálculo de AUSENTE al listar tiene costo O(horarios del día) cada vez que se abre `/asistencias`. Para una institución con cientos de horarios por día puede pesar; mitigación: índice por `(institucion_id, dia_semana, activo)` (ya está).
- Conversión de distancia a score 0-1 es lineal y simplifica la realidad de LBPH (la relación no es estrictamente lineal). Para un PoC sirve.
- Para justificar una ausencia es necesario primero cargarla manualmente — dos pasos donde podrían ser uno. Trade-off por mantener limpio el modelo.

## Referencias

- ADR-0007 — Reconocimiento facial (donde está la distancia LBPH y el umbral).
- V001 — Tabla `asistencias` con CHECK constraints sobre estado, método y la coherencia método↔modelo↔confianza.
- Sprint 5 commits: `fb7d0d2` (Fase A+B), `f1045c8` (Fase C), siguiente (Fase D+E).
