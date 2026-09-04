# 05 — Trazabilidad de requerimientos

> **Versión:** 1.2 · **Actualizado:** 2026-08-21 · **Estado:** vigente
> **Se actualiza cuando:** se implementa, se modifica o se descarta un requerimiento.
>
> Fuente de los requerimientos: `Requerimientos.docx` (abril 2026),
> `Documentacion/2-requerimientos/`. Fuente de los estados: el código, verificado archivo
> por archivo.

**Leyenda:** ✅ implementado · ⚠ implementado con desvío · 🟡 parcial · ❌ no implementado
· 🚫 descartado a propósito

---

## Resumen

> ⚠ **Los conteos de esta tabla están desactualizados y no hay que usarlos.** El documento de
> requerimientos tiene RF-01 a RF-83 y RNF hasta RNF-50; esta matriz cubre RF-01 a RF-37 más
> la sección de marca de salida (RF-74 a RF-83). Faltan de la matriz RF-38 a RF-73, varios de
> ellos ya implementados. Reconciliarla es una tarea pendiente en sí misma: hay que verificar
> el estado de cada uno contra el código, no darlo por hecho porque figure en el documento.

| | RF (37) | RNF (27) |
|---|---|---|
| ✅ Implementado | 30 | 22 |
| ⚠ Con desvío | 2 | — |
| 🟡 Parcial | 1 | 5 |
| ❌ No implementado | 1 | — |
| 🚫 Descartado | 3 | — |

---

## Autenticación y seguridad

| ID | Requerimiento | Estado | Dónde vive |
|---|---|---|---|
| RF-01 | Inicio de sesión | ✅ | `config/SecurityConfig`, `seguridad/CargadorDeUsuarios`, `HomeController` → `/login` |
| RF-02 | Gestión de contraseñas | ✅ | `CuentaController` → `/mi-cuenta/password`; `RecuperacionController` → `/recuperar/codigo` |
| RF-03 | Control de acceso por rol | ✅ | `SecurityConfig`, `model/Rol`, `seguridad/UsuarioAutenticado` |
| RF-04 | Aislamiento multi-tenant | ✅ | `TenantContext`, `TenantInterceptor`, `TenantFilterAspect`, `BaseTenantEntity` |

## Gestión de instituciones

| ID | Requerimiento | Estado | Dónde vive |
|---|---|---|---|
| RF-05 | Alta de institución | ✅ | `AltaInstitucionController`, `AltaInstitucionService` (ADR-0010) |
| RF-06 | CRUD de administradores | ✅ | `UsuarioController` → `/usuarios`, `UsuarioService` |

## Gestión de docentes

| ID | Requerimiento | Estado | Dónde vive |
|---|---|---|---|
| RF-07 | CRUD de docentes | ✅ | `DocenteController` → `/docentes`, `FichaDocenteController` |
| RF-08 | Registro del modelo facial | ⚠ | `RegistroFacialController` → `/docentes/{id}/rostro/registrar`, `ModeloFacialService`, `CifradoBiometricoService` |
| RF-09 | Re-registro facial | ✅ | `ModeloFacialService.registrar` (registra y re-registra); `modelos_faciales.activo` / `fecha_baja` |
| RF-10 | Consentimiento informado | ✅ | `ConsentimientoController`, `ConsentimientoBiometricoService`, `TextoConsentimiento` |

**Desvío RF-08.** El requerimiento habla de *embeddings*. La implementación usa **LBPH**,
que no genera un embedding vectorial sino un modelo entrenado por persona, serializado
como YAML, comprimido con gzip y cifrado con AES-256-GCM. La columna se llama
`embedding_cifrado` por herencia del diseño original. El requerimiento sustantivo —*no
almacenar fotografías, solo información biométrica derivada*— **se cumple**. Justificación
en ADR-0007.

## Gestión académica

