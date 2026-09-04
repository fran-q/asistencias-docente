-- =============================================================================
--  V003__rename_rol_superadmin_to_institucion.sql
--  Renombra el rol SUPERADMIN_INSTITUCION a INSTITUCION (mas corto y estandar).
--  Las relaciones (usuarios.rol_id) son por id, asi que no rompen.
-- =============================================================================

UPDATE roles
SET codigo      = 'INSTITUCION',
    descripcion = 'Cuenta institucional - gestiona los administradores'
WHERE codigo = 'SUPERADMIN_INSTITUCION';
