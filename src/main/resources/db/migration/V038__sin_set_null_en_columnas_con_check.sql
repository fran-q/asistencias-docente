-- =============================================================================
--  V038 - Las columnas con CHECK dejan de tener ON DELETE SET NULL (TD-011)
-- =============================================================================
--  QUE RESUELVE
--  MariaDB 10.6 en adelante rechaza que una columna este a la vez en un CHECK y en
--  una clave foranea con ON DELETE SET NULL: la accion de la foranea podria poner
--  un NULL que viole el CHECK sin que nadie lo pida. 10.4 lo aceptaba, y por eso el
--  esquema quedo atado a un motor que no tiene soporte desde junio de 2024.
--
--  Son cinco foraneas. Se sacan las acciones y se conservan los CHECK, porque los
--  CHECK son lo que sostiene las reglas, y las acciones no se usaban casi nunca:
--    - Las tres hacia modelos_faciales las disparaba un solo camino, la supresion
--      ARCO. Ahora ModeloFacialService.suprimirDatosBiometricos pone esas columnas
--      en NULL antes de borrar. Las asistencias se siguen conservando.
--    - Las dos hacia usuarios no se disparaban: las cuentas son baja logica y ningun
--      camino del sistema borra una fila de usuarios.
--
--  ESTO NO ALCANZA PARA UNA BASE NUEVA EN UN MOTOR NUEVO
--  V001 sigue sin aplicar ahi, y una migracion aplicada no se edita. Una base nueva
--  arranca de despliegue/base-inicial.sql, que ya trae el esquema hasta aca y el
--  historial de Flyway: V001 no vuelve a correr.
--
--  DOS ALTER POR TABLA
--  MariaDB no deja sacar y agregar una foranea con el mismo
--  nombre en la misma sentencia. El DDL no es transaccional: si esto se corta a la
--  mitad, ver 04-convenciones (Migraciones).
--
--  PARA VOLVER ATRAS
--  Volver a agregarlas con ON DELETE SET NULL, solamente sobre MariaDB 10.4.
-- =============================================================================

ALTER TABLE ${esquema}.asistencias
  DROP FOREIGN KEY `fk_asistencias_modelo_facial`;

ALTER TABLE ${esquema}.asistencias
  ADD CONSTRAINT `fk_asistencias_modelo_facial` FOREIGN KEY (`modelo_facial_id`)
    REFERENCES ${esquema}.modelos_faciales (`id`);

ALTER TABLE ${esquema}.bloques_presencia
  DROP FOREIGN KEY `fk_bloques_modelo_entrada`,
  DROP FOREIGN KEY `fk_bloques_modelo_salida`,
  DROP FOREIGN KEY `fk_bloques_cerrado_por`,
  DROP FOREIGN KEY `fk_bloques_abierto_por`;

ALTER TABLE ${esquema}.bloques_presencia
  ADD CONSTRAINT `fk_bloques_modelo_entrada` FOREIGN KEY (`modelo_facial_entrada_id`)
    REFERENCES ${esquema}.modelos_faciales (`id`),
  ADD CONSTRAINT `fk_bloques_modelo_salida` FOREIGN KEY (`modelo_facial_salida_id`)
    REFERENCES ${esquema}.modelos_faciales (`id`),
  ADD CONSTRAINT `fk_bloques_cerrado_por` FOREIGN KEY (`cerrado_por_usuario_id`)
    REFERENCES ${esquema}.usuarios (`id`),
  ADD CONSTRAINT `fk_bloques_abierto_por` FOREIGN KEY (`abierto_por_usuario_id`)
    REFERENCES ${esquema}.usuarios (`id`);
