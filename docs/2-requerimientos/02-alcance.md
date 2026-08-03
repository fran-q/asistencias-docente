# 02 — Definición del Alcance

| | |
|---|---|
| **Proyecto** | Sistema de Asistencias Digital con Reconocimiento Facial |
| **Documento** | 02 — Definición del Alcance del Proyecto |
| **Materia** | Prácticas Profesionalizantes III — CENT35 |
| **Ubicación** | Provincia de Tierra del Fuego, Argentina |
| **Autor** | Francisco Quiroga |
| **Versión** | 1.0 |
| **Fecha** | Junio 2026 |

---

## 1. Contexto y Problema

### 1.1. Situación actual

El control de asistencia de docentes en las instituciones educativas de nivel
superior de la provincia se realiza de forma manual, mediante planillas en
papel donde los docentes firman al llegar. Este método presenta:

- **Falta de confiabilidad**: depende de la buena fe del docente y de la
  verificación manual del personal administrativo.
- **Posibilidad de falsificación**: las firmas pueden ser adulteradas o
  registradas por terceros (suplantación de identidad).
- **Dificultad para generar estadísticas y reportes** en tiempo real.
- **Proceso lento y propenso a errores** al digitalizar los datos a posteriori.

### 1.2. Solución propuesta

Un sistema web **multi-tenant** que automatiza el registro de asistencia
docente mediante **reconocimiento facial**, eliminando la suplantación de
identidad y agilizando el control. Una cámara web fija (sala de profesores o
secretaría) detecta al docente, lo identifica contra su modelo biométrico
cifrado y registra la asistencia automáticamente con su estado (presente,
tarde o ausente).

---

## 2. Objetivos

### 2.1. Objetivo general

Desarrollar un sistema web multi-tenant de registro de asistencia docente con
reconocimiento facial automático que garantice la veracidad de los registros,
elimine la suplantación de identidad y provea herramientas de gestión y
reportes para el personal administrativo de instituciones de nivel superior.

### 2.2. Objetivos específicos

1. Implementar un módulo de reconocimiento facial open source en Java que
   identifique unívocamente a cada docente sin almacenar fotografías.
2. Diseñar un flujo de toma de asistencia automatizado que detecte al docente,
   determine materia/horario y registre el estado con la hora exacta.
3. Desarrollar la gestión administrativa (CRUD con borrado lógico) de
   docentes, carreras, materias, comisiones y horarios.
4. Implementar carga manual de asistencia como respaldo ante fallas del
   reconocimiento, con registro del responsable y el motivo.
5. Construir un módulo de reportes con filtros y exportación.
6. Diseñar una arquitectura multi-tenant con aislamiento total entre
   instituciones.
7. Implementar un sistema de roles y permisos jerárquicos.
8. Garantizar el cumplimiento de la Ley N° 25.326 y la Resolución AAIP
   255/2022 sobre datos biométricos, incluyendo el consentimiento informado.

---

## 3. Alcance de la Primera Entrega

> **Enfoque metodológico.** Según el requerimiento **RNF-25**, el sistema se
> desarrolla en **etapas incrementales por prototipos funcionales**. La
> primera entrega corresponde al **hito inicial** del cronograma (6 sprints:
> abril–junio 2026). En consecuencia, un subconjunto de requerimientos quedó
> deliberadamente **planificado para iteraciones siguientes** (backlog), lo
> cual constituye una decisión de gestión de alcance y no un incumplimiento.
> La sección 4 detalla la trazabilidad requerimiento por requerimiento.

### 3.1. Dentro del alcance (entregado en el hito 1)

**Núcleo del negocio:**
- Reconocimiento facial completo: registro del modelo mediante **captura
  guiada por poses**, con control de nitidez, iluminación y encuadre de cada
  fotograma y verificación de que las capturas sean distintas entre sí;
  entrenamiento LBPH local, cifrado AES, sin almacenar imágenes; re-registro
  conservando el modelo anterior como histórico.
- Pase de asistencia automático: identificación → **confirmación sostenida de
  la identidad** → determinación de comisión/horario en curso → clasificación
  PRESENTE/TARDE → registro idempotente.