| ID | Requerimiento | Estado | Dónde vive |
|---|---|---|---|
| RF-11 | Gestión de carreras | ✅ | `CarreraController` → `/carreras` |
| RF-12 | Gestión de materias | ✅ | `MateriaController` → `/materias` |
| RF-13 | Gestión de comisiones | ✅ | `ComisionController` → `/comisiones` |
| RF-14 | Gestión de horarios | ✅ | `HorarioController` → `/horarios`, `GrillaController` (grilla semanal), `ManejadorDeColisiones` |

## Reconocimiento facial y toma de asistencia

| ID | Requerimiento | Estado | Dónde vive |
|---|---|---|---|
| RF-15 | Captura de video en vivo | ✅ | `static/js/facial/pase-asistencia.js`, `facial/registro-facial.js` (`getUserMedia`) |
| RF-16 | Detección e identificación facial | ✅ | `DeteccionRostroService`, `IdentificacionFacialService`, `MotorLbphService` |
| RF-17 | Registro automático de asistencia | ✅ | `PaseAsistenciaController` → `POST /asistencia/pase/marcar`, `PaseAsistenciaService` |
| RF-18 | Determinación automática de materia y horario | ✅ | `PaseAsistenciaService` |
| RF-19 | Clasificación del estado | ✅ | `AsistenciaService.calcularEstado`, `Horario.llegadaEnHora`, `GeneradorAusenciasService`. Segundo desvío cerrado por ADR-0018 |
| RF-20 | Retroalimentación visual | ✅ | `templates/asistencia/pase.html` + `facial/pase-asistencia.js`: entrada en verde con «ENTRA · Nombre», salida en azul con «SALE · Nombre». El azul quedó libre al desaparecer «ya estaba marcado» del flujo normal |
| RF-21 | Registro de metadatos | ✅ | Tabla `asistencias` |

**Desvío RF-19.** El requerimiento fija una tolerancia global de 15 minutos. La
implementación la hace **configurable por horario** (`horarios.tolerancia_min`, default
15, rango 0-120). Es más flexible que lo pedido, no menos. Además: la ausencia **no se
marca, se genera** — `GeneradorAusenciasService` corre por cron y materializa las
ausencias de horarios cuyo `hora_fin` ya pasó sin marca.

**Segundo desvío RF-19, detectado el 2026-08-31.** El requerimiento dice "Presente (dentro
de la tolerancia), Llegada Tarde (pasada la tolerancia)", y así lo describen también el
glosario y el javadoc de `Horario`. **El código hace otra cosa**:
`AsistenciaService.calcularEstado` clasifica TARDE apenas pasa `hora_inicio`, y la
tolerancia solo abre la ventana de admisión *antes* del inicio. Con tolerancia 15, quien
llega 18:05 a una clase de 18:00 queda TARDE cuando el requerimiento dice PRESENTE.

El desvío venía de ADR-0008 §1, que lo eligió deliberadamente citando un pedido del
cliente en entrevista — opuesto al RF-19 escrito. No se detectó antes porque el único test
de clasificación usa las 18:30, que es TARDE bajo cualquiera de las dos lecturas.
**Resuelto el 2026-09-01.** ADR-0018 lo cierra a favor del requerimiento acordado y ya está
implementado: `Horario.llegadaEnHora` y `Horario.salidaEnHora` aplican la tolerancia hacia los
dos lados con el mismo número de minutos, y `MINUTOS_MAXIMOS_DE_ANTICIPO` pasó a llamarse
`MINUTOS_MAXIMOS_DE_TOLERANCIA` porque ahora topea los dos extremos.

**El histórico no se recalcula**: las marcas anteriores conservan el estado con el que se
guardaron. Dos registros iguales pueden tener estados distintos según de qué lado del
2026-09-01 caigan, y eso es un cambio de criterio, no un error del sistema.

⚠ **Falta avisarle al equipo administrativo.** Con tolerancia 15, quien llega 18:05 pasa de
TARDE a PRESENTE. Es el efecto buscado, pero tienen que enterarse antes de verlo en un
reporte.

