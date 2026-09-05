-- =============================================================================
--  V023 - Ciclos lectivos y periodos, y la comision atada a uno
-- =============================================================================
--  QUE RESUELVE
--  El ano calendario no existia en el sistema. Lo unico que se le parecia era
--  materias.anio, que es el ano DENTRO del plan --primero, segundo, tercero--,
--  no 2026 ni 2027.
--
--  Sin eso pasaban tres cosas:
--
--  1. uq_comisiones_materia_codigo (materia_id, codigo) impedia que existiera
--     "Matematica I - Comision A" en 2026 y otra vez en 2027. Para dar la misma
--     materia el ano siguiente habia que pisar la comision existente, y con eso
--     el ano anterior dejaba de poder reconstruirse: los reportes de 2026
--     empezaban a mostrar el docente de 2027.
--
--  2. El job de ausencias tomaba todos los horarios activos de ese dia de la
--     semana, sin ningun limite de fechas. Generaba ausencias en enero y en el
--     receso, y en marzo de 2027 habria seguido generandolas con los horarios
--     de 2026 hasta que alguien los diera de baja a mano.
--
--  3. Dar de baja una materia se bloqueaba si tenia comisiones activas. Una
--     comision de 2026 impedia para siempre sacar la materia del plan.
--
--  POR QUE EL PERIODO ES UNA TABLA Y NO UN ENUM EN LA COMISION
--  Hay materias anuales y materias cuatrimestrales. Con un enum
--  (ANUAL | PRIMER_CUATRIMESTRE | SEGUNDO_CUATRIMESTRE) las fechas de corte
--  quedarian repartidas entre columnas del ciclo y un switch en Java, y cada
--  consumidor tendria que ramificar por tipo para saber si una fecha cae
--  adentro.
--
--  Con la tabla, "Anual" y "1er cuatrimestre" son los dos lo mismo --un nombre
--  y dos fechas-- y todos preguntan igual:  :fecha BETWEEN p.fecha_inicio AND
--  p.fecha_fin. Ademas admite trimestres o un periodo de verano sin cambiar el
--  esquema.
--
--  POR QUE EL CICLO CUELGA DE LA COMISION Y NO DE LA MATERIA
--  Lo que se vuelve a ofrecer cada ano es la comision: su docente, sus
--  horarios, su cupo. La materia y la carrera son el plan y son estables. Las
--  asistencias llegan al ciclo por su comision, asi que no hace falta tocar esa
--  tabla ni denormalizar nada mas.
--
--  QUE CUIDAR EN EL BACKFILL
--  Las comisiones que ya existen necesitan un periodo o la columna no puede
--  quedar NOT NULL. Se crea un ciclo del ano en curso por institucion, con un
--  unico periodo "Anual" que va del 1 de enero al 31 de diciembre.
--
--  El ano completo es a proposito y no un descuido: la migracion no puede saber
--  cuando empiezan y terminan las clases de cada institucion, y cualquier rango
--  mas angosto correria el riesgo de dejar asistencias ya cargadas FUERA de su
--  propio ciclo. La institucion lo ajusta desde la pantalla.
--
--  PARA VOLVER ATRAS
--    ALTER TABLE comisiones
--      DROP FOREIGN KEY fk_comisiones_periodo,
--      DROP INDEX uq_comisiones_materia_codigo_periodo,
--      DROP COLUMN periodo_id,
--      ADD UNIQUE KEY uq_comisiones_materia_codigo (materia_id, codigo);
--    DROP TABLE periodos_lectivos;
--    DROP TABLE ciclos_lectivos;
-- =============================================================================

