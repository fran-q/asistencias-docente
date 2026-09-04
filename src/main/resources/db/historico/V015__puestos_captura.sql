-- =============================================================================
--  V015__puestos_captura.sql
--
--  Los equipos desde los que se permite capturar datos biometricos.
--
--  Por que existe esta tabla. El pase de asistencia y el registro del rostro se
--  toman en un lugar fisico, con una camara puesta donde los docentes pasan. No
--  son pantallas que se consultan: son una estacion de trabajo. Cuando se abre
--  el acceso movil para la gestion --listados, reportes, justificaciones-- hay
--  que poder decir que ESAS dos pantallas no viajan con la persona.
--
--  Ademas de operativo es de tratamiento: lo que se captura son datos sensibles
--  segun la Ley 25.326, y que la captura ocurra en equipos registrados por la
--  institucion es lo que la Resolucion AAIP 255/2022 espera de un entorno
--  controlado. Repartido entre telefonos personales, ese control no existe.
--
--  Por que no alcanzaba con el tamano de la pantalla. Es la salida que primero
--  aparece y falla en las dos direcciones: si la secretaria angosta la ventana
--  del navegador queda bloqueada justo en la unica maquina que tiene que
--  funcionar, y una tablet en horizontal informa 1024 px y pasa igual. El ancho
--  de la ventana no dice que equipo es: dice cuanto espacio hay para dibujar.
--  El user-agent tampoco sirve, se falsifica desde el menu del navegador.
--
--  Por que hay tabla y no una cookie autofirmada. Una cookie firmada con la
--  clave de la aplicacion seria mas barata y no necesitaria esta migracion,
--  pero no habria manera de revocarla ni de saber que equipos estan
--  habilitados. Con la tabla la institucion ve la lista, nombra cada puesto y
--  da de baja el que ya no corresponde. Sin ella, un equipo autorizado lo queda
--  para siempre y en silencio.
--
--  Detalle completo en ADR-0015.
-- =============================================================================

CREATE TABLE ${esquema}.puestos_captura (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    institucion_id  BIGINT          NOT NULL,
    nombre          VARCHAR(80)     NOT NULL                COMMENT 'Como lo llama la institucion: "Secretaria PC-1"',
    token_hash      VARCHAR(255)    NOT NULL                COMMENT 'Hash del token que vive en la cookie del equipo - nunca en texto plano',
    activo          TINYINT(1)      NOT NULL DEFAULT 1,
    fecha_baja      DATE            NULL                    COMMENT 'Cuando se revoco. NULL = sigue habilitado',
    designado_por   BIGINT          NULL                    COMMENT 'Usuario que autorizo el equipo. NULL si esa cuenta se borro',
    ultimo_uso_en   TIMESTAMP       NULL                    COMMENT 'Ultima vez que el puesto paso el control',
    creado_en       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_puestos_captura           PRIMARY KEY (id),
    CONSTRAINT fk_puestos_captura_inst      FOREIGN KEY (institucion_id) REFERENCES ${esquema}.instituciones (id) ON DELETE RESTRICT,
    -- SET NULL y no CASCADE: si se elimina la cuenta que designo el puesto, el puesto
    -- sigue habilitado. Quien lo autorizo es un dato de rastro, no una dependencia:
    -- borrar un usuario no puede dejar a la institucion sin poder tomar asistencia.
    CONSTRAINT fk_puestos_captura_designante FOREIGN KEY (designado_por) REFERENCES ${esquema}.usuarios (id) ON DELETE SET NULL,

    -- El token identifica al equipo por si solo, asi que no puede repetirse entre
    -- instituciones: si dos coincidieran, una cookie valdria en el tenant equivocado.
    CONSTRAINT uq_puestos_captura_token     UNIQUE (token_hash),
    -- Dos puestos con el mismo nombre en la misma institucion no se distinguen en la
    -- pantalla de revocacion, que es donde importa saber cual se esta dando de baja.
    CONSTRAINT uq_puestos_captura_nombre    UNIQUE (institucion_id, nombre),

    -- El listado de puestos de una institucion, que es la unica consulta de pantalla.
    INDEX ix_puestos_captura_inst (institucion_id, activo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Equipos autorizados a capturar datos biometricos (ADR-0015)';

-- ---------------------------------------------------------------------------
--  No se siembra ningun puesto
-- ---------------------------------------------------------------------------
--  Ni siquiera para el CENT 35 de prueba. Sembrar uno significaria inventar un
--  token y dejarlo escrito en una migracion versionada de un repositorio
--  publico, que es exactamente lo que el hash de la columna existe para evitar.
--
--  La consecuencia es que despues de aplicar esta migracion NADIE puede tomar
--  asistencia hasta designar el primer puesto desde la aplicacion. Es un paso
--  de puesta en marcha, no una falla, y esta anotado como tal en ADR-0015.
