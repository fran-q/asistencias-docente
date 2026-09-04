# 06 — Glosario

> **Versión:** 1.0 · **Actualizado:** 2026-08-14 · **Estado:** vigente
> **Se actualiza cuando:** aparece un término nuevo del dominio o cambia el significado
> de uno existente.

El objetivo es que el equipo, el cliente y la documentación usen la misma palabra para la
misma cosa.

---

## Dominio del negocio

**Visum** — Nombre del producto. El repositorio y el paquete Java todavía dicen
"asistencias"; es una inconsistencia conocida.

**Institución** — La entidad educativa. Es a la vez el *tenant* del sistema y un rol de
usuario: la cuenta raíz desde la que se gestionan los administradores.

**Administrador** — Personal designado por la institución que opera el sistema día a día.
Es el usuario real del producto.

**Docente** — Sujeto pasivo. No tiene cuenta ni interfaz: solo se para frente a la
cámara. Su perfil existe para vincular las asistencias.

**Carrera** — Programa académico. Sirve como criterio de agrupación y filtrado.

**Materia** — Asignatura, asociada a una carrera y opcionalmente a un docente titular.

**Comisión** — División de una materia. Una misma materia puede tener varias, cada una
con su propio horario y su docente asignado. El *docente asignado* de la comisión puede
no ser el *docente titular* de la materia.

**Horario** — Bloque semanal de una comisión: día (1 = lunes, ISO 8601), hora de inicio,
hora de fin y tolerancia propia.

**Grilla** — Vista semanal de los horarios de una carrera, en formato de calendario.

**Pase de asistencia** — La pantalla donde la cámara está activa y el sistema marca
asistencia al reconocer a un docente. Es el modo de operación normal del sistema.

**Marca** — Un registro individual de asistencia.

**Tolerancia** — Minutos de margen **alrededor** del horario, hacia los dos lados y con el
mismo valor en los dos extremos (ADR-0018). Se llega en hora hasta `hora_inicio + tolerancia`
y se sale en hora desde `hora_fin - tolerancia`. Se configura **por horario**, no de forma
global. Por defecto 15, topeada a 30 por `MINUTOS_MAXIMOS_DE_TOLERANCIA`.

Hasta el 2026-09-01 el código la aplicaba solo como **anticipo** —abría la ventana antes del
inicio pero clasificaba TARDE apenas pasaba `hora_inicio`—, contra lo que decían el RF-19,
este glosario y el javadoc de `Horario`. Las marcas anteriores a esa fecha conservan el
estado con el que se guardaron: **el histórico no se recalculó**.

**Estado de asistencia** — `PRESENTE`, `TARDE` o `AUSENTE`.

**Método de asistencia** — `AUTOMATICO` (reconocimiento facial) o `MANUAL` (cargado por
un administrador).

**Carga manual** — Registro de asistencia hecho a mano cuando el reconocimiento falla o
no está disponible. Siempre lleva motivo y queda asociada al administrador que la hizo.

**Justificación** — Motivo por el que una ausencia se considera justificada. Opcionalmente
con documento adjunto.

**Ausencia generada** — Las ausencias no se marcan: las deriva un proceso programado a
partir de los horarios cuyo `hora_fin` ya pasó sin marca.

### Marca de salida y bloques

> 🚧 **Implementado por dentro, todavía sin conectar al pase.** Los términos corresponden a
> ADR-0017 y ADR-0018, los dos Aceptados. El esquema, el ciclo de vida del bloque y el cierre
> automático existen y están probados, pero **la pantalla del pase todavía no los usa**: en la
> aplicación corriendo, la marca de salida aún no ocurre.

**Bloque de presencia** — El lapso continuo durante el cual un docente estuvo en la
institución. Agrupa uno o varios horarios consecutivos y tiene exactamente **una entrada y
una salida**, sin importar cuántas clases abarque. Es la unidad de la que se predica la
*permanencia*; el horario sigue siendo la unidad de la que se predica la *asistencia*. No
se usa "turno" para esto: en el sistema educativo argentino turno significa mañana, tarde
o noche.

**Marca de entrada / Marca de salida** — Los dos extremos de un bloque. El sistema deduce
cuál es cuál de su propio estado: sin bloque abierto la marca es entrada; con bloque
abierto y la permanencia mínima cumplida, es salida. **No hay selector de modo en la
pantalla.**

**Umbral de separación** — Minutos de hueco entre dos clases consecutivas que las mantienen
dentro del mismo bloque. Se configura **por institución**. Con separación menor o igual
hay un solo bloque; si la supera, son dos. Agrupa **horarios de la grilla**, no pasadas
frente a la cámara — es la confusión más fácil de cometer con este término.

**Permanencia mínima** — Lapso que tiene que transcurrir desde la entrada para que se
acepte una salida (10 minutos). Evita que quien se queda frente a la cámara después de
entrar reciba su propia salida a los pocos segundos.

