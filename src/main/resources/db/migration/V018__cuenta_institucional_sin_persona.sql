-- =============================================================================
--  V018 - La cuenta institucional deja de tener una persona detras
-- =============================================================================
--  QUE RESUELVE
--  Desde V016 toda cuenta necesitaba una persona, asi que el alta de institucion
--  creaba una con el nombre y el apellido que se pedian en el formulario. Pero la
--  cuenta que representa al establecimiento NO es una persona fisica: es el
--  establecimiento. Forzarle una identidad personal mezclaba dos abstracciones
--  distintas y obligaba a pedir en el alta datos de alguien que todavia no
--  importa, porque los administradores de carne y hueso se crean despues, desde
--  adentro del sistema.
--
--  A partir de aca persona_id admite NULL. Una cuenta sin persona es una cuenta
--  institucional; una con persona es alguien concreto que administra o da clases.
--
--  QUE CUIDAR
--  Las consultas de usuarios filtraban el tenant por persona.institucionId. Con
--  cuentas sin persona ese filtro las dejaria afuera de los listados, asi que
--  pasan a filtrar por usuarios.institucion_id, que es tenant-scoped por si mismo
--  y no depende de que haya una persona del otro lado. Ver UsuarioRepository.
-- =============================================================================

ALTER TABLE ${esquema}.usuarios
  MODIFY COLUMN `persona_id` bigint(20) DEFAULT NULL COMMENT 'NULL en las cuentas institucionales, que no representan a una persona fisica.';
