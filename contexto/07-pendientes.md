# 07 — Pendientes y rumbo

> **Versión:** 1.6 · **Actualizado:** 2026-10-30 · **Estado:** vigente
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
| TD-008 | Límite de códigos por cuenta, no por origen | Baja en local, media expuesto a internet | **Cerrada** el 2026-10-30 (`FrenoDeOrigenService`) |
| TD-009 | La recuperación de contraseña depende de que haya SMTP | Media | **Cerrada en la instalación autónoma** (ADR-0022, V032); abierta para un despliegue con correo |
| TD-010 | En instalación autónoma, un ADMIN que olvida su contraseña queda afuera | Media (solo instalable) | Aceptada como limitación de la versión instalable |
| TD-011 | El esquema solo aplica sobre MariaDB 10.4 | Baja hoy | **Cerrada para el despliegue** (ADR-0025, V038). Abierta para el instalable, que sigue en 10.4 |

**Cerradas:** TD-002 (driver MySQL reemplazado por el nativo de MariaDB), TD-007 (el
aspecto multi-tenant había quedado inactivo tras la reorganización de paquetes) y TD-012
(lógica que leía `now()` suelto: desde el 2026-10-14 todo lo que decide según la hora sale
de un `Clock`; ver `04-convenciones`, *Servicios*).

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

**TD-011 se cerró para el despliegue el 2026-10-30.** `V001` no aplica de MariaDB 10.6 en adelante:
rechazan que una columna esté a la vez en un `CHECK` y en una foránea con `ON DELETE SET NULL`. Eran
cinco; V038 les sacó la acción y conservó los `CHECK`. Como `V001` sigue sin aplicar y no se edita,
una base nueva sobre 11.4 arranca de `despliegue/base-inicial.sql` (ADR-0025). **El instalable sigue
en 10.4**, que no tiene soporte desde junio de 2024: acotado porque esa base escucha solo en la
máquina, y pasarlo al mismo archivo es lo que queda.

**TD-010 es la contracara de la clave de recuperación.** La clave destraba la cuenta
INSTITUCION, que es la que, perdida, dejaría la instalación inservible. Para una cuenta ADMIN no
hay camino: sin correo no llega ningún código, `habilitar-cambio-password` solo levanta el límite
de 24 horas, y la pantalla que permitía fijarle la contraseña a otro **se eliminó a propósito**
para que nadie conozca la clave con la que entra otra persona. Se acepta como limitación de la
versión instalable: cerrarlo obliga a reintroducir eso mismo que se quitó, y esa decisión se toma
cuando el instalable esté en uso y se sepa cuánto molesta. Mientras tanto, la salida es crear una
cuenta nueva y dar de baja la anterior.

**TD-009** es una deuda de exposición: hoy no molesta porque el despliegue es local y dentro
de la red de la institución. **El día que el sistema esté en internet, se vuelve urgente.**
TD-008 era la otra: se cerró con un freno por dirección, además del de cuenta. **TD-009 quedó
cerrada del lado de la instalación autónoma**, donde la recuperación va por la clave que genera
la configuración inicial (ADR-0022): lo que sigue sin respuesta es el mismo problema en un
despliegue con correo, donde TD-009
necesita un procedimiento de recuperación de la cuenta institucional que no dependa del
correo.

---

## Brechas de requerimientos

Del documento original, lo que falta:

| ID | Qué falta | Estado |
|---|---|---|
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

Cerrada la funcionalidad (ADR-0017, ADR-0018), quedaron cosas que **no se ven en un build
verde** y por eso conviene tenerlas escritas:

- [ ] **Avisarle al equipo administrativo que cambió la clasificación de llegadas.** Con
      tolerancia 15, quien llega 18:05 pasó de TARDE a PRESENTE. Es el efecto buscado, pero
      tienen que enterarse antes de verlo en un reporte, no después. El histórico no se
      recalculó: dos registros iguales pueden tener estados distintos según de qué lado del
      2026-09-01 caigan.

- [ ] **El cambio del pase es JavaScript y no lo cubre ningún test.** Que el backend mande
      `tipoDeMarca` está testeado; que `pase-asistencia.js` pinte el recuadro azul al salir,
      no. El proyecto no tiene infraestructura de tests de JS y no vale la pena montarla por
      esto solo, pero conviene saber que esa línea se rompe en silencio.

