# 01 — Definición de Requerimientos

| | |
|---|---|
| **Proyecto** | Sistema de Asistencias Digital con Reconocimiento Facial |
| **Documento** | 01 — Requerimientos Funcionales y No Funcionales |
| **Materia** | Prácticas Profesionalizantes III — CENT35 |
| **Ubicación** | Provincia de Tierra del Fuego, Argentina |
| **Autor** | Francisco Quiroga |
| **Versión** | 1.0 |
| **Fecha** | Junio 2026 |

---

## 1. Introducción

Este documento define los **requerimientos funcionales y no funcionales** del
Sistema de Asistencias Digital con Reconocimiento Facial, destinado a
universidades e institutos terciarios de la provincia de Tierra del Fuego.
El sistema reemplaza el registro manual de asistencia docente (planillas en
papel y firma) por un flujo automatizado mediante reconocimiento facial, con
gestión administrativa, reportes y cumplimiento de la normativa de protección
de datos biométricos.

El detalle de contexto, objetivos y alcance entregado en la primera versión
se desarrolla en el documento **02 — Definición del Alcance**. El estado de
implementación de cada requerimiento (Implementado / Parcial / Backlog) se
detalla en ese mismo documento.

---

## 2. Actores y Roles del Sistema

El sistema contempla tres tipos de actores con distintos niveles de acceso:

| Rol | Descripción | Acceso |
|---|---|---|
| **Institución (Superadministrador)** | Cuenta raíz de la entidad educativa. | Crear, modificar y dar de baja administradores de su institución. Acceso total a los datos de su institución. |
| **Administrador** | Personal designado para operar el sistema día a día. | CRUD de docentes, registro facial, gestión académica, carga manual de asistencia y reportes. |
| **Docente** | Sujeto pasivo. No interactúa con la interfaz. | Sin acceso. Solo se posiciona frente a la cámara; su perfil existe para vincular asistencias. |

---

## 3. Requerimientos Funcionales

### 3.1. Módulo de Autenticación y Seguridad

| ID | Requerimiento | Descripción |
|---|---|---|
| RF-01 | Inicio de sesión | El sistema debe permitir a administradores e instituciones autenticarse mediante usuario y contraseña. |
| RF-02 | Gestión de contraseñas | El sistema debe permitir el cambio de contraseña y aplicar políticas mínimas de seguridad (longitud mínima, complejidad). Las contraseñas se almacenan con una función de hash de un solo sentido: no deben poder recuperarse, solo restablecerse. |
| RF-03 | Control de acceso por rol | El sistema debe restringir el acceso a funcionalidades y datos según el rol del usuario autenticado. |
| RF-04 | Aislamiento multi-tenant | Un administrador de la institución A no debe poder acceder, consultar ni modificar datos de la institución B bajo ninguna circunstancia. |
| RF-39 | Verificación del correo de la cuenta | El sistema debe permitir a cada cuenta confirmar que controla la dirección de correo declarada, mediante un código de un solo uso enviado a esa dirección. Una dirección sin verificar no ofrece garantía de ser alcanzable. |
| RF-40 | Recuperación autónoma de contraseña | Una cuenta que olvidó su contraseña debe poder fijar una nueva sin intervención de otro usuario, acreditando el control de su correo. El sistema no debe revelar si una cuenta existe al responder a estas solicitudes. |
| RF-42 | Verificación obligatoria para operar | Una cuenta que no verificó su correo no debe poder acceder a ninguna funcionalidad del sistema, salvo la pantalla donde se verifica y el cierre de sesión. La verificación debe surtir efecto de inmediato, sin exigir un nuevo inicio de sesión: un bloqueo que persista después de cumplir la condición deja a la persona sin acceso a su propia cuenta. |
| RF-43 | Identidad reutilizable entre instituciones | Una misma persona debe poder tener cuenta en más de una institución con la misma dirección de correo, y esa dirección debe poder coincidir con la de un docente registrado. La unicidad del usuario y del correo rige dentro de cada institución, no en todo el sistema. |
| RF-57 | Revalidación al cambiar el correo | Modificar la dirección de una cuenta debe invalidar su verificación y volver a bloquearla hasta confirmar la nueva. La anterior fue comprobada; la nueva no, y sin esto la cuenta seguiría figurando verificada con un buzón que nadie probó, al que además pasaría a apuntar la recuperación de contraseña. |
| RF-56 | Registro del último acceso | El sistema debe registrar la fecha y hora del último inicio de sesión de cada cuenta, y mostrarla en la administración de usuarios. Permite detectar cuentas que quedaron sin usar y dar contexto ante cualquier revisión posterior. |

