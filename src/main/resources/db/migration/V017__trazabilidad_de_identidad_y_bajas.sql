-- =============================================================================
--  V017 - Trazabilidad del cambio de identidad y de las bajas
-- =============================================================================
--  QUE RESUELVE
--  Desde V016 los datos de identidad son compartidos: cambiar el nombre de una
--  persona lo cambia en su cuenta y en su ficha de docente a la vez. La pantalla
--  avisa antes de hacerlo, pero si alguien confirma no quedaba ningun rastro de
--  que ese cambio ocurrio. Advertir sobre algo delicado y despues no registrarlo
--  es incoherente.
--
--  Lo mismo con las bajas: habia fecha_baja pero no quien la hizo.
--
--  QUE NO ES
--  No es la auditoria general que se descarto en V009 (RF-34 a RF-36). Aquella
--  registraba toda operacion sobre toda entidad, con el estado completo antes y
--  despues en JSON. Esta registra dos cosas puntuales, y por un motivo: cada fila
--  de auditoria con valores anteriores es OTRA copia de datos personales, sujeta
--  a la misma Ley 25.326 que la original. Cuanto mas amplia, mas dificil de
--  sostener frente a un pedido de supresion. Se guarda lo que sirve para
--  responderle a alguien, y nada mas.
--
--  RETENCION
--  Pendiente de definir junto con el resto de la politica de conservacion; ver
--  ADR-0016. Esta tabla es la primera candidata a una purga por antiguedad.
-- =============================================================================


-- -----------------------------------------------------------------------------
--  1. Cambios sobre la identidad de una persona
-- -----------------------------------------------------------------------------
--  Una fila por CAMPO modificado, no por operacion. Cambiar solo el telefono
--  deja una fila y no una copia entera de la persona: es menos dato personal
--  guardado para la misma capacidad de responder que cambio.

CREATE TABLE ${esquema}.cambios_identidad (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL,
  `persona_id` bigint(20) NOT NULL,
  `usuario_id` bigint(20) NOT NULL COMMENT 'Quien hizo el cambio.',
  `campo` varchar(20) NOT NULL,
  `valor_anterior` varchar(120) DEFAULT NULL COMMENT 'NULL cuando el campo estaba vacio.',
  `valor_nuevo` varchar(120) DEFAULT NULL,
  `origen` varchar(20) NOT NULL COMMENT 'Desde que pantalla se hizo el cambio.',
  `fecha` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_cambios_persona_fecha` (`persona_id`,`fecha`),
  KEY `idx_cambios_institucion` (`institucion_id`),
  CONSTRAINT `fk_cambios_institucion` FOREIGN KEY (`institucion_id`) REFERENCES ${esquema}.instituciones (`id`),
  CONSTRAINT `fk_cambios_persona` FOREIGN KEY (`persona_id`) REFERENCES ${esquema}.personas (`id`),
  CONSTRAINT `fk_cambios_usuario` FOREIGN KEY (`usuario_id`) REFERENCES ${esquema}.usuarios (`id`),
  CONSTRAINT `ck_cambios_campo` CHECK (`campo` in ('dni','nombre','apellido','email','telefono')),
  CONSTRAINT `ck_cambios_origen` CHECK (`origen` in ('DOCENTE','USUARIO','REINGRESO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Historial de cambios sobre los datos de identidad de una persona (ADR-0016).';


-- -----------------------------------------------------------------------------
--  2. Quien dio de baja
-- -----------------------------------------------------------------------------
--  Solo una columna con el usuario, sin tabla aparte: la baja ya deja su
--  fecha_baja en la propia fila, asi que lo unico que faltaba era el autor.
--
--  Van las tablas que se dan de baja desde la aplicacion. Quedan afuera
--  instituciones y motivos_carga_manual: la primera no se da de baja desde
--  adentro del sistema y la segunda es un catalogo global que nadie edita.
--
--  ON DELETE SET NULL en todas: si algun dia se suprime la cuenta que hizo la
--  baja, el registro de la baja sobrevive sin su autor, que es preferible a
--  perder la baja entera o a bloquear la supresion de la cuenta.

ALTER TABLE ${esquema}.carreras
  ADD COLUMN `dado_de_baja_por` bigint(20) DEFAULT NULL AFTER `fecha_baja`,
  ADD CONSTRAINT `fk_carreras_baja_usuario` FOREIGN KEY (`dado_de_baja_por`) REFERENCES ${esquema}.usuarios (`id`) ON DELETE SET NULL;

ALTER TABLE ${esquema}.materias
  ADD COLUMN `dado_de_baja_por` bigint(20) DEFAULT NULL AFTER `fecha_baja`,
  ADD CONSTRAINT `fk_materias_baja_usuario` FOREIGN KEY (`dado_de_baja_por`) REFERENCES ${esquema}.usuarios (`id`) ON DELETE SET NULL;

ALTER TABLE ${esquema}.comisiones
  ADD COLUMN `dado_de_baja_por` bigint(20) DEFAULT NULL AFTER `fecha_baja`,
  ADD CONSTRAINT `fk_comisiones_baja_usuario` FOREIGN KEY (`dado_de_baja_por`) REFERENCES ${esquema}.usuarios (`id`) ON DELETE SET NULL;

ALTER TABLE ${esquema}.horarios
  ADD COLUMN `dado_de_baja_por` bigint(20) DEFAULT NULL AFTER `fecha_baja`,
  ADD CONSTRAINT `fk_horarios_baja_usuario` FOREIGN KEY (`dado_de_baja_por`) REFERENCES ${esquema}.usuarios (`id`) ON DELETE SET NULL;

ALTER TABLE ${esquema}.docentes
  ADD COLUMN `dado_de_baja_por` bigint(20) DEFAULT NULL AFTER `fecha_baja`,
  ADD CONSTRAINT `fk_docentes_baja_usuario` FOREIGN KEY (`dado_de_baja_por`) REFERENCES ${esquema}.usuarios (`id`) ON DELETE SET NULL;

ALTER TABLE ${esquema}.usuarios
  ADD COLUMN `dado_de_baja_por` bigint(20) DEFAULT NULL AFTER `fecha_baja`,
  ADD CONSTRAINT `fk_usuarios_baja_usuario` FOREIGN KEY (`dado_de_baja_por`) REFERENCES ${esquema}.usuarios (`id`) ON DELETE SET NULL;

ALTER TABLE ${esquema}.puestos_captura
  ADD COLUMN `dado_de_baja_por` bigint(20) DEFAULT NULL AFTER `fecha_baja`,
  ADD CONSTRAINT `fk_puestos_baja_usuario` FOREIGN KEY (`dado_de_baja_por`) REFERENCES ${esquema}.usuarios (`id`) ON DELETE SET NULL;

ALTER TABLE ${esquema}.modelos_faciales
  ADD COLUMN `dado_de_baja_por` bigint(20) DEFAULT NULL AFTER `fecha_baja`,
  ADD CONSTRAINT `fk_modelos_baja_usuario` FOREIGN KEY (`dado_de_baja_por`) REFERENCES ${esquema}.usuarios (`id`) ON DELETE SET NULL;