- Carga manual de asistencia con catálogo de motivos y trazabilidad.
- Justificación de ausencias.
- Listado de asistencias del día con AUSENTES calculadas.
- Reportes filtrables con exportación a CSV (compatible con Excel).

**Gestión y seguridad:**
- Autenticación con Spring Security (sesión HTTP) y BCrypt.
- Roles INSTITUCION y ADMIN con control de acceso.
- Multi-tenancy con aislamiento total entre instituciones (triple defensa).
- CRUD de usuarios administradores y edición de la institución.
- Alta de instituciones nuevas desde la aplicación, validada por código enviado
  al correo: nada se crea hasta que ese código se confirma, y la cuenta inicial
  nace con su dirección ya comprobada.
- Verificación obligatoria del correo de cada cuenta y recuperación autónoma de
  la contraseña. Las tres cosas —alta, verificación y recuperación— usan el
  **mismo mecanismo** de código de un solo uso.
- CRUD académico completo: carreras, materias, comisiones, horarios + grilla
  semanal.
- CRUD de docentes.

**Cumplimiento legal:**
- Consentimiento biométrico versionado con auditoría forense (IP, User-Agent),
  otorgamiento y revocación.
- Datos biométricos cifrados; sin fotografías.

### 3.2. Fuera del alcance del hito 1 (backlog planificado)

Los siguientes requerimientos quedaron planificados para iteraciones
posteriores:

| Funcionalidad diferida | Requerimiento | Motivo de la postergación |
|---|---|---|
| Exportación a **PDF** | RF-31 | Exportación a PDF | ✅ | Entregado; el detalle está en RF-61 | RF-32 | El CSV generado abre correctamente en Excel; el .xlsx nativo se difiere. |
| **Visualizaciones gráficas** en reportes | RF-33 | Requiere librería de charts; no crítico para el MVP. |
| **Reporte por carrera** | RF-29 | El reporte filtra por docente, materia, estado y método; el agrupamiento por carrera se difiere. |
| **Dashboard** con métricas en vivo | RF-37 | Panel de inicio | 🟡 | Entregado en RF-60; falta el listado de próximos horarios | RNF-22 | Se entregó el modo oscuro (por defecto); el conmutador a modo claro se difiere. |
| **Detección de vivacidad** | RF-38 | El pase acepta hoy una fotografía sostenida frente a la cámara. La limitación está declarada y el control compensatorio es la presencia del administrador durante el pase. |

### 3.3. Exclusiones permanentes (no forman parte del proyecto)

- Aplicación móvil nativa (el sistema es web, optimizado para escritorio —
  RNF-23).
- Integración con sistemas externos de RR.HH. o liquidación de sueldos.
- Reconocimiento facial sin consentimiento previo (prohibido por normativa).

---

## 4. Matriz de Trazabilidad (Requerimiento → Estado)

**Leyenda:** ✅ Implementado · 🟡 Parcial · 🔜 Backlog (planificado)

### 4.1. Requerimientos Funcionales

