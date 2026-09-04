-- =============================================================================
--  V001__init.sql
--  Migracion inicial del esquema completo del Sistema de Asistencias Digital.
--
--  Motor: MariaDB 10.4 (compatible MySQL 8).
--  Charset: utf8mb4 / utf8mb4_unicode_ci.
--  Engine: InnoDB (transacciones + FKs).
--
--  ATENCION: una vez aplicada, esta migracion NO se edita. Los cambios
--  futuros se hacen en V002__..., V003__..., etc.
-- =============================================================================

-- =============================================================================
--  1. INSTITUCIONES (tenant root del sistema multi-tenant)
-- =============================================================================
CREATE TABLE instituciones (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    nombre              VARCHAR(150)    NOT NULL,
    cuit                VARCHAR(13)     NULL,
    direccion           VARCHAR(200)    NULL,
    email_contacto      VARCHAR(120)    NULL,
    telefono_contacto   VARCHAR(30)     NULL,
    activo              BOOLEAN         NOT NULL DEFAULT TRUE,
    creado_en           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_instituciones                 PRIMARY KEY (id),
    CONSTRAINT uq_instituciones_nombre          UNIQUE (nombre),
    CONSTRAINT uq_instituciones_cuit            UNIQUE (cuit)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Instituciones educativas - tenant root del sistema multi-tenant';


-- =============================================================================
--  2. ROLES (catalogo global, sin tenant)
-- =============================================================================
CREATE TABLE roles (
    id              SMALLINT        NOT NULL AUTO_INCREMENT,
    codigo          VARCHAR(30)     NOT NULL,
    descripcion     VARCHAR(120)    NOT NULL,

    CONSTRAINT pk_roles             PRIMARY KEY (id),
    CONSTRAINT uq_roles_codigo      UNIQUE (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Catalogo global de roles del sistema';

-- Seed: roles del sistema (RF-03)
INSERT INTO roles (codigo, descripcion) VALUES
    ('SUPERADMIN_INSTITUCION',  'Cuenta raiz de la institucion - gestiona los administradores'),
    ('ADMIN',                   'Personal administrativo - opera el sistema dia a dia');


-- =============================================================================
--  3. USUARIOS (login del sistema - solo superadmins y admins; el docente NO loguea)
-- =============================================================================
CREATE TABLE usuarios (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    institucion_id  BIGINT          NOT NULL,
    rol_id          SMALLINT        NOT NULL,
    username        VARCHAR(60)     NOT NULL,
    email           VARCHAR(120)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL                COMMENT 'BCrypt - nunca en texto plano (RNF-06)',
    nombre          VARCHAR(80)     NOT NULL,
    apellido        VARCHAR(80)     NOT NULL,
    activo          BOOLEAN         NOT NULL DEFAULT TRUE,
    ultimo_login    TIMESTAMP       NULL,
    creado_en       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_usuarios                      PRIMARY KEY (id),
    CONSTRAINT uq_usuarios_inst_username        UNIQUE (institucion_id, username),
    CONSTRAINT uq_usuarios_inst_email           UNIQUE (institucion_id, email),
    CONSTRAINT fk_usuarios_institucion          FOREIGN KEY (institucion_id) REFERENCES instituciones (id) ON DELETE RESTRICT,
    CONSTRAINT fk_usuarios_rol                  FOREIGN KEY (rol_id)         REFERENCES roles (id)         ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Usuarios con acceso al sistema (superadmins y admins). El docente NO es usuario.';

CREATE INDEX idx_usuarios_inst_activo ON usuarios (institucion_id, activo);


-- =============================================================================
--  4. DOCENTES (sujetos pasivos del sistema - sin login)
-- =============================================================================
CREATE TABLE docentes (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    institucion_id  BIGINT          NOT NULL,
    dni             VARCHAR(15)     NOT NULL,
    legajo          VARCHAR(30)     NULL,
    nombre          VARCHAR(80)     NOT NULL,
    apellido        VARCHAR(80)     NOT NULL,
    email           VARCHAR(120)    NULL,
    telefono        VARCHAR(30)     NULL,
    fecha_alta      DATE            NOT NULL,
    activo          BOOLEAN         NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_docentes                      PRIMARY KEY (id),
    CONSTRAINT uq_docentes_inst_dni             UNIQUE (institucion_id, dni),
    CONSTRAINT uq_docentes_inst_legajo          UNIQUE (institucion_id, legajo),
    CONSTRAINT fk_docentes_institucion          FOREIGN KEY (institucion_id) REFERENCES instituciones (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Personal docente - sujetos pasivos del sistema (RF-07)';

CREATE INDEX idx_docentes_inst_activo ON docentes (institucion_id, activo);
CREATE INDEX idx_docentes_apellido    ON docentes (apellido, nombre);


-- =============================================================================
--  5. CONSENTIMIENTOS BIOMETRICOS (Ley 25.326 / AAIP 255/2022)
-- =============================================================================
CREATE TABLE consentimientos_biometricos (
    id                          BIGINT          NOT NULL AUTO_INCREMENT,
    docente_id                  BIGINT          NOT NULL,
    version_terminos            VARCHAR(20)     NOT NULL                COMMENT 'Version del texto de consentimiento firmado',
    metodo                      VARCHAR(20)     NOT NULL,
    documento_url               VARCHAR(255)    NULL                    COMMENT 'Ruta al documento firmado (opcional)',
    fecha_consentimiento        TIMESTAMP       NOT NULL,
    fecha_revocacion            TIMESTAMP       NULL                    COMMENT 'Si no es null, el consentimiento fue revocado (Derechos ARCO - RNF-14)',
    vigente                     BOOLEAN         NOT NULL DEFAULT TRUE,
    registrado_por_usuario_id   BIGINT          NOT NULL,

    CONSTRAINT pk_consentimientos                       PRIMARY KEY (id),
    CONSTRAINT fk_consentimientos_docente               FOREIGN KEY (docente_id)                REFERENCES docentes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_consentimientos_usuario               FOREIGN KEY (registrado_por_usuario_id) REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT ck_consentimientos_metodo                CHECK (metodo IN ('ESCRITO','DIGITAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Consentimiento informado del docente para tratamiento biometrico (RF-10, RNF-13)';

CREATE INDEX idx_consentimientos_docente_vigente ON consentimientos_biometricos (docente_id, vigente);


-- =============================================================================
--  6. MODELOS FACIALES (embeddings cifrados - NUNCA imagenes)
-- =============================================================================
CREATE TABLE modelos_faciales (
    id                          BIGINT          NOT NULL AUTO_INCREMENT,
    docente_id                  BIGINT          NOT NULL,
    embedding_cifrado           BLOB            NOT NULL                COMMENT 'Vector biometrico cifrado (Spring Security Crypto)',
    algoritmo                   VARCHAR(50)     NOT NULL                COMMENT 'Ej: LBPH, FaceNet, ArcFace',
    version_algoritmo           VARCHAR(20)     NOT NULL,
    dimensiones                 SMALLINT        NOT NULL                COMMENT 'Largo del vector (ej: 128, 512)',
    activo                      BOOLEAN         NOT NULL DEFAULT TRUE   COMMENT 'Solo 1 activo por docente (validado en aplicacion)',
    registrado_por_usuario_id   BIGINT          NOT NULL,
    fecha_registro              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_baja                  TIMESTAMP       NULL                    COMMENT 'Re-registro RF-09: el modelo anterior se da de baja',

    CONSTRAINT pk_modelos_faciales                  PRIMARY KEY (id),
    CONSTRAINT fk_modelos_faciales_docente          FOREIGN KEY (docente_id)                REFERENCES docentes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_modelos_faciales_usuario          FOREIGN KEY (registrado_por_usuario_id) REFERENCES usuarios (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Embeddings biometricos cifrados (RF-08, RF-09, RNF-07, RNF-08). Sin fotografias.';

CREATE INDEX idx_modelos_faciales_docente_activo ON modelos_faciales (docente_id, activo);


-- =============================================================================
--  7. CARRERAS
-- =============================================================================
CREATE TABLE carreras (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    institucion_id  BIGINT          NOT NULL,
    codigo          VARCHAR(30)     NOT NULL,
    nombre          VARCHAR(150)    NOT NULL,
    activo          BOOLEAN         NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_carreras                  PRIMARY KEY (id),
    CONSTRAINT uq_carreras_inst_codigo      UNIQUE (institucion_id, codigo),
    CONSTRAINT fk_carreras_institucion      FOREIGN KEY (institucion_id) REFERENCES instituciones (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Carreras / programas academicos (RF-11)';

CREATE INDEX idx_carreras_inst_activo ON carreras (institucion_id, activo);


-- =============================================================================
--  8. MATERIAS
-- =============================================================================
CREATE TABLE materias (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    institucion_id      BIGINT          NOT NULL                COMMENT 'Denormalizado para reforzar aislamiento multi-tenant',
    carrera_id          BIGINT          NOT NULL,
    codigo              VARCHAR(30)     NOT NULL,
    nombre              VARCHAR(150)    NOT NULL,
    docente_titular_id  BIGINT          NULL,
    activo              BOOLEAN         NOT NULL DEFAULT TRUE,
    creado_en           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_materias                  PRIMARY KEY (id),
    CONSTRAINT uq_materias_inst_codigo      UNIQUE (institucion_id, codigo),
    CONSTRAINT fk_materias_institucion      FOREIGN KEY (institucion_id)     REFERENCES instituciones (id) ON DELETE RESTRICT,
    CONSTRAINT fk_materias_carrera          FOREIGN KEY (carrera_id)         REFERENCES carreras (id)      ON DELETE RESTRICT,
    CONSTRAINT fk_materias_docente_titular  FOREIGN KEY (docente_titular_id) REFERENCES docentes (id)      ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Materias asociadas a carrera y opcionalmente a docente titular (RF-12)';

CREATE INDEX idx_materias_inst_carrera_activo ON materias (institucion_id, carrera_id, activo);


-- =============================================================================
--  9. COMISIONES (varias por materia, RF-13)
-- =============================================================================
CREATE TABLE comisiones (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    materia_id              BIGINT          NOT NULL,
    codigo                  VARCHAR(30)     NOT NULL                COMMENT 'Ej: A, B, Noche, Manana',
    docente_asignado_id     BIGINT          NOT NULL,
    cupo                    INT             NULL,
    activo                  BOOLEAN         NOT NULL DEFAULT TRUE,
    creado_en               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_comisiones                    PRIMARY KEY (id),
    CONSTRAINT uq_comisiones_materia_codigo     UNIQUE (materia_id, codigo),
    CONSTRAINT fk_comisiones_materia            FOREIGN KEY (materia_id)          REFERENCES materias (id) ON DELETE RESTRICT,
    CONSTRAINT fk_comisiones_docente            FOREIGN KEY (docente_asignado_id) REFERENCES docentes (id) ON DELETE RESTRICT,
    CONSTRAINT ck_comisiones_cupo_positivo      CHECK (cupo IS NULL OR cupo > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Comisiones de cada materia con su docente asignado (RF-13)';

CREATE INDEX idx_comisiones_docente_activo ON comisiones (docente_asignado_id, activo);


-- =============================================================================
-- 10. HORARIOS
-- =============================================================================
CREATE TABLE horarios (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    comision_id         BIGINT          NOT NULL,
    dia_semana          TINYINT         NOT NULL                COMMENT '1=Lunes, 7=Domingo (ISO 8601)',
    hora_inicio         TIME            NOT NULL,
    hora_fin            TIME            NOT NULL,
    tolerancia_min      SMALLINT        NOT NULL DEFAULT 15     COMMENT 'Minutos de tolerancia para PRESENTE vs TARDE (RF-19)',
    vigente_desde       DATE            NOT NULL,
    vigente_hasta       DATE            NULL,
    activo              BOOLEAN         NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_horarios                      PRIMARY KEY (id),
    CONSTRAINT fk_horarios_comision             FOREIGN KEY (comision_id) REFERENCES comisiones (id) ON DELETE RESTRICT,
    CONSTRAINT ck_horarios_dia_semana           CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT ck_horarios_horas                CHECK (hora_fin > hora_inicio),
    CONSTRAINT ck_horarios_tolerancia           CHECK (tolerancia_min >= 0 AND tolerancia_min <= 120),
    CONSTRAINT ck_horarios_vigencia             CHECK (vigente_hasta IS NULL OR vigente_hasta >= vigente_desde)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Horarios semanales de cada comision (RF-14)';

CREATE INDEX idx_horarios_comision_dia_activo ON horarios (comision_id, dia_semana, activo);


-- =============================================================================
-- 11. ASISTENCIAS (nucleo del negocio)
-- =============================================================================
CREATE TABLE asistencias (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    institucion_id      BIGINT          NOT NULL                COMMENT 'Denormalizado: refuerza aislamiento y acelera reportes',
    docente_id          BIGINT          NOT NULL,
    comision_id         BIGINT          NOT NULL,
    horario_id          BIGINT          NOT NULL,
    fecha               DATE            NOT NULL,
    hora_registrada     TIME            NOT NULL,
    estado              VARCHAR(15)     NOT NULL,
    metodo              VARCHAR(15)     NOT NULL,
    modelo_facial_id    BIGINT          NULL                    COMMENT 'Modelo usado para identificar (solo si metodo = AUTOMATICO)',
    confianza           DECIMAL(5,4)    NULL                    COMMENT 'Score 0.0000 a 1.0000 (solo si metodo = AUTOMATICO)',
    creado_en           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_asistencias                       PRIMARY KEY (id),
    CONSTRAINT uq_asistencias_doc_horario_fecha     UNIQUE (docente_id, horario_id, fecha),
    CONSTRAINT fk_asistencias_institucion           FOREIGN KEY (institucion_id)   REFERENCES instituciones (id)     ON DELETE RESTRICT,
    CONSTRAINT fk_asistencias_docente               FOREIGN KEY (docente_id)       REFERENCES docentes (id)          ON DELETE RESTRICT,
    CONSTRAINT fk_asistencias_comision              FOREIGN KEY (comision_id)      REFERENCES comisiones (id)        ON DELETE RESTRICT,
    CONSTRAINT fk_asistencias_horario               FOREIGN KEY (horario_id)       REFERENCES horarios (id)          ON DELETE RESTRICT,
    CONSTRAINT fk_asistencias_modelo_facial         FOREIGN KEY (modelo_facial_id) REFERENCES modelos_faciales (id)  ON DELETE SET NULL,
    CONSTRAINT ck_asistencias_estado                CHECK (estado IN ('PRESENTE','TARDE','AUSENTE')),
    CONSTRAINT ck_asistencias_metodo                CHECK (metodo IN ('AUTOMATICO','MANUAL')),
    CONSTRAINT ck_asistencias_confianza             CHECK (confianza IS NULL OR (confianza >= 0 AND confianza <= 1)),
    CONSTRAINT ck_asistencias_metodo_modelo         CHECK (
        (metodo = 'MANUAL'    AND modelo_facial_id IS NULL AND confianza IS NULL) OR
        (metodo = 'AUTOMATICO')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Registros de asistencia - automatica o manual (RF-17 a RF-21)';

CREATE INDEX idx_asistencias_inst_fecha     ON asistencias (institucion_id, fecha);
CREATE INDEX idx_asistencias_docente_fecha  ON asistencias (docente_id, fecha DESC);
CREATE INDEX idx_asistencias_comision_fecha ON asistencias (comision_id, fecha);


-- =============================================================================
-- 12. MOTIVOS DE CARGA MANUAL (catalogo global, RF-23)
-- =============================================================================
CREATE TABLE motivos_carga_manual (
    id              SMALLINT        NOT NULL AUTO_INCREMENT,
    codigo          VARCHAR(40)     NOT NULL,
    descripcion     VARCHAR(150)    NOT NULL,
    activo          BOOLEAN         NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_motivos_carga_manual          PRIMARY KEY (id),
    CONSTRAINT uq_motivos_carga_manual_codigo   UNIQUE (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Catalogo de motivos predefinidos para carga manual de asistencia (RF-23)';

-- Seed: motivos predefinidos del documento de requerimientos (RF-23)
INSERT INTO motivos_carga_manual (codigo, descripcion) VALUES
    ('FALLA_CAMARA',          'Falla tecnica de la camara web'),
    ('FALLA_RECONOCIMIENTO',  'Falla en el algoritmo de reconocimiento facial'),
    ('NO_REGISTRADO',         'Docente no registrado facialmente en el sistema'),
    ('OTRO',                  'Otro motivo (detallar en texto libre)');


-- =============================================================================
-- 13. ASISTENCIAS MANUALES (detalle 1:1 cuando metodo = MANUAL, RF-24)
-- =============================================================================
CREATE TABLE asistencias_manuales (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    asistencia_id       BIGINT          NOT NULL,
    usuario_id          BIGINT          NOT NULL                COMMENT 'Admin que registro manualmente',
    motivo_id           SMALLINT        NOT NULL,
    detalle_adicional   TEXT            NULL,
    creado_en           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_asistencias_manuales                  PRIMARY KEY (id),
    CONSTRAINT uq_asistencias_manuales_asistencia       UNIQUE (asistencia_id),
    CONSTRAINT fk_asistencias_manuales_asistencia       FOREIGN KEY (asistencia_id) REFERENCES asistencias (id)           ON DELETE CASCADE,
    CONSTRAINT fk_asistencias_manuales_usuario          FOREIGN KEY (usuario_id)    REFERENCES usuarios (id)              ON DELETE RESTRICT,
    CONSTRAINT fk_asistencias_manuales_motivo           FOREIGN KEY (motivo_id)     REFERENCES motivos_carga_manual (id)  ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Detalle de carga manual de asistencia con admin responsable y motivo (RF-22 a RF-24)';


-- =============================================================================
-- 14. JUSTIFICACIONES DE AUSENCIA (1:1 sobre asistencia AUSENTE, RF-25/RF-26)
-- =============================================================================
CREATE TABLE justificaciones_ausencia (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    asistencia_id   BIGINT          NOT NULL,
    usuario_id      BIGINT          NOT NULL                COMMENT 'Admin que justifico',
    motivo          TEXT            NOT NULL,
    documento_url   VARCHAR(255)    NULL                    COMMENT 'Adjunto opcional (certificado, etc.)',
    creado_en       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_justificaciones                       PRIMARY KEY (id),
    CONSTRAINT uq_justificaciones_asistencia            UNIQUE (asistencia_id),
    CONSTRAINT fk_justificaciones_asistencia            FOREIGN KEY (asistencia_id) REFERENCES asistencias (id) ON DELETE CASCADE,
    CONSTRAINT fk_justificaciones_usuario               FOREIGN KEY (usuario_id)    REFERENCES usuarios (id)    ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Justificacion de ausencias con admin responsable y motivo (RF-25, RF-26)';


-- =============================================================================
-- 15. AUDITORIA (RF-34 a RF-36)
-- =============================================================================
CREATE TABLE auditoria (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    institucion_id          BIGINT          NULL                    COMMENT 'Nullable: acciones a nivel sistema no tienen tenant',
    usuario_id              BIGINT          NOT NULL,
    accion                  VARCHAR(20)     NOT NULL,
    entidad                 VARCHAR(50)     NOT NULL                COMMENT 'Nombre de la tabla afectada',
    entidad_id              BIGINT          NOT NULL,
    valores_anteriores      JSON            NULL                    COMMENT 'Estado previo (UPDATE, DELETE)',
    valores_nuevos          JSON            NULL                    COMMENT 'Estado nuevo (CREATE, UPDATE)',
    ip_origen               VARCHAR(45)     NULL                    COMMENT 'Soporta IPv4 e IPv6',
    user_agent              VARCHAR(255)    NULL,
    fecha_hora              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_auditoria                 PRIMARY KEY (id),
    CONSTRAINT fk_auditoria_institucion     FOREIGN KEY (institucion_id) REFERENCES instituciones (id) ON DELETE SET NULL,
    CONSTRAINT fk_auditoria_usuario         FOREIGN KEY (usuario_id)     REFERENCES usuarios (id)      ON DELETE RESTRICT,
    CONSTRAINT ck_auditoria_accion          CHECK (accion IN ('CREATE','UPDATE','DELETE','LOGIN','LOGOUT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Log de acciones administrativas relevantes (RF-34 a RF-36)';

CREATE INDEX idx_auditoria_inst_fecha       ON auditoria (institucion_id, fecha_hora DESC);
CREATE INDEX idx_auditoria_entidad          ON auditoria (entidad, entidad_id);
CREATE INDEX idx_auditoria_usuario_fecha    ON auditoria (usuario_id, fecha_hora DESC);


-- =============================================================================
--  FIN V001
--  Tablas creadas: 15
--  Catalogos seed: roles (2), motivos_carga_manual (4)
-- =============================================================================