### 3.2. Módulo de Gestión de Instituciones

| ID | Requerimiento | Descripción |
|---|---|---|
| RF-05 | Alta de institución | El sistema debe permitir registrar nuevas instituciones educativas con sus datos básicos (nombre, dirección, contacto), creando en el mismo acto la cuenta con la cual esa institución accede. Una institución sin cuenta de acceso es inutilizable y bloquea el nombre para futuros intentos. |
| RF-44 | Validación del alta de institución | El alta debe validarse con el mismo código de un solo uso que el resto del sistema, enviado al correo declarado. No puede exigir un rol, porque se ejecuta antes de que exista ningún usuario de esa institución. La institución no debe crearse hasta que ese código se valide: así ninguna llega a existir con una dirección sin comprobar, y un alta abandonada no deja registros ni nombres ocupados. |
| RF-45 | Unicidad de la institución | El nombre y el CUIT de una institución deben ser únicos en todo el sistema. Dos instituciones homónimas serían indistinguibles para quien administra el despliegue, y un CUIT repetido indica un dato mal cargado. El CUIT debe normalizarse a una única forma antes de compararlo: escrito con guiones y de corrido es el mismo número, y comparándolo como texto la restricción no lo detectaría. |
| RF-58 | Validez del CUIT | El sistema debe comprobar el dígito verificador del CUIT y rechazar los que no cierren. El formato correcto no garantiza que el número exista: el último dígito se calcula a partir de los diez anteriores, de modo que la comprobación detecta cualquier error de tipeo y no solo los del último dígito. Un CUIT ausente es válido, porque el dato es opcional. |
| RF-06 | CRUD de administradores | La institución (superadministrador) debe poder crear, consultar, modificar y dar de baja (lógica) a los administradores de su institución. |

### 3.3. Módulo de Gestión de Docentes

| ID | Requerimiento | Descripción |
|---|---|---|
| RF-46 | Registro del período en funciones | El sistema debe dejar constancia de desde y hasta cuándo un docente prestó servicios. La fecha de alta la registra el sistema en el momento de la carga, sin pedirla; la de baja la indica el administrador, porque la baja se carga después del hecho y forzar la fecha del día falsearía el registro. No se admite una baja futura ni anterior al alta. |
| RF-07 | CRUD de docentes | El administrador debe poder dar de alta, consultar, modificar y dar de baja (lógica) docentes, incluyendo datos personales, de contacto y asignación a materias. |
| RF-08 | Registro del modelo facial | El sistema debe capturar múltiples fotogramas del docente, generar los datos biométricos y almacenar exclusivamente esa información, descartando las imágenes originales. |
| RF-47 | Captura guiada por poses | El registro del rostro debe conducir a la persona por una secuencia de poses definida, capturando cuando la imagen cumple los criterios de aceptación y no cuando se agota un tiempo fijo. Un modelo entrenado con una única pose repetida no tolera después ninguna variación de ángulo o distancia, de modo que la duración de la captura no es garantía de nada: lo es la variedad obtenida. |
| RF-48 | Criterios de aceptación de cada captura | Cada fotograma candidato debe evaluarse por nitidez, iluminación y proporción del cuadro ocupada por el rostro, y descartarse si no los cumple. Cuando se descarta, el sistema debe indicar **qué corregir** en términos accionables, no limitarse a informar el fallo. |
| RF-49 | Variedad entre las capturas de entrenamiento | Antes de entrenar, el sistema debe verificar que las capturas sean suficientemente distintas entre sí y descartar las redundantes. Sin esta verificación, una secuencia guiada completada sin moverse produce el mismo modelo pobre que una grabación continua. |
| RF-09 | Re-registro facial | El sistema debe permitir actualizar el modelo facial de un docente. El modelo anterior se da de baja lógica y se activa el nuevo. |
| RF-50 | Bloqueo del registro sin consentimiento | El sistema no debe permitir registrar ni actualizar el modelo facial de un docente que no tenga consentimiento biométrico vigente. La comprobación debe realizarse en el servidor, no solo ocultando la acción en la interfaz. |
| RF-10 | Consentimiento informado | El sistema debe registrar el consentimiento libre, expreso e informado del docente para el tratamiento de sus datos biométricos (Ley N° 25.326). |

