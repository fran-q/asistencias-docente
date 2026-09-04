# 07 — Pendientes y rumbo

> **Versión:** 1.0 · **Actualizado:** 2026-08-14 · **Estado:** vigente
> **Se actualiza cuando:** se cierra una deuda, aparece una nueva, o cambia el rumbo.
>
> ⚠ **No hay fechas de entrega definidas.** El horizonte del producto final es marzo de
> 2027; las fechas intermedias se van a proponer más cerca de esa instancia. No inventar
> ni asumir plazos.

---

## Deuda técnica activa

El detalle completo de cada una está en `Documentacion/4-arquitectura/TECH_DEBT.md`. Acá
va el resumen operativo.

| ID | Qué | Severidad | Estado |
|---|---|---|---|
| TD-001 | MariaDB ignora los nombres de PRIMARY KEY | Baja (cosmética) | Aceptada, no se corrige |
| TD-003 | El filtro de tenant no se propaga a los JOINs | **Alta** | Aceptada como invariante, mitigada con regla de código |
| TD-004 | Conversión lineal distancia LBPH → confianza | Baja | Aceptada |
| TD-005 | Cache de modelos LBPH sin TTL ni límite de memoria | Baja en prototipo, media en producción | **Cerrada** — ver abajo |
| TD-006 | Reportes sin paginación | Baja en prototipo, media en producción | Abierta, mitigada con tope de filas |
| TD-008 | Límite de códigos por cuenta, no por origen | Baja en local, media expuesto a internet | Abierta |
| TD-009 | La recuperación de contraseña depende de que haya SMTP | Media | Abierta |

**Cerradas:** TD-002 (driver MySQL reemplazado por el nativo de MariaDB) y TD-007 (el
aspecto multi-tenant había quedado inactivo tras la reorganización de paquetes).

### Las que importan de verdad

**TD-003** no es una deuda que se vaya a "resolver": es una característica de Hibernate
que hay que conocer y respetar. La regla del `WHERE institucionId` explícito en todo JOIN
está en `04-convenciones.md` y no se negocia.

**TD-005 está cerrada, y la tabla decía lo contrario.** Detectado el 2026-09-02: el cache
tiene barrido por inactividad desde hace tiempo — `descartarModelosInactivos`, un
`@Scheduled` que suelta los modelos que nadie usó en 30 minutos
(`app.biometria.cache-minutos-inactividad`). La deuda seguía figurando como abierta en esta
tabla y en `02-arquitectura.md`, y de ahí pasó a las instrucciones de trabajo: se venía
pidiendo "tener presente el cache sin TTL" sobre algo que ya no era cierto.

Queda un resto real, y es distinto del original: **no hay límite de tamaño**. Con muchos
docentes activos al mismo tiempo, el cache crece hasta donde llegue la memoria antes de que
el barrido alcance a soltar nada. Caffeine con `maximumSize` sigue siendo el próximo paso si
eso llega a doler.

**TD-006** sí se vuelve real cuando el sistema salga a la nube y tenga carga verdadera: el
reporte sin paginación carga todo en memoria. Próximo paso definido: `Pageable` y streaming
del CSV.

**TD-008 y TD-009** son deudas de exposición: hoy no molestan porque el despliegue es
local y dentro de la red de la institución. **El día que el sistema esté en internet, se
vuelven urgentes.** TD-008 necesita límite por IP además del límite por cuenta; TD-009
necesita un procedimiento de recuperación de la cuenta institucional que no dependa del
correo.

---

## Brechas de requerimientos

Del documento original, lo que falta:

| ID | Qué falta | Estado |
|---|---|---|
| RF-33 | Visualizaciones gráficas en los reportes | No implementado. En el rumbo |
| RF-32 | Exportación a `.xlsx` nativo (hoy es CSV) | Desvío. Decidir si se cierra o se acepta formalmente |
| RF-30 | Filtro por día de la semana y períodos predefinidos | Parcial |
| RNF-01/02/03 | Medición formal de los tiempos de respuesta | Sin medir formalmente |
| RNF-04 | Prueba de carga con 200-400 docentes | Sin probar a escala |
| RF-34/35/36 | Módulo de auditoría administrativa | **Descartado a propósito.** No proponerlo salvo pedido explícito |

