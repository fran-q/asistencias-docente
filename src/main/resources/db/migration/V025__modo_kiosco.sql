-- =============================================================================
--  V025 - Modo kiosco: tomar asistencia sin sesion abierta
-- =============================================================================
--  QUE RESUELVE
--  El pase exige una sesion abierta, y en los turnos sin personal administrativo
--  no hay quien la abra: son turnos sin registro. Lo que la institucion venia
--  haciendo para evitarlo -dejar una sesion abierta todo el dia- es peor, porque
--  esa sesion habilita TODAS las funciones de la cuenta a quien se siente en esa
--  maquina y no solo el pase.
--
--  A partir de aca el puesto de captura puede operar desatendido: la institucion
--  del registro sale del propio equipo, cuyo token ya identifica a una sola
--  (uq_puestos_captura_token es unico global). Ver ADR-0019.
--
--  QUE CUIDAR
--  1. Habilitar el kiosco NO es lo mismo que designar el equipo, y por eso son
--     dos columnas y dos momentos. Designar dice "la captura ocurre aca";
--     habilitar el kiosco dice "ademas puede ocurrir sin nadie mirando". La
--     segunda es una atenuacion deliberada de un control de seguridad y tiene
--     que quedar asentado quien la tomo (RF-85).
--
--  2. El rastro NO se borra al deshabilitar. Las columnas conservan la ultima
--     habilitacion aunque kiosco_habilitado vuelva a 0, asi que se puede decir
--     "estuvo desatendido desde tal fecha". Limpiarlas seria perder justo el
--     dato que hace falta para responder por un periodo pasado.
--
--  3. El vencimiento por inactividad (RF-88) NO agrega columna: se calcula
--     contra ultimo_uso_en, que ya existe desde V001 y el interceptor ya
--     refresca en cada peticion del puesto.
--
--  4. puesto_id va con ON DELETE RESTRICT, que es la regla general del sistema.
--     Los puestos no se borran: se revocan con baja logica (activo, fecha_baja),
--     y el historial de puestos se conserva a proposito porque dice desde donde
--     se capturo y hasta cuando. No aplica el SET NULL de modelos_faciales, que
--     existe porque ARCO si borra fisicamente.
--
--  SI ESTA MIGRACION FALLA
--    ALTER TABLE asistenciautomatica.asistencias
--      DROP FOREIGN KEY fk_asistencias_puesto, DROP COLUMN puesto_id;
--    ALTER TABLE asistenciautomatica.bloques_presencia
--      DROP FOREIGN KEY fk_bloques_puesto, DROP COLUMN puesto_id;
--    ALTER TABLE asistenciautomatica.puestos_captura
--      DROP CONSTRAINT ck_puestos_kiosco_fechado,
--      DROP FOREIGN KEY fk_puestos_kiosco_habilitado_por,
--      DROP COLUMN kiosco_habilitado_por,
--      DROP COLUMN kiosco_habilitado_en,
--      DROP COLUMN kiosco_habilitado;
--    DELETE FROM asistenciautomatica_meta.flyway_schema_history WHERE version = '25';
--
--  El orden importa: las dos primeras sueltan las FK que apuntan a puestos.
-- =============================================================================


-- -----------------------------------------------------------------------------
--  1. El puesto puede operar sin sesion
-- -----------------------------------------------------------------------------
--  Nace en 0 para TODOS los puestos existentes, incluido el que ya este
--  designado. Que una migracion habilite el funcionamiento desatendido por su
--  cuenta seria exactamente lo que el punto 1 de QUE CUIDAR quiere evitar: la
--  institucion tiene que pedirlo.

ALTER TABLE ${esquema}.puestos_captura
  ADD COLUMN `kiosco_habilitado` tinyint(1) NOT NULL DEFAULT 0
    COMMENT 'Si este equipo puede tomar asistencia sin sesion abierta (RF-84, RF-85).'
    AFTER `activo`,
  ADD COLUMN `kiosco_habilitado_en` timestamp NULL DEFAULT NULL
    COMMENT 'Cuando se habilito por ultima vez. Se conserva aunque despues se deshabilite.'
    AFTER `kiosco_habilitado`,
  ADD COLUMN `kiosco_habilitado_por` bigint(20) DEFAULT NULL
    COMMENT 'Quien lo habilito. NULL si esa cuenta se suprimio.'
    AFTER `kiosco_habilitado_en`,

  ADD KEY `fk_puestos_kiosco_habilitado_por` (`kiosco_habilitado_por`),
  ADD CONSTRAINT `fk_puestos_kiosco_habilitado_por` FOREIGN KEY (`kiosco_habilitado_por`)
    REFERENCES ${esquema}.usuarios (`id`) ON DELETE SET NULL,

  -- Un kiosco habilitado sin fecha no se puede auditar: no habria desde cuando
  -- ese equipo esta operando sin supervision. La fecha se exige; el usuario no,
  -- porque la FK lo pone en NULL si algun dia se suprime esa cuenta y eso no
  -- puede romper una fila ya escrita (mismo criterio que V020).
  ADD CONSTRAINT `ck_puestos_kiosco_fechado` CHECK (
    `kiosco_habilitado` = 0 OR `kiosco_habilitado_en` IS NOT NULL
  );


-- -----------------------------------------------------------------------------
--  2. De que equipo salio cada marca
-- -----------------------------------------------------------------------------
--  Sin sesion no hay usuario a quien atribuir el registro, y "desde donde se
--  hizo" es lo que reemplaza a "quien lo hizo" (RF-89). Sin esto el kiosco
--  produce marcas que no se pueden rastrear desde el sistema: el dato quedaria
--  solo en archivos de log, que se rotan.
--
--  NULL a proposito en tres casos que significan cosas distintas: las marcas
--  anteriores a esta migracion, las cargas manuales -que las hace una persona
--  identificada, asi que el equipo no agrega nada- y las ausencias que genera el
--  job, que no salen de ningun equipo.

ALTER TABLE ${esquema}.asistencias
  ADD COLUMN `puesto_id` bigint(20) DEFAULT NULL
    COMMENT 'Equipo desde el que se registro. NULL en cargas manuales, ausencias generadas y marcas previas a V025.'
    AFTER `bloque_id`,
  ADD KEY `fk_asistencias_puesto` (`puesto_id`),
  ADD CONSTRAINT `fk_asistencias_puesto` FOREIGN KEY (`puesto_id`)
    REFERENCES ${esquema}.puestos_captura (`id`);

ALTER TABLE ${esquema}.bloques_presencia
  ADD COLUMN `puesto_id` bigint(20) DEFAULT NULL
    COMMENT 'Equipo desde el que se abrio el bloque. NULL en los previos a V025.'
    AFTER `estado_salida`,
  ADD KEY `fk_bloques_puesto` (`puesto_id`),
  ADD CONSTRAINT `fk_bloques_puesto` FOREIGN KEY (`puesto_id`)
    REFERENCES ${esquema}.puestos_captura (`id`);