### 3.4. Módulo de Gestión Académica

| ID | Requerimiento | Descripción |
|---|---|---|
| RF-11 | Gestión de carreras | El administrador debe poder registrar y administrar las carreras/programas académicos. Sirven como criterio de agrupación y filtrado. |
| RF-12 | Gestión de materias | El administrador debe poder crear, modificar y dar de baja materias, asociándolas a una carrera y a un docente titular. |
| RF-13 | Gestión de comisiones | Una misma materia puede tener más de una comisión, cada una con su horario y docente asignado. |
| RF-14 | Gestión de horarios | El administrador debe cargar los horarios de cada materia/comisión: día, hora de inicio, hora de fin y docente asignado. Un docente puede tener múltiples horarios. |

### 3.5. Módulo de Reconocimiento Facial y Toma de Asistencia

| ID | Requerimiento | Descripción |
|---|---|---|
| RF-15 | Captura de video en vivo | El sistema debe acceder a la cámara web a través del navegador y transmitir los fotogramas al servidor. |
| RF-16 | Detección e identificación facial | El servidor debe detectar un rostro, compararlo contra los modelos biométricos y determinar la identidad con un umbral de confianza definido. |
| RF-17 | Registro automático de asistencia | Confirmada la identidad, el sistema debe registrar la asistencia sin pedirle ninguna acción al docente ni al operador. |
| RF-51 | Confirmación sostenida de la identidad | El sistema no debe registrar asistencia a partir de un único fotograma: la misma identidad debe sostenerse durante un lapso mínimo continuo, y la aparición de una identidad distinta debe reiniciar ese conteo. Bajo iluminación cambiante el reconocimiento oscila entre personas parecidas, y sin esta condición el primer fotograma que resulte ganador escribe el registro. |
| RF-52 | Margen exigido respecto del segundo candidato | Una identificación debe cumplir dos condiciones: que la distancia del mejor candidato esté bajo el umbral **y** que le gane al segundo por un margen mínimo. Un empate no es una identificación: cuál gana lo decide el ruido y no la identidad. El algoritmo empleado devuelve siempre el más parecido y nunca "no conozco a esta persona", así que sin la segunda condición cualquier rostro sin registrar termina adjudicado a alguien. Ambas distancias deben quedar registradas para poder calibrar. |
| RF-59 | Una sola persona en cuadro | Tanto el registro del rostro como el pase de asistencia deben exigir que haya exactamente una persona frente a la cámara, y avisarlo por texto cuando haya más. El sistema no puede elegir por su cuenta a cuál de varias personas atribuir la captura, y hacerlo por tamaño del recuadro adjudicaría la marca a quien esté más cerca. El aviso debe distinguirse del de "no se detecta ningún rostro": son situaciones opuestas y se corrigen distinto. Mientras haya más de una, no debe señalarse a ninguna con el recuadro. |
| RF-53 | Idempotencia del registro | Un mismo docente en una misma clase y fecha no puede generar más de un registro de asistencia, independientemente de cuántas veces pase frente a la cámara. La condición debe garantizarse en la base de datos y no únicamente en la aplicación. |
| RF-18 | Determinación automática de materia y horario | El sistema debe cruzar la hora del registro con los horarios cargados para determinar la materia/comisión correspondiente. |
| RF-19 | Clasificación del estado de asistencia | El sistema debe clasificar la asistencia en Presente (dentro de la tolerancia), Llegada Tarde (pasada la tolerancia) o Ausente (sin registro). |
| RF-20 | Retroalimentación visual | El sistema debe mostrar una notificación clara indicando si el reconocimiento fue exitoso (nombre + materia) o si no fue posible identificar. |
| RF-21 | Registro de metadatos | Cada registro debe almacenar: fecha, hora exacta, docente, materia/comisión, estado, método (automático/manual) e institución. |
| RF-38 | Detección de vivacidad | El sistema debe distinguir a una persona presente de una reproducción de su imagen (fotografía o pantalla), verificando señales de vida como parpadeo o micromovimiento entre fotogramas, dentro del mismo presupuesto de tiempo del RF-17. |