## Marca de salida y bloque de presencia

> ✅ **Completo.** Requerimientos incorporados el 2026-08-31, ADR-0017 y ADR-0018 Aceptados.
> V019 y V020 aplicadas y verificadas contra MariaDB 10.4.32, y `MigracionesIT` las vuelve a
> aplicar de cero en cada build. Los reportes muestran minutos dictados sobre programados
> (`AsistenciaReporteRowDto.minutosEfectivos`), en pantalla, CSV y PDF.
>
> ⚠ **Sin cubrir por tests:** el cambio del pase es JavaScript y el proyecto no tiene tests de
> JS. Que el backend mande `tipoDeMarca` sí está testeado; que el JS lo pinte distinto, no.

| ID | Requerimiento | Estado | Dónde va a vivir |
|---|---|---|---|
| RF-74 | Registro de la salida por reconocimiento facial | ✅ | `PaseAsistenciaService.pasar` → `BloquePresenciaService.registrar`; `POST /asistencia/pase/marcar` |
| RF-75 | Bloque de presencia | ✅ | `ResolutorDeBloquesService.agrupar`, `BloqueDeHorarios`, `BloquePresenciaService`, tabla `bloques_presencia` (V019) |
| RF-76 | Umbral de separación por institución | 🟡 | `instituciones.umbral_separacion_min` (V019), leído por `ResolutorDeBloquesService`. **Falta**: pantalla para configurarlo; hoy solo se cambia por SQL |
| RF-77 | Permanencia mínima entre entrada y salida | ✅ | `BloquePresenciaService`, `app.asistencia.permanencia-minima-min` (10) |
| RF-78 | Clasificación de la salida | ✅ | `Horario.salidaEnHora`, `BloquePresenciaService.clasificarSalida`, columna `estado_salida` |
| RF-79 | Aviso de salidas pendientes | ✅ | `PanelInicioService.pendientes` los anuncia **primero** (son lo único que hay que resolver hoy), con enlace a `/asistencias/bloques/pendientes` |
| RF-80 | La asistencia no depende de la marca de salida | ✅ | `BloquePresenciaService.cerrarBloquesVencidos`, llamado por `GeneradorAusenciasService` antes de generar ausencias |
| RF-81 | Imputación por permanencia efectiva | ✅ | `AsistenciaService.imputarDelBloque`, `BloquePresenciaService.clasesCubiertas`, en el cierre por rostro y en el del job |
| RF-82 | Cierre biométrico exige consentimiento vigente | ✅ | `BloquePresenciaService.registrar`, primera guarda del método |
| RF-83 | Cierre manual del bloque | ✅ | `BloquePresenciaService.cerrarManualmente`, `BloquePresenciaController`, `bloques-pendientes.html`, columnas de V020 |

**Fuga de consentimiento, cerrada el 2026-09-01.** Revocar el consentimiento marcaba
`vigente = false` pero **no tocaba el modelo facial**, y el pase compara contra los modelos
*activos* del tenant sin mirar el consentimiento: un docente que revocaba seguía siendo
reconocido y seguía recibiendo marcas automáticas. Era una violación de la regla dura
—sin consentimiento vigente no se registra **ni se usa** un rostro— y del RNF-13.

`ConsentimientoBiometricoService.revocar` ahora da de baja el modelo activo y lo evicta del
cache, en ese orden, igual que la supresión ARCO. Sigue siendo **baja lógica**: revocar es el
derecho de oposición y no obliga a suprimir; el borrado físico es cancelación y tiene su
propio camino. **Volver a otorgar el consentimiento no reactiva el modelo**: hay que registrar
el rostro de nuevo, y el panel de inicio ya lista a los docentes sin modelo facial.

