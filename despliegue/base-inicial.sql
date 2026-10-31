-- =============================================================================
--  base-inicial.sql
--  El esquema hasta V038 y el historial de Flyway, para crear una base NUEVA en un
--  MariaDB con soporte (TD-011). No se aplica sobre una base que ya tenga datos.
-- =============================================================================
--  POR QUE EXISTE
--  V001 no aplica de MariaDB 10.6 en adelante, y una migracion aplicada no se edita.
--  Con este archivo, una base nueva no pasa por V001: Flyway encuentra su historial
--  hasta V038, comprueba las huellas y sigue con las migraciones posteriores.
--
--  COMO SE USA
--  Una sola vez, sobre un motor vacio y con un usuario que pueda crear bases. El
--  contenedor de MariaDB lo corre solo si esta en /docker-entrypoint-initdb.d.
--    mariadb --default-character-set=utf8mb4 -u root -p < despliegue/base-inicial.sql
--
--  NO SE EDITA A MANO NI SE REGENERA POR CADA MIGRACION
--  Las posteriores a V038 las aplica Flyway, igual que en cualquier otra base. Lo
--  genero mysqldump despues de aplicar V001 a V038 sobre MariaDB 10.4.34, sin datos
--  salvo los catalogos sembrados y el historial. No tiene datos personales.
--  BaseInicialIT comprueba en el CI que Flyway lo sigue aceptando.
-- =============================================================================


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `asistenciautomatica` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */;