| ID | Requerimiento | Estado | Observación |
|---|---|---|---|
| RF-01 | Inicio de sesión | ✅ | Spring Security, sesión HTTP |
| RF-02 | Gestión de contraseñas | ✅ | Cambio de contraseña + política de longitud mínima |
| RF-03 | Control de acceso por rol | ✅ | `@PreAuthorize` por rol |
| RF-04 | Aislamiento multi-tenant | ✅ | TenantContext + filtro Hibernate + validación en servicios |
| RF-05 | Alta de institución | ✅ | Pantalla pública `/alta-institucion`; crea institución y cuenta inicial en una transacción |
| RF-06 | CRUD de administradores | ✅ | Módulo Usuarios |
| RF-07 | CRUD de docentes | ✅ | Con borrado lógico |
| RF-08 | Registro del modelo facial | ✅ | Captura guiada + LBPH + cifrado, sin imágenes |
| RF-09 | Re-registro facial | ✅ | Da de baja el modelo anterior |
| RF-10 | Consentimiento informado | ✅ | Versionado, con auditoría forense |
| RF-11 | Gestión de carreras | ✅ | CRUD con borrado lógico |
| RF-12 | Gestión de materias | ✅ | Con carrera y docente titular |
| RF-13 | Gestión de comisiones | ✅ | Con docente asignado |
| RF-14 | Gestión de horarios | ✅ | Con día, horas y tolerancia |
| RF-15 | Captura de video en vivo | ✅ | getUserMedia (navegador) |
| RF-16 | Detección e identificación facial | ✅ | OpenCV/LBPH con umbral configurable |
| RF-17 | Registro automático de asistencia | ✅ | Idempotente |
| RF-18 | Determinación automática de materia/horario | ✅ | Cruce hora actual con horarios |
| RF-19 | Clasificación del estado | ✅ | PRESENTE/TARDE; AUSENTE calculada al listar |
| RF-20 | Retroalimentación visual | ✅ | Recuadro verde/amarillo/rojo + nombre |
| RF-21 | Registro de metadatos | ✅ | Fecha, hora, docente, comisión, estado, método, confianza |
| RF-22 | Carga manual de asistencia | ✅ | Con catálogo de motivos |
| RF-23 | Motivo de carga manual | ✅ | Opciones predefinidas + texto libre |
| RF-24 | Trazabilidad de carga manual | ✅ | Usuario + fecha + motivo |
| RF-25 | Clasificación de ausencias | 🟡 | Derivada: ausencia con justificación = justificada |
| RF-26 | Justificación de ausencias | ✅ | Sobre ausencias persistidas (las calculadas se cargan manual primero) |
| RF-27 | Reporte por docente | 🟡 | Vía filtro por docente en el reporte general |
| RF-28 | Reporte por materia | 🟡 | Vía filtro por materia en el reporte general |
| RF-29 | Reporte por carrera | 🔜 | No hay filtro/agrupamiento por carrera |
| RF-30 | Filtros avanzados | 🟡 | Rango de fechas sí; filtro por día/mes específico, diferido |
| RF-31 | Exportación a PDF | ✅ | Entregado; el detalle está en RF-61 |
| RF-32 | Exportación a Excel (.xlsx) | 🟡 | CSV compatible con Excel; .xlsx nativo diferido |
| RF-33 | Visualizaciones gráficas | 🔜 | Sin gráficos en esta entrega |
| RF-37 | Panel de inicio | 🟡 | Entregado en RF-60; falta el listado de próximos horarios |
| RF-38 | Detección de vivacidad | 🔜 | El pase acepta hoy una fotografía; limitación declarada en ADR-0007 |
| RF-39 | Verificación del correo | ✅ | Código de 6 dígitos, hasheado, vence a los 15 min |
| RF-40 | Recuperación autónoma de contraseña | ✅ | Sin revelar si la cuenta existe |
| RF-41 | Registro automático de ausencias | ✅ | Tarea programada con propagación manual del tenant |
| RF-42 | Verificación obligatoria para operar | ✅ | `VerificacionInterceptor` con lista blanca; desbloqueo en la misma sesión |
| RF-43 | Identidad reutilizable entre instituciones | ✅ | Unicidad de usuario y correo acotada a la institución |
| RF-44 | Validación del alta de institución | ✅ | Código al correo; la institución se crea recién al validarlo |
| RF-45 | Unicidad de la institución | ✅ | Nombre y CUIT únicos; el CUIT se normaliza antes de comparar |
| RF-58 | Validez del CUIT | ✅ | Dígito verificador comprobado en el alta y en la edición |
| RF-46 | Registro del período en funciones | ✅ | Alta automática al cargar; baja elegible, acotada entre el alta y hoy |
| RF-47 | Captura guiada por poses | ✅ | 5 poses × 3 capturas; termina por calidad, no por tiempo |
| RF-48 | Criterios de aceptación de cada captura | ✅ | Nitidez, luz y encuadre, con el motivo del descarte en pantalla |
| RF-49 | Variedad entre las capturas | ✅ | Comparación entre recortes antes de entrenar |
| RF-50 | Bloqueo del registro sin consentimiento | ✅ | Verificado en el servicio, no solo en la interfaz |
| RF-51 | Confirmación sostenida de la identidad | ✅ | 3 s continuos; otra identidad reinicia el conteo |
| RF-52 | Margen respecto del segundo candidato | ✅ | Se registra en el log de calibración de cada intento |
| RF-62 | Año de cursada de la materia | ✅ | Acotado por la duración de la carrera, validado en el servicio |
| RF-63 | Docente propuesto al crear una comisión | ✅ | Se propone el titular de la materia y queda editable |
| RF-61 | Exportación del reporte en PDF | ✅ | Botón junto al de CSV, apaisado y con encabezado repetido |
| RF-60 | Panel de inicio con estado del día | ✅ | Clases en curso, números del día y cargas incompletas |
| RF-59 | Una sola persona en cuadro | ✅ | Aviso por texto y sin recuadro mientras haya más de una |
| RF-53 | Idempotencia del registro | ✅ | Restricción única (docente, horario, fecha) en la base |
| RF-54 | Integridad referencial en las bajas | ✅ | Informa cuántas dependencias activas impiden la baja |
| RF-55 | Búsqueda y filtrado en los listados | ✅ | Los 6 catálogos; insensible a mayúsculas y acentos |
| RF-56 | Registro del último acceso | ✅ | Columna en la administración de usuarios |
| RF-57 | Revalidación al cambiar el correo | ✅ | Cambiar la dirección vuelve a bloquear la cuenta |

