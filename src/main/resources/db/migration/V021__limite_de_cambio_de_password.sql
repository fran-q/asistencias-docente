-- =============================================================================
--  V021 - Una contrasena nueva por dia, con destrabe administrativo
-- =============================================================================
--  QUE RESUELVE
--  Hasta aca no habia ningun limite sobre el cambio de contrasena en si. Habia
--  un tope de cinco CODIGOS por hora, que es otra cosa: acota cuantas veces se
--  puede pedir el codigo, no cuantas veces se puede fijar una clave nueva. Con
--  un codigo en la mano se podia cambiar la contrasena las veces que uno
--  quisiera, y eso vuelve barato tantear hasta dar con una que el sistema
--  acepte, y ensucia el rastro de cuando cambio de verdad.
--
--  Ahora rige una ventana de 24 horas desde el ultimo cambio, para los dos
--  caminos que fijan contrasena: "Mi cuenta" y la recuperacion publica.
--
--  QUE CUIDAR
--  password_cambiada_en arranca en NULL para las cuentas que ya existen. NULL
--  significa "nunca se cambio desde que existe esta columna" y NO bloquea: si
--  se hubiera puesto la fecha de la migracion, todo el mundo quedaria trabado
--  24 horas por un cambio que nadie hizo.
--
--  cambio_password_habilitado_en es el destrabe: otro administrador o la
--  institucion levanta el bloqueo y la persona vuelve a recuperar por correo.
--  Va como columna aparte y no borrando password_cambiada_en, porque esa fecha
--  es el registro de cuando cambio la contrasena y perderla para destrabar
--  seria falsear el dato. Vale mientras sea POSTERIOR al ultimo cambio; al
--  fijar la contrasena nueva se consume y vuelve a NULL.
--
--  El destrabe deja quien lo hizo, con ON DELETE SET NULL como el resto de las
--  columnas de trazabilidad que agrego V017.
--
--  PARA VOLVER ATRAS
--    ALTER TABLE usuarios
--      DROP FOREIGN KEY fk_usuarios_habilito_cambio,
--      DROP COLUMN cambio_password_habilitado_por,
--      DROP COLUMN cambio_password_habilitado_en,
--      DROP COLUMN password_cambiada_en;
-- =============================================================================

ALTER TABLE ${esquema}.usuarios
  ADD COLUMN `password_cambiada_en` timestamp NULL DEFAULT NULL
    COMMENT 'Ultima vez que se fijo una contrasena nueva. NULL = nunca desde V021; no bloquea.',
  ADD COLUMN `cambio_password_habilitado_en` timestamp NULL DEFAULT NULL
    COMMENT 'Destrabe administrativo. Vale si es posterior a password_cambiada_en; se consume al cambiar.',
  ADD COLUMN `cambio_password_habilitado_por` bigint(20) DEFAULT NULL
    COMMENT 'Quien levanto el bloqueo. NULL si nadie lo hizo o si esa cuenta ya no existe.';

ALTER TABLE ${esquema}.usuarios
  ADD CONSTRAINT `fk_usuarios_habilito_cambio` FOREIGN KEY (`cambio_password_habilitado_por`)
    REFERENCES ${esquema}.usuarios (`id`) ON DELETE SET NULL;