USE `asistenciautomatica`;
DROP TABLE IF EXISTS `asistencias`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `asistencias` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL COMMENT 'Denormalizado: refuerza aislamiento y acelera reportes',
  `docente_id` bigint(20) NOT NULL,
  `comision_id` bigint(20) NOT NULL,
  `horario_id` bigint(20) NOT NULL,
  `bloque_id` bigint(20) DEFAULT NULL COMMENT 'Bloque de presencia que cubre esta clase. NULL en registros previos a V019.',
  `puesto_id` bigint(20) DEFAULT NULL COMMENT 'Equipo desde el que se registro. NULL en cargas manuales, ausencias generadas y marcas previas a V025.',
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
  KEY `fk_asistencias_bloque` (`bloque_id`),
  KEY `fk_asistencias_puesto` (`puesto_id`),
  CONSTRAINT `fk_asistencias_bloque` FOREIGN KEY (`bloque_id`) REFERENCES `bloques_presencia` (`id`),
  CONSTRAINT `fk_asistencias_comision` FOREIGN KEY (`comision_id`) REFERENCES `comisiones` (`id`),
  CONSTRAINT `fk_asistencias_docente` FOREIGN KEY (`docente_id`) REFERENCES `docentes` (`id`),
  CONSTRAINT `fk_asistencias_horario` FOREIGN KEY (`horario_id`) REFERENCES `horarios` (`id`),
  CONSTRAINT `fk_asistencias_institucion` FOREIGN KEY (`institucion_id`) REFERENCES `instituciones` (`id`),
  CONSTRAINT `fk_asistencias_modelo_facial` FOREIGN KEY (`modelo_facial_id`) REFERENCES `modelos_faciales` (`id`),
  CONSTRAINT `fk_asistencias_puesto` FOREIGN KEY (`puesto_id`) REFERENCES `puestos_captura` (`id`),
  CONSTRAINT `ck_asistencias_estado` CHECK (`estado` in ('PRESENTE','TARDE','AUSENTE')),
  CONSTRAINT `ck_asistencias_confianza` CHECK (`confianza` is null or `confianza` >= 0 and `confianza` <= 1),
  CONSTRAINT `ck_asistencias_metodo` CHECK (`metodo` in ('AUTOMATICO','MANUAL','SISTEMA')),
  CONSTRAINT `ck_asistencias_metodo_modelo` CHECK (`metodo` = 'AUTOMATICO' or `modelo_facial_id` is null and `confianza` is null),
  CONSTRAINT `ck_asistencias_sistema` CHECK (`metodo` <> 'SISTEMA' or `estado` = 'AUSENTE' and `bloque_id` is null),
  CONSTRAINT `ck_asistencias_camara_presente` CHECK (`metodo` <> 'AUTOMATICO' or `estado` <> 'AUSENTE')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Registros de asistencia - automatica o manual (RF-17 a RF-21)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `asistencias_manuales`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `asistencias_manuales` (
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
  CONSTRAINT `fk_asistencias_manuales_asistencia` FOREIGN KEY (`asistencia_id`) REFERENCES `asistencias` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_asistencias_manuales_motivo` FOREIGN KEY (`motivo_id`) REFERENCES `motivos_carga_manual` (`id`),
  CONSTRAINT `fk_asistencias_manuales_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Detalle de carga manual de asistencia con admin responsable y motivo (RF-22 a RF-24)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bloques_presencia`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `bloques_presencia` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL COMMENT 'Denormalizado: refuerza aislamiento y acelera reportes',
  `docente_id` bigint(20) NOT NULL,
  `fecha` date NOT NULL,
  `hora_entrada` time NOT NULL,
  `hora_salida` time DEFAULT NULL COMMENT 'NULL mientras el bloque siga abierto.',
  `origen_entrada` varchar(15) NOT NULL,
  `origen_salida` varchar(15) DEFAULT NULL COMMENT 'PRESUNTO = la completo el sistema, nadie la observo (RF-80).',
  `modelo_facial_entrada_id` bigint(20) DEFAULT NULL COMMENT 'Modelo usado para abrir el bloque (solo si origen = AUTOMATICO)',
  `confianza_entrada` decimal(5,4) DEFAULT NULL COMMENT 'Score 0.0000 a 1.0000',
  `abierto_por_usuario_id` bigint(20) DEFAULT NULL COMMENT 'Admin que abrio el bloque sin camara. NULL en las entradas por rostro.',
  `motivo_entrada_id` smallint(6) DEFAULT NULL COMMENT 'Motivo del catalogo compartido con la carga manual (RF-23).',
  `detalle_entrada` text DEFAULT NULL COMMENT 'Texto libre del admin. Obligatorio cuando el motivo es OTRO, validado en el service.',
  `modelo_facial_salida_id` bigint(20) DEFAULT NULL COMMENT 'Modelo usado para cerrar el bloque (solo si origen = AUTOMATICO)',
  `confianza_salida` decimal(5,4) DEFAULT NULL COMMENT 'Score 0.0000 a 1.0000',
  `estado_cierre` varchar(20) NOT NULL DEFAULT 'ABIERTO',
  `estado_salida` varchar(15) DEFAULT NULL COMMENT 'Como se fue. NULL mientras el bloque siga abierto.',
  `puesto_id` bigint(20) DEFAULT NULL COMMENT 'Equipo desde el que se abrio el bloque. NULL en los previos a V025.',
  `puesto_salida_id` bigint(20) DEFAULT NULL COMMENT 'Equipo desde el que se registro la salida. NULL si la cerro el job o un admin sin equipo.',
  `cerrado_por_usuario_id` bigint(20) DEFAULT NULL COMMENT 'Admin que cerro o corrigio la salida a mano. NULL en los otros cierres.',
  `motivo_cierre_id` smallint(6) DEFAULT NULL COMMENT 'Motivo del catalogo compartido con la carga manual (RF-23).',
  `detalle_cierre` text DEFAULT NULL COMMENT 'Texto libre del admin. Obligatorio cuando el motivo es OTRO, validado en el service.',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `bloque_abierto_de` bigint(20) GENERATED ALWAYS AS (if(`estado_cierre` = 'ABIERTO',`docente_id`,NULL)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_bloques_doc_fecha_entrada` (`docente_id`,`fecha`,`hora_entrada`),
  UNIQUE KEY `uq_bloques_un_solo_abierto_por_docente` (`bloque_abierto_de`),
  KEY `idx_bloques_inst_fecha` (`institucion_id`,`fecha`),
  KEY `idx_bloques_docente_fecha` (`docente_id`,`fecha`),
  KEY `idx_bloques_pendientes` (`institucion_id`,`estado_cierre`,`fecha`),
  KEY `fk_bloques_modelo_entrada` (`modelo_facial_entrada_id`),
  KEY `fk_bloques_modelo_salida` (`modelo_facial_salida_id`),
  KEY `fk_bloques_cerrado_por` (`cerrado_por_usuario_id`),
  KEY `fk_bloques_motivo_cierre` (`motivo_cierre_id`),
  KEY `fk_bloques_puesto` (`puesto_id`),
  KEY `fk_bloques_abierto_por` (`abierto_por_usuario_id`),
  KEY `fk_bloques_motivo_entrada` (`motivo_entrada_id`),
  KEY `fk_bloques_puesto_salida` (`puesto_salida_id`),
  CONSTRAINT `fk_bloques_abierto_por` FOREIGN KEY (`abierto_por_usuario_id`) REFERENCES `usuarios` (`id`),
  CONSTRAINT `fk_bloques_cerrado_por` FOREIGN KEY (`cerrado_por_usuario_id`) REFERENCES `usuarios` (`id`),
  CONSTRAINT `fk_bloques_docente` FOREIGN KEY (`docente_id`) REFERENCES `docentes` (`id`),
  CONSTRAINT `fk_bloques_institucion` FOREIGN KEY (`institucion_id`) REFERENCES `instituciones` (`id`),
  CONSTRAINT `fk_bloques_modelo_entrada` FOREIGN KEY (`modelo_facial_entrada_id`) REFERENCES `modelos_faciales` (`id`),
  CONSTRAINT `fk_bloques_modelo_salida` FOREIGN KEY (`modelo_facial_salida_id`) REFERENCES `modelos_faciales` (`id`),
  CONSTRAINT `fk_bloques_motivo_cierre` FOREIGN KEY (`motivo_cierre_id`) REFERENCES `motivos_carga_manual` (`id`),
  CONSTRAINT `fk_bloques_motivo_entrada` FOREIGN KEY (`motivo_entrada_id`) REFERENCES `motivos_carga_manual` (`id`),
  CONSTRAINT `fk_bloques_puesto` FOREIGN KEY (`puesto_id`) REFERENCES `puestos_captura` (`id`),
  CONSTRAINT `fk_bloques_puesto_salida` FOREIGN KEY (`puesto_salida_id`) REFERENCES `puestos_captura` (`id`),
  CONSTRAINT `ck_bloques_origen_entrada` CHECK (`origen_entrada` in ('AUTOMATICO','MANUAL')),
  CONSTRAINT `ck_bloques_origen_salida` CHECK (`origen_salida` is null or `origen_salida` in ('AUTOMATICO','MANUAL','PRESUNTO')),
  CONSTRAINT `ck_bloques_estado_cierre` CHECK (`estado_cierre` in ('ABIERTO','CERRADO_POR_ROSTRO','CERRADO_POR_ADMIN','SIN_CIERRE')),
  CONSTRAINT `ck_bloques_estado_salida` CHECK (`estado_salida` is null or `estado_salida` in ('EN_HORA','ANTICIPADA','SIN_MARCA')),
  CONSTRAINT `ck_bloques_confianza_entrada` CHECK (`confianza_entrada` is null or `confianza_entrada` >= 0 and `confianza_entrada` <= 1),
  CONSTRAINT `ck_bloques_confianza_salida` CHECK (`confianza_salida` is null or `confianza_salida` >= 0 and `confianza_salida` <= 1),
  CONSTRAINT `ck_bloques_cierre_coherente` CHECK (`estado_cierre` = 'ABIERTO' and `hora_salida` is null and `origen_salida` is null and `estado_salida` is null or `estado_cierre` <> 'ABIERTO' and `hora_salida` is not null and `origen_salida` is not null and `estado_salida` is not null),
  CONSTRAINT `ck_bloques_salida_posterior` CHECK (`hora_salida` is null or `hora_salida` > `hora_entrada`),
  CONSTRAINT `ck_bloques_entrada_modelo` CHECK (`origen_entrada` = 'AUTOMATICO' or `modelo_facial_entrada_id` is null and `confianza_entrada` is null),
  CONSTRAINT `ck_bloques_salida_modelo` CHECK (`origen_salida` = 'AUTOMATICO' or `modelo_facial_salida_id` is null and `confianza_salida` is null),
  CONSTRAINT `ck_bloques_cierre_admin` CHECK (`estado_cierre` = 'CERRADO_POR_ADMIN' and `motivo_cierre_id` is not null or `estado_cierre` <> 'CERRADO_POR_ADMIN' and `motivo_cierre_id` is null and `cerrado_por_usuario_id` is null and `detalle_cierre` is null),
  CONSTRAINT `ck_bloques_entrada_admin` CHECK (`origen_entrada` = 'MANUAL' and `motivo_entrada_id` is not null or `origen_entrada` <> 'MANUAL' and `motivo_entrada_id` is null and `abierto_por_usuario_id` is null and `detalle_entrada` is null),
  CONSTRAINT `ck_bloques_puesto_salida_con_salida` CHECK (`puesto_salida_id` is null or `hora_salida` is not null),
  CONSTRAINT `ck_bloques_cierre_origen` CHECK (`estado_cierre` = 'ABIERTO' or `estado_cierre` = 'CERRADO_POR_ROSTRO' and `origen_salida` = 'AUTOMATICO' and `estado_salida` in ('EN_HORA','ANTICIPADA') or `estado_cierre` = 'CERRADO_POR_ADMIN' and `origen_salida` = 'MANUAL' and `estado_salida` in ('EN_HORA','ANTICIPADA') or `estado_cierre` = 'SIN_CIERRE' and `origen_salida` = 'PRESUNTO' and `estado_salida` = 'SIN_MARCA')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Lapso continuo de permanencia de un docente, con marca de entrada y de salida (RF-74 a RF-83, ADR-0017).';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cambios_identidad`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cambios_identidad` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL,
  `persona_id` bigint(20) NOT NULL,
  `usuario_id` bigint(20) NOT NULL COMMENT 'Quien hizo el cambio.',
  `campo` varchar(20) NOT NULL,
  `valor_anterior` varchar(120) DEFAULT NULL COMMENT 'NULL cuando el campo estaba vacio.',
  `valor_nuevo` varchar(120) DEFAULT NULL,
  `origen` varchar(20) NOT NULL COMMENT 'Desde que pantalla se hizo el cambio.',
  `fecha` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_cambios_persona_fecha` (`persona_id`,`fecha`),
  KEY `idx_cambios_institucion` (`institucion_id`),
  KEY `fk_cambios_usuario` (`usuario_id`),
  CONSTRAINT `fk_cambios_institucion` FOREIGN KEY (`institucion_id`) REFERENCES `instituciones` (`id`),
  CONSTRAINT `fk_cambios_persona` FOREIGN KEY (`persona_id`) REFERENCES `personas` (`id`),
  CONSTRAINT `fk_cambios_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`),
  CONSTRAINT `ck_cambios_origen` CHECK (`origen` in ('DOCENTE','USUARIO','REINGRESO')),
  CONSTRAINT `ck_cambios_campo` CHECK (`campo` in ('DNI','NOMBRE','APELLIDO','EMAIL','TELEFONO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Historial de cambios sobre los datos de identidad de una persona (ADR-0016).';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `carreras`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `carreras` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL,
  `codigo` varchar(30) NOT NULL,
  `nombre` varchar(150) NOT NULL,
  `duracion_anios` smallint(6) NOT NULL DEFAULT 3 COMMENT 'Cuantos anios dura la carrera. Acota el anio de sus materias.',
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `fecha_baja` date DEFAULT NULL COMMENT 'Cuando se dio de baja. NULL = no fue dada de baja.',
  `dado_de_baja_por` bigint(20) DEFAULT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_carreras_inst_codigo` (`institucion_id`,`codigo`),
  KEY `idx_carreras_inst_activo` (`institucion_id`,`activo`),
  KEY `fk_carreras_baja_usuario` (`dado_de_baja_por`),
  CONSTRAINT `fk_carreras_baja_usuario` FOREIGN KEY (`dado_de_baja_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_carreras_institucion` FOREIGN KEY (`institucion_id`) REFERENCES `instituciones` (`id`),
  CONSTRAINT `ck_carreras_duracion_razonable` CHECK (`duracion_anios` between 1 and 10),
  CONSTRAINT `ck_carreras_baja_coherente` CHECK (`activo` = 1 and `fecha_baja` is null or `activo` = 0 and `fecha_baja` is not null)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Carreras / programas academicos (RF-11)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ciclos_lectivos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ciclos_lectivos` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL,
  `anio` smallint(6) NOT NULL COMMENT 'Ano calendario: 2026, 2027. No confundir con materias.anio, que es el ano del plan.',
  `fecha_inicio` date NOT NULL COMMENT 'Primer dia del ciclo. Acota a sus periodos.',
  `fecha_fin` date NOT NULL COMMENT 'Ultimo dia del ciclo.',
  `estado` varchar(20) NOT NULL DEFAULT 'PREPARACION' COMMENT 'PREPARACION | ACTIVO | CERRADO. Cerrado congela la estructura, no las asistencias.',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `cerrado_en` timestamp NULL DEFAULT NULL COMMENT 'Ultimo cierre. Se conserva al reabrir: si esta cerrado lo dice estado, no esta columna.',
  `cerrado_por` bigint(20) DEFAULT NULL COMMENT 'Quien lo cerro. NULL si esa cuenta ya no existe.',
  `reabierto_en` timestamp NULL DEFAULT NULL COMMENT 'Ultima vez que se reabrio despues de cerrado. NULL = nunca.',
  `reabierto_por` bigint(20) DEFAULT NULL COMMENT 'Quien lo reabrio. NULL si nunca se reabrio o si esa cuenta ya no existe.',
  `ciclo_activo_de` bigint(20) GENERATED ALWAYS AS (if(`estado` = 'ACTIVO',`institucion_id`,NULL)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ciclos_inst_anio` (`institucion_id`,`anio`),
  UNIQUE KEY `uq_ciclos_un_activo_por_institucion` (`ciclo_activo_de`),
  KEY `ix_ciclos_inst_estado` (`institucion_id`,`estado`),
  KEY `fk_ciclos_cerrado_por` (`cerrado_por`),
  KEY `fk_ciclos_reabierto_por` (`reabierto_por`),
  CONSTRAINT `fk_ciclos_cerrado_por` FOREIGN KEY (`cerrado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_ciclos_institucion` FOREIGN KEY (`institucion_id`) REFERENCES `instituciones` (`id`),
  CONSTRAINT `fk_ciclos_reabierto_por` FOREIGN KEY (`reabierto_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_ciclos_rango` CHECK (`fecha_fin` >= `fecha_inicio`),
  CONSTRAINT `ck_ciclos_anio_razonable` CHECK (`anio` between 2000 and 2200),
  CONSTRAINT `ck_ciclos_estado` CHECK (`estado` in ('PREPARACION','ACTIVO','CERRADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Ano calendario de cursada. Agrupa los periodos y, por ellos, la oferta de comisiones.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `codigos_verificacion`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `codigos_verificacion` (
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
  CONSTRAINT `fk_codigos_verificacion_inst` FOREIGN KEY (`institucion_id`) REFERENCES `instituciones` (`id`),
  CONSTRAINT `fk_codigos_verificacion_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Codigos de un solo uso para verificar correo y recuperar contrasena';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `comisiones`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `comisiones` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `materia_id` bigint(20) NOT NULL,
  `codigo` varchar(30) NOT NULL COMMENT 'Ej: A, B, Noche, Manana',
  `docente_asignado_id` bigint(20) DEFAULT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `fecha_baja` date DEFAULT NULL COMMENT 'Cuando se dio de baja. NULL = no fue dada de baja.',
  `dado_de_baja_por` bigint(20) DEFAULT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `periodo_id` bigint(20) NOT NULL COMMENT 'Tramo del ciclo en el que corre esta comision. Es lo que la ata a un ano concreto.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_comisiones_materia_codigo_periodo` (`materia_id`,`codigo`,`periodo_id`),
  KEY `idx_comisiones_docente_activo` (`docente_asignado_id`,`activo`),
  KEY `fk_comisiones_baja_usuario` (`dado_de_baja_por`),
  KEY `fk_comisiones_periodo` (`periodo_id`),
  CONSTRAINT `fk_comisiones_baja_usuario` FOREIGN KEY (`dado_de_baja_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_comisiones_docente` FOREIGN KEY (`docente_asignado_id`) REFERENCES `docentes` (`id`),
  CONSTRAINT `fk_comisiones_materia` FOREIGN KEY (`materia_id`) REFERENCES `materias` (`id`),
  CONSTRAINT `fk_comisiones_periodo` FOREIGN KEY (`periodo_id`) REFERENCES `periodos_lectivos` (`id`),
  CONSTRAINT `ck_comisiones_baja_coherente` CHECK (`activo` = 1 and `fecha_baja` is null or `activo` = 0 and `fecha_baja` is not null)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Comisiones de cada materia con su docente asignado (RF-13)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `consentimientos_biometricos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `consentimientos_biometricos` (
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
  `consentimiento_vigente_de` bigint(20) GENERATED ALWAYS AS (if(`vigente` = 1,`docente_id`,NULL)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_consentimientos_un_vigente_por_docente` (`consentimiento_vigente_de`),
  KEY `fk_consentimientos_usuario` (`registrado_por_usuario_id`),
  KEY `idx_consentimientos_docente_vigente` (`docente_id`,`vigente`),
  KEY `fk_consentimientos_revocado_por` (`revocado_por_usuario_id`),
  CONSTRAINT `fk_consentimientos_docente` FOREIGN KEY (`docente_id`) REFERENCES `docentes` (`id`),
  CONSTRAINT `fk_consentimientos_revocado_por` FOREIGN KEY (`revocado_por_usuario_id`) REFERENCES `usuarios` (`id`),
  CONSTRAINT `fk_consentimientos_usuario` FOREIGN KEY (`registrado_por_usuario_id`) REFERENCES `usuarios` (`id`),
  CONSTRAINT `ck_consentimientos_metodo` CHECK (`metodo` = 'ESCRITO'),
  CONSTRAINT `ck_consentimientos_vigencia` CHECK (`vigente` = 1 and `fecha_revocacion` is null or `vigente` = 0 and `fecha_revocacion` is not null)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Consentimiento informado del docente para tratamiento biometrico (RF-10, RNF-13)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dias_no_laborables`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `dias_no_laborables` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL,
  `fecha` date NOT NULL COMMENT 'El dia en que no hay clases.',
  `tipo` varchar(20) NOT NULL DEFAULT 'OTRO' COMMENT 'NACIONAL, PROVINCIAL, INSTITUCIONAL, RECESO u OTRO. Es por lo que se filtra el listado.',
  `motivo` varchar(120) NOT NULL COMMENT 'Por que. Se muestra en el listado para poder revisarlo despues.',
  `creado_por` bigint(20) DEFAULT NULL COMMENT 'Quien lo cargo. NULL si esa cuenta ya no existe.',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_dias_inst_fecha` (`institucion_id`,`fecha`),
  KEY `fk_dias_creado_por` (`creado_por`),
  CONSTRAINT `fk_dias_creado_por` FOREIGN KEY (`creado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_dias_institucion` FOREIGN KEY (`institucion_id`) REFERENCES `instituciones` (`id`),
  CONSTRAINT `ck_dias_tipo` CHECK (`tipo` in ('NACIONAL','PROVINCIAL','INSTITUCIONAL','RECESO','OTRO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dias dentro del ciclo en los que no se dicta clase. El job de ausencias los saltea.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `docentes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `docentes` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL,
  `persona_id` bigint(20) NOT NULL,
  `legajo` varchar(30) DEFAULT NULL,
  `fecha_alta` date NOT NULL,
  `fecha_baja` date DEFAULT NULL COMMENT 'Fecha en que el docente dejo de prestar servicios. NULL = no fue dado de baja.',
  `dado_de_baja_por` bigint(20) DEFAULT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_docentes_inst_activo` (`institucion_id`,`activo`),
  KEY `idx_docentes_persona` (`persona_id`),
  KEY `idx_docentes_inst_legajo` (`institucion_id`,`legajo`),
  KEY `fk_docentes_baja_usuario` (`dado_de_baja_por`),
  CONSTRAINT `fk_docentes_baja_usuario` FOREIGN KEY (`dado_de_baja_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_docentes_institucion` FOREIGN KEY (`institucion_id`) REFERENCES `instituciones` (`id`),
  CONSTRAINT `fk_docentes_persona` FOREIGN KEY (`persona_id`) REFERENCES `personas` (`id`),
  CONSTRAINT `ck_docentes_baja_posterior_al_alta` CHECK (`fecha_baja` is null or `fecha_baja` >= `fecha_alta`),
  CONSTRAINT `ck_docentes_baja_coherente` CHECK (`activo` = 1 and `fecha_baja` is null or `activo` = 0 and `fecha_baja` is not null)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Personal docente - sujetos pasivos del sistema (RF-07)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `horarios`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `horarios` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `comision_id` bigint(20) NOT NULL,
  `dia_semana` tinyint(4) NOT NULL COMMENT '1=Lunes, 7=Domingo (ISO 8601)',
  `hora_inicio` time NOT NULL,
  `hora_fin` time NOT NULL,
  `tolerancia_min` smallint(6) NOT NULL DEFAULT 15 COMMENT 'Minutos de tolerancia para PRESENTE vs TARDE (RF-19)',
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `fecha_baja` date DEFAULT NULL COMMENT 'Cuando se dio de baja. NULL = no fue dado de baja.',
  `dado_de_baja_por` bigint(20) DEFAULT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Alta del horario. En filas anteriores a V011, la fecha de la migracion.',
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Ultima modificacion del horario.',
  PRIMARY KEY (`id`),
  KEY `idx_horarios_comision_dia_activo` (`comision_id`,`dia_semana`,`activo`),
  KEY `fk_horarios_baja_usuario` (`dado_de_baja_por`),
  CONSTRAINT `fk_horarios_baja_usuario` FOREIGN KEY (`dado_de_baja_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_horarios_comision` FOREIGN KEY (`comision_id`) REFERENCES `comisiones` (`id`),
  CONSTRAINT `ck_horarios_dia_semana` CHECK (`dia_semana` between 1 and 7),
  CONSTRAINT `ck_horarios_horas` CHECK (`hora_fin` > `hora_inicio`),
  CONSTRAINT `ck_horarios_tolerancia` CHECK (`tolerancia_min` >= 0 and `tolerancia_min` <= 120),
  CONSTRAINT `ck_horarios_baja_coherente` CHECK (`activo` = 1 and `fecha_baja` is null or `activo` = 0 and `fecha_baja` is not null)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Horarios semanales de cada comision (RF-14)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `instituciones`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `instituciones` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(150) NOT NULL,
  `cuit` varchar(13) DEFAULT NULL,
  `direccion` varchar(200) DEFAULT NULL,
  `email_contacto` varchar(120) DEFAULT NULL,
  `telefono_contacto` varchar(30) DEFAULT NULL,
  `umbral_separacion_min` smallint(6) NOT NULL DEFAULT 60 COMMENT 'Minutos de hueco entre clases que las mantienen en el mismo bloque de presencia (RF-76).',
  `max_puestos_habilitados` smallint(6) DEFAULT NULL COMMENT 'Tope de equipos autorizados a la vez. NULL = sin tope.',
  `clave_recuperacion_hash` varchar(100) DEFAULT NULL COMMENT 'Hash de la clave de recuperacion. NULL si la institucion no tiene uno.',
  `clave_recuperacion_creada_en` datetime DEFAULT NULL COMMENT 'Cuando se genero la clave vigente. Sirve para decir cual de las copias impresas vale.',
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `fecha_baja` date DEFAULT NULL COMMENT 'Cuando se dio de baja. NULL = no fue dada de baja.',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_instituciones_nombre` (`nombre`),
  UNIQUE KEY `uq_instituciones_cuit` (`cuit`),
  CONSTRAINT `ck_instituciones_umbral_separacion` CHECK (`umbral_separacion_min` >= 0 and `umbral_separacion_min` <= 240),
  CONSTRAINT `ck_instituciones_max_puestos` CHECK (`max_puestos_habilitados` is null or `max_puestos_habilitados` >= 1),
  CONSTRAINT `ck_instituciones_clave_recuperacion` CHECK (`clave_recuperacion_hash` is null and `clave_recuperacion_creada_en` is null or `clave_recuperacion_hash` is not null and `clave_recuperacion_creada_en` is not null),
  CONSTRAINT `ck_instituciones_baja_coherente` CHECK (`activo` = 1 and `fecha_baja` is null or `activo` = 0 and `fecha_baja` is not null)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Instituciones educativas - tenant root del sistema multi-tenant';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `justificaciones_ausencia`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `justificaciones_ausencia` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `asistencia_id` bigint(20) NOT NULL,
  `usuario_id` bigint(20) NOT NULL COMMENT 'Admin que justifico',
  `motivo` text NOT NULL,
  `documento_url` varchar(255) DEFAULT NULL COMMENT 'Adjunto opcional (certificado, etc.)',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_justificaciones_asistencia` (`asistencia_id`),
  KEY `fk_justificaciones_usuario` (`usuario_id`),
  CONSTRAINT `fk_justificaciones_asistencia` FOREIGN KEY (`asistencia_id`) REFERENCES `asistencias` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_justificaciones_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Justificacion de ausencias con admin responsable y motivo (RF-25, RF-26)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `materias`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `materias` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL COMMENT 'Denormalizado para reforzar aislamiento multi-tenant',
  `carrera_id` bigint(20) NOT NULL,
  `codigo` varchar(30) NOT NULL,
  `nombre` varchar(150) NOT NULL,
  `anio` smallint(6) NOT NULL DEFAULT 1 COMMENT 'Anio de la carrera en el que se cursa esta materia.',
  `docente_titular_id` bigint(20) DEFAULT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `fecha_baja` date DEFAULT NULL COMMENT 'Cuando se dio de baja. NULL = no fue dada de baja.',
  `dado_de_baja_por` bigint(20) DEFAULT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_materias_inst_codigo` (`institucion_id`,`codigo`),
  KEY `fk_materias_carrera` (`carrera_id`),
  KEY `fk_materias_docente_titular` (`docente_titular_id`),
  KEY `idx_materias_inst_carrera_activo` (`institucion_id`,`carrera_id`,`activo`),
  KEY `fk_materias_baja_usuario` (`dado_de_baja_por`),
  CONSTRAINT `fk_materias_baja_usuario` FOREIGN KEY (`dado_de_baja_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_materias_carrera` FOREIGN KEY (`carrera_id`) REFERENCES `carreras` (`id`),
  CONSTRAINT `fk_materias_docente_titular` FOREIGN KEY (`docente_titular_id`) REFERENCES `docentes` (`id`),
  CONSTRAINT `fk_materias_institucion` FOREIGN KEY (`institucion_id`) REFERENCES `instituciones` (`id`),
  CONSTRAINT `ck_materias_anio_positivo` CHECK (`anio` >= 1),
  CONSTRAINT `ck_materias_baja_coherente` CHECK (`activo` = 1 and `fecha_baja` is null or `activo` = 0 and `fecha_baja` is not null)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Materias asociadas a carrera y opcionalmente a docente titular (RF-12)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `modelos_faciales`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `modelos_faciales` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `docente_id` bigint(20) NOT NULL,
  `embedding_cifrado` longblob DEFAULT NULL COMMENT 'NULL cuando el dato fue suprimido. La fila queda como constancia.',
  `algoritmo` varchar(50) NOT NULL COMMENT 'Ej: LBPH, FaceNet, ArcFace',
  `version_algoritmo` varchar(20) NOT NULL,
  `dimensiones` smallint(6) NOT NULL COMMENT 'Largo del vector (ej: 128, 512)',
  `activo` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Solo 1 activo por docente (validado en aplicacion)',
  `registrado_por_usuario_id` bigint(20) NOT NULL,
  `fecha_registro` timestamp NOT NULL DEFAULT current_timestamp(),
  `fecha_baja` timestamp NULL DEFAULT NULL COMMENT 'Re-registro RF-09: el modelo anterior se da de baja',
  `dado_de_baja_por` bigint(20) DEFAULT NULL,
  `fecha_supresion` timestamp NULL DEFAULT NULL COMMENT 'Cuando se borro el embedding. NULL si sigue vigente.',
  `motivo_supresion` varchar(120) DEFAULT NULL COMMENT 'Por que se borro: fin de vinculo, derecho ARCO, etc.',
  `modelo_activo_de` bigint(20) GENERATED ALWAYS AS (if(`activo` = 1,`docente_id`,NULL)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_modelos_un_activo_por_docente` (`modelo_activo_de`),
  KEY `fk_modelos_faciales_usuario` (`registrado_por_usuario_id`),
  KEY `idx_modelos_faciales_docente_activo` (`docente_id`,`activo`),
  KEY `fk_modelos_baja_usuario` (`dado_de_baja_por`),
  CONSTRAINT `fk_modelos_baja_usuario` FOREIGN KEY (`dado_de_baja_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_modelos_faciales_docente` FOREIGN KEY (`docente_id`) REFERENCES `docentes` (`id`),
  CONSTRAINT `fk_modelos_faciales_usuario` FOREIGN KEY (`registrado_por_usuario_id`) REFERENCES `usuarios` (`id`),
  CONSTRAINT `ck_modelos_baja_coherente` CHECK (`activo` = 1 and `fecha_baja` is null or `activo` = 0 and `fecha_baja` is not null)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Embeddings biometricos cifrados (RF-08, RF-09, RNF-07, RNF-08). Sin fotografias.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `motivos_carga_manual`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `motivos_carga_manual` (
  `id` smallint(6) NOT NULL AUTO_INCREMENT,
  `codigo` varchar(40) NOT NULL,
  `descripcion` varchar(150) NOT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `fecha_baja` date DEFAULT NULL COMMENT 'Cuando se dio de baja. NULL = no fue dado de baja.',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Alta del registro. En filas anteriores a V012, la fecha de la migracion.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_motivos_carga_manual_codigo` (`codigo`),
  CONSTRAINT `ck_motivos_baja_coherente` CHECK (`activo` = 1 and `fecha_baja` is null or `activo` = 0 and `fecha_baja` is not null)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Catalogo de motivos predefinidos para carga manual de asistencia (RF-23)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `periodos_lectivos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `periodos_lectivos` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `ciclo_id` bigint(20) NOT NULL,
  `institucion_id` bigint(20) NOT NULL COMMENT 'Denormalizado desde el ciclo, para que el filtro de tenant actue sin JOIN.',
  `nombre` varchar(60) NOT NULL COMMENT 'Anual, 1er cuatrimestre, 2do cuatrimestre. Lo elige la institucion.',
  `fecha_inicio` date NOT NULL,
  `fecha_fin` date NOT NULL,
  `orden` smallint(6) NOT NULL DEFAULT 1 COMMENT 'Para listarlos en un orden que tenga sentido y no por fecha o alfabeto.',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_periodos_ciclo_nombre` (`ciclo_id`,`nombre`),
  KEY `ix_periodos_inst_fechas` (`institucion_id`,`fecha_inicio`,`fecha_fin`),
  CONSTRAINT `fk_periodos_ciclo` FOREIGN KEY (`ciclo_id`) REFERENCES `ciclos_lectivos` (`id`),
  CONSTRAINT `fk_periodos_institucion` FOREIGN KEY (`institucion_id`) REFERENCES `instituciones` (`id`),
  CONSTRAINT `ck_periodos_rango` CHECK (`fecha_fin` >= `fecha_inicio`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Tramo del ciclo en el que corre una comision. Anual y cuatrimestral son el mismo tipo de cosa.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `personas`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `personas` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL,
  `dni` varchar(15) DEFAULT NULL COMMENT 'Puede faltar: una persona creada a partir de una cuenta de acceso no lo trae.',
  `nombre` varchar(80) NOT NULL,
  `apellido` varchar(80) DEFAULT NULL COMMENT 'NULL en las cuentas institucionales, que no son una persona fisica.',
  `email` varchar(120) DEFAULT NULL COMMENT 'Correo de contacto. El de acceso al sistema vive en usuarios.email.',
  `telefono` varchar(30) DEFAULT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_personas_inst_dni` (`institucion_id`,`dni`),
  KEY `idx_personas_apellido` (`apellido`,`nombre`),
  KEY `idx_personas_institucion` (`institucion_id`),
  CONSTRAINT `fk_personas_institucion` FOREIGN KEY (`institucion_id`) REFERENCES `instituciones` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Identidad de una persona dentro de una institucion. No se borra nunca.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `puestos_captura`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `puestos_captura` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL,
  `nombre` varchar(80) NOT NULL COMMENT 'Como lo llama la institucion: "Secretaria PC-1"',
  `token_hash` varchar(255) NOT NULL COMMENT 'Hash del token que vive en la cookie del equipo - nunca en texto plano',
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `kiosco_habilitado` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Si este equipo puede tomar asistencia sin sesion abierta (RF-84, RF-85).',
  `kiosco_habilitado_en` timestamp NULL DEFAULT NULL COMMENT 'Cuando se habilito por ultima vez. Se conserva aunque despues se deshabilite.',
  `kiosco_habilitado_por` bigint(20) DEFAULT NULL COMMENT 'Quien lo habilito. NULL si esa cuenta se suprimio.',
  `camara_dispositivo_id` varchar(255) DEFAULT NULL COMMENT 'deviceId que da el navegador. Vale solo en esa maquina. NULL = la predeterminada del sistema.',
  `camara_etiqueta` varchar(120) DEFAULT NULL COMMENT 'Nombre que reporto el dispositivo, para poder mostrar que camara usa el puesto.',
  `camara_elegida_en` timestamp NULL DEFAULT NULL COMMENT 'Ultimo cambio de camara. Se conserva al volver a la predeterminada.',
  `fecha_baja` date DEFAULT NULL COMMENT 'Cuando se revoco. NULL = sigue habilitado',
  `dado_de_baja_por` bigint(20) DEFAULT NULL,
  `designado_por` bigint(20) DEFAULT NULL COMMENT 'Usuario que autorizo el equipo. NULL si esa cuenta se borro',
  `ultimo_uso_en` timestamp NULL DEFAULT NULL COMMENT 'Ultima vez que el puesto paso el control',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_puestos_captura_token` (`token_hash`),
  UNIQUE KEY `uq_puestos_captura_nombre` (`institucion_id`,`nombre`),
  KEY `fk_puestos_captura_designante` (`designado_por`),
  KEY `ix_puestos_captura_inst` (`institucion_id`,`activo`),
  KEY `fk_puestos_baja_usuario` (`dado_de_baja_por`),
  KEY `fk_puestos_kiosco_habilitado_por` (`kiosco_habilitado_por`),
  CONSTRAINT `fk_puestos_baja_usuario` FOREIGN KEY (`dado_de_baja_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_puestos_captura_designante` FOREIGN KEY (`designado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_puestos_captura_inst` FOREIGN KEY (`institucion_id`) REFERENCES `instituciones` (`id`),
  CONSTRAINT `fk_puestos_kiosco_habilitado_por` FOREIGN KEY (`kiosco_habilitado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_puestos_kiosco_fechado` CHECK (`kiosco_habilitado` = 0 or `kiosco_habilitado_en` is not null),
  CONSTRAINT `ck_puestos_camara_con_nombre` CHECK (`camara_dispositivo_id` is null and `camara_etiqueta` is null or `camara_dispositivo_id` is not null and `camara_etiqueta` is not null),
  CONSTRAINT `ck_puestos_baja_coherente` CHECK (`activo` = 1 and `fecha_baja` is null or `activo` = 0 and `fecha_baja` is not null)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Equipos autorizados a capturar datos biometricos (ADR-0015)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `roles` (
  `id` smallint(6) NOT NULL AUTO_INCREMENT,
  `codigo` varchar(30) NOT NULL,
  `descripcion` varchar(120) NOT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Alta del registro. En filas anteriores a V012, la fecha de la migracion.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_roles_codigo` (`codigo`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Catalogo global de roles del sistema';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `usuarios`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `usuarios` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL,
  `persona_id` bigint(20) DEFAULT NULL COMMENT 'NULL en las cuentas institucionales, que no representan a una persona fisica.',
  `rol_id` smallint(6) NOT NULL,
  `username` varchar(60) NOT NULL,
  `email` varchar(120) NOT NULL,
  `email_verificado_en` timestamp NULL DEFAULT NULL COMMENT 'Cuando la persona confirmo que controla este correo. NULL = sin verificar',
  `password_hash` varchar(255) NOT NULL COMMENT 'BCrypt - nunca en texto plano (RNF-06)',
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `fecha_baja` date DEFAULT NULL COMMENT 'Cuando se dio de baja. NULL = no fue dado de baja.',
  `dado_de_baja_por` bigint(20) DEFAULT NULL,
  `ultimo_login` timestamp NULL DEFAULT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `password_cambiada_en` timestamp NULL DEFAULT NULL COMMENT 'Ultima vez que se fijo una contrasena nueva. NULL = nunca desde V021; no bloquea.',
  `cambio_password_habilitado_en` timestamp NULL DEFAULT NULL COMMENT 'Destrabe administrativo. Vale si es posterior a password_cambiada_en; se consume al cambiar.',
  `cambio_password_habilitado_por` bigint(20) DEFAULT NULL COMMENT 'Quien levanto el bloqueo. NULL si nadie lo hizo o si esa cuenta ya no existe.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_usuarios_inst_username` (`institucion_id`,`username`),
  UNIQUE KEY `uq_usuarios_inst_email` (`institucion_id`,`email`),
  UNIQUE KEY `uq_usuarios_persona` (`persona_id`),
  KEY `fk_usuarios_rol` (`rol_id`),
  KEY `idx_usuarios_inst_activo` (`institucion_id`,`activo`),
  KEY `fk_usuarios_baja_usuario` (`dado_de_baja_por`),
  KEY `fk_usuarios_habilito_cambio` (`cambio_password_habilitado_por`),
  CONSTRAINT `fk_usuarios_baja_usuario` FOREIGN KEY (`dado_de_baja_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_usuarios_habilito_cambio` FOREIGN KEY (`cambio_password_habilitado_por`) REFERENCES `usuarios` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_usuarios_institucion` FOREIGN KEY (`institucion_id`) REFERENCES `instituciones` (`id`),
  CONSTRAINT `fk_usuarios_persona` FOREIGN KEY (`persona_id`) REFERENCES `personas` (`id`),
  CONSTRAINT `fk_usuarios_rol` FOREIGN KEY (`rol_id`) REFERENCES `roles` (`id`),
  CONSTRAINT `ck_usuarios_baja_coherente` CHECK (`activo` = 1 and `fecha_baja` is null or `activo` = 0 and `fecha_baja` is not null)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Usuarios con acceso al sistema (superadmins y admins). El docente NO es usuario.';
/*!40101 SET character_set_client = @saved_cs_client */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `asistenciautomatica_meta` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */;

USE `asistenciautomatica_meta`;
DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int(11) NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int(11) DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT current_timestamp(),
  `execution_time` int(11) NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


USE `asistenciautomatica`;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

/*!40000 ALTER TABLE `roles` DISABLE KEYS */;
INSERT INTO `roles` (`id`, `codigo`, `descripcion`, `creado_en`) VALUES (1,'INSTITUCION','Cuenta institucional - gestiona los administradores','2026-10-30 22:07:52');
INSERT INTO `roles` (`id`, `codigo`, `descripcion`, `creado_en`) VALUES (2,'ADMIN','Personal administrativo - opera el sistema dia a dia','2026-10-30 22:07:52');
/*!40000 ALTER TABLE `roles` ENABLE KEYS */;

/*!40000 ALTER TABLE `motivos_carga_manual` DISABLE KEYS */;
INSERT INTO `motivos_carga_manual` (`id`, `codigo`, `descripcion`, `activo`, `fecha_baja`, `creado_en`) VALUES (1,'FALLA_CAMARA','Falla tecnica de la camara web',1,NULL,'2026-10-30 22:07:52');
INSERT INTO `motivos_carga_manual` (`id`, `codigo`, `descripcion`, `activo`, `fecha_baja`, `creado_en`) VALUES (2,'FALLA_RECONOCIMIENTO','Falla en el algoritmo de reconocimiento facial',1,NULL,'2026-10-30 22:07:52');
INSERT INTO `motivos_carga_manual` (`id`, `codigo`, `descripcion`, `activo`, `fecha_baja`, `creado_en`) VALUES (3,'NO_REGISTRADO','Docente no registrado facialmente en el sistema',1,NULL,'2026-10-30 22:07:52');
INSERT INTO `motivos_carga_manual` (`id`, `codigo`, `descripcion`, `activo`, `fecha_baja`, `creado_en`) VALUES (4,'OTRO','Otro motivo (detallar en texto libre)',1,NULL,'2026-10-30 22:07:52');
/*!40000 ALTER TABLE `motivos_carga_manual` ENABLE KEYS */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


USE `asistenciautomatica_meta`;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (0,NULL,'<< Flyway Schema Creation >>','SCHEMA','`asistenciautomatica_meta`,`asistenciautomatica`',NULL,'root','2026-10-30 22:07:51',0,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (1,'001','esquema consolidado','SQL','V001__esquema_consolidado.sql',-476549456,'root','2026-10-30 22:07:52',652,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (2,'016','personas y vinculos docentes','SQL','V016__personas_y_vinculos_docentes.sql',23471793,'root','2026-10-30 22:07:52',317,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (3,'017','trazabilidad de identidad y bajas','SQL','V017__trazabilidad_de_identidad_y_bajas.sql',-1068474946,'root','2026-10-30 22:07:53',537,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (4,'018','cuenta institucional sin persona','SQL','V018__cuenta_institucional_sin_persona.sql',-2084754775,'root','2026-10-30 22:07:53',61,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (5,'019','bloques de presencia','SQL','V019__bloques_de_presencia.sql',1791888545,'root','2026-10-30 22:07:53',155,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (6,'020','cierre manual del bloque','SQL','V020__cierre_manual_del_bloque.sql',-1502762322,'root','2026-10-30 22:07:53',68,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (7,'021','limite de cambio de password','SQL','V021__limite_de_cambio_de_password.sql',1778387137,'root','2026-10-30 22:07:53',72,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (8,'022','un solo puesto habilitado','SQL','V022__un_solo_puesto_habilitado.sql',-785308191,'root','2026-10-30 22:07:53',67,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (9,'023','ciclos y periodos lectivos','SQL','V023__ciclos_y_periodos_lectivos.sql',-816304627,'root','2026-10-30 22:07:54',195,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (10,'024','dias no laborables','SQL','V024__dias_no_laborables.sql',2021789481,'root','2026-10-30 22:07:54',33,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (11,'025','modo kiosco','SQL','V025__modo_kiosco.sql',310866853,'root','2026-10-30 22:07:54',212,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (12,'026','camara del puesto','SQL','V026__camara_del_puesto.sql',1375105402,'root','2026-10-30 22:07:54',69,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (13,'027','reabrir ciclo lectivo','SQL','V027__reabrir_ciclo_lectivo.sql',2035856898,'root','2026-10-30 22:07:54',51,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (14,'028','tipo de dia sin clase','SQL','V028__tipo_de_dia_sin_clase.sql',-1467251969,'root','2026-10-30 22:07:54',56,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (15,'029','entrada sin camara','SQL','V029__entrada_sin_camara.sql',991741316,'root','2026-10-30 22:07:54',77,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (16,'030','varios puestos por institucion','SQL','V030__varios_puestos_por_institucion.sql',-1156324806,'root','2026-10-30 22:07:54',126,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (17,'031','equipo de salida','SQL','V031__equipo_de_salida.sql',1552844135,'root','2026-10-30 22:07:55',78,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (18,'032','clave de recuperacion','SQL','V032__clave_de_recuperacion.sql',-650795014,'root','2026-10-30 22:07:55',60,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (19,'033','entrada sin camara como manual','SQL','V033__entrada_sin_camara_como_manual.sql',691625285,'root','2026-10-30 22:07:55',6,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (20,'034','metodo sistema para ausencias del job','SQL','V034__metodo_sistema_para_ausencias_del_job.sql',154947589,'root','2026-10-30 22:07:55',74,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (21,'035','consentimiento sin metodo digital','SQL','V035__consentimiento_sin_metodo_digital.sql',-59407983,'root','2026-10-30 22:07:55',66,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (22,'036','cambios identidad en mayusculas','SQL','V036__cambios_identidad_en_mayusculas.sql',604041358,'root','2026-10-30 22:07:55',62,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (23,'037','estados atados en la base','SQL','V037__estados_atados_en_la_base.sql',499163055,'root','2026-10-30 22:07:56',846,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (24,'038','sin set null en columnas con check','SQL','V038__sin_set_null_en_columnas_con_check.sql',1821439334,'root','2026-10-30 22:07:56',179,1);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