**Resumen RF:** 51 implementados · 6 parciales · 3 backlog (total 60).

### 4.2. Requerimientos No Funcionales

| ID | Requerimiento | Estado | Observación |
|---|---|---|---|
| RNF-01 | Tiempo de reconocimiento (≤3 s) | ✅ | Cumple en condiciones normales |
| RNF-02 | Tiempo de respuesta web (≤2 s) | ✅ | |
| RNF-03 | Generación de reportes (≤10 s) | ✅ | |
| RNF-04 | Escalabilidad de usuarios | ✅ | Arquitectura preparada (200-400/inst.) |
| RNF-05 | Multi-tenancy | ✅ | Discriminador institucion_id |
| RNF-06 | Cifrado de contraseñas | ✅ | BCrypt |
| RNF-07 | Protección de datos biométricos | ✅ | AES (Spring Security Crypto) |
| RNF-08 | No almacenamiento de imágenes | ✅ | Solo embeddings |
| RNF-09 | Sesiones seguras | ✅ | CSRF + expiración de sesión |
| RNF-10 | Aislamiento de datos | ✅ | Triple defensa |
| RNF-11 | Ley N° 25.326 | ✅ | Consentimiento + cifrado + sin fotos |
| RNF-12 | Resolución AAIP 255/2022 | ✅ | Datos biométricos tratados como sensibles |
| RNF-13 | Consentimiento informado | ✅ | Versionado y auditable |
| RNF-14 | Derechos ARCO | 🟡 | Acceso/rectificación (editar docente) y oposición (revocar consentimiento) cubiertos; flujo ARCO formal diferido |
| RNF-15 | Backend en Spring Boot | ✅ | Java 21 + Spring Boot 3.5 |
| RNF-16 | Reconocimiento facial en Java | ✅ | JavaCV/OpenCV |
| RNF-17 | Aplicación web | ✅ | Thymeleaf server-side |
| RNF-18 | Open source | ✅ | Todo el stack |
| RNF-19 | Base de datos relacional | ✅ | MariaDB |
| RNF-20 | Compatibilidad de cámara | ✅ | getUserMedia / cámara USB |
| RNF-21 | Diseño minimalista | ✅ | |
| RNF-22 | Modo oscuro y claro | 🔜 | Solo modo oscuro entregado |
| RNF-23 | Optimización para escritorio | ✅ | |
| RNF-24 | Retroalimentación clara | ✅ | Toasts + mensajes |
| RNF-25 | Desarrollo incremental | ✅ | 6 sprints con tags de cierre |
| RNF-26 | Código documentado | ✅ | ADRs, Javadoc, manuales |
| RNF-27 | Despliegue local | ✅ | XAMPP/MariaDB local |
| RNF-28 | Conservación del registro administrativo | ✅ | La supresión biométrica no toca las asistencias |
| RNF-29 | Errores de integridad legibles | ✅ | Validación en cada servicio + manejador global que traduce cada restricción |
| RNF-30 | Tiempo total del pase | ✅ | ~4-5 s medidos: procesamiento más la ventana de confirmación |
| RNF-31 | Respuestas que no revelan existencia | ✅ | "No encontrado" ante otro tenant; recuperación uniforme |
| RNF-32 | Protección de los códigos de un solo uso | ✅ | Hasheados, 15 min, un solo uso, 5 intentos, 5 por hora |
| RNF-33 | Interfaz íntegramente en español | ✅ | Barrido sobre los 33 templates |
| RNF-34 | Parámetros de reconocimiento configurables | ✅ | Umbral, calidad y ventana en `application.properties` |
| RNF-35 | Esquema versionado e inmutable | ✅ | Flyway V001–V009; migración aplicada no se edita |
| RNF-36 | Pruebas automatizadas de las reglas críticas | ✅ | 222 pruebas; las de defectos verificadas por mutación |
| RNF-37 | Tope de envíos por dirección de destino | ✅ | 3 por hora en el alta pública de institución |
| RNF-38 | Encabezado visible al recorrer un listado | ✅ | thead fijo dentro del contenedor con scroll |
| RNF-39 | Identificadores internos fuera de la interfaz | ✅ | Se quitó el ID de las seis pantallas donde figuraba |
| RNF-40 | Selección de hora independiente del navegador | ✅ | Dos listas, hora y minutos de 5 en 5 |
| RNF-41 | Bloque de datos del sistema uniforme | ✅ | Fragmento único aplicado en las seis pantallas |
| RNF-42 | Búsqueda por escritura en desplegables largos | ✅ | Seis desplegables; el select real queda detrás |

