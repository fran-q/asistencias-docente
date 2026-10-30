-- =============================================================================
--  V035 - El consentimiento deja de admitir el metodo DIGITAL
-- =============================================================================
--  QUE RESUELVE
--  DIGITAL estaba reservado para cuando el docente aceptara por si mismo desde
--  un login propio (ADR-0005). Ese login quedo fuera del alcance: el docente es
--  sujeto pasivo, sin cuenta ni interfaz (contexto/01-proyecto.md). Ningun
--  camino del codigo lo escribio nunca, asi que era un valor que la base
--  aceptaba y el sistema no podia producir.
--
--  SI HUBIERA FILAS CON DIGITAL
--  La migracion falla a proposito. La aplicacion no las puede haber escrito, y
--  un consentimiento es un documento legal: no se reescribe solo. Alguien tiene
--  que mirar de donde salieron.
--
--  PARA VOLVER ATRAS
--    ALTER TABLE consentimientos_biometricos
--      DROP CONSTRAINT ck_consentimientos_metodo;
--    ALTER TABLE consentimientos_biometricos
--      ADD CONSTRAINT ck_consentimientos_metodo
--        CHECK (metodo in ('ESCRITO','DIGITAL'));
-- =============================================================================

ALTER TABLE ${esquema}.consentimientos_biometricos
  DROP CONSTRAINT `ck_consentimientos_metodo`;

ALTER TABLE ${esquema}.consentimientos_biometricos
  ADD CONSTRAINT `ck_consentimientos_metodo` CHECK (`metodo` in ('ESCRITO'));
