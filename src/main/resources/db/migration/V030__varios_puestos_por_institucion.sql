-- =============================================================================
--  V030 - Varios equipos de captura por institucion, con tope configurable
-- =============================================================================
--  QUE RESUELVE
--  V022 dejo un solo equipo habilitado por institucion. El argumento era que,
--  mientras hubiera un unico lugar fisico donde se toma asistencia, varios
--  equipos habilitados eran superficie de ataque sin contrapartida.
--
--  Ese argumento se cayo solo: las instituciones con mas de una entrada
--  necesitan una camara en cada puerta. Obligar al docente a cruzar el edificio
--  para marcar la salida termina en salidas sin marcar, que es exactamente el
--  dato que despues falta en el reporte y que el sistema tiene que presumir.
--
--  QUE LO REEMPLAZA
--  El tope pasa a ser una decision de cada institucion: max_puestos_habilitados,
--  NULL = sin tope. Sigue siendo un limite declarado en la base y verificado en
--  el servicio, no una regla suelta en Java que un INSERT se saltee.
--
--  QUE NO CAMBIA
--  Designar sigue autorizando UNICAMENTE la maquina que manda el pedido: no hay
--  forma de habilitar una a distancia, y eso es lo que sostiene que la captura
--  biometrica ocurra en maquinas conocidas (ADR-0015). Lo que se abre es
--  cuantas pueden estarlo a la vez, no como se autorizan.
--
--  PARA VOLVER ATRAS
--    ALTER TABLE puestos_captura
--      ADD COLUMN `institucion_si_habilitado` bigint(20)
--        AS (IF(`activo` = 1, `institucion_id`, NULL)) VIRTUAL,
--      ADD UNIQUE KEY `uq_puestos_uno_habilitado` (`institucion_si_habilitado`);
--    ALTER TABLE instituciones
--      DROP CONSTRAINT ck_instituciones_max_puestos,
--      DROP COLUMN max_puestos_habilitados;
--
--  La vuelta atras falla si alguna institucion quedo con dos equipos
--  habilitados, que es justamente lo que esta migracion viene a permitir: hay
--  que revocar los sobrantes primero.
-- =============================================================================

-- -----------------------------------------------------------------------------
--  1. El tope, por institucion
-- -----------------------------------------------------------------------------
ALTER TABLE ${esquema}.instituciones
  ADD COLUMN `max_puestos_habilitados` smallint(6) DEFAULT NULL
    COMMENT 'Tope de equipos autorizados a la vez. NULL = sin tope.'
    AFTER `umbral_separacion_min`,
  ADD CONSTRAINT `ck_instituciones_max_puestos`
    CHECK (`max_puestos_habilitados` IS NULL OR `max_puestos_habilitados` >= 1);

-- -----------------------------------------------------------------------------
--  2. Se cae el tope de uno
-- -----------------------------------------------------------------------------
--  El indice va primero: la columna generada existe solo para sostenerlo.
ALTER TABLE ${esquema}.puestos_captura
  DROP INDEX `uq_puestos_uno_habilitado`,
  DROP COLUMN `institucion_si_habilitado`;
