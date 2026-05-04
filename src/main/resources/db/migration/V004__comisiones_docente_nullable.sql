-- =============================================================================
--  V004__comisiones_docente_nullable.sql
--  Hace que comisiones.docente_asignado_id pueda ser NULL temporalmente.
--
--  Motivo: en Sprint 2 (academico) creamos comisiones, pero los docentes
--  todavia no existen como entidad gestionable (eso es Sprint 3). Hasta
--  que se cree el modulo de docentes, las comisiones se cargan sin
--  docente asignado y se completan despues.
--
--  En Sprint 3, cuando ya existan docentes, se evaluara si vale la pena
--  volver a hacerlo NOT NULL (probablemente si, via otra migracion).
-- =============================================================================

ALTER TABLE comisiones
    MODIFY COLUMN docente_asignado_id BIGINT NULL;
