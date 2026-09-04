-- =============================================================================
--  V019 - Bloques de presencia: la marca de salida
-- =============================================================================
--  QUE RESUELVE
--  Hasta aca el sistema registraba un solo evento por clase: el docente pasa por
--  la camara al llegar y eso produce una fila en asistencias. Nada acredita hasta
--  cuando se quedo, asi que el sistema podia decir que vino pero no que dicto la
--  clase.
--
--  Agregar una hora_salida a asistencias no alcanza. Un docente con clases
--  encadenadas tiene que registrarse UNA vez al entrar y UNA al salir, no una por
--  clase, y en ese caso hay varias filas de asistencias y una sola salida real:
--  ponerla en la ultima deja a las otras mintiendo por omision, y repetirla en
--  todas inventa eventos que nunca ocurrieron.
--
--  La unidad de la que se predica una entrada y una salida no es el horario: es
--  el lapso continuo en que la persona estuvo en la institucion. Eso es el bloque
--  de presencia. Ver ADR-0017.
--
--  QUE NO CAMBIA
--  asistencias sigue siendo una fila por (docente, horario, fecha) y conserva su
--  UNIQUE, sus CHECK y su significado. Lo unico que suma es a que bloque
--  pertenece. Reportes, justificaciones, carga manual y generacion de ausencias
--  siguen operando igual.
--
--  QUE CUIDAR
--  1. bloque_id admite NULL a proposito. Las asistencias anteriores a esta
--     migracion quedan en NULL y eso SIGNIFICA algo -"registro previo a la marca
--     de salida"- que no es lo mismo que un dato que se perdio. Rellenarlas
--     hacia atras inventaria bloques que nunca existieron.
--
--  2. Los CHECK de este archivo NO se ejercitan en los tests. El perfil test
--     corre sobre H2 con flyway.enabled=false y ddl-auto=create-drop, asi que el
--     esquema de los tests sale de las entidades y no de aca. Un test verde no
--     prueba que estos constraints funcionen: eso se verifica contra MariaDB.
--
--  3. bloque_abierto_de es una columna generada, no un dato que alguien escribe.
--     Vale docente_id mientras el bloque este ABIERTO y NULL cuando se cierra;
--     su UNIQUE es lo que impide que un docente tenga dos bloques abiertos a la
--     vez. Es el equivalente al UNIQUE de asistencias para la idempotencia del
--     pase: sin esto, dos requests del loop del navegador en el mismo instante
--     abren dos bloques y el docente queda adentro dos veces. MariaDB permite
--     repetir NULL en un UNIQUE, que es justo lo que hace falta para que si
--     pueda tener varios bloques CERRADOS el mismo dia.
--
--  SI ESTA MIGRACION FALLA
--  El DDL de MariaDB no es transaccional: el script puede quedar aplicado a
--  medias, y Flyway deja su fila en flyway_schema_history con success = 0, con lo
--  cual la aplicacion no vuelve a arrancar hasta limpiarla.
--
--  VERIFICADA el 2026-09-01 contra MariaDB 10.4.32, aplicandola sobre una copia
--  estructural de la base real: se aplica limpia, el UNIQUE sobre la columna
--  generada del paso 2 funciona (era el punto que estaba en duda) y los diez CHECK
--  rechazan lo que tienen que rechazar. El procedimiento de abajo tambien se probo.
--
--  Para volver al estado anterior, en este orden:
--
--    ALTER TABLE asistenciautomatica.asistencias
--      DROP FOREIGN KEY fk_asistencias_bloque, DROP COLUMN bloque_id;
--    DROP TABLE IF EXISTS asistenciautomatica.bloques_presencia;
--    ALTER TABLE asistenciautomatica.instituciones
--      DROP CONSTRAINT ck_instituciones_umbral_separacion,
--      DROP COLUMN umbral_separacion_min;
--    DELETE FROM asistenciautomatica_meta.flyway_schema_history WHERE version = '19';
--
--  El orden importa: bloques_presencia no se puede dropear mientras asistencias.bloque_id
--  la siga referenciando. Verificado contra MariaDB 10.4.32 el 2026-09-01, junto con el
--  resto de la migracion.
--
--  Cada sentencia falla sola si ese paso no se habia aplicado, que es lo
--  esperable: se ignora el error y se sigue con la siguiente.
-- =============================================================================


