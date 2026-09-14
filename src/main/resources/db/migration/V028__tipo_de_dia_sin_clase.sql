-- =============================================================================
--  V028 - Tipo de dia sin clase
-- =============================================================================
--  QUE RESUELVE
--  Un dia sin clase era una fecha y un motivo escrito a mano. Para revisar el
--  ano --que feriados nacionales se cargaron, cuando fue el receso-- habia que
--  leer los motivos uno por uno, y el mismo feriado podia estar escrito de
--  tres maneras. El tipo es la clasificacion por la que se filtra; el motivo
--  sigue siendo la descripcion libre de por que ese dia no hay clases.
--
--  LOS TIPOS
--  NACIONAL y PROVINCIAL (feriados), INSTITUCIONAL (jornada, acto o cierre
--  propio), RECESO (invierno o verano) y OTRO. Van como texto con un CHECK,
--  igual que ciclos_lectivos.estado: se leen en una consulta sin traducir
--  numeros, y el CHECK impide que una carga a mano meta uno que la aplicacion
--  no conoce.
--
--  LOS QUE YA ESTABAN
--  Quedan como OTRO. Adivinar el tipo leyendo el motivo acertaria casi
--  siempre, y el casi es el problema: un dia mal clasificado por la migracion
--  es peor que uno sin clasificar. Se corrigen borrandolos y volviendolos a
--  cargar.
--
--  PARA VOLVER ATRAS
--    ALTER TABLE dias_no_laborables
--      DROP CONSTRAINT ck_dias_tipo,
--      DROP COLUMN tipo;
-- =============================================================================

ALTER TABLE ${esquema}.dias_no_laborables
  ADD COLUMN `tipo` varchar(20) NOT NULL DEFAULT 'OTRO'
    COMMENT 'NACIONAL, PROVINCIAL, INSTITUCIONAL, RECESO u OTRO. Es por lo que se filtra el listado.' AFTER `fecha`,
  ADD CONSTRAINT `ck_dias_tipo` CHECK (`tipo` in ('NACIONAL','PROVINCIAL','INSTITUCIONAL','RECESO','OTRO'));
