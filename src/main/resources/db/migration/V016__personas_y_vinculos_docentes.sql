-- =============================================================================
--  V016 - Persona separada de usuario y de vinculo docente
-- =============================================================================
--  Ver ADR-0016.
--
--  QUE CAMBIA
--  Hasta aca `usuarios` y `docentes` guardaban cada uno su copia de la identidad
--  (nombre, apellido, email) sin conocerse entre si. Un administrador que ademas
--  da clases existia dos veces y nada decia que fuera la misma persona.
--
--  A partir de esta migracion la identidad vive en `personas` y las otras dos
--  tablas la referencian: `usuarios` pasa a ser solo la cuenta de acceso y
--  `docentes` pasa a ser un periodo de vinculo laboral.
--
--  POR QUE `personas` LLEVA institucion_id
--  Porque la misma persona fisica que trabaja en dos institutos tiene que ser dos
--  filas, una por institucion. Si el DNI fuera unico a nivel sistema, al cargar a
--  alguien que ya existe en otra institucion el sistema tendria que reaccionar de
--  alguna forma, y ahi mismo revela que esa persona trabaja en otro lado. Cada
--  institucion es responsable de los datos que ella recolecto. Ver ADR-0002.
--
--  POR QUE SE CAE EL UNIQUE DEL LEGAJO
--  Con varios periodos por persona, el mismo legajo se repite de forma legitima
--  entre un vinculo cerrado y su reingreso. La unicidad entre vinculos vigentes se
--  valida en el service, que es donde se puede expresar "vigente".
--
--  SOBRE EL BACKFILL
--  Se aplica sobre una base vacia, asi que en la practica no mueve ninguna fila.
--  Va escrito igual, y sin deduplicar: se crea una persona por cada docente y una
--  por cada usuario. `usuarios` no tiene DNI, asi que cruzarlos por nombre o correo
--  seria adivinar, y un backfill que inventa vinculos entre personas es peor que no
--  hacer el cambio. Fusionar dos personas que son la misma es una accion manual.
-- =============================================================================


-- -----------------------------------------------------------------------------
--  1. La identidad
-- -----------------------------------------------------------------------------