-- -----------------------------------------------------------------------------
--  1. El ciclo lectivo
-- -----------------------------------------------------------------------------
CREATE TABLE ${esquema}.ciclos_lectivos (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL,
  `anio` smallint(6) NOT NULL COMMENT 'Ano calendario: 2026, 2027. No confundir con materias.anio, que es el ano del plan.',
  `fecha_inicio` date NOT NULL COMMENT 'Primer dia del ciclo. Acota a sus periodos.',
  `fecha_fin` date NOT NULL COMMENT 'Ultimo dia del ciclo.',
  `estado` varchar(20) NOT NULL DEFAULT 'PREPARACION'
    COMMENT 'PREPARACION | ACTIVO | CERRADO. Cerrado congela la estructura, no las asistencias.',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `cerrado_en` timestamp NULL DEFAULT NULL COMMENT 'Cuando se cerro. NULL = sigue abierto.',
  `cerrado_por` bigint(20) DEFAULT NULL COMMENT 'Quien lo cerro. NULL si esa cuenta ya no existe.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ciclos_inst_anio` (`institucion_id`,`anio`),
  KEY `ix_ciclos_inst_estado` (`institucion_id`,`estado`),
  CONSTRAINT `fk_ciclos_institucion` FOREIGN KEY (`institucion_id`) REFERENCES ${esquema}.instituciones (`id`),
  CONSTRAINT `fk_ciclos_cerrado_por` FOREIGN KEY (`cerrado_por`) REFERENCES ${esquema}.usuarios (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_ciclos_rango` CHECK (`fecha_fin` >= `fecha_inicio`),
  CONSTRAINT `ck_ciclos_anio_razonable` CHECK (`anio` between 2000 and 2200),
  CONSTRAINT `ck_ciclos_estado` CHECK (`estado` in ('PREPARACION','ACTIVO','CERRADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Ano calendario de cursada. Agrupa los periodos y, por ellos, la oferta de comisiones.';

-- -----------------------------------------------------------------------------
--  2. Los periodos de cada ciclo
-- -----------------------------------------------------------------------------
--  institucion_id va denormalizado igual que en asistencias: el filtro de
--  Hibernate actua por columna propia, y sin ella el periodo dependeria de un
--  JOIN al ciclo para acotarse por tenant.
CREATE TABLE ${esquema}.periodos_lectivos (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `ciclo_id` bigint(20) NOT NULL,
  `institucion_id` bigint(20) NOT NULL COMMENT 'Denormalizado desde el ciclo, para que el filtro de tenant actue sin JOIN.',
  `nombre` varchar(60) NOT NULL COMMENT 'Anual, 1er cuatrimestre, 2do cuatrimestre. Lo elige la institucion.',
  `fecha_inicio` date NOT NULL,
  `fecha_fin` date NOT NULL,
  `orden` smallint(6) NOT NULL DEFAULT 1 COMMENT 'Para listarlos en un orden que tenga sentido y no por fecha o alfabeto.',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_periodos_ciclo_nombre` (`ciclo_id`,`nombre`),
  KEY `ix_periodos_inst_fechas` (`institucion_id`,`fecha_inicio`,`fecha_fin`),
  CONSTRAINT `fk_periodos_ciclo` FOREIGN KEY (`ciclo_id`) REFERENCES ${esquema}.ciclos_lectivos (`id`),
  CONSTRAINT `fk_periodos_institucion` FOREIGN KEY (`institucion_id`) REFERENCES ${esquema}.instituciones (`id`),
  CONSTRAINT `ck_periodos_rango` CHECK (`fecha_fin` >= `fecha_inicio`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Tramo del ciclo en el que corre una comision. Anual y cuatrimestral son el mismo tipo de cosa.';

-- -----------------------------------------------------------------------------
--  3. Un ciclo con su periodo anual para lo que ya existe
-- -----------------------------------------------------------------------------
--  Solo para las instituciones que tienen comisiones cargadas: crear un ciclo
--  vacio en una institucion recien dada de alta seria inventarle estructura que
--  nadie pidio.
INSERT INTO ${esquema}.ciclos_lectivos (institucion_id, anio, fecha_inicio, fecha_fin, estado)
SELECT DISTINCT m.institucion_id,
       YEAR(CURDATE()),
       MAKEDATE(YEAR(CURDATE()), 1),
       CONCAT(YEAR(CURDATE()), '-12-31'),
       'ACTIVO'
  FROM ${esquema}.comisiones c
  JOIN ${esquema}.materias m ON m.id = c.materia_id;

INSERT INTO ${esquema}.periodos_lectivos (ciclo_id, institucion_id, nombre, fecha_inicio, fecha_fin, orden)
SELECT cl.id, cl.institucion_id, 'Anual', cl.fecha_inicio, cl.fecha_fin, 1
  FROM ${esquema}.ciclos_lectivos cl;

-- -----------------------------------------------------------------------------
--  4. La comision elige periodo
-- -----------------------------------------------------------------------------
ALTER TABLE ${esquema}.comisiones
  ADD COLUMN `periodo_id` bigint(20) DEFAULT NULL
    COMMENT 'Tramo del ciclo en el que corre esta comision. Es lo que la ata a un ano concreto.';

UPDATE ${esquema}.comisiones c
  JOIN ${esquema}.materias m ON m.id = c.materia_id
  JOIN ${esquema}.ciclos_lectivos cl ON cl.institucion_id = m.institucion_id
  JOIN ${esquema}.periodos_lectivos p ON p.ciclo_id = cl.id AND p.nombre = 'Anual'
   SET c.periodo_id = p.id
 WHERE c.periodo_id IS NULL;

ALTER TABLE ${esquema}.comisiones
  MODIFY COLUMN `periodo_id` bigint(20) NOT NULL
    COMMENT 'Tramo del ciclo en el que corre esta comision. Es lo que la ata a un ano concreto.';

-- El codigo de comision se repite entre periodos a proposito: "Comision A" de
-- 2026 y "Comision A" de 2027 son ofertas distintas de la misma materia, y esa
-- repeticion es justamente lo que antes estaba prohibido.
ALTER TABLE ${esquema}.comisiones
  DROP INDEX `uq_comisiones_materia_codigo`,
  ADD UNIQUE KEY `uq_comisiones_materia_codigo_periodo` (`materia_id`,`codigo`,`periodo_id`),
  ADD CONSTRAINT `fk_comisiones_periodo` FOREIGN KEY (`periodo_id`)
    REFERENCES ${esquema}.periodos_lectivos (`id`);
