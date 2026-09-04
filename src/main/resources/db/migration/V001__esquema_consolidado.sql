-- =============================================================================
--  V001 - Esquema consolidado
-- =============================================================================
--  Reemplaza a las quince migraciones originales V001 a V015, que se conservan
--  sin cambios en db/historico/ como referencia de como se llego hasta aca.
--
--  POR QUE EXISTE
--  Las migraciones V001 a V014 creaban las tablas sin calificar el esquema
--  (CREATE TABLE instituciones, a secas). Mientras el historial de Flyway vivio
--  en la misma base eso funciono. Al mudarse el historial a
--  asistenciautomatica_meta, ese paso a ser tambien el esquema por defecto de la
--  conexion durante la migracion, y una instalacion desde cero terminaba creando
--  las 16 tablas dentro de la base del historial. V015, la unica escrita despues
--  de la mudanza y la unica calificada, fallaba al no encontrar ahi las tablas
--  que referenciaba.
--
--  Es decir: el esquema no era reproducible desde cero. La instalacion existente
--  solo funcionaba porque las primeras catorce migraciones se habian aplicado
--  antes del cambio. Se detecto recien al intentar reconstruir la base de cero,
--  porque el CI corre sobre H2, donde no existe la separacion en dos esquemas.
--
--  QUE HAY QUE SABER ANTES DE TOCAR ESTE ARCHIVO
--  1. Toda tabla y toda referencia se nombra con ${esquema}. Sin excepcion.
--  2. Las tablas van en orden de dependencias, no alfabetico, para que cada
--     clave foranea encuentre su destino ya creado.
--  3. Una instalacion ya migrada NO debe aplicar este archivo: se marca con
--     baseline en la version 1. Lo resuelve spring.flyway.baseline-on-migrate.
-- =============================================================================