**Qué NO cambia.** El pipeline de registro facial no se toca en absoluto, y del pipeline de
identificación quedan intactos los pasos 1 a 5: detección, comparación LBPH, umbral y
margen (ADR-0014) y ventana de confirmación (ADR-0013). La salida se identifica con el
mismo modelo ya entrenado y el mismo criterio que la entrada. Todo el cambio vive del cruce
con horarios para abajo.

## Carga manual

| ID | Requerimiento | Estado | Dónde vive |
|---|---|---|---|
| RF-22 | Carga manual de asistencia | ✅ | `AsistenciaController` → `/asistencias/manual/nueva` |
| RF-23 | Motivo de carga manual | ✅ | Catálogo `motivos_carga_manual` (seed en V001) + `detalle_adicional` |
| RF-24 | Trazabilidad de carga manual | ✅ | Tabla `asistencias_manuales` (usuario, motivo, fecha) |

## Gestión de ausencias

| ID | Requerimiento | Estado | Dónde vive |
|---|---|---|---|
| RF-25 | Clasificación de ausencias | ✅ | Tabla `justificaciones_ausencia` |
| RF-26 | Justificación de ausencias | ✅ | `AsistenciaController` → `/asistencias/{id}/justificar` |

## Reportes

| ID | Requerimiento | Estado | Dónde vive |
|---|---|---|---|
| RF-27 | Reporte por docente | ✅ | `ReporteController` → filtro `docenteId`. Desde 2026-09-02 incluye **minutos dictados sobre programados** |
| RF-28 | Reporte por materia | ✅ | filtro `materiaId` |
| RF-29 | Reporte por carrera | ✅ | filtro `carreraId` |
| RF-30 | Filtros avanzados | 🟡 | `ReporteFiltroDto`: `desde`, `hasta`, `docenteId`, `materiaId`, `carreraId`, `estado`, `metodo` |
| RF-31 | Exportación a PDF | ✅ | `GET /reportes/pdf`, `ReportePdfService` (OpenPDF) |
| RF-32 | Exportación a Excel (.xlsx) | ⚠ | `GET /reportes/csv` |
| RF-33 | Visualizaciones gráficas | ❌ | — |

**RF-30 parcial.** Están el rango de fechas y los filtros por entidad, estado y método.
**No hay filtro por día de la semana** (el día aparece como columna del resultado, no
como criterio) ni selector de "período" predefinido; los meses se cubren con el rango de
fechas. Default: mes actual hasta hoy.

**Horas efectivas (2026-09-02).** Las tres vistas del reporte suman `hora_salida`, si esa hora
fue presumida, y los minutos dictados contra los programados. Los minutos son la
**intersección** entre la permanencia del docente y la franja de la clase, no su permanencia
total: un docente con tres clases seguidas tiene un solo bloque, y contar la permanencia
completa en cada una diría que dictó el triple.

**Vacío y cero no son lo mismo** y el reporte no los muestra igual: cero dice que no dio la
clase, vacío que de esa fila no hay dato — marcas anteriores a V019, cargas manuales sin
bloque, o un docente todavía adentro. En una planilla esa diferencia decide si el promedio de
horas es una mentira.

**Desvío RF-32.** La exportación es **CSV con separador `;` y BOM UTF-8**, no `.xlsx`
real. Abre directo en Excel con acentos correctos y se sigue trabajando en planilla, que
es el uso previsto. No es un archivo Excel nativo: no lleva formato, fórmulas ni hojas.

**RF-33 no implementado.** No hay librería de gráficos en el proyecto. Es una brecha
abierta y está en el rumbo hacia el producto final — ver `07-pendientes.md`.

**Tope de filas.** El reporte trae hasta `maxFilas` y la pantalla avisa cuando el
resultado quedó truncado. Un reporte cortado en silencio se lee como un reporte completo.

## Auditoría

| ID | Requerimiento | Estado |
|---|---|---|
| RF-34 | Registro de auditoría | 🚫 |
| RF-35 | Historial de acciones | 🚫 |
| RF-36 | Consulta de auditoría | 🚫 |