### 3.6. Módulo de Carga Manual de Asistencia

| ID | Requerimiento | Descripción |
|---|---|---|
| RF-22 | Carga manual de asistencia | El administrador debe poder registrar manualmente la asistencia cuando el reconocimiento facial falle o no esté disponible. |
| RF-23 | Motivo de carga manual | El sistema debe solicitar un motivo, con opciones predefinidas (falla de cámara, falla de reconocimiento, docente no registrado, otro) y texto libre. |
| RF-24 | Trazabilidad de carga manual | Cada registro manual debe quedar asociado al administrador que lo realizó, la fecha/hora y el motivo. |

### 3.7. Módulo de Gestión de Ausencias

| ID | Requerimiento | Descripción |
|---|---|---|
| RF-41 | Registro automático de ausencias | El sistema debe registrar la ausencia de los docentes que no fueron marcados en una clase ya finalizada, sin intervención del administrador. Una ausencia que solo se calcula al consultar no puede justificarse ni auditarse, porque no existe como registro. |
| RF-25 | Clasificación de ausencias | Las ausencias deben poder clasificarse como justificadas o injustificadas. |
| RF-26 | Justificación de ausencias | El administrador debe poder marcar una ausencia como justificada e ingresar una descripción del motivo. |

### 3.8. Módulo de Reportes

| ID | Requerimiento | Descripción |
|---|---|---|
| RF-27 | Reporte por docente | El sistema debe generar reportes filtrados por un docente, mostrando su historial completo. |
| RF-28 | Reporte por materia | El sistema debe generar reportes agrupados por materia. |
| RF-29 | Reporte por carrera | El sistema debe generar reportes agrupados por carrera. |
| RF-30 | Filtros avanzados | Los reportes deben permitir filtrar por rangos de fechas, días, meses y períodos personalizados. |
| RF-31 | Exportación a PDF | El sistema debe permitir exportar cualquier reporte en formato PDF. |
| RF-32 | Exportación a Excel | El sistema debe permitir exportar cualquier reporte en formato Excel (.xlsx). |
| RF-33 | Visualizaciones gráficas | Los reportes deben incluir gráficos que faciliten la interpretación de los datos. |

### 3.9. Dashboard

| ID | Requerimiento | Descripción |
|---|---|---|
| RF-37 | Panel de inicio | El sistema debe presentar un dashboard con información del día: asistencias registradas, docentes presentes/ausentes/tarde, próximos horarios y alertas. |

### 3.10. Funciones transversales de gestión

Aplican a todos los módulos de administración por igual, así que no pertenecen a ninguno en particular.

| ID | Requerimiento | Descripción |
|---|---|---|
| RF-54 | Integridad referencial en las bajas | El sistema no debe permitir dar de baja un elemento del que dependan otros activos: un docente que sea titular de materias o esté asignado a comisiones, una carrera con materias vigentes, una materia con comisiones vigentes. El rechazo debe indicar **cuántas dependencias existen**, para que se sepa qué reasignar. |
| RF-55 | Búsqueda y filtrado en los listados | Todo listado de catálogo —docentes, usuarios, carreras, materias, comisiones y horarios— debe permitir buscar por texto sobre cualquiera de sus columnas y filtrar por estado. La búsqueda debe ser insensible a mayúsculas y a acentos, e informar cuántos registros quedaron a la vista. |

---

## 4. Requerimientos No Funcionales

### 4.1. Rendimiento