-- -----------------------------------------------------------------------------
--  1. Umbral de separacion, por institucion
-- -----------------------------------------------------------------------------
--  Cuantos minutos de hueco entre dos clases consecutivas las mantienen dentro
--  del mismo bloque. Menor o igual al umbral: un bloque. Mayor: dos bloques con
--  entrada y salida propias.
--
--  Va en instituciones y no en una constante porque la realidad edilicia lo
--  determina: un instituto con recreos de quince minutos y otro con turnos
--  separados por cuarenta y cinco no pueden compartir el numero (RF-76).
--
--  El tope de 240 no es decorativo. Un umbral generoso encadena clases que no
--  tienen nada que ver entre si: con 240, el docente que da a las 08 y vuelve a
--  las 14 queda en un solo bloque de seis horas y el sistema acredita que estuvo
--  todo el mediodia. Ver la consecuencia negativa correspondiente en ADR-0017.

ALTER TABLE ${esquema}.instituciones
  ADD COLUMN `umbral_separacion_min` smallint(6) NOT NULL DEFAULT 60
    COMMENT 'Minutos de hueco entre clases que las mantienen en el mismo bloque de presencia (RF-76).'
    AFTER `telefono_contacto`,
  ADD CONSTRAINT `ck_instituciones_umbral_separacion`
    CHECK (`umbral_separacion_min` >= 0 AND `umbral_separacion_min` <= 240);


-- -----------------------------------------------------------------------------
--  2. Bloques de presencia
-- -----------------------------------------------------------------------------
--  Un bloque = un docente, una fecha, una entrada y una salida. Abarca todos los
--  horarios consecutivos que el umbral de la institucion mantenga juntos, sin
--  importar de que materia o carrera sean: lo que el bloque acredita es que la
--  persona estuvo, no que dicto (RF-75).
--
--  Las dos evidencias biometricas son las del reconocimiento que abrio y cerro
--  el bloque. Un docente con cuatro clases encadenadas deja dos, no ocho: menos
--  dato biometrico tratado para el mismo resultado, que es lo que la Resolucion
--  AAIP 255/2022 espera cuando pregunta por que se trata cada dato.
--
--  ON DELETE SET NULL en las dos FK a modelos_faciales, igual que en asistencias:
--  si el docente ejerce el derecho de cancelacion (ARCO) el registro de su
--  permanencia sobrevive sin el dato biometrico (RNF-14).

