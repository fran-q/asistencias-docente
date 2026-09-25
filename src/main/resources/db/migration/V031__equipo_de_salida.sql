-- =============================================================================
--  V031 - De que equipo salio la marca de salida
-- =============================================================================
--  QUE RESUELVE
--  V025 dejo asentado de que equipo salio la marca que ABRE la jornada
--  (bloques_presencia.puesto_id, RF-89). El cierre no toca esa columna, asi que
--  la jornada guarda por donde entro el docente y nada mas.
--
--  Con un solo equipo por institucion daba igual: entrada y salida eran la
--  misma maquina por definicion. Desde V030 puede haber una camara por entrada,
--  y el caso que lo motivo es justamente ese: el docente entra por una puerta y
--  sale por la otra. Sin esta columna, el registro afirma que salio por donde
--  entro, que es un dato falso y no uno incompleto.
--
--  POR QUE UNA COLUMNA Y NO UNA TABLA DE EVENTOS
--  Una jornada tiene exactamente una entrada y una salida --eso lo sostiene
--  V019 con su CHECK de coherencia--, asi que el par de columnas dice todo lo
--  que hay que decir. Una tabla de eventos seria la auditoria que V009
--  descarto.
--
--  QUE QUEDA EN NULL
--  - Las jornadas cerradas antes de esta migracion.
--  - Las que cierra el job por vencimiento: ahi no hay equipo, hay un reloj.
--  - Las que cierra un admin desde la pantalla de salidas pendientes, que no
--    exige equipo autorizado.
--  Por eso el CHECK solo exige lo que de verdad es imposible: un equipo de
--  salida en una jornada que todavia no tiene hora de salida.
--
--  PARA VOLVER ATRAS
--    ALTER TABLE bloques_presencia
--      DROP CONSTRAINT ck_bloques_puesto_salida_con_salida,
--      DROP FOREIGN KEY fk_bloques_puesto_salida,
--      DROP COLUMN puesto_salida_id;
-- =============================================================================

ALTER TABLE ${esquema}.bloques_presencia
  ADD COLUMN `puesto_salida_id` bigint(20) DEFAULT NULL
    COMMENT 'Equipo desde el que se registro la salida. NULL si la cerro el job o un admin sin equipo.'
    AFTER `puesto_id`,
  ADD KEY `fk_bloques_puesto_salida` (`puesto_salida_id`),
  ADD CONSTRAINT `fk_bloques_puesto_salida` FOREIGN KEY (`puesto_salida_id`)
    REFERENCES ${esquema}.puestos_captura (`id`),
  ADD CONSTRAINT `ck_bloques_puesto_salida_con_salida` CHECK (
    `puesto_salida_id` IS NULL OR `hora_salida` IS NOT NULL
  );