**Descartado a propósito.** La tabla `auditoria` se diseñó en V001 y nunca se escribió
una fila. Se eliminó en V009. El módulo queda pospuesto sin fecha, y cuando se retome
habrá que diseñarlo con los requerimientos de ese momento.

**No confundir:** la auditoría forense del consentimiento biométrico (IP y User-Agent del
otorgamiento y de la revocación) **sí existe y es obligatoria** por Ley 25.326. Está en
`consentimientos_biometricos` y en `codigos_verificacion.ip_solicitud`.

## Dashboard

| ID | Requerimiento | Estado | Dónde vive |
|---|---|---|---|
| RF-37 | Panel de inicio | ✅ | `PanelInicioService`, `PanelInicioDto` |

Tres bloques: qué está corriendo ahora (con tope de clases mostradas), el día en números,
y qué sigue (hasta tres clases). El reloj es inyectable para que los tests no dependan de
la hora a la que se corren.

---

## Requerimientos no funcionales

### Rendimiento

| ID | Requerimiento | Estado | Nota |
|---|---|---|---|
| RNF-01 | Reconocimiento ≤ 3 s | 🟡 | Funciona dentro de ese margen en uso normal. Medición formal en `Documentacion/5-informes/` (calibración del umbral) |
| RNF-02 | Carga web < 2 s | 🟡 | No medido formalmente |
| RNF-03 | Reportes < 10 s | 🟡 | Mitigado con tope de filas. Sin paginación real (TD-006) |

### Escalabilidad

| ID | Requerimiento | Estado | Nota |
|---|---|---|---|
| RNF-04 | 200-400 docentes por institución | 🟡 | Soportado por diseño. No probado a esa escala con datos reales |
| RNF-05 | Multi-tenancy sin instancias separadas | ✅ | Discriminador por `institucion_id` (ADR-0002) |

### Seguridad

| ID | Requerimiento | Estado | Dónde vive |
|---|---|---|---|
| RNF-06 | Cifrado de contraseñas | ✅ | BCrypt vía Spring Security |
| RNF-07 | Protección de datos biométricos | ✅ | AES-256-GCM (Spring Security Crypto), gzip previo |
| RNF-08 | No almacenamiento de imágenes | ✅ | Los frames se descartan tras el entrenamiento |
| RNF-09 | Sesiones seguras | ✅ | Timeout 30 min configurable, CSRF activo y testeado |
| RNF-10 | Aislamiento de datos | ✅ | Cuatro capas de defensa. Test de integración con dos tenants |

### Cumplimiento legal

| ID | Requerimiento | Estado | Dónde vive |
|---|---|---|---|
| RNF-11 | Ley 25.326 | ✅ | ADR-0005, `Documentacion/3-legal/` |
| RNF-12 | Resolución AAIP 255/2022 | ✅ | Ídem |
| RNF-13 | Consentimiento informado | ✅ | `TextoConsentimiento` versionado, `ConsentimientoBiometricoService` |
| RNF-14 | Derechos ARCO | ✅ | `ModeloFacialService.suprimirDatosBiometricos` (DELETE físico) + `ConstanciaArcoService` (constancia en PDF) |

### Tecnología y arquitectura

| ID | Requerimiento | Estado |
|---|---|---|
| RNF-15 | Backend en Spring Boot | ✅ Spring Boot 3.5.14, Java 21 |
| RNF-16 | Reconocimiento facial en Java | ✅ JavaCV 1.5.11 + OpenCV 4.10.0 |
| RNF-17 | Aplicación web sin instalar nada | ✅ Thymeleaf server-side |
| RNF-18 | Open source | ✅ Incluye la elección de OpenPDF sobre alternativas con licencia |
| RNF-19 | Base de datos relacional con multi-tenancy | ✅ MariaDB + discriminador |
| RNF-20 | Cámara USB vía `getUserMedia` | ✅ MediaDevices API |

### Usabilidad