**Salida pendiente** — Un bloque que quedó sin salida registrada. Se informa en el panel de
inicio y **se arrastra entre días** hasta que un administrador lo resuelva.

**Hora presumida** — La hora de salida que completa el sistema cuando nadie la registró,
tomando el fin de la última clase del bloque. Se distingue explícitamente de una hora
observada: el sistema la completa para poder imputar la asistencia, pero nunca la hace
pasar por medida. Un cierre por reconocimiento, un cierre manual y una hora presumida
tienen distinto valor probatorio y no pueden verse iguales en un reporte.

**Salida anticipada** — Retirarse antes de `hora_fin` menos la tolerancia. **No cambia el
estado de la asistencia**, que sigue describiendo cómo llegó el docente: se registra en su
propia columna para que un mismo registro pueda decir que llegó tarde y además se fue
antes.

---

## Términos técnicos

**Multi-tenant** — Una sola instancia del sistema sirviendo a varias instituciones, con
los datos aislados entre sí.

**Tenant** — Cada institución. En el código se maneja como `institucionId`.

**Tenant-scoped** — Una entidad que pertenece a una institución y que el filtro de
Hibernate debe restringir.

**Discriminador** — La estrategia de multi-tenancy elegida: una columna `institucion_id`
en la misma base, en vez de bases o esquemas separados.

**Cross-tenant** — Un intento de acceder a datos de otra institución. Se bloquea y se
responde "no encontrado", nunca "no autorizado".

**Baja lógica** — Marcar un registro como inactivo (`activo = false`, `fecha_baja`) en
vez de borrarlo. Es la regla del sistema. Única excepción: la supresión biométrica ARCO.

**LBPH** (*Local Binary Patterns Histograms*) — El algoritmo de reconocimiento facial que
usa el sistema, vía OpenCV. Entrena **un modelo por persona** a partir de varias capturas
de esa misma persona.

**Modelo facial** — El artefacto biométrico de un docente: el modelo LBPH entrenado,
serializado, comprimido con gzip y cifrado. **No es una fotografía y no es reversible a
una imagen.**

**Embedding** — Representación numérica de un rostro. El término aparece en el documento
de requerimientos y en el nombre de la columna `embedding_cifrado`, pero **LBPH no
produce un embedding**: produce un modelo entrenado. Ver el desvío del RF-08 en
`05-trazabilidad.md`.

**Umbral de confianza** — Distancia máxima admitida para dar por buena una
identificación. LBPH devuelve *distancia* (menor es mejor), y el sistema la convierte a
un score 0-1 (mayor es mejor) por conversión lineal (TD-004).

**Margen contra el segundo candidato** — Regla adicional: no alcanza con superar el
umbral, hay que superarlo con distancia suficiente respecto del siguiente candidato más
parecido (ADR-0014).

**Ventana de confirmación** — Mecanismo que exige varios frames coincidentes antes de
marcar, para no registrar por un fotograma suelto (ADR-0013).

**Captura guiada** — El flujo de registro facial que pide poses concretas al docente en
vez de grabar libremente (ADR-0012).

**Idempotencia del pase** — Que reconocer al mismo docente dos veces en la misma clase
produzca una sola marca. Garantizada en tres niveles: UNIQUE en base, verificación en el
service y pausa de 5 segundos en el frontend.

---

## Marco legal

**Ley 25.326** — Ley Nacional de Protección de Datos Personales. Exige consentimiento
libre, expreso e informado para tratar datos personales.

**Resolución AAIP 255/2022** — Incluye explícitamente a los datos biométricos dentro de
la categoría de datos sensibles, que requieren protección reforzada.

**AAIP** — Agencia de Acceso a la Información Pública. El organismo de control.

**Datos sensibles** — Categoría legal reforzada. Los datos biométricos entran ahí.

**Consentimiento biométrico** — La autorización del docente para tratar su rostro.
Versionado (`TextoConsentimiento.VERSION_ACTUAL`), revocable, y con registro forense de
IP y User-Agent. **Sin consentimiento vigente no se registra ni se usa un rostro**: revocarlo
da de baja el modelo facial y lo evicta del cache, así que el docente deja de ser reconocido.
Volver a otorgarlo **no** reactiva el modelo, hay que registrar el rostro de nuevo.

**Derechos ARCO** — Acceso, Rectificación, Cancelación y Oposición sobre los datos
personales. En el sistema: la cancelación es el borrado físico del modelo facial, y la
oposición es la revocación del consentimiento.

**Constancia ARCO** — El PDF que se lleva el docente cuando ejerce un derecho: qué se
hizo, cuándo y quién lo hizo. Es el único artefacto del sistema pensado para salir de él.

**Auditoría forense** — El registro de IP y User-Agent del momento en que se otorgó o
revocó un consentimiento. Es lo que permite acreditar ante la AAIP que hubo una sesión
concreta. **No es el módulo de auditoría administrativa**, que fue descartado.
