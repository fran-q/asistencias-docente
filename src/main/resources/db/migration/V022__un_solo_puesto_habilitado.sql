-- =============================================================================
--  V022 - Un solo puesto de captura habilitado por institucion
-- =============================================================================
--  QUE RESUELVE
--  ADR-0015 permitia varios equipos autorizados a la vez. La decision ahora es
--  que haya uno solo: mientras el sistema tenga un unico lugar fisico donde se
--  toma asistencia, varios puestos habilitados son superficie de ataque sin
--  contrapartida. Para mudar la captura a otra maquina hay que revocar la
--  actual primero.
--
--  El tope se declara aca ademas de en el servicio. Una regla que vive solo en
--  Java se saltea con un INSERT, y este limite es lo que sostiene que la captura
--  biometrica ocurre en una maquina conocida.
--
--  COMO SE EXPRESA "UNO SOLO ENTRE LOS ACTIVOS"
--  Un UNIQUE sobre institucion_id prohibiria tambien los revocados, y el
--  historial de puestos se conserva a proposito: dice desde donde se capturo y
--  hasta cuando. Como no hay indices unicos parciales, se usa una columna
--  generada que vale institucion_id mientras el puesto este habilitado y NULL
--  cuando no. En un indice UNIQUE los NULL no chocan entre si, asi que quedan
--  fuera del alcance de la restriccion sin que haya que excluirlos a mano.
--
--  Es VIRTUAL y no PERSISTENT: se calcula al leer y no ocupa lugar en la fila.
--  El indice si se materializa, que es lo unico que hace falta.
--
--  QUE CUIDAR
--  La migracion falla si alguna institucion ya tiene dos puestos habilitados.
--  Es deliberado: elegir cual sobrevive no es una decision que pueda tomar una
--  migracion sola. Si pasa, hay que revocar los sobrantes a mano y reintentar.
--
--  PARA VOLVER ATRAS
--    ALTER TABLE puestos_captura
--      DROP INDEX uq_puestos_uno_habilitado,
--      DROP COLUMN institucion_si_habilitado;
-- =============================================================================

ALTER TABLE ${esquema}.puestos_captura
  ADD COLUMN `institucion_si_habilitado` bigint(20)
    AS (IF(`activo` = 1, `institucion_id`, NULL)) VIRTUAL
    COMMENT 'Solo para el UNIQUE de abajo: institucion mientras el puesto este habilitado, NULL si esta revocado.',
  ADD UNIQUE KEY `uq_puestos_uno_habilitado` (`institucion_si_habilitado`);