| ID | Requerimiento | Estado |
|---|---|---|
| RNF-21 | Diseño minimalista | ✅ |
| RNF-22 | Modo oscuro y claro | ✅ `static/js/comun/tema.js`, oscuro por defecto |
| RNF-23 | Optimización para escritorio | 🔄 **Reinterpretado.** Escritorio para la captura biométrica; móvil para gestión y consulta. Ver nota abajo |
| RNF-24 | Retroalimentación clara | ✅ Toasts, modales de confirmación, errores de integridad legibles (ADR-0011) |

> **Nota sobre RNF-23.** El requerimiento original decía "optimización para escritorio", y
> hasta 2026-08-21 se leyó como "sin adaptación a móvil". Esa lectura se revisó: lo que
> tiene que quedar en el escritorio es la **captura biométrica**, no el sistema entero.
>
> - **Solo escritorio, en un puesto autorizado:** pase de asistencia, registro del rostro
>   y el endpoint de reconocimiento. No por tamaño de pantalla —la secretaria puede
>   angostar la ventana— sino por equipo designado. Ver ADR-0015.
> - **También en móvil:** listado del día, justificación, carga manual, reportes y
>   consulta de docentes.
> - **Sigue siendo de escritorio, sin bloqueo:** la carga académica (carreras, materias,
>   comisiones, horarios) y la grilla semanal. Es alta de datos pesada, y la grilla
>   necesita 624 px como mínimo por sus siete columnas. No se bloquea, simplemente no se
>   adapta.

### Mantenibilidad y despliegue

| ID | Requerimiento | Estado |
|---|---|---|
| RNF-25 | Desarrollo incremental | ✅ Seis sprints con tag de cierre + etapa de revisión |
| RNF-26 | Código documentado | ✅ Convención de comentarios unificada en 181 archivos |
| RNF-27 | Despliegue local, migrable a la nube | 🟡 Corre en local sobre XAMPP. La migración a la nube es objetivo del producto final |

---

## Funcionalidad fuera del documento original

Esto está implementado pero **no figura en `Requerimientos.docx`**. Necesita entrar
formalmente al alcance acordado.

| Qué | Dónde vive | Por qué se agregó |
|---|---|---|
| Verificación de correo por código de un solo uso | `VerificacionCuentaService`, `CodigoVerificacionService`, `codigos_verificacion` | ADR-0009 |
| Recuperación de contraseña por mail | `RecuperacionController`, `NotificadorEmailService` | Antes, si el superadmin olvidaba la clave, la institución quedaba sin acceso |
| Bloqueo de cuenta hasta verificar el correo | `VerificacionInterceptor` | ADR-0010 |
| Límite de envío de códigos | `FrenoDeEnviosService` | Cinco por hora y por cuenta (TD-008) |
| Constancia ARCO en PDF | `ConstanciaArcoService` | Ejercer un derecho y no poder demostrarlo es casi no poder ejercerlo |
| Supresión física del dato biométrico | `ModeloFacialService.suprimirDatosBiometricos` | Derecho de cancelación |
| Generación automática de ausencias | `GeneradorAusenciasService` (cron) | Las ausencias no se marcan, se derivan |
| Captura guiada por poses | `CalidadCapturaService`, `EtapaCaptura` | ADR-0012 |
| Ventana de confirmación del pase | `VentanaConfirmacionService` | ADR-0013 — evita marcar por un frame suelto |
| Margen contra el segundo candidato | `MotorLbphService` | ADR-0014 |
| Grilla semanal por carrera | `GrillaController`, `GrillaService` | Visualización de horarios |
| Validación de superposición de horarios | `ManejadorDeColisiones` | Evita cargar dos clases encimadas |
| Alta autogestionada de institución | `AltaInstitucionController` | ADR-0010 |
| Puestos de captura autorizados | `PuestoCapturaService`, `PuestoCapturaInterceptor`, `puestos_captura` | ADR-0015 — la captura biométrica solo desde equipos registrados. Habilita el acceso móvil al resto |
