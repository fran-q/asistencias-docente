-- =============================================================================
--  V008__docentes_fecha_baja.sql
--
--  Suma la fecha de baja del docente, que hasta ahora no se registraba en
--  ningun lado: la baja era logica (activo = 0) y no dejaba constancia de
--  desde cuando.
--
--  Por que hace falta. La institucion necesita poder responder "hasta que dia
--  este docente estuvo en funciones", que es un dato administrativo, no una
--  curiosidad. Sin la columna, la unica pista era actualizado_en, que cambia
--  con cualquier edicion posterior y por lo tanto no sirve como registro.
--
--  Por que la fecha de baja se elige y la de alta no. Son dos hechos de
--  naturaleza distinta:
--
--    - El alta ocurre cuando se carga el docente en el sistema. La persona que
--      lo carga esta ahi, en ese momento: pedirle que ademas tipee la fecha
--      solo habilita el error de tipeo, no aporta ninguna informacion que el
--      sistema no tenga.
--
--    - La baja se registra despues del hecho. El docente dejo de prestar
--      servicios el viernes y el administrativo lo carga el lunes siguiente.
--      Forzar "hoy" falsearia el registro.
--
--  La columna admite NULL a proposito, y significa exactamente una cosa: este
--  docente no fue dado de baja. Los que ya estaban inactivos antes de esta
--  migracion quedan con NULL porque esa fecha no existe en ningun lado; darles
--  una inventada seria peor que dejarla vacia.
-- =============================================================================

ALTER TABLE docentes
    ADD COLUMN fecha_baja DATE NULL
        COMMENT 'Fecha en que el docente dejo de prestar servicios. NULL = no fue dado de baja.'
        AFTER fecha_alta;

-- Un docente no puede haberse ido antes de haber entrado. La base lo rechaza
-- ademas de la validacion del servicio: la aplicacion no es el unico camino
-- por el que se escriben estas filas (migraciones, correcciones manuales).
ALTER TABLE docentes
    ADD CONSTRAINT ck_docentes_baja_posterior_al_alta
        CHECK (fecha_baja IS NULL OR fecha_baja >= fecha_alta);
