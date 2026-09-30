-- =============================================================================
--  V032 - Clave de recuperacion de la institucion
-- =============================================================================
--  QUE RESUELVE
--  TD-009: recuperar una contrasena depende de que haya un servidor de correo.
--  En una instalacion autonoma --la que corre en la maquina de la institucion,
--  sin internet y sin SMTP (ADR-0022)-- ese camino no existe, y la cuenta
--  INSTITUCION es la unica que administra usuarios, ciclos y puestos. Olvidarla
--  dejaba la instalacion sin acceso y sin forma de recuperarlo.
--
--  QUE GUARDA
--  El HASH de una clave larga que la aplicacion genera y muestra una sola vez,
--  al terminar la configuracion inicial. Mismo criterio que las contrasenas y
--  que el token del puesto (ADR-0015): una copia de la base no alcanza para
--  fabricar una clave valida.
--
--  POR QUE EN instituciones Y NO EN usuarios
--  Porque no recupera "una cuenta cualquiera": destraba a la institucion. La
--  cuenta INSTITUCION es una sola y representa al establecimiento (V018), asi
--  que la clave pertenece al mismo sujeto. Colgada del usuario habria que
--  decidir que pasa cuando esa cuenta se reemplaza.
--
--  QUE QUEDA EN NULL
--  Todas las instituciones ya creadas, y las que se creen por el alta publica:
--  ahi el correo esta comprobado y la recuperacion por codigo funciona. La clave
--  la genera el asistente de primer arranque, que es el unico camino que corre
--  sin correo. Por eso la columna es nullable y no tiene default.
--
--  PARA VOLVER ATRAS
--    ALTER TABLE instituciones
--      DROP COLUMN clave_recuperacion_creada_en,
--      DROP COLUMN clave_recuperacion_hash;
-- =============================================================================

ALTER TABLE ${esquema}.instituciones
  ADD COLUMN `clave_recuperacion_hash` varchar(100) DEFAULT NULL
    COMMENT 'Hash de la clave de recuperacion. NULL si la institucion no tiene uno.'
    AFTER `max_puestos_habilitados`,
  ADD COLUMN `clave_recuperacion_creada_en` datetime DEFAULT NULL
    COMMENT 'Cuando se genero la clave vigente. Sirve para decir cual de las copias impresas vale.'
    AFTER `clave_recuperacion_hash`,
  ADD CONSTRAINT `ck_instituciones_clave_recuperacion` CHECK (
    (`clave_recuperacion_hash` IS NULL AND `clave_recuperacion_creada_en` IS NULL)
    OR (`clave_recuperacion_hash` IS NOT NULL AND `clave_recuperacion_creada_en` IS NOT NULL)
  );