Y lo inverso: hay funcionalidad implementada que **no está en el documento de
requerimientos** (verificación de correo, recuperación de contraseña, constancia ARCO,
generación automática de ausencias, captura guiada, alta autogestionada de institución).
El listado completo está al final de `05-trazabilidad.md`. Todo eso necesita entrar
formalmente al alcance acordado en `Documentacion/2-requerimientos/`.

---

## Lo que deja abierto la marca de salida

Cerrada la funcionalidad (ADR-0017, ADR-0018), quedan cuatro cosas que **no se ven en un
build verde** y por eso conviene tenerlas escritas:

- [ ] **Avisarle al equipo administrativo que cambió la clasificación de llegadas.** Con
      tolerancia 15, quien llega 18:05 pasó de TARDE a PRESENTE. Es el efecto buscado, pero
      tienen que enterarse antes de verlo en un reporte, no después. El histórico no se
      recalculó: dos registros iguales pueden tener estados distintos según de qué lado del
      2026-09-01 caigan.

- [ ] **El cambio del pase es JavaScript y no lo cubre ningún test.** Que el backend mande
      `tipoDeMarca` está testeado; que `pase-asistencia.js` pinte el recuadro azul al salir,
      no. El proyecto no tiene infraestructura de tests de JS y no vale la pena montarla por
      esto solo, pero conviene saber que esa línea se rompe en silencio.

- [ ] **Los CHECK de V019 y V020 se verifican a mano.** El perfil `test` corre sobre H2 con
      Flyway apagado, así que ningún constraint de esas migraciones se ejercita. Se probaron
      contra MariaDB 10.4.32 el 2026-09-01 —los diez de V019, uno por uno— pero **hay que
      repetirlo si se toca el esquema de la tabla**. `MigracionesIT` sí verifica que las
      migraciones apliquen de cero y que las columnas estén; lo que no comprueba es que los
      CHECK rechacen lo que deben.

- [ ] **El umbral de separación solo se cambia por SQL** (RF-76 queda 🟡). Está en
      `instituciones.umbral_separacion_min` con default 60 y la lógica lo respeta, pero no
      hay pantalla para configurarlo. Mal elegido produce bloques absurdos: con un valor
      generoso, el docente de la mañana y el mismo docente a la noche terminan en el mismo
      bloque.

---

## Rumbo hacia el producto final

Definido por el cliente. **El orden de esta lista no implica prioridad ni cronograma.**

### 1. Despliegue en la nube

Es el cambio de mayor impacto. Hoy el sistema corre en local sobre XAMPP (RNF-27
contemplaba la migración desde el principio). Salir a internet arrastra:

- TD-008 y TD-009 pasan de teóricas a urgentes.
- Gestión de secretos: hoy las credenciales viven en `application-local.properties`.
- La cámara requiere HTTPS: `getUserMedia` solo funciona en contexto seguro.
- Backups y retención — hoy no hay política definida, y los datos son biométricos.
- Revisión del cifrado en tránsito y de dónde vive la clave AES.

### 2. Base de datos más robusta

El cliente lo pidió explícitamente. Todavía **sin definir el alcance**: puede ir desde
normalización e índices hasta cambio de motor. Si se evalúa PostgreSQL, tener en cuenta
que los `CONSTRAINT pk_xxx` de V001 ya están escritos pensando en un motor que honre los
nombres (TD-001).

### 3. Frontend más fluido

Reemplazar o complementar el renderizado server-side de Thymeleaf por algo con mejor
respuesta percibida. **Sin tecnología definida.** El resto del stack —Java, Spring Boot,
JavaCV, la estrategia de multi-tenancy— **se mantiene**.

### 4. Gráficos en los reportes

Cierra el RF-33. **Enfoque a definir**: del lado del cliente con una librería JS, o del
lado del servidor generando imágenes que también entren al PDF. La restricción de open
source (RNF-18) aplica.

