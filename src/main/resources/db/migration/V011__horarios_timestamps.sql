-- =============================================================================
--  V011__horarios_timestamps.sql
--
--  Suma creado_en y actualizado_en a horarios, que era la unica entidad
--  editable del sistema que no los tenia.
--
--  Por que hace falta. Todas las pantallas de edicion muestran el mismo bloque
--  "Datos del sistema" con el estado, el alta y la ultima modificacion. Que una
--  sola pantalla mostrara menos que las demas no es un detalle estetico: quien
--  la usa no tiene forma de saber si eso es porque el horario no tiene esos
--  datos o porque la pantalla se olvido de mostrarlos. Un formulario que a
--  veces informa y a veces no enseña a desconfiar de los que si informan.
--
--  Las filas que ya existen quedan con la fecha en que se corre esta migracion.
--  Es una fecha falsa y conviene saberlo: no es cuando se cargo el horario,
--  sino cuando la columna empezo a existir. La alternativa era dejarlas en NULL
--  y mostrar un hueco, pero entonces la columna no podria ser NOT NULL y cada
--  lectura tendria que contemplar el caso; para horarios que se cargaron hace
--  semanas, la diferencia entre una fecha aproximada y ninguna no cambia
--  ninguna decision.
-- =============================================================================

ALTER TABLE horarios
    ADD COLUMN creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT 'Alta del horario. En filas anteriores a V011, la fecha de la migracion.',
    ADD COLUMN actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT 'Ultima modificacion del horario.';
