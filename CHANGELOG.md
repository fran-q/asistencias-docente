# Changelog

Highlights de cada sprint del proyecto, en orden cronológico inverso.

> Formato basado en [Keep a Changelog](https://keepachangelog.com/) y siguiendo
> los tags `sprint-N-cierre` del repositorio.

---

## Sprint 6 — Cierre del proyecto (sprint-6-cierre)

**Período:** 19 al 24 de junio de 2026.

### Agregado
- **Reportes con exportación a CSV** (`/reportes`). Filtros por rango de fechas,
  docente, materia, estado y método. Descarga UTF-8 con BOM para que abra
  directo en Excel.
- **Diagramas UML** en formato PlantUML (`docs/uml/`):
  - Casos de uso.
  - Clases del dominio.
  - Secuencia del pase de asistencia.
- **Manuales** en Markdown (`docs/manuales/`):
  - Manual del administrador (cómo usar el sistema).
  - Manual técnico (instalación, configuración, backup, troubleshooting).
- Tests del `ReporteAsistenciaService`.
- TD-004, TD-005 y TD-006 documentados en `TECH_DEBT.md`.
- README final con resumen ejecutivo.

---

## Sprint 5 — Asistencia automática end-to-end (sprint-5-cierre)

**Período:** 12 al 18 de junio de 2026.
**ADR:** [0008 - Asistencia automática](docs/4-arquitectura/adr/0008-asistencia-automatica.md).

### Agregado
- **Pase de asistencia** (`/asistencia/pase`): la pantalla del reconocimiento
  reemplaza el "modo prueba" y ahora **marca asistencia** al reconocer:
  - Recuadro verde cuando se marca PRESENTE/TARDE.
  - Recuadro azul cuando "ya estaba marcado" (idempotente).
  - Recuadro amarillo cuando hay rostro pero no hay clase en curso.
  - Recuadro rojo cuando hay rostro pero no se reconoce.
  - **Pausa de 5 segundos** tras cada marca exitosa (evita ruido).
- **Listado** `/asistencias` con filtros (fecha, estado, docente). Incluye
  **AUSENTES calculadas** al vuelo para horarios cuyo `hora_fin` ya pasó y
  no tienen marca.
- **Carga manual** de asistencia (`/asistencias/manual/nueva`) con catálogo
  de motivos (FALLA_CAMARA, FALLA_RECONOCIMIENTO, NO_REGISTRADO, OTRO).
- **Justificación de ausencias** (`/asistencias/{id}/justificar`) con
  motivo libre + URL de documento opcional.
- Tabla `asistencias`, `asistencias_manuales`, `motivos_carga_manual`,
  `justificaciones_ausencia` (todas ya estaban diseñadas en V001 desde el
  Sprint 0; Sprint 5 les pone la lógica encima).

### Decisiones técnicas
- **PRESENTE vs TARDE**: la tolerancia es **por horario** (`tolerancia_min`
  en cada `Horario`). Antes del `hora_inicio` (dentro de la tolerancia)
  → PRESENTE; después del `hora_inicio` → TARDE.
- **AUSENTE no se persiste** desde el flujo automático: se calcula al
  listar.
- **Idempotencia tres-niveles**: UNIQUE en BD + verificación en service +
  pausa en frontend.
- **Defensa contra race condition**: `saveAndFlush` + catch de
  `DataIntegrityViolationException` para releer y devolver `yaEstaba`.
- Conversión lineal **distancia LBPH → confianza 0-1** (TD-004).

---

## Sprint 4 — Reconocimiento facial (sprint-4-cierre)

**Período:** 29 de mayo al 11 de junio de 2026.
**ADR:** [0007 - Reconocimiento facial con JavaCV + LBPH](docs/4-arquitectura/adr/0007-reconocimiento-facial-lbph.md).

### Agregado
- **JavaCV 1.5.11 + OpenCV 4.10.0** integrados al build.
- `gradle.properties` con `javacpp.platform=windows-x86_64` para reducir
  la descarga de binarios nativos.
- **Registro del modelo facial** del docente (`/docentes/{id}/rostro/registrar`):
  grabación de 30 s con webcam, entrenamiento LBPH local, cifrado AES-256-GCM
  (Spring Security Crypto), compresión gzip antes de cifrar.
- **Identificación en vivo** (`/reconocimiento/prueba` en Sprint 4, reemplazada
  en Sprint 5): loop continuo, cache de recognizers en memoria.
- V005 (auditoría forense de consentimientos) y **V006** (`embedding_cifrado`
  pasa de `BLOB` a `LONGBLOB`).
- Tests de `CifradoBiometricoService`, `ModeloFacialService` e `IdentificacionFacialService`.

### Decisiones técnicas
- **LBPH por docente** (cada modelo entrenado con varias capturas de UNA
  persona) — encaja con la tabla `modelos_faciales`.
- **gzip antes de cifrar** — el YAML de OpenCV es altamente repetitivo,
  comprime 5-10x. Sin esto el INSERT supera `max_allowed_packet` de
  MariaDB y corrompe tablas del sistema (lección aprendida durante el
  sprint).
- `@JdbcTypeCode(SqlTypes.LONGVARBINARY)` en el `byte[]` para que Hibernate
  6 espere LONGBLOB.
- Cache en memoria de recognizers (TD-005).

---

## Sprint 3.5 — Reorganización a package-by-layer

**Período:** dentro del Sprint 3, post-cierre del Sprint 3.
**ADR:** [0006 - Package by layer](docs/4-arquitectura/adr/0006-organizacion-por-capas.md).

A pedido del docente de Prácticas Profesionalizantes III, se migra el código
de **package-by-feature** (ADR-0001) a **package-by-layer**:
`controller/`, `service/`, `repository/`, `model/`, `dto/`, `config/`.

47 archivos `.java` movidos con `git mv` (preservando historial). 38
`package-info.java` eliminados; uno nuevo creado en `model/` con el
`@FilterDef("tenant")`.

ADR-0001 marcada como reemplazada por ADR-0006.

---

## Sprint 3 — Docentes + Consentimiento biométrico (sprint-3-cierre)

**Período:** 7 al 28 de mayo de 2026.
**ADR:** [0005 - Consentimiento biométrico](docs/4-arquitectura/adr/0005-consentimiento-biometrico.md).

### Agregado
- CRUD completo de **Docentes** (`/docentes`).
- Asignación de **docente titular** a Materia (`Materia.docenteTitular`).
- Asignación de **docente asignado** a Comisión (`Comision.docenteAsignado`).
- **Consentimiento biométrico** (Ley 25.326 + AAIP 255/2022):
  - Texto legal versionado (`TextoConsentimiento.VERSION_ACTUAL = "2026-05-v1"`).
  - Otorgamiento / revocación con auditoría forense (IP, User-Agent).
  - Estados: NUNCA_OTORGADO, ACTIVO, REVOCADO (calculado).
  - Badges en la ficha del docente y columna en el listado.
- V004 (docente_asignado_id nullable durante la transición) y V005
  (columnas de auditoría forense en consentimientos).
- Pantallas de error custom (`/error.html`).

---

## Sprint 2 — Académico + grilla semanal (sprint-2-cierre)

**Período:** 5 al 6 de mayo de 2026.
**ADR:** [0004 - Defensa en JOINs](docs/4-arquitectura/adr/0004-tenant-filter-en-joins.md).

### Agregado
- CRUD de **Carreras**, **Materias**, **Comisiones**, **Horarios**.
- **Grilla semanal** por carrera (CSS Grid con posiciones pre-calculadas).
- Validación de superposición de horarios por comisión.
- Fix de fuga multi-tenant en JPQL con JOINs (TD-003).
- Componentes JS reutilizables: toast, confirm-modal, navbar overflow
  detection, password show/hide con SVG.

---

## Sprint 1 — Multi-tenancy + autenticación real + CRUDs (sprint-1-cierre)

**Período:** 1 al 4 de mayo de 2026.

### Agregado
- **Multi-tenancy por discriminator** (`institucion_id`) con
  `@Filter("tenant")` Hibernate y `TenantContext` ThreadLocal.
- **Autenticación real** con Spring Security, BCrypt, sesión HTTP cookie
  clásica (ADR-0003).
- CRUD de **Mi Institución** y **Usuarios administradores**.
- Roles `INSTITUCION` y `ADMIN`.
- UX: validación de formularios con Bean Validation, mensajes en español,
  ñ y tildes correctos.
- V003 (rename `SUPERADMIN_INSTITUCION` → `INSTITUCION`).

---

## Sprint 0 — Setup inicial (sprint-0-cierre)

**Período:** 24 al 30 de abril de 2026.
**ADR:** [0001 - Monolito Modular](docs/4-arquitectura/adr/0001-monolito-modular.md),
[0002 - Multi-tenancy por discriminator](docs/4-arquitectura/adr/0002-multi-tenant-discriminator.md).

### Agregado
- Setup Spring Boot 3.5 + Gradle + Java 21.
- MariaDB 10.4 vía XAMPP.
- Flyway con V001 (esquema completo de 16 tablas, con CHECK constraints).
- V002 con seed de datos de prueba.
- Login dummy en memoria (reemplazado en Sprint 1).
- CI GitHub Actions con build + tests.
- README inicial, diagrama BD, requerimientos.