CREATE TABLE ${esquema}.bloques_presencia (
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
  `modelo_facial_salida_id` bigint(20) DEFAULT NULL COMMENT 'Modelo usado para cerrar el bloque (solo si origen = AUTOMATICO)',
  `confianza_salida` decimal(5,4) DEFAULT NULL COMMENT 'Score 0.0000 a 1.0000',

  `estado_cierre` varchar(20) NOT NULL DEFAULT 'ABIERTO',
  `estado_salida` varchar(15) DEFAULT NULL COMMENT 'Como se fue. NULL mientras el bloque siga abierto.',

  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),

  -- Vale docente_id solo mientras el bloque este ABIERTO. Ver punto 3 de QUE CUIDAR.
  `bloque_abierto_de` bigint(20) AS (IF(`estado_cierre` = 'ABIERTO', `docente_id`, NULL)) VIRTUAL,

  PRIMARY KEY (`id`),

  -- Un docente no puede tener dos bloques abiertos a la vez. Los cerrados no
  -- chocan porque la columna generada vale NULL y el UNIQUE admite repetirlo.
  UNIQUE KEY `uq_bloques_un_solo_abierto_por_docente` (`bloque_abierto_de`),

  -- Idempotencia del pase: dos requests del loop en el mismo segundo no abren
  -- dos bloques. Mismo rol que uq_asistencias_doc_horario_fecha (RF-53).
  UNIQUE KEY `uq_bloques_doc_fecha_entrada` (`docente_id`,`fecha`,`hora_entrada`),

  KEY `idx_bloques_inst_fecha` (`institucion_id`,`fecha`),
  KEY `idx_bloques_docente_fecha` (`docente_id`,`fecha`),
  KEY `idx_bloques_pendientes` (`institucion_id`,`estado_cierre`,`fecha`),
  KEY `fk_bloques_modelo_entrada` (`modelo_facial_entrada_id`),
  KEY `fk_bloques_modelo_salida` (`modelo_facial_salida_id`),

  CONSTRAINT `fk_bloques_institucion` FOREIGN KEY (`institucion_id`) REFERENCES ${esquema}.instituciones (`id`),
  CONSTRAINT `fk_bloques_docente` FOREIGN KEY (`docente_id`) REFERENCES ${esquema}.docentes (`id`),
  CONSTRAINT `fk_bloques_modelo_entrada` FOREIGN KEY (`modelo_facial_entrada_id`) REFERENCES ${esquema}.modelos_faciales (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_bloques_modelo_salida` FOREIGN KEY (`modelo_facial_salida_id`) REFERENCES ${esquema}.modelos_faciales (`id`) ON DELETE SET NULL,

  CONSTRAINT `ck_bloques_origen_entrada` CHECK (`origen_entrada` in ('AUTOMATICO','MANUAL')),
  CONSTRAINT `ck_bloques_origen_salida` CHECK (`origen_salida` is null or `origen_salida` in ('AUTOMATICO','MANUAL','PRESUNTO')),
  CONSTRAINT `ck_bloques_estado_cierre` CHECK (`estado_cierre` in ('ABIERTO','CERRADO_POR_ROSTRO','CERRADO_POR_ADMIN','SIN_CIERRE')),
  CONSTRAINT `ck_bloques_estado_salida` CHECK (`estado_salida` is null or `estado_salida` in ('EN_HORA','ANTICIPADA','SIN_MARCA')),

  CONSTRAINT `ck_bloques_confianza_entrada` CHECK (`confianza_entrada` is null or (`confianza_entrada` >= 0 and `confianza_entrada` <= 1)),
  CONSTRAINT `ck_bloques_confianza_salida` CHECK (`confianza_salida` is null or (`confianza_salida` >= 0 and `confianza_salida` <= 1)),

  -- Un bloque abierto no tiene salida, y uno cerrado si. Sin esto, un bloque
  -- puede quedar diciendo a la vez que sigue abierto y que se fue a las 22.
  CONSTRAINT `ck_bloques_cierre_coherente` CHECK (
    (`estado_cierre` = 'ABIERTO' and `hora_salida` is null and `origen_salida` is null and `estado_salida` is null)
    or
    (`estado_cierre` <> 'ABIERTO' and `hora_salida` is not null and `origen_salida` is not null and `estado_salida` is not null)
  ),

  -- Irse antes de haber llegado no es un caso de borde: es un dato corrupto.
  CONSTRAINT `ck_bloques_salida_posterior` CHECK (`hora_salida` is null or `hora_salida` > `hora_entrada`),

  -- Solo un reconocimiento facial deja modelo y confianza. Una hora cargada a
  -- mano o presumida por el sistema no tiene evidencia biometrica detras, y
  -- guardarsela haria pasar por medido algo que nadie midio. Mismo criterio que
  -- ck_asistencias_metodo_modelo.
  CONSTRAINT `ck_bloques_entrada_modelo` CHECK (
    `origen_entrada` = 'AUTOMATICO'
    or (`modelo_facial_entrada_id` is null and `confianza_entrada` is null)
  ),
  CONSTRAINT `ck_bloques_salida_modelo` CHECK (
    `origen_salida` = 'AUTOMATICO'
    or (`modelo_facial_salida_id` is null and `confianza_salida` is null)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Lapso continuo de permanencia de un docente, con marca de entrada y de salida (RF-74 a RF-83, ADR-0017).';


-- -----------------------------------------------------------------------------
--  3. A que bloque pertenece cada asistencia
-- -----------------------------------------------------------------------------
--  NULL en las filas anteriores a esta funcionalidad, y en las que se carguen a
--  mano sin bloque asociado. Ver punto 1 de QUE CUIDAR.
--
--  RESTRICT como el resto del sistema: un bloque con asistencias imputadas no se
--  borra. Los bloques no tienen baja logica, igual que asistencias: son el
--  registro de un hecho, no una entidad que se administre.

ALTER TABLE ${esquema}.asistencias
  ADD COLUMN `bloque_id` bigint(20) DEFAULT NULL
    COMMENT 'Bloque de presencia que cubre esta clase. NULL en registros previos a V019.'
    AFTER `horario_id`,
  ADD KEY `fk_asistencias_bloque` (`bloque_id`),
  ADD CONSTRAINT `fk_asistencias_bloque` FOREIGN KEY (`bloque_id`) REFERENCES ${esquema}.bloques_presencia (`id`);
