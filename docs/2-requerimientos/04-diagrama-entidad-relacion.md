# 04 — Diagrama Entidad-Relación

| | |
|---|---|
| **Proyecto** | Sistema de Asistencias Digital con Reconocimiento Facial |
| **Documento** | 04 — Diagrama Entidad-Relación (DER) |
| **Materia** | Prácticas Profesionalizantes III — CENT35 |
| **Ubicación** | Provincia de Tierra del Fuego, Argentina |
| **Autor** | Francisco Quiroga |
| **Versión** | 1.0 |
| **Fecha** | Junio 2026 |
| **Motor** | MariaDB 10.4 · InnoDB · utf8mb4 |

---

## 1. Introducción

Este documento presenta el modelo de datos **tal como está implementado** en
la primera entrega (migraciones Flyway V001 a V006). El esquema es relacional
y **multi-tenant por discriminador**: las tablas principales llevan una
columna `institucion_id` que aísla los datos de cada institución.

> El diagrama está expresado en Mermaid (se renderiza en GitHub). Para una
> imagen de alta calidad se incluye, en el Anexo, una referencia al código
> DBML que se pega en [dbdiagram.io](https://dbdiagram.io) y se exporta a
> PNG/PDF.

---

## 2. Diagrama Entidad-Relación

```mermaid
erDiagram
    instituciones ||--o{ usuarios : tiene
    instituciones ||--o{ docentes : tiene
    instituciones ||--o{ carreras : tiene
    instituciones ||--o{ materias : tiene
    instituciones ||--o{ asistencias : tiene
    roles ||--o{ usuarios : clasifica

    carreras ||--o{ materias : agrupa
    materias ||--o{ comisiones : "se divide en"
    comisiones ||--o{ horarios : "se dicta en"

    docentes ||--o{ consentimientos_biometricos : firma
    docentes ||--o{ modelos_faciales : registra
    docentes |o--o{ materias : "es titular"
    docentes |o--o{ comisiones : "es asignado"
    docentes ||--o{ asistencias : marca

    usuarios ||--o{ consentimientos_biometricos : "registra/revoca"
    usuarios ||--o{ modelos_faciales : registra
    usuarios ||--o{ asistencias_manuales : carga
    usuarios ||--o{ justificaciones_ausencia : justifica
    usuarios ||--o{ codigos_verificacion : "recibe códigos"

    comisiones ||--o{ asistencias : "ocurre en"
    horarios ||--o{ asistencias : "en franja"
    modelos_faciales |o--o{ asistencias : identifica

    asistencias ||--o| asistencias_manuales : "detalle (1:1)"
    asistencias ||--o| justificaciones_ausencia : "justif. (1:1)"
    motivos_carga_manual ||--o{ asistencias_manuales : motivo

    instituciones {
        bigint id PK
        varchar nombre UK
        varchar cuit UK
        boolean activo
    }
    roles {
        smallint id PK
        varchar codigo UK "INSTITUCION | ADMIN"
    }
    usuarios {
        bigint id PK
        bigint institucion_id FK
        smallint rol_id FK
        varchar username
        varchar email
        timestamp email_verificado_en "NULL = sin verificar"
        varchar password_hash "BCrypt"
        boolean activo
        timestamp ultimo_login
    }
    codigos_verificacion {
        bigint id PK
        bigint institucion_id FK
        bigint usuario_id FK
        varchar proposito "VERIFICACION_EMAIL | RECUPERACION_PASSWORD"
        varchar email "a qué buzón se envió"
        varchar codigo_hash "nunca en texto plano"
        timestamp expira_en
        timestamp usado_en "NOT NULL = ya consumido"
        smallint intentos
    }
    docentes {
        bigint id PK
        bigint institucion_id FK
        varchar dni
        varchar legajo
        varchar apellido
        boolean activo
    }
    consentimientos_biometricos {
        bigint id PK
        bigint docente_id FK
        varchar version_terminos
        timestamp fecha_consentimiento
        timestamp fecha_revocacion
        boolean vigente
    }
    modelos_faciales {
        bigint id PK
        bigint docente_id FK
        longblob embedding_cifrado "AES, sin imágenes"
        varchar algoritmo "LBPH"
        boolean activo
    }
    carreras {
        bigint id PK
        bigint institucion_id FK
        varchar codigo
        boolean activo
    }
    materias {
        bigint id PK
        bigint institucion_id FK
        bigint carrera_id FK
        bigint docente_titular_id FK
        varchar codigo
        boolean activo
    }
    comisiones {
        bigint id PK
        bigint materia_id FK
        bigint docente_asignado_id FK
        varchar codigo
        boolean activo
    }
    horarios {
        bigint id PK
        bigint comision_id FK
        tinyint dia_semana "1-7"
        time hora_inicio
        time hora_fin
        smallint tolerancia_min
    }
    asistencias {
        bigint id PK
        bigint institucion_id FK
        bigint docente_id FK
        bigint comision_id FK
        bigint horario_id FK
        bigint modelo_facial_id FK
        date fecha
        time hora_registrada
        varchar estado "PRESENTE|TARDE|AUSENTE"
        varchar metodo "AUTOMATICO|MANUAL"
        decimal confianza
    }
    motivos_carga_manual {
        smallint id PK
        varchar codigo UK
        varchar descripcion
    }
    asistencias_manuales {
        bigint id PK
        bigint asistencia_id FK_UK
        bigint usuario_id FK
        smallint motivo_id FK
        text detalle_adicional
    }
    justificaciones_ausencia {
        bigint id PK
        bigint asistencia_id FK_UK
        bigint usuario_id FK
        text motivo
        varchar documento_url
    }
```

---

## 3. Descripción de las entidades

### Dominio Tenant / Seguridad
- **instituciones** — Entidad raíz del multi-tenant. Cada institución
  educativa tiene su espacio lógico aislado.
- **roles** — Catálogo global de roles del sistema: `INSTITUCION` (cuenta
  raíz) y `ADMIN` (operativo).
- **usuarios** — Personas con acceso al sistema (login). Pertenecen a una
  institución y tienen un rol. La contraseña se guarda con BCrypt.
- **codigos_verificacion** — Códigos de un solo uso enviados por correo, tanto
  para confirmar que la persona controla su buzón como para recuperar la
  contraseña olvidada. El código se guarda hasheado, vence a los 15 minutos, se
  consume en el primer uso y lleva contador de intentos: seis dígitos son un
  millón de combinaciones, y sin ese tope se probarían por fuerza bruta.

### Dominio Docentes / Biometría
- **docentes** — Personal docente; sujetos pasivos (no se loguean). Su perfil
  vincula las asistencias.
- **consentimientos_biometricos** — Histórico de consentimientos de cada
  docente (otorga → revoca → vuelve a otorgar). Solo uno vigente a la vez.
  Cumple Ley 25.326.
- **modelos_faciales** — Modelo facial (LBPH) entrenado, **cifrado**. Nunca se
  almacenan fotografías. Solo uno activo por docente.

### Dominio Académico
- **carreras** — Programas académicos; criterio de agrupación.
- **materias** — Asignaturas, pertenecen a una carrera y opcionalmente tienen
  un docente titular.
- **comisiones** — Divisiones de una materia (turnos), cada una con su docente
  asignado.
- **horarios** — Franjas semanales de cada comisión (día, hora inicio/fin,
  tolerancia para clasificar Presente/Tarde).

### Dominio Asistencia (núcleo)
- **asistencias** — Registro de asistencia de un docente a una clase. Guarda
  estado, método, hora exacta y (si es automático) el modelo facial y la
  confianza.
- **motivos_carga_manual** — Catálogo de motivos para carga manual.
- **asistencias_manuales** — Detalle 1:1 cuando la marca es manual: quién la
  cargó y por qué.
- **justificaciones_ausencia** — Detalle 1:1 de la justificación de una
  ausencia.

---

## 4. Reglas de integridad destacadas

| Regla | Implementación |
|---|---|
| **Aislamiento multi-tenant** | Columna `institucion_id` en las tablas tenant-scoped + filtro a nivel aplicación |
| **Idempotencia de asistencia** | `UNIQUE (docente_id, horario_id, fecha)` en `asistencias`: una sola marca por docente/clase/día |
| **Relaciones 1:1** | `UNIQUE (asistencia_id)` en `asistencias_manuales` y `justificaciones_ausencia`, con borrado en cascada |
| **Borrado lógico** | Columna `activo` en catálogos y maestros (no se elimina físicamente) |
| **Integridad de dominio** | CHECK constraints: `estado ∈ {PRESENTE, TARDE, AUSENTE}`, `metodo ∈ {AUTOMATICO, MANUAL}`, `confianza ∈ [0,1]` |
| **Coherencia método-modelo** | CHECK: si `metodo = MANUAL`, entonces `modelo_facial_id` y `confianza` son NULL |
| **Protección de borrado** | FKs con `ON DELETE RESTRICT` (no se pierde historial); cascada solo en los detalles 1:1 |

---

## 5. Anexo — Exportar imagen en alta calidad

Para una imagen prolija del DER (entrega impresa), pegar el código **DBML**
del esquema actual en [dbdiagram.io](https://dbdiagram.io) y exportar a
PNG/PDF. El DBML se mantiene como fuente versionada en el repositorio del
proyecto.