CREATE TABLE ${esquema}.instituciones (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(150) NOT NULL,
  `cuit` varchar(13) DEFAULT NULL,
  `direccion` varchar(200) DEFAULT NULL,
  `email_contacto` varchar(120) DEFAULT NULL,
  `telefono_contacto` varchar(30) DEFAULT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `fecha_baja` date DEFAULT NULL COMMENT 'Cuando se dio de baja. NULL = no fue dada de baja.',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_instituciones_nombre` (`nombre`),
  UNIQUE KEY `uq_instituciones_cuit` (`cuit`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Instituciones educativas - tenant root del sistema multi-tenant';

CREATE TABLE ${esquema}.roles (
  `id` smallint(6) NOT NULL AUTO_INCREMENT,
  `codigo` varchar(30) NOT NULL,
  `descripcion` varchar(120) NOT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Alta del registro. En filas anteriores a V012, la fecha de la migracion.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_roles_codigo` (`codigo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Catalogo global de roles del sistema';

CREATE TABLE ${esquema}.motivos_carga_manual (
  `id` smallint(6) NOT NULL AUTO_INCREMENT,
  `codigo` varchar(40) NOT NULL,
  `descripcion` varchar(150) NOT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `fecha_baja` date DEFAULT NULL COMMENT 'Cuando se dio de baja. NULL = no fue dado de baja.',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Alta del registro. En filas anteriores a V012, la fecha de la migracion.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_motivos_carga_manual_codigo` (`codigo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Catalogo de motivos predefinidos para carga manual de asistencia (RF-23)';

CREATE TABLE ${esquema}.usuarios (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL,
  `rol_id` smallint(6) NOT NULL,
  `username` varchar(60) NOT NULL,
  `email` varchar(120) NOT NULL,
  `email_verificado_en` timestamp NULL DEFAULT NULL COMMENT 'Cuando la persona confirmo que controla este correo. NULL = sin verificar',
  `password_hash` varchar(255) NOT NULL COMMENT 'BCrypt - nunca en texto plano (RNF-06)',
  `nombre` varchar(80) NOT NULL,
  `apellido` varchar(80) DEFAULT NULL COMMENT 'Apellido de la persona. NULL en las cuentas de rol INSTITUCION, que no son personas.',
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `fecha_baja` date DEFAULT NULL COMMENT 'Cuando se dio de baja. NULL = no fue dado de baja.',
  `ultimo_login` timestamp NULL DEFAULT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_usuarios_inst_username` (`institucion_id`,`username`),
  UNIQUE KEY `uq_usuarios_inst_email` (`institucion_id`,`email`),
  KEY `fk_usuarios_rol` (`rol_id`),
  KEY `idx_usuarios_inst_activo` (`institucion_id`,`activo`),
  CONSTRAINT `fk_usuarios_institucion` FOREIGN KEY (`institucion_id`) REFERENCES ${esquema}.instituciones (`id`),
  CONSTRAINT `fk_usuarios_rol` FOREIGN KEY (`rol_id`) REFERENCES ${esquema}.roles (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Usuarios con acceso al sistema (superadmins y admins). El docente NO es usuario.';

CREATE TABLE ${esquema}.docentes (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL,
  `dni` varchar(15) NOT NULL,
  `legajo` varchar(30) DEFAULT NULL,
  `nombre` varchar(80) NOT NULL,
  `apellido` varchar(80) NOT NULL,
  `email` varchar(120) DEFAULT NULL,
  `telefono` varchar(30) DEFAULT NULL,
  `fecha_alta` date NOT NULL,
  `fecha_baja` date DEFAULT NULL COMMENT 'Fecha en que el docente dejo de prestar servicios. NULL = no fue dado de baja.',
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_docentes_inst_dni` (`institucion_id`,`dni`),
  UNIQUE KEY `uq_docentes_inst_legajo` (`institucion_id`,`legajo`),
  KEY `idx_docentes_inst_activo` (`institucion_id`,`activo`),
  KEY `idx_docentes_apellido` (`apellido`,`nombre`),
  CONSTRAINT `fk_docentes_institucion` FOREIGN KEY (`institucion_id`) REFERENCES ${esquema}.instituciones (`id`),
  CONSTRAINT `ck_docentes_baja_posterior_al_alta` CHECK (`fecha_baja` is null or `fecha_baja` >= `fecha_alta`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Personal docente - sujetos pasivos del sistema (RF-07)';

CREATE TABLE ${esquema}.carreras (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL,
  `codigo` varchar(30) NOT NULL,
  `nombre` varchar(150) NOT NULL,
  `duracion_anios` smallint(6) NOT NULL DEFAULT 3 COMMENT 'Cuantos anios dura la carrera. Acota el anio de sus materias.',
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `fecha_baja` date DEFAULT NULL COMMENT 'Cuando se dio de baja. NULL = no fue dada de baja.',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_carreras_inst_codigo` (`institucion_id`,`codigo`),
  KEY `idx_carreras_inst_activo` (`institucion_id`,`activo`),
  CONSTRAINT `fk_carreras_institucion` FOREIGN KEY (`institucion_id`) REFERENCES ${esquema}.instituciones (`id`),
  CONSTRAINT `ck_carreras_duracion_razonable` CHECK (`duracion_anios` between 1 and 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Carreras / programas academicos (RF-11)';

CREATE TABLE ${esquema}.materias (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL COMMENT 'Denormalizado para reforzar aislamiento multi-tenant',
  `carrera_id` bigint(20) NOT NULL,
  `codigo` varchar(30) NOT NULL,
  `nombre` varchar(150) NOT NULL,
  `anio` smallint(6) NOT NULL DEFAULT 1 COMMENT 'Anio de la carrera en el que se cursa esta materia.',
  `docente_titular_id` bigint(20) DEFAULT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `fecha_baja` date DEFAULT NULL COMMENT 'Cuando se dio de baja. NULL = no fue dada de baja.',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_materias_inst_codigo` (`institucion_id`,`codigo`),
  KEY `fk_materias_carrera` (`carrera_id`),
  KEY `fk_materias_docente_titular` (`docente_titular_id`),
  KEY `idx_materias_inst_carrera_activo` (`institucion_id`,`carrera_id`,`activo`),
  CONSTRAINT `fk_materias_carrera` FOREIGN KEY (`carrera_id`) REFERENCES ${esquema}.carreras (`id`),
  CONSTRAINT `fk_materias_docente_titular` FOREIGN KEY (`docente_titular_id`) REFERENCES ${esquema}.docentes (`id`),
  CONSTRAINT `fk_materias_institucion` FOREIGN KEY (`institucion_id`) REFERENCES ${esquema}.instituciones (`id`),
  CONSTRAINT `ck_materias_anio_positivo` CHECK (`anio` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Materias asociadas a carrera y opcionalmente a docente titular (RF-12)';

CREATE TABLE ${esquema}.codigos_verificacion (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL,
  `usuario_id` bigint(20) NOT NULL,
  `proposito` varchar(30) NOT NULL COMMENT 'VERIFICACION_EMAIL | RECUPERACION_PASSWORD',
  `email` varchar(120) NOT NULL COMMENT 'Direccion a la que se envio el codigo',
  `codigo_hash` varchar(255) NOT NULL COMMENT 'Hash del OTP - nunca en texto plano',
  `expira_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Despues de este instante el codigo no sirve',
  `usado_en` timestamp NULL DEFAULT NULL COMMENT 'Cuando se consumio. NOT NULL = ya usado',
  `intentos` smallint(6) NOT NULL DEFAULT 0 COMMENT 'Validaciones fallidas acumuladas',
  `ip_solicitud` varchar(45) DEFAULT NULL COMMENT 'IP desde donde se pidio el codigo (auditoria)',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `fk_codigos_verificacion_inst` (`institucion_id`),
  KEY `ix_codigos_verificacion_busqueda` (`usuario_id`,`proposito`,`usado_en`),
  KEY `ix_codigos_verificacion_expira` (`expira_en`),
  CONSTRAINT `fk_codigos_verificacion_inst` FOREIGN KEY (`institucion_id`) REFERENCES ${esquema}.instituciones (`id`),
  CONSTRAINT `fk_codigos_verificacion_usuario` FOREIGN KEY (`usuario_id`) REFERENCES ${esquema}.usuarios (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Codigos de un solo uso para verificar correo y recuperar contrasena';

CREATE TABLE ${esquema}.puestos_captura (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL,
  `nombre` varchar(80) NOT NULL COMMENT 'Como lo llama la institucion: "Secretaria PC-1"',
  `token_hash` varchar(255) NOT NULL COMMENT 'Hash del token que vive en la cookie del equipo - nunca en texto plano',
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `fecha_baja` date DEFAULT NULL COMMENT 'Cuando se revoco. NULL = sigue habilitado',
  `designado_por` bigint(20) DEFAULT NULL COMMENT 'Usuario que autorizo el equipo. NULL si esa cuenta se borro',
  `ultimo_uso_en` timestamp NULL DEFAULT NULL COMMENT 'Ultima vez que el puesto paso el control',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_puestos_captura_token` (`token_hash`),
  UNIQUE KEY `uq_puestos_captura_nombre` (`institucion_id`,`nombre`),
  KEY `fk_puestos_captura_designante` (`designado_por`),
  KEY `ix_puestos_captura_inst` (`institucion_id`,`activo`),
  CONSTRAINT `fk_puestos_captura_designante` FOREIGN KEY (`designado_por`) REFERENCES ${esquema}.usuarios (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_puestos_captura_inst` FOREIGN KEY (`institucion_id`) REFERENCES ${esquema}.instituciones (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Equipos autorizados a capturar datos biometricos (ADR-0015)';

CREATE TABLE ${esquema}.modelos_faciales (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `docente_id` bigint(20) NOT NULL,
  `embedding_cifrado` longblob NOT NULL COMMENT 'Modelo LBPH entrenado y serializado, cifrado con AES (Spring Security Crypto)',
  `algoritmo` varchar(50) NOT NULL COMMENT 'Ej: LBPH, FaceNet, ArcFace',
  `version_algoritmo` varchar(20) NOT NULL,
  `dimensiones` smallint(6) NOT NULL COMMENT 'Largo del vector (ej: 128, 512)',
  `activo` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Solo 1 activo por docente (validado en aplicacion)',
  `registrado_por_usuario_id` bigint(20) NOT NULL,
  `fecha_registro` timestamp NOT NULL DEFAULT current_timestamp(),
  `fecha_baja` timestamp NULL DEFAULT NULL COMMENT 'Re-registro RF-09: el modelo anterior se da de baja',
  PRIMARY KEY (`id`),
  KEY `fk_modelos_faciales_usuario` (`registrado_por_usuario_id`),
  KEY `idx_modelos_faciales_docente_activo` (`docente_id`,`activo`),
  CONSTRAINT `fk_modelos_faciales_docente` FOREIGN KEY (`docente_id`) REFERENCES ${esquema}.docentes (`id`),
  CONSTRAINT `fk_modelos_faciales_usuario` FOREIGN KEY (`registrado_por_usuario_id`) REFERENCES ${esquema}.usuarios (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Embeddings biometricos cifrados (RF-08, RF-09, RNF-07, RNF-08). Sin fotografias.';

CREATE TABLE ${esquema}.consentimientos_biometricos (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `docente_id` bigint(20) NOT NULL,
  `version_terminos` varchar(20) NOT NULL COMMENT 'Version del texto de consentimiento firmado',
  `metodo` varchar(20) NOT NULL,
  `documento_url` varchar(255) DEFAULT NULL COMMENT 'Ruta al documento firmado (opcional)',
  `fecha_consentimiento` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `fecha_revocacion` timestamp NULL DEFAULT NULL COMMENT 'Si no es null, el consentimiento fue revocado (Derechos ARCO - RNF-14)',
  `vigente` tinyint(1) NOT NULL DEFAULT 1,
  `registrado_por_usuario_id` bigint(20) NOT NULL,
  `ip_otorgamiento` varchar(45) DEFAULT NULL COMMENT 'IPv4 / IPv6 desde donde se cargo el consentimiento',
  `user_agent_otorgamiento` varchar(500) DEFAULT NULL COMMENT 'User-Agent del navegador que cargo el consentimiento',
  `revocado_por_usuario_id` bigint(20) DEFAULT NULL COMMENT 'Usuario que ejecuto la revocacion (NULL si no fue revocado)',
  `ip_revocacion` varchar(45) DEFAULT NULL COMMENT 'IP desde donde se ejecuto la revocacion',
  `user_agent_revocacion` varchar(500) DEFAULT NULL COMMENT 'User-Agent del navegador que revoco',
  `motivo_revocacion` varchar(500) DEFAULT NULL COMMENT 'Texto libre opcional - derecho ARCO (RNF-14)',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Cuando se inserto la fila en la BD (distinto de fecha_consentimiento si fue carga retroactiva)',
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Ultima modificacion del registro',
  PRIMARY KEY (`id`),
  KEY `fk_consentimientos_usuario` (`registrado_por_usuario_id`),
  KEY `idx_consentimientos_docente_vigente` (`docente_id`,`vigente`),
  KEY `fk_consentimientos_revocado_por` (`revocado_por_usuario_id`),
  CONSTRAINT `fk_consentimientos_docente` FOREIGN KEY (`docente_id`) REFERENCES ${esquema}.docentes (`id`),
  CONSTRAINT `fk_consentimientos_revocado_por` FOREIGN KEY (`revocado_por_usuario_id`) REFERENCES ${esquema}.usuarios (`id`),
  CONSTRAINT `fk_consentimientos_usuario` FOREIGN KEY (`registrado_por_usuario_id`) REFERENCES ${esquema}.usuarios (`id`),
  CONSTRAINT `ck_consentimientos_metodo` CHECK (`metodo` in ('ESCRITO','DIGITAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Consentimiento informado del docente para tratamiento biometrico (RF-10, RNF-13)';

CREATE TABLE ${esquema}.comisiones (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `materia_id` bigint(20) NOT NULL,
  `codigo` varchar(30) NOT NULL COMMENT 'Ej: A, B, Noche, Manana',
  `docente_asignado_id` bigint(20) DEFAULT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `fecha_baja` date DEFAULT NULL COMMENT 'Cuando se dio de baja. NULL = no fue dada de baja.',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_comisiones_materia_codigo` (`materia_id`,`codigo`),
  KEY `idx_comisiones_docente_activo` (`docente_asignado_id`,`activo`),
  CONSTRAINT `fk_comisiones_docente` FOREIGN KEY (`docente_asignado_id`) REFERENCES ${esquema}.docentes (`id`),
  CONSTRAINT `fk_comisiones_materia` FOREIGN KEY (`materia_id`) REFERENCES ${esquema}.materias (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Comisiones de cada materia con su docente asignado (RF-13)';

CREATE TABLE ${esquema}.horarios (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `comision_id` bigint(20) NOT NULL,
  `dia_semana` tinyint(4) NOT NULL COMMENT '1=Lunes, 7=Domingo (ISO 8601)',
  `hora_inicio` time NOT NULL,
  `hora_fin` time NOT NULL,
  `tolerancia_min` smallint(6) NOT NULL DEFAULT 15 COMMENT 'Minutos de tolerancia para PRESENTE vs TARDE (RF-19)',
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `fecha_baja` date DEFAULT NULL COMMENT 'Cuando se dio de baja. NULL = no fue dado de baja.',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Alta del horario. En filas anteriores a V011, la fecha de la migracion.',
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Ultima modificacion del horario.',
  PRIMARY KEY (`id`),
  KEY `idx_horarios_comision_dia_activo` (`comision_id`,`dia_semana`,`activo`),
  CONSTRAINT `fk_horarios_comision` FOREIGN KEY (`comision_id`) REFERENCES ${esquema}.comisiones (`id`),
  CONSTRAINT `ck_horarios_dia_semana` CHECK (`dia_semana` between 1 and 7),
  CONSTRAINT `ck_horarios_horas` CHECK (`hora_fin` > `hora_inicio`),
  CONSTRAINT `ck_horarios_tolerancia` CHECK (`tolerancia_min` >= 0 and `tolerancia_min` <= 120)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Horarios semanales de cada comision (RF-14)';

CREATE TABLE ${esquema}.asistencias (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL COMMENT 'Denormalizado: refuerza aislamiento y acelera reportes',
  `docente_id` bigint(20) NOT NULL,
  `comision_id` bigint(20) NOT NULL,
  `horario_id` bigint(20) NOT NULL,
  `fecha` date NOT NULL,
  `hora_registrada` time NOT NULL,
  `estado` varchar(15) NOT NULL,
  `metodo` varchar(15) NOT NULL,
  `modelo_facial_id` bigint(20) DEFAULT NULL COMMENT 'Modelo usado para identificar (solo si metodo = AUTOMATICO)',
  `confianza` decimal(5,4) DEFAULT NULL COMMENT 'Score 0.0000 a 1.0000 (solo si metodo = AUTOMATICO)',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_asistencias_doc_horario_fecha` (`docente_id`,`horario_id`,`fecha`),
  KEY `fk_asistencias_horario` (`horario_id`),
  KEY `fk_asistencias_modelo_facial` (`modelo_facial_id`),
  KEY `idx_asistencias_inst_fecha` (`institucion_id`,`fecha`),
  KEY `idx_asistencias_docente_fecha` (`docente_id`,`fecha`),
  KEY `idx_asistencias_comision_fecha` (`comision_id`,`fecha`),
  CONSTRAINT `fk_asistencias_comision` FOREIGN KEY (`comision_id`) REFERENCES ${esquema}.comisiones (`id`),
  CONSTRAINT `fk_asistencias_docente` FOREIGN KEY (`docente_id`) REFERENCES ${esquema}.docentes (`id`),
  CONSTRAINT `fk_asistencias_horario` FOREIGN KEY (`horario_id`) REFERENCES ${esquema}.horarios (`id`),
  CONSTRAINT `fk_asistencias_institucion` FOREIGN KEY (`institucion_id`) REFERENCES ${esquema}.instituciones (`id`),
  CONSTRAINT `fk_asistencias_modelo_facial` FOREIGN KEY (`modelo_facial_id`) REFERENCES ${esquema}.modelos_faciales (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_asistencias_estado` CHECK (`estado` in ('PRESENTE','TARDE','AUSENTE')),
  CONSTRAINT `ck_asistencias_metodo` CHECK (`metodo` in ('AUTOMATICO','MANUAL')),
  CONSTRAINT `ck_asistencias_confianza` CHECK (`confianza` is null or `confianza` >= 0 and `confianza` <= 1),
  CONSTRAINT `ck_asistencias_metodo_modelo` CHECK (`metodo` = 'MANUAL' and `modelo_facial_id` is null and `confianza` is null or `metodo` = 'AUTOMATICO')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Registros de asistencia - automatica o manual (RF-17 a RF-21)';

CREATE TABLE ${esquema}.asistencias_manuales (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `asistencia_id` bigint(20) NOT NULL,
  `usuario_id` bigint(20) NOT NULL COMMENT 'Admin que registro manualmente',
  `motivo_id` smallint(6) NOT NULL,
  `detalle_adicional` text DEFAULT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_asistencias_manuales_asistencia` (`asistencia_id`),
  KEY `fk_asistencias_manuales_usuario` (`usuario_id`),
  KEY `fk_asistencias_manuales_motivo` (`motivo_id`),
  CONSTRAINT `fk_asistencias_manuales_asistencia` FOREIGN KEY (`asistencia_id`) REFERENCES ${esquema}.asistencias (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_asistencias_manuales_motivo` FOREIGN KEY (`motivo_id`) REFERENCES ${esquema}.motivos_carga_manual (`id`),
  CONSTRAINT `fk_asistencias_manuales_usuario` FOREIGN KEY (`usuario_id`) REFERENCES ${esquema}.usuarios (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Detalle de carga manual de asistencia con admin responsable y motivo (RF-22 a RF-24)';

CREATE TABLE ${esquema}.justificaciones_ausencia (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `asistencia_id` bigint(20) NOT NULL,
  `usuario_id` bigint(20) NOT NULL COMMENT 'Admin que justifico',
  `motivo` text NOT NULL,
  `documento_url` varchar(255) DEFAULT NULL COMMENT 'Adjunto opcional (certificado, etc.)',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_justificaciones_asistencia` (`asistencia_id`),
  KEY `fk_justificaciones_usuario` (`usuario_id`),
  CONSTRAINT `fk_justificaciones_asistencia` FOREIGN KEY (`asistencia_id`) REFERENCES ${esquema}.asistencias (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_justificaciones_usuario` FOREIGN KEY (`usuario_id`) REFERENCES ${esquema}.usuarios (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Justificacion de ausencias con admin responsable y motivo (RF-25, RF-26)';


-- =============================================================================
--  Catalogos del sistema
-- =============================================================================
--  Sin estas filas la aplicacion no arranca de forma util: sin roles nadie puede
--  iniciar sesion, y sin motivos no se puede cargar una asistencia a mano.
--  creado_en se deja al DEFAULT para que refleje la fecha real de instalacion.

INSERT INTO ${esquema}.roles (id, codigo, descripcion) VALUES
  (1, 'INSTITUCION', 'Cuenta institucional - gestiona los administradores'),
  (2, 'ADMIN',       'Personal administrativo - opera el sistema dia a dia');

INSERT INTO ${esquema}.motivos_carga_manual (id, codigo, descripcion, activo) VALUES
  (1, 'FALLA_CAMARA',         'Falla tecnica de la camara web',                   1),
  (2, 'FALLA_RECONOCIMIENTO', 'Falla en el algoritmo de reconocimiento facial',   1),
  (3, 'NO_REGISTRADO',        'Docente no registrado facialmente en el sistema',  1),
  (4, 'OTRO',                 'Otro motivo (detallar en texto libre)',            1);