- [x] **Los CHECK de V019, V020 y V029 se verificaban a mano.** Automatizado el 2026-10-17:
      `MigracionesIT` los ejercita contra MariaDB, en el CI en cada push, junto con el de V031
      y el índice único de los bloques abiertos. Quedan a mano los `ON DELETE SET NULL` y los
      CHECK de otras tablas (ver `03-modelo-datos.md`).

---

## Decisiones abiertas

- [x] **Con varios equipos (V030), la contraseña institucional sola alcanzaba para mover la
      captura biométrica a otra máquina.** Resuelta el 2026-10-28: autorizar un equipo después
      del primero de la historia de la institución pide un código al correo de la institución,
      también si antes se revocaron todos. En la instalación autónoma no hay correo y no se
      pide. Ahí el riesgo queda aceptado: el sistema solo se alcanza desde la red del edificio,
      y la clave de recuperación no sirve de segundo control porque la regenera cualquiera que
      tenga la contraseña. Si una instalación autónoma se abriera a internet, hay que volver a
      decidirlo. Detalle en ADR-0015, *Después del primero, un código al correo*.

- [ ] **ADR-0016 espera el plazo legal de conservación.** Está implementado desde el
      2026-09-03, pero el propio ADR condiciona su aceptación a poder citar la norma que obliga
      a conservar los registros de asistencia docente, y por cuántos años: una resolución del
      Ministerio de Educación provincial o el reglamento del CENT 35. Sin eso, la retención que
      construye no tiene con qué sostenerse frente a un pedido de supresión.

---

## Rumbo hacia el producto final

Definido por el cliente. **El orden de esta lista no implica prioridad ni cronograma.**

### 1. Despliegue en la nube

Es el cambio de mayor impacto. Hoy el sistema corre en local sobre XAMPP (RNF-27
contemplaba la migración desde el principio).

**Auditado el 2026-10-30. Las correcciones de código ya están hechas** (2026-10-30): zona horaria
fija, control de arranque, freno por origen, proxy, cookies y encabezados, cierre de sesiones al
cambiar la contraseña, interruptor del alta pública, y cache y comparación del reconocimiento. El
perfil es `prod` (ver `02-arquitectura.md`, *Entornos*). **Falta, en este orden:**

1. El servidor (DonWeb, en Argentina), el dominio `.com.ar` y el correo con SPF, DKIM y DMARC. El
   empaquetado ya está, en `despliegue/` (ADR-0026), con su paso a paso.
2. Una puesta a prueba **solo con datos ficticios**: dos instituciones, teléfonos reales, webcam
   real, carga con varias cámaras, y restaurar un respaldo de verdad.
3. **Antes de cargar un rostro real:** una versión nueva del consentimiento (la vigente dice que
   los datos no se comparten con terceros, y el proveedor del servidor es un encargado de
   tratamiento), política de privacidad, procedimiento ARCO, cuánto duran los respaldos (un dato
   suprimido sigue en las copias viejas), y la consulta sobre la inscripción de la base ante la
   AAIP, que no se pudo confirmar. Si el servidor queda fuera de los países adecuados, además
   cláusulas modelo o consentimiento expreso (Ley 25.326, art. 12).

**Queda abierto de la auditoría, sin bloquear la puesta a prueba:**

- **El kiosco ya no está acotado por la red.** Su cookie sirve desde cualquier lugar, y quien la
  tenga puede mandar una foto en lugar de usar la cámara. RF-38 (vivacidad) sigue fuera de alcance
  y su motivo vale para el pase atendido; para el kiosco falta poder restringir cada equipo a la
  red de la institución.
- **Se puede saber si una cuenta existe midiendo el tiempo de `/recuperar`:** con cuenta manda un
  correo, sin cuenta no. La respuesta es idéntica y la demora no. Se resuelve mandando el correo
  por fuera del pedido.
- **`imdecode` no limita las dimensiones de la imagen**, y en la aplicación el JSON de la cámara no
  tiene tope de tamaño. Solo se alcanza con sesión o con la credencial de un equipo, y Caddy corta
  en 25 MB.
- **El alta con la pública cerrada es a mano:** se abre `ALTA_PUBLICA`, se crea y se vuelve a
  cerrar. Una invitación por institución es el paso siguiente si llegan a ser varias.