| ID | Requerimiento | Descripción |
|---|---|---|
| RNF-01 | Tiempo de procesamiento por fotograma | La detección, la identificación contra todos los modelos y el registro deben completarse en menos de 3 segundos de **procesamiento** por fotograma, de modo que el sistema pueda sostener el ritmo del bucle de la cámara. |
| RNF-30 | Tiempo total del pase | Desde que el docente se posiciona hasta que su asistencia queda registrada no deben transcurrir más de 8 segundos en condiciones normales. Este límite es distinto del RNF-01 y **lo supera necesariamente**: incluye los segundos que la identidad debe sostenerse antes de marcar (RF-51), que no son tiempo de cómputo sino una espera deliberada. Separarlos evita que una mejora de rendimiento se confunda con un recorte de esa verificación. |
| RNF-02 | Tiempo de respuesta web | Las páginas de consulta y gestión deben cargar en menos de 2 segundos en condiciones normales. |
| RNF-03 | Generación de reportes | Los reportes deben generarse en no más de 10 segundos, incluso con grandes volúmenes. |

### 4.2. Escalabilidad

| ID | Requerimiento | Descripción |
|---|---|---|
| RNF-04 | Escalabilidad de usuarios | El sistema debe soportar 200 a 400 docentes por institución con capacidad de crecimiento sin rediseño. |
| RNF-05 | Multi-tenancy | La arquitectura debe permitir incorporar nuevas instituciones sin instancias separadas, con aislamiento completo. |

### 4.3. Seguridad

| ID | Requerimiento | Descripción |
|---|---|---|
| RNF-06 | Cifrado de contraseñas | Las contraseñas deben almacenarse con hashing seguro (bcrypt o equivalente), nunca en texto plano. |
| RNF-07 | Protección de datos biométricos | Los embeddings faciales deben almacenarse cifrados, con acceso restringido a los procesos de reconocimiento. |
| RNF-08 | No almacenamiento de imágenes | El sistema no debe almacenar las fotografías capturadas; solo los vectores biométricos derivados. |
| RNF-09 | Sesiones seguras | Las sesiones deben tener expiración configurable y protección contra CSRF y XSS. |
| RNF-10 | Aislamiento de datos | Ningún usuario de una institución debe acceder a datos de otra, ni desde la interfaz ni desde la capa de datos. |
| RNF-31 | Respuestas que no revelan existencia | Ante un identificador de otra institución, o ante una cuenta inexistente en la recuperación de contraseña, el sistema debe responder exactamente igual que ante un caso legítimo negativo. Distinguir "no existe" de "no tenés permiso" confirma que el registro existe, y es suficiente para enumerar los datos ajenos probando identificadores. |
| RNF-37 | Tope de envíos por dirección de destino | Los formularios públicos que envían códigos deben acotar cuántos puede recibir una misma dirección en una ventana de tiempo. Sin ese tope, una pantalla accesible sin sesión permite usar el sistema para enviar mensajes repetidos a la casilla de un tercero. |
| RNF-32 | Protección de los códigos de un solo uso | Los códigos de verificación y de recuperación deben almacenarse hasheados, vencer en pocos minutos, consumirse en el primer uso, invalidarse al emitirse uno nuevo, y estar acotados tanto en intentos fallidos como en emisiones por hora. Seis dígitos son un millón de combinaciones: sin tope de intentos se agotan por fuerza bruta. |

### 4.4. Cumplimiento Legal y Normativo

| ID | Requerimiento | Descripción |
|---|---|---|
| RNF-11 | Ley N° 25.326 | El sistema debe cumplir con la Ley Nacional de Protección de Datos Personales. |
| RNF-12 | Resolución AAIP 255/2022 | El sistema debe respetar la clasificación de los datos biométricos como datos sensibles, con protección reforzada. |
| RNF-13 | Consentimiento informado | El tratamiento de datos biométricos requiere consentimiento libre, expreso e informado, por escrito o medio equivalente. |
| RNF-14 | Derechos ARCO | El sistema debe facilitar los derechos de Acceso, Rectificación, Cancelación y Oposición sobre los datos del docente. El ejercicio de la Cancelación sobre datos biométricos debe producir una **supresión efectiva**: no alcanza con marcar el registro como inactivo, porque el dato sensible seguiría almacenado. |
| RNF-28 | Conservación del registro administrativo | La supresión de los datos biométricos de un docente no debe eliminar sus asistencias históricas, que constituyen registro administrativo de la institución. Deben conservarse sin ninguna referencia al dato biométrico suprimido. |

### 4.5. Tecnología y Arquitectura

