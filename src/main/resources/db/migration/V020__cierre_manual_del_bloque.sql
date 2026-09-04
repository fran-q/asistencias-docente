-- =============================================================================
--  V020 - Quien cerro el bloque a mano, y por que
-- =============================================================================
--  QUE RESUELVE
--  V019 dejo tres formas de que un bloque termine: CERRADO_POR_ROSTRO (el docente
--  paso por la camara), SIN_CIERRE (nadie marco y la hora la presumio el sistema)
--  y CERRADO_POR_ADMIN. Las dos primeras se explican solas; la tercera no decia
--  nada: quedaba una hora de salida cargada por alguien, sin ese alguien y sin
--  motivo.
--
--  Con el algoritmo actual el reconocimiento puede fallar al salir por un cambio
--  de iluminacion respecto del momento de la entrada, asi que el cierre manual no
--  es un caso de borde sino parte del flujo normal (RF-83). Y una marca
--  administrativa que no dice quien la hizo no se puede defender.
--
--  QUE NO ES
--  No es la auditoria general descartada en V009 (RF-34 a RF-36), que registraba
--  toda operacion sobre toda entidad. Son tres columnas sobre la fila que ya
--  existe, con el mismo criterio de V017: se guarda lo que sirve para responderle
--  a alguien, y nada mas.
--
--  QUE CUIDAR
--  1. El catalogo de motivos es el MISMO de la carga manual de asistencia
--     (motivos_carga_manual, RF-23). No se crea uno nuevo: los motivos por los que
--     falla el reconocimiento al salir son los mismos por los que falla al entrar,
--     y dos catalogos paralelos se desincronizan solos.
--
--  2. cerrado_por_usuario_id va con ON DELETE SET NULL, igual que dado_de_baja_por
--     en V017: si algun dia se suprime la cuenta, el registro del cierre sobrevive
--     sin su autor. Por eso el CHECK exige el MOTIVO y no el usuario — si exigiera
--     el usuario, la supresion de una cuenta romperia filas ya escritas.
--
--  3. NO se guarda el valor anterior al corregir una salida. Corregir pisa la
--     hora, el motivo y el autor. Es una decision consciente y su limite: alcanza
--     para saber quien afirma que el docente se fue a tal hora, no para reconstruir
--     que decia el registro antes. Guardar el historial completo seria la auditoria
--     que se descarto.
--
--  SI ESTA MIGRACION FALLA
--    ALTER TABLE asistenciautomatica.bloques_presencia
--      DROP CONSTRAINT ck_bloques_cierre_admin,
--      DROP FOREIGN KEY fk_bloques_cerrado_por,
--      DROP FOREIGN KEY fk_bloques_motivo_cierre,
--      DROP COLUMN cerrado_por_usuario_id,
--      DROP COLUMN motivo_cierre_id,
--      DROP COLUMN detalle_cierre;
--    DELETE FROM asistenciautomatica_meta.flyway_schema_history WHERE version = '20';
-- =============================================================================

ALTER TABLE ${esquema}.bloques_presencia
  ADD COLUMN `cerrado_por_usuario_id` bigint(20) DEFAULT NULL
    COMMENT 'Admin que cerro o corrigio la salida a mano. NULL en los otros cierres.'
    AFTER `estado_salida`,
  ADD COLUMN `motivo_cierre_id` smallint(6) DEFAULT NULL
    COMMENT 'Motivo del catalogo compartido con la carga manual (RF-23).'
    AFTER `cerrado_por_usuario_id`,
  ADD COLUMN `detalle_cierre` text DEFAULT NULL
    COMMENT 'Texto libre del admin. Obligatorio cuando el motivo es OTRO, validado en el service.'
    AFTER `motivo_cierre_id`,

  ADD KEY `fk_bloques_cerrado_por` (`cerrado_por_usuario_id`),
  ADD KEY `fk_bloques_motivo_cierre` (`motivo_cierre_id`),

  ADD CONSTRAINT `fk_bloques_cerrado_por` FOREIGN KEY (`cerrado_por_usuario_id`)
    REFERENCES ${esquema}.usuarios (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_bloques_motivo_cierre` FOREIGN KEY (`motivo_cierre_id`)
    REFERENCES ${esquema}.motivos_carga_manual (`id`),

  -- Un cierre administrativo sin motivo es lo mismo que no tener el dato: dice que
  -- alguien fijo la hora pero no por que. Al reves tambien: un cierre por rostro o
  -- presunto con motivo cargado estaria diciendo que hubo una decision humana que
  -- no hubo.
  ADD CONSTRAINT `ck_bloques_cierre_admin` CHECK (
    (`estado_cierre` = 'CERRADO_POR_ADMIN' AND `motivo_cierre_id` IS NOT NULL)
    OR
    (`estado_cierre` <> 'CERRADO_POR_ADMIN'
     AND `motivo_cierre_id` IS NULL
     AND `cerrado_por_usuario_id` IS NULL
     AND `detalle_cierre` IS NULL)
  );