- **No hay forma de rotar la clave biométrica.** Cambiarla deja los modelos ilegibles y obliga a
  registrar los rostros de nuevo.
- **Objetivos táctiles chicos.** Casi todas las pantallas tienen algún control de menos de 32 px
  de alto; falta revisarlos en un teléfono real.
- **Carga real de identificación.** Medido con imágenes sintéticas; falta repetirlo con varias
  cámaras y rostros reales, en el servidor. La detección con Haar comparte un solo clasificador
  entre hilos: 210 detecciones simultáneas no dieron error, pero sin rostros en la imagen.

Salir a internet arrastra además:

- TD-009 pasa de teórica a urgente.
- La cámara requiere HTTPS: `getUserMedia` solo funciona en contexto seguro.
- Un servidor Linux necesita GTK 2 (`libgtk2.0-0t64` en Ubuntu 24.04) aunque no tenga
  pantalla: el reconocedor LBPH carga el módulo de ventanas de OpenCV, enlazado contra GTK 2,
  y sin él el reconocimiento facial no arranca. El CI ya lo instala; la imagen del servidor
  o del contenedor tiene que traerlo (ADR-0007, aprendizaje 9).
- Backups y retención — hoy no hay política definida, y los datos son biométricos.
- Dónde vive la clave AES: separada de los respaldos, o una copia alcanza para leerlo todo.
- **Una sola instancia, por diseño.** Las sesiones, los frenos y los modelos viven en memoria, así
  que el sistema escala hacia arriba y no hacia los costados. Reiniciar cierra las sesiones: los
  despliegues van fuera del horario de clases.

### 2. Base de datos más robusta

El motivo concreto y medido que había, TD-011, se resolvió con ADR-0025: el despliegue corre sobre
MariaDB 11.4, con parches. Lo que siga bajo este punto ya es una mejora y no una condición.

El cliente lo pidió explícitamente. Todavía **sin definir el alcance**: puede ir desde
normalización e índices hasta cambio de motor. Si se evalúa PostgreSQL, tener en cuenta
que los `CONSTRAINT pk_xxx` de V001 ya están escritos pensando en un motor que honre los
nombres (TD-001).

### 3. Frontend más fluido

Reemplazar o complementar el renderizado server-side de Thymeleaf por algo con mejor
respuesta percibida. **Sin tecnología definida.** El resto del stack —Java, Spring Boot,
JavaCV, la estrategia de multi-tenancy— **se mantiene**.

### 4. Gráficos en los reportes — hecho el 2026-09-27

Cerró el RF-33. **Enfoque elegido**: del lado del servidor y sin librería, en su propia
pantalla (`/reportes/graficos`). El detalle está en `05-trazabilidad.md`.

Quedó afuera a propósito: **los gráficos no entran al PDF**. Meterlos obligaría a generarlos
como imagen en el servidor —otra dependencia— y el PDF ya cumple su función, que es la hoja
de detalle que se archiva. Si alguna vez hace falta, el cálculo ya está separado del dibujo.

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

- [x] **`asistencias/README.md` está desactualizado.** Reescrito el 2026-10-14. Dice "primera entrega cerrada",
      describe una fase que ya no aplica y lista carpetas de `Documentacion/` que no
      existen (diagramas, manuales, imprimibles).
- [x] **Links rotos en `CHANGELOG.md`.** Pasados a texto el 2026-10-14. Apuntan a `docs/4-arquitectura/adr/`, ruta que
      dejó de existir cuando la documentación salió del repositorio.
- [x] **`CHANGELOG.md` dice "16 tablas"** en el Sprint 0. Son 15. Corregido el 2026-10-14.
- [ ] **`Documentacion/` no tiene respaldo.** Desde el 2026-10-14 es un repositorio git
      propio, así que tiene historial, pero vive solo en esta máquina: falta el remoto
      privado. Son todos los ADR, la matriz legal y los apuntes de defensa dependiendo de
      que nadie borre una carpeta. De acá a marzo de 2027 es mucho tiempo.
- [ ] **Inconsistencia de nombre:** el producto es Visum; el repositorio, el paquete Java
      y la aplicación dicen "asistencias".
- [ ] **Alcance ampliado sin documentar:** la funcionalidad agregada después del
      relevamiento no figura en `Documentacion/2-requerimientos/`.