**Resumen RNF:** 40 implementados · 1 parcial · 1 backlog (total 42).

### 4.3. Resumen global

| | Implementado ✅ | Parcial 🟡 | Backlog 🔜 | Total |
|---|---|---|---|---|
| Funcionales | 51 | 6 | 3 | 60 |
| No funcionales | 40 | 1 | 1 | 42 |
| **Total** | **91** | **7** | **4** | **102** |

**Cobertura del hito 1:** 91 de 102 requerimientos completamente
implementados (≈89%), 7 parcialmente cubiertos (≈7%) y 4 en backlog
planificado (≈4%).

> El módulo de auditoría (antes RF-34 a RF-36) se retiró del alcance del
> proyecto. Su tabla existía en la base sin que ningún punto del código
> escribiera en ella, y una tabla de auditoría vacía es peor que ninguna: quien
> la consulte encuentra el registro en blanco y concluye que no pasó nada.
> Los identificadores no se reutilizan.

---

## 5. Supuestos y Restricciones

### 5.1. Supuestos
- Cada institución cuenta con una cámara web USB estándar en sala de
  profesores o secretaría.
- Los docentes prestan su consentimiento por escrito antes del registro
  facial.
- La iluminación del punto de captura es razonable para el reconocimiento.

### 5.2. Restricciones técnicas (definidas por la cátedra/cliente)
- Backend obligatorio en **Java + Spring Boot** (RNF-15).
- Reconocimiento facial **en Java**, open source (RNF-16, RNF-18).
- Base de datos **relacional** con multi-tenancy (RNF-19).
- Despliegue **local** en la primera etapa (RNF-27).
- Interfaz **web** optimizada para escritorio (RNF-17, RNF-23).

---

## 6. Entregables de la Primera Entrega

1. Aplicación web funcional (código fuente en repositorio Git).
2. Base de datos con esquema versionado (migraciones Flyway V001-V006).
3. Documentación técnica: ADRs, diagramas UML, manual de administrador y
   manual técnico, CHANGELOG.
4. Documentación de análisis (este TP): requerimientos, alcance, DFD, DER,
   casos de uso, presentación del prototipo.
5. Prototipo de interfaz (mockups exportables a Figma).
6. Video demostrativo del flujo principal.

---

## 7. Criterios de Aceptación

- El sistema permite registrar asistencia automática de un docente
  identificado por reconocimiento facial, clasificando correctamente
  PRESENTE/TARDE.
- El aislamiento multi-tenant impide el acceso cruzado entre instituciones
  (verificado).
- Los datos biométricos se almacenan cifrados y nunca como imágenes.
- El consentimiento biométrico se registra y puede revocarse.
- La carga manual y la justificación de ausencias funcionan como respaldo.
- Los reportes se generan y exportan a CSV.
- El build compila y la suite de tests pasa en su totalidad.