### 5. Acceso móvil para gestión, con la captura anclada al escritorio

Decidido el 2026-08-21. **Revierte la lectura anterior de RNF-23**, que daba la
adaptación a móvil por descartada. Lo que se mantiene en el escritorio es la captura
biométrica; el resto de la gestión pasa a poder hacerse desde un teléfono.

**Por qué la restricción no puede ser por tamaño de pantalla.** La regla real es "solo
desde la PC de secretaría". Resolverla con un `max-width` falla en las dos direcciones:
si la secretaria angosta la ventana queda bloqueada justo en la única máquina que tiene
que funcionar, y una tablet en horizontal pasa igual. El user-agent tampoco sirve, es
falsificable. Por eso se introduce el concepto de **puesto autorizado** (ADR-0015).

Como efecto secundario, la restricción deja de ser una decisión de interfaz y pasa a ser
un control de tratamiento: la captura de datos biométricos ocurre únicamente en equipos
registrados por la institución, que es lo que la Resolución AAIP 255/2022 espera de un
entorno controlado.

**Alcance cerrado:**

| Pantalla | Móvil | Nota |
|---|---|---|
| Inicio | Sí | Ya es fluido |
| Listado del día | Sí | Tabla → tarjetas |
| Justificar ausencia | Sí | Formulario, revisar en angosto |
| Carga manual | Sí | Usa select buscable |
| Reportes | Sí | Tabla → tarjetas; revisar descarga CSV/PDF |
| Docentes: listado y ficha | Sí, consulta | Tabla → tarjetas |
| Pase de asistencia | **No** | Puesto autorizado |
| Registro del rostro | **No** | Puesto autorizado |
| Carreras, materias, comisiones, horarios | No se adapta | Alta de datos pesada. No se bloquea |
| Grilla semanal | No se adapta | 624 px mínimo por sus siete columnas |

**Deuda que esto no resuelve:** los cuatro `@media` de `main.css` siguen siendo
`max-width`. Se irán invirtiendo a `min-width` a medida que se toque cada zona, no en una
pasada aparte: la base ya es fluida por `auto-fit`/`minmax`, así que la dirección de los
breakpoints es cosmética y no justifica una migración con riesgo de regresión.

### 6. Otros

Hay más cosas previstas, todavía sin especificar.

---

## Fuera del rumbo

Para evitar que se proponga:

- **Módulo de auditoría administrativa.** Descartado, tabla eliminada.
- **Asistencia de alumnos.** El sistema es de asistencia docente.
- **Login del docente.** El docente es sujeto pasivo por diseño.
- **Captura biométrica fuera del puesto autorizado.** El pase y el registro del rostro
  se quedan en la PC de secretaría. Lo demás sí va a móvil: ver la línea 5 del rumbo.
- **Cambio del stack backend.** Java + Spring Boot + JavaCV se quedan.

---

## Higiene de documentación

Cosas que hay que arreglar y no dependen de nadie más:

- [ ] **`asistencias/README.md` está desactualizado.** Dice "primera entrega cerrada",
      describe una fase que ya no aplica y lista carpetas de `Documentacion/` que no
      existen (diagramas, manuales, imprimibles).
- [ ] **Links rotos en `CHANGELOG.md`.** Apuntan a `docs/4-arquitectura/adr/`, ruta que
      dejó de existir cuando la documentación salió del repositorio.
- [ ] **`CHANGELOG.md` dice "16 tablas"** en el Sprint 0. Son 15.
- [ ] **`Documentacion/` no está versionada ni respaldada.** Son 14 ADR, la matriz legal
      y los apuntes de defensa dependiendo de que nadie borre una carpeta. De acá a marzo
      de 2027 es mucho tiempo.
- [ ] **Inconsistencia de nombre:** el producto es Visum; el repositorio, el paquete Java
      y la aplicación dicen "asistencias".
- [ ] **Alcance ampliado sin documentar:** la funcionalidad agregada después del
      relevamiento no figura en `Documentacion/2-requerimientos/`.