CREATE TABLE ${esquema}.personas (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `institucion_id` bigint(20) NOT NULL,
  `dni` varchar(15) DEFAULT NULL COMMENT 'Puede faltar: una persona creada a partir de una cuenta de acceso no lo trae.',
  `nombre` varchar(80) NOT NULL,
  `apellido` varchar(80) DEFAULT NULL COMMENT 'NULL en las cuentas institucionales, que no son una persona fisica.',
  `email` varchar(120) DEFAULT NULL COMMENT 'Correo de contacto. El de acceso al sistema vive en usuarios.email.',
  `telefono` varchar(30) DEFAULT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `actualizado_en` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_personas_inst_dni` (`institucion_id`,`dni`),
  KEY `idx_personas_apellido` (`apellido`,`nombre`),
  KEY `idx_personas_institucion` (`institucion_id`),
  CONSTRAINT `fk_personas_institucion` FOREIGN KEY (`institucion_id`) REFERENCES ${esquema}.instituciones (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Identidad de una persona dentro de una institucion. No se borra nunca.';


-- -----------------------------------------------------------------------------
--  2. Las dos tablas pasan a referenciarla
-- -----------------------------------------------------------------------------

ALTER TABLE ${esquema}.usuarios
  ADD COLUMN `persona_id` bigint(20) DEFAULT NULL AFTER `institucion_id`;

ALTER TABLE ${esquema}.docentes
  ADD COLUMN `persona_id` bigint(20) DEFAULT NULL AFTER `institucion_id`;


-- -----------------------------------------------------------------------------
--  3. Backfill
-- -----------------------------------------------------------------------------
--  Las dos columnas temporales existen solo para correlacionar cada persona recien
--  creada con la fila que la origino. Se descartan al final del bloque.

ALTER TABLE ${esquema}.personas
  ADD COLUMN `origen_tmp` varchar(10) DEFAULT NULL,
  ADD COLUMN `origen_id_tmp` bigint(20) DEFAULT NULL;

INSERT INTO ${esquema}.personas
  (institucion_id, dni, nombre, apellido, email, telefono, origen_tmp, origen_id_tmp)
SELECT institucion_id, dni, nombre, apellido, email, telefono, 'docente', id
  FROM ${esquema}.docentes;

UPDATE ${esquema}.docentes d
  JOIN ${esquema}.personas p
    ON p.origen_tmp = 'docente' AND p.origen_id_tmp = d.id
   SET d.persona_id = p.id;

INSERT INTO ${esquema}.personas
  (institucion_id, nombre, apellido, email, origen_tmp, origen_id_tmp)
SELECT institucion_id, nombre, apellido, email, 'usuario', id
  FROM ${esquema}.usuarios;

UPDATE ${esquema}.usuarios u
  JOIN ${esquema}.personas p
    ON p.origen_tmp = 'usuario' AND p.origen_id_tmp = u.id
   SET u.persona_id = p.id;

ALTER TABLE ${esquema}.personas
  DROP COLUMN `origen_tmp`,
  DROP COLUMN `origen_id_tmp`;


-- -----------------------------------------------------------------------------
--  4. La referencia pasa a ser obligatoria
-- -----------------------------------------------------------------------------

ALTER TABLE ${esquema}.usuarios
  MODIFY COLUMN `persona_id` bigint(20) NOT NULL,
  ADD UNIQUE KEY `uq_usuarios_persona` (`persona_id`),
  ADD CONSTRAINT `fk_usuarios_persona` FOREIGN KEY (`persona_id`) REFERENCES ${esquema}.personas (`id`);

ALTER TABLE ${esquema}.docentes
  MODIFY COLUMN `persona_id` bigint(20) NOT NULL,
  ADD KEY `idx_docentes_persona` (`persona_id`),
  ADD CONSTRAINT `fk_docentes_persona` FOREIGN KEY (`persona_id`) REFERENCES ${esquema}.personas (`id`);


-- -----------------------------------------------------------------------------
--  5. Se van las columnas que ahora viven en personas
-- -----------------------------------------------------------------------------
--  usuarios.email NO se va: sigue siendo el identificador de acceso y el destino
--  de los codigos de verificacion y de recuperacion.

ALTER TABLE ${esquema}.usuarios
  DROP COLUMN `nombre`,
  DROP COLUMN `apellido`;

ALTER TABLE ${esquema}.docentes
  DROP INDEX `uq_docentes_inst_dni`,
  DROP INDEX `uq_docentes_inst_legajo`,
  DROP INDEX `idx_docentes_apellido`,
  DROP COLUMN `dni`,
  DROP COLUMN `nombre`,
  DROP COLUMN `apellido`,
  DROP COLUMN `email`,
  DROP COLUMN `telefono`,
  ADD KEY `idx_docentes_inst_legajo` (`institucion_id`,`legajo`);


-- -----------------------------------------------------------------------------
--  6. Constancia del dato biometrico suprimido
-- -----------------------------------------------------------------------------
--  Al cerrarse un vinculo docente muere su consentimiento, y un modelo facial que
--  sobreviviera quedaria almacenado sin base legal que lo respalde. Guardarlo
--  "inactivo" no alcanza: una fila inactiva sigue conteniendo el dato biometrico.
--
--  Entonces se borra el CONTENIDO y se conserva la FILA. El embedding pasa a NULL
--  y quedan las fechas y el motivo. Una inspeccion puede ver que existio un modelo,
--  cuando se registro y cuando se suprimio, sin que el dato sensible siga ahi.
--  Ademas las asistencias historicas mantienen su modelo_facial_id, que con el
--  borrado fisico de la fila se habria puesto en NULL.

ALTER TABLE ${esquema}.modelos_faciales
  MODIFY COLUMN `embedding_cifrado` longblob DEFAULT NULL COMMENT 'NULL cuando el dato fue suprimido. La fila queda como constancia.',
  ADD COLUMN `fecha_supresion` timestamp NULL DEFAULT NULL COMMENT 'Cuando se borro el embedding. NULL si sigue vigente.' AFTER `fecha_baja`,
  ADD COLUMN `motivo_supresion` varchar(120) DEFAULT NULL COMMENT 'Por que se borro: fin de vinculo, derecho ARCO, etc.' AFTER `fecha_supresion`;
