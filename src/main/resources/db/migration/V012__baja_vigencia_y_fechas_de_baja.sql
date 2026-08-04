-- =============================================================================
--  V012__baja_vigencia_y_fechas_de_baja.sql
--
--  Dos cambios que van juntos porque son la misma idea:
--
--    1. Se elimina la vigencia de los horarios (vigente_desde / vigente_hasta).
--    2. Toda entidad que se da de baja registra CUANDO se dio de baja.
--
--  Por que se va la vigencia. El sistema tenia dos formas de decir lo mismo y
--  no coincidian: un horario podia estar activo = 1 y fuera de su ventana de
--  vigencia al mismo tiempo, o al reves. Peor: nada leia la vigencia. El pase
--  decide con activo, la grilla filtra por activo, el generador de ausencias
--  usa activo. Las dos fechas se pedian en el formulario, se guardaban, y no
--  cambiaban el comportamiento de nada.
--
--  Un campo que se carga y no se lee es peor que uno que falta: quien lo
--  completa cree que esta acotando algo, y no esta acotando nada. Queda una
--  sola forma de decirlo --activo / inactivo-- que es la que el sistema ya usa
--  en todas las demas entidades.
--
--  Por que la fecha de baja. Hasta ahora la baja logica solo dejaba activo = 0,
--  sin constancia de cuando. La institucion necesita poder responder "¿desde
--  cuando esta materia no se dicta?", que es un dato administrativo. Con
--  actualizado_en no alcanza: cambia con cualquier edicion posterior.
--
--  Que tablas la llevan y cuales no. La llevan las que tienen ciclo de vida,
--  es decir las que tienen columna activo. NO la llevan asistencias, codigos,
--  consentimientos ni justificaciones: son registros de hechos ocurridos, no
--  entidades que se dan de baja. Una asistencia no se "desactiva"; existe o no
--  existe. Ponerles una fecha de baja seria sugerir una operacion que no tiene
--  sentido para ellas.
--
--  La fecha admite NULL y significa exactamente una cosa: esta fila no fue dada
--  de baja. Las que ya estaban inactivas antes de esta migracion quedan en NULL
--  porque ese dato no existe en ningun lado; inventarlo seria peor que dejarlo
--  vacio.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) Fuera la vigencia de los horarios.
-- ---------------------------------------------------------------------------
ALTER TABLE horarios DROP CONSTRAINT ck_horarios_vigencia;
ALTER TABLE horarios DROP COLUMN vigente_hasta;
ALTER TABLE horarios DROP COLUMN vigente_desde;

-- ---------------------------------------------------------------------------
-- 2) Fecha de baja en todo lo que tiene baja logica.
-- ---------------------------------------------------------------------------
ALTER TABLE carreras       ADD COLUMN fecha_baja DATE NULL
    COMMENT 'Cuando se dio de baja. NULL = no fue dada de baja.' AFTER activo;
ALTER TABLE materias       ADD COLUMN fecha_baja DATE NULL
    COMMENT 'Cuando se dio de baja. NULL = no fue dada de baja.' AFTER activo;
ALTER TABLE comisiones     ADD COLUMN fecha_baja DATE NULL
    COMMENT 'Cuando se dio de baja. NULL = no fue dada de baja.' AFTER activo;
ALTER TABLE horarios       ADD COLUMN fecha_baja DATE NULL
    COMMENT 'Cuando se dio de baja. NULL = no fue dado de baja.' AFTER activo;
ALTER TABLE instituciones  ADD COLUMN fecha_baja DATE NULL
    COMMENT 'Cuando se dio de baja. NULL = no fue dada de baja.' AFTER activo;
ALTER TABLE usuarios       ADD COLUMN fecha_baja DATE NULL
    COMMENT 'Cuando se dio de baja. NULL = no fue dado de baja.' AFTER activo;

-- ---------------------------------------------------------------------------
-- 3) Fecha de creacion en las tres tablas que no la tenian.
--
--    Las filas existentes quedan con la fecha de esta migracion. Es una fecha
--    aproximada y conviene saberlo: no es cuando se creo la fila, es cuando la
--    columna empezo a existir.
-- ---------------------------------------------------------------------------
ALTER TABLE modelos_faciales     ADD COLUMN creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    COMMENT 'Alta del registro. En filas anteriores a V012, la fecha de la migracion.';
ALTER TABLE motivos_carga_manual ADD COLUMN creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    COMMENT 'Alta del registro. En filas anteriores a V012, la fecha de la migracion.';
ALTER TABLE motivos_carga_manual ADD COLUMN fecha_baja DATE NULL
    COMMENT 'Cuando se dio de baja. NULL = no fue dado de baja.' AFTER activo;
ALTER TABLE roles                ADD COLUMN creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    COMMENT 'Alta del registro. En filas anteriores a V012, la fecha de la migracion.';