| ID | Requerimiento | Descripción |
|---|---|---|
| RNF-15 | Backend en Spring Boot | El backend debe desarrollarse en Java con Spring Boot (restricción técnica del proyecto). |
| RNF-16 | Reconocimiento facial en Java | El módulo de reconocimiento debe implementarse en Java con bibliotecas open source (OpenCV/JavaCV, DJL u otras). |
| RNF-17 | Aplicación web | El sistema debe ejecutarse en un navegador sin instalar software adicional en el cliente. |
| RNF-18 | Open source | Todas las tecnologías deben ser de código abierto y libres de licencia. |
| RNF-19 | Base de datos relacional | El sistema debe usar un motor relacional con separación de datos por institución (multi-tenancy por discriminador). |
| RNF-20 | Compatibilidad de cámara | El sistema debe ser compatible con cámaras web USB estándar vía la API MediaDevices (getUserMedia). |

### 4.6. Usabilidad

| ID | Requerimiento | Descripción |
|---|---|---|
| RNF-21 | Diseño minimalista | Interfaz limpia y minimalista, priorizando el uso frecuente y prolongado por el administrador. |
| RNF-22 | Modo oscuro y claro | El sistema debe ofrecer modo oscuro (por defecto) y modo claro conmutable. |
| RNF-23 | Optimización para escritorio | La interfaz está diseñada para PC de escritorio; no se requiere adaptación móvil en esta etapa. |
| RNF-24 | Retroalimentación clara | El sistema debe dar mensajes claros sobre el resultado de cada acción (confirmaciones, errores, advertencias). |
| RNF-29 | Errores de integridad legibles | Un dato que choca con una restricción de la base debe explicarse en los términos del usuario, indicando qué campo se repite y en qué ámbito. Nunca debe mostrarse el mensaje del motor de base de datos, que expone nombres de tablas e índices y valores concretos, ni una pantalla de error genérica. |
| RNF-33 | Interfaz íntegramente en español | Todo el texto visible —etiquetas, encabezados de tabla, botones, mensajes y opciones— debe estar en español. Los identificadores internos no deben aparecer en pantalla: un rol se muestra como "Institución", no como `INSTITUCION`. |

### 4.7. Mantenibilidad y Despliegue

| ID | Requerimiento | Descripción |
|---|---|---|
| RNF-25 | Desarrollo incremental | El sistema debe desarrollarse en etapas incrementales (prototipos funcionales) con validaciones tempranas. |
| RNF-26 | Código documentado | El código debe estar documentado y seguir convenciones estándar de Java/Spring Boot. Cada archivo lleva un encabezado que explica su propósito y cada función una línea que describe qué hace, en español. |
| RNF-27 | Despliegue local | En su primera etapa debe poder desplegarse localmente, con arquitectura preparada para migrar a la nube. |
| RNF-34 | Parámetros de reconocimiento configurables | El umbral de confianza, los criterios de calidad de captura y la ventana de confirmación deben poder ajustarse por configuración, sin recompilar. Sus valores dependen de la cámara y de la iluminación de cada instalación, de modo que un valor fijo en el código sería correcto en un lugar e inservible en otro. |
| RNF-35 | Esquema versionado e inmutable | Todo cambio en la base debe entregarse como una migración numerada. Una migración ya aplicada no se edita: para revertir algo se agrega una nueva. La herramienta valida la huella de cada una e impide arrancar si alguna fue alterada. |
| RNF-36 | Pruebas automatizadas de las reglas críticas | El aislamiento entre instituciones, la autorización por rol, las reglas de asistencia y los mecanismos de seguridad deben estar cubiertos por pruebas automatizadas que corran con el build. Cada prueba escrita para un defecto debe verificarse reintroduciendo el defecto y comprobando que efectivamente falla. |

---

## 5. Resumen cuantitativo

| Categoría | Cantidad |
|---|---|
| Requerimientos funcionales (RF) | 55 |
| Requerimientos no funcionales (RNF) | 37 |
| **Total** | **92** |

> Los identificadores RF-34 a RF-36 correspondían al módulo de auditoría, que se
> retiró del alcance del proyecto. No se reutilizan: renumerar rompería las
> referencias de los ADR y de los documentos ya entregados.

> El cumplimiento de cada requerimiento en la primera entrega se documenta en
> **02 — Definición del Alcance** (matriz de trazabilidad).
