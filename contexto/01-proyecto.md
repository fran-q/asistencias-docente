# 01 — El proyecto

> **Versión:** 1.0 · **Actualizado:** 2026-08-14 · **Estado:** vigente
> **Se actualiza cuando:** cambia el alcance, el destinatario o la definición del producto.

---

## El problema

En los institutos terciarios y universidades de Tierra del Fuego, la asistencia docente
se controla con una planilla de papel donde cada docente firma al llegar. Eso trae
cuatro problemas concretos:

- **No es confiable.** Depende de la buena fe del docente y de que alguien verifique a mano.
- **Se puede falsificar.** Un tercero puede firmar por otro.
- **No hay estadística en tiempo real.** Para saber cuántas clases se dictaron el mes
  pasado hay que digitalizar planillas a posteriori.
- **Es lento y se equivoca.** La digitalización manual introduce errores.

## La solución

Un sistema web multi-tenant que registra la asistencia docente por reconocimiento
facial. Una cámara web fija en sala de profesores o secretaría; el docente se para
enfrente dos o tres segundos; el sistema lo identifica, cruza la hora con los horarios
cargados, determina qué clase le corresponde y registra la marca con su estado.

La suplantación deja de ser posible y el dato queda disponible en el momento.

## Quién lo usa

| Actor | Qué hace | Accede al sistema |
|---|---|---|
| **Institución** (cuenta raíz) | Da de alta y de baja a los administradores de su institución. Acceso total a los datos de su institución. | Sí |
| **Administrador** | Opera el sistema día a día: docentes, estructura académica, registro facial, carga manual, reportes. | Sí |
| **Docente** | Se para frente a la cámara. Nada más. | **No.** No tiene login. Su perfil existe para vincular las asistencias. |

**El cliente es el equipo administrativo.** Es quien sufre el problema y quien va a
convivir con la herramienta varias horas por día. Todas las decisiones de usabilidad se
resuelven a su favor.

Es un proyecto académico (Prácticas Profesionalizantes III, CENT 35), pero se trabaja
como si el cliente fuera real. La documentación tiene dos lectores: ese equipo
administrativo y el tribunal evaluador.

## Escala prevista

- 200 a 400 docentes por institución.
- Varias instituciones sobre una única instalación, con aislamiento total de datos.
- Arquitectura pensada para crecer sin rediseño.

## Estado actual

**Prototipo funcional completo.** Cubre el ciclo entero: alta de institución, gestión de
administradores, estructura académica (carreras, materias, comisiones, horarios con
grilla semanal), docentes, consentimiento biométrico, registro facial guiado,
identificación en vivo, pase de asistencia automático, generación de ausencias, carga
manual, justificaciones y reportes exportables a CSV y PDF.

Se desarrolló en seis sprints entre abril y junio de 2026, más una etapa posterior de
revisión y endurecimiento. Hoy corre en entorno local sobre XAMPP.

**Horizonte:** producto final para marzo de 2027. Entre hoy y esa fecha van a cambiar
varias cosas — ver `07-pendientes.md`. **No hay fechas de entrega intermedias
definidas**, y no corresponde proponerlas todavía.

## Dentro del alcance

- Registro automático de asistencia docente por reconocimiento facial.
- Carga manual como respaldo, con motivo y responsable registrados.
- Gestión de la estructura académica: carreras, materias, comisiones, horarios.
- Gestión de docentes y de su modelo facial.
- Consentimiento biométrico versionado, revocable, con auditoría forense.
- Derechos ARCO: supresión del dato biométrico con constancia en PDF.
- Reportes filtrados con exportación a CSV y PDF.
- Multi-tenancy con aislamiento total.
- Verificación de correo y recuperación de contraseña por código de un solo uso.

## Fuera del alcance

Esto es tan importante como lo anterior: son cosas que **se evaluaron y se dejaron
afuera a propósito**. No son olvidos.

| Qué | Por qué |
|---|---|
| **Asistencia de alumnos** | El sistema registra docentes. La tabla `comisiones.cupo` existe pero no hay padrón de alumnos. |
| **Módulo de auditoría administrativa** (RF-34 a RF-36) | Se diseñó la tabla en V001, nunca se escribió una fila, y se eliminó en V009. Pospuesto sin fecha. La auditoría forense del consentimiento es otra cosa y sí existe. |
| **Login del docente** | El docente es sujeto pasivo. No tiene cuenta ni interfaz. |
| **Captura biométrica fuera del puesto de secretaría** | El pase de asistencia y el registro del rostro solo funcionan desde un puesto autorizado. No es una restricción de tamaño de pantalla: es de equipo. Ver RNF-23 y ADR-0015. |
| **Notificaciones al docente** | El correo se usa solo para verificación de cuenta y recuperación de contraseña de los usuarios administrativos. |
| **Integración con sistemas de gestión académica existentes** | No se relevó ninguno. |

## Marco legal

El sistema trata datos biométricos, que son datos sensibles. Rige:

- **Ley Nacional N° 25.326** de Protección de Datos Personales.
- **Resolución AAIP N° 255/2022**, que incluye explícitamente a los datos biométricos
  dentro de la categoría de datos sensibles.

Consecuencias operativas, no declarativas:

1. Consentimiento libre, expreso e informado **antes** de capturar el rostro. Versionado
   y revocable.
2. **No se almacenan fotografías.** Solo el modelo derivado, comprimido y cifrado.
3. El docente puede pedir el borrado de su dato biométrico y llevarse una constancia
   escrita de qué se borró, cuándo y quién lo hizo.
4. Registro forense (IP y User-Agent) del momento en que se otorgó y se revocó el
   consentimiento, para poder acreditarlo ante la AAIP.

El detalle está en `Documentacion/3-legal/` y en el ADR-0005.
