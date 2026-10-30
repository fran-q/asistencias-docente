-- =============================================================================
--  V036 - cambios_identidad.campo se guarda como los demas enums
-- =============================================================================
--  QUE RESUELVE
--  campo y origen eran texto suelto en Java, validado solo por el CHECK. Un
--  error de tipeo compilaba y recien fallaba en MariaDB, porque los tests corren
--  sobre H2, sin estos CHECK. Ahora son enums (CampoIdentidad y
--  OrigenCambioIdentidad), que se guardan con su nombre, en mayusculas, igual
--  que todos los demas del sistema.
--
--  origen ya estaba en mayusculas y no se toca. campo estaba en minusculas.
--
--  EL HISTORIAL
--  Es un registro que no se edita, y esta migracion no le cambia el significado:
--  'dni' y 'DNI' dicen lo mismo. Solo unifica como esta escrito, para que Hibernate
--  pueda leer las filas viejas con el enum.
--
--  POR QUE SE REEMPLAZA EL CHECK SI LA COLLATION NO DISTINGUE MAYUSCULAS
--  El viejo aceptaria 'DNI' igual. Pero su texto tiene que decir lo mismo que el
--  enum: es lo que compara MigracionesIT, y lo que lee quien abre el esquema.
--
--  PARA VOLVER ATRAS
--    ALTER TABLE cambios_identidad DROP CONSTRAINT ck_cambios_campo;
--    UPDATE cambios_identidad SET campo = LOWER(campo);
--    ALTER TABLE cambios_identidad ADD CONSTRAINT ck_cambios_campo
--      CHECK (campo in ('dni','nombre','apellido','email','telefono'));
-- =============================================================================

ALTER TABLE ${esquema}.cambios_identidad
  DROP CONSTRAINT `ck_cambios_campo`;

UPDATE ${esquema}.cambios_identidad
   SET campo = UPPER(campo);

ALTER TABLE ${esquema}.cambios_identidad
  ADD CONSTRAINT `ck_cambios_campo`
    CHECK (`campo` in ('DNI','NOMBRE','APELLIDO','EMAIL','TELEFONO'));
