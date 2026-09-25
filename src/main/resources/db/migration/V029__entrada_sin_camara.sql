-- =============================================================================
--  V029 - Quien abrio el bloque a mano, y por que
-- =============================================================================
--  QUE RESUELVE
--  V019 ya admite `origen_entrada` = 'MANUAL', pero no habia forma de registrar
--  una: la unica manera de abrir un bloque era pasar por la camara. Un docente al
--  que el reconocimiento no acierta quedaba sin ninguna salida en el momento, y
--  su asistencia dependia de que despues alguien la cargara desde otra pantalla,
--  sin bloque, o sea sin hora de salida ni permanencia.
--
--  Con LBPH eso no es un caso de borde: basta un cambio de iluminacion, un rostro
--  todavia sin registrar o un consentimiento revocado --donde el rostro NO se
--  puede usar (RF-82)-- para que la camara no sirva. V020 resolvio el mismo
--  problema del lado de la salida; estas columnas son su espejo del lado de la
--  entrada.
--
--  QUE NO ES
--  No es la auditoria descartada en V009. Son tres columnas sobre la fila que ya
--  existe, con el mismo criterio de V017 y V020: se guarda lo que sirve para
--  responderle a alguien, y nada mas.
--
--  QUE CUIDAR
--  1. El catalogo de motivos es el MISMO de siempre (motivos_carga_manual, RF-23),
--     por la razon que ya decia V020: los motivos por los que falla el
--     reconocimiento al salir son los mismos por los que falla al entrar. Las tres
--     filas que describen este caso ya estan sembradas desde V001 --FALLA_CAMARA,
--     FALLA_RECONOCIMIENTO, NO_REGISTRADO-- y no hace falta agregar ninguna.
--
--  2. abierto_por_usuario_id va con ON DELETE SET NULL, igual que
--     cerrado_por_usuario_id en V020: si algun dia se suprime la cuenta, el
--     registro de la entrada sobrevive sin su autor. Por eso el CHECK exige el
--     MOTIVO y no el usuario.
--
--  3. El CHECK cuelga de `origen_entrada` y no de `estado_cierre`, que es de lo
--     que colgaba el de V020. No es un descuido: la entrada no tiene un estado
--     propio --el bloque nace abierto de cualquiera de las dos formas-- y lo unico
--     que distingue una entrada cargada a mano de una por rostro es su origen.
--
--  SI ESTA MIGRACION FALLA
--    ALTER TABLE asistenciautomatica.bloques_presencia
--      DROP CONSTRAINT ck_bloques_entrada_admin,
--      DROP FOREIGN KEY fk_bloques_abierto_por,
--      DROP FOREIGN KEY fk_bloques_motivo_entrada,
--      DROP COLUMN abierto_por_usuario_id,
--      DROP COLUMN motivo_entrada_id,
--      DROP COLUMN detalle_entrada;
--    DELETE FROM asistenciautomatica_meta.flyway_schema_history WHERE version = '29';
-- =============================================================================

ALTER TABLE ${esquema}.bloques_presencia
  ADD COLUMN `abierto_por_usuario_id` bigint(20) DEFAULT NULL
    COMMENT 'Admin que abrio el bloque sin camara. NULL en las entradas por rostro.'
    AFTER `confianza_entrada`,
  ADD COLUMN `motivo_entrada_id` smallint(6) DEFAULT NULL
    COMMENT 'Motivo del catalogo compartido con la carga manual (RF-23).'
    AFTER `abierto_por_usuario_id`,
  ADD COLUMN `detalle_entrada` text DEFAULT NULL
    COMMENT 'Texto libre del admin. Obligatorio cuando el motivo es OTRO, validado en el service.'
    AFTER `motivo_entrada_id`,

  ADD KEY `fk_bloques_abierto_por` (`abierto_por_usuario_id`),
  ADD KEY `fk_bloques_motivo_entrada` (`motivo_entrada_id`),

  ADD CONSTRAINT `fk_bloques_abierto_por` FOREIGN KEY (`abierto_por_usuario_id`)
    REFERENCES ${esquema}.usuarios (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_bloques_motivo_entrada` FOREIGN KEY (`motivo_entrada_id`)
    REFERENCES ${esquema}.motivos_carga_manual (`id`),

  -- Una entrada cargada a mano sin motivo dice que alguien fijo la hora pero no
  -- por que. Al reves tambien: una entrada por rostro con motivo cargado estaria
  -- diciendo que hubo una decision humana que no hubo. Mismo par que V020.
  ADD CONSTRAINT `ck_bloques_entrada_admin` CHECK (
    (`origen_entrada` = 'MANUAL' AND `motivo_entrada_id` IS NOT NULL)
    OR
    (`origen_entrada` <> 'MANUAL'
     AND `motivo_entrada_id` IS NULL
     AND `abierto_por_usuario_id` IS NULL
     AND `detalle_entrada` IS NULL)
  );
