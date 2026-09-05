-- =============================================================================
--  V024 - Dias sin clase
-- =============================================================================
--  QUE RESUELVE
--  Los ciclos de V023 le ponen al job de ausencias el limite grueso: fuera del
--  ciclo no hay clases, asi que no hay ausencias. Lo que queda adentro sigue
--  sin resolverse: feriados nacionales, receso de invierno, jornadas
--  institucionales, un paro. Para el sistema son dias de cursada normales y le
--  genera una ausencia AUTOMATICA a cada docente que tenia clase.
--
--  Eso no es un dato incompleto, es un dato FALSO: dice que alguien falto un
--  dia en el que la institucion estaba cerrada. Y como las ausencias se
--  materializan como filas reales, limpiar despues cuesta mas que no generarlas.
--
--  QUE NO HACE
--  No bloquea tomar asistencia. Si alguien viene a trabajar un feriado, la
--  camara lo registra igual y esa marca es tan valida como cualquier otra: lo
--  que el dia no laborable dice es "no esperes que vengan", no "no pueden
--  venir". Impedirlo convertiria una excepcion administrativa en una pared.
--
--  POR QUE POR FECHA SUELTA Y NO POR CICLO
--  Un feriado es del calendario, no del ciclo: cae el 25 de mayo exista o no un
--  ciclo abierto ese ano. Colgarlo del ciclo obligaria a recargarlos cada vez
--  que se abre uno nuevo, y a decidir que pasa con un feriado que cae fuera de
--  todos los ciclos.
--
--  QUE CUIDAR
--  El UNIQUE por (institucion_id, fecha) es lo que evita que el mismo feriado
--  quede cargado dos veces con motivos distintos. No hay baja logica: un dia
--  cargado por error se borra, porque no es informacion de la que dependa nada
--  --ninguna asistencia lo referencia-- y conservar basura marcada como
--  inactiva solo ensuciaria el listado.
--
--  PARA VOLVER ATRAS
--    DROP TABLE dias_no_laborables;
-- =============================================================================

CREATE TABLE ${esquema}.dias_no_laborables (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL,
  `fecha` date NOT NULL COMMENT 'El dia en que no hay clases.',
  `motivo` varchar(120) NOT NULL COMMENT 'Por que. Se muestra en el listado para poder revisarlo despues.',
  `creado_por` bigint(20) DEFAULT NULL COMMENT 'Quien lo cargo. NULL si esa cuenta ya no existe.',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_dias_inst_fecha` (`institucion_id`,`fecha`),
  CONSTRAINT `fk_dias_institucion` FOREIGN KEY (`institucion_id`) REFERENCES ${esquema}.instituciones (`id`),
  CONSTRAINT `fk_dias_creado_por` FOREIGN KEY (`creado_por`) REFERENCES ${esquema}.usuarios (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Dias dentro del ciclo en los que no se dicta clase. El job de ausencias los saltea.';
