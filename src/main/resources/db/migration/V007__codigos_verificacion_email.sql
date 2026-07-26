-- =============================================================================
--  V007__codigos_verificacion_email.sql
--
--  Suma la verificacion de correo de las cuentas y la recuperacion de
--  contrasena autogestionada, ambas resueltas con un codigo de un solo uso
--  enviado por mail (OTP de 6 digitos).
--
--  Por que un OTP y no un link: la aplicacion se despliega en localhost, asi
--  que un enlace de verificacion solo funcionaria si el correo se abre en la
--  misma maquina. Con un codigo, la persona lo tipea en la pantalla y el flujo
--  no depende de que la URL sea alcanzable desde donde llego el mail.
--
--  Por que una sola tabla para los dos propositos: los dos flujos comparten
--  exactamente el mismo ciclo de vida (generar, enviar, validar, invalidar) y
--  las mismas defensas. Duplicar la tabla duplicaria tambien la logica de
--  expiracion e intentos, que es donde se cometen los errores de seguridad.
--
--  Decisiones de seguridad, todas verificables en esta tabla:
--    1. codigo_hash: el codigo NUNCA se guarda en claro, igual que las
--       contrasenas. Quien lea la base no puede usar un codigo pendiente.
--    2. expira_en: ventana corta. Un codigo viejo no sirve aunque se filtre.
--    3. usado_en: un solo uso. Reutilizarlo no revalida nada.
--    4. intentos: un OTP de 6 digitos son un millon de combinaciones, pero sin
--       tope de intentos se prueban por fuerza bruta. Al llegar al maximo el
--       codigo se invalida.
--    5. email: se guarda a que direccion se envio, no se lee del usuario al
--       validar. Si alguien cambia el correo despues de pedir el codigo, el
--       codigo sigue atado a la direccion que efectivamente lo recibio.
--
--  institucion_id: la tabla es tenant-scoped como el resto, aunque la
--  recuperacion de contrasena se resuelve ANTES del login, cuando todavia no
--  hay TenantContext. En ese flujo el service filtra explicitamente por el
--  usuario encontrado, sin depender del filtro de Hibernate.
-- =============================================================================

-- ---------------------------------------------------------------------------
--  1. Marca de correo verificado en la cuenta
-- ---------------------------------------------------------------------------
ALTER TABLE usuarios
    ADD COLUMN email_verificado_en TIMESTAMP NULL
        COMMENT 'Cuando la persona confirmo que controla este correo. NULL = sin verificar'
        AFTER email;

-- ---------------------------------------------------------------------------
--  2. Codigos de un solo uso
-- ---------------------------------------------------------------------------
CREATE TABLE codigos_verificacion (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    institucion_id  BIGINT          NOT NULL,
    usuario_id      BIGINT          NOT NULL,
    proposito       VARCHAR(30)     NOT NULL                COMMENT 'VERIFICACION_EMAIL | RECUPERACION_PASSWORD',
    email           VARCHAR(120)    NOT NULL                COMMENT 'Direccion a la que se envio el codigo',
    codigo_hash     VARCHAR(255)    NOT NULL                COMMENT 'Hash del OTP - nunca en texto plano',
    expira_en       TIMESTAMP       NOT NULL                COMMENT 'Despues de este instante el codigo no sirve',
    usado_en        TIMESTAMP       NULL                    COMMENT 'Cuando se consumio. NOT NULL = ya usado',
    intentos        SMALLINT        NOT NULL DEFAULT 0      COMMENT 'Validaciones fallidas acumuladas',
    ip_solicitud    VARCHAR(45)     NULL                    COMMENT 'IP desde donde se pidio el codigo (auditoria)',
    creado_en       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_codigos_verificacion          PRIMARY KEY (id),
    CONSTRAINT fk_codigos_verificacion_usuario  FOREIGN KEY (usuario_id)     REFERENCES usuarios (id)      ON DELETE CASCADE,
    CONSTRAINT fk_codigos_verificacion_inst     FOREIGN KEY (institucion_id) REFERENCES instituciones (id) ON DELETE RESTRICT,

    -- Busqueda del codigo vigente de una persona para un proposito dado.
    INDEX ix_codigos_verificacion_busqueda (usuario_id, proposito, usado_en),
    -- Limpieza periodica de los vencidos.
    INDEX ix_codigos_verificacion_expira (expira_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Codigos de un solo uso para verificar correo y recuperar contrasena';

-- ---------------------------------------------------------------------------
--  3. Los usuarios sembrados quedan sin verificar a proposito
-- ---------------------------------------------------------------------------
--  No se marcan como verificados: nadie confirmo esos buzones, y darlos por
--  buenos vaciaria de sentido a la propia verificacion. Se verifican desde la
--  aplicacion como cualquier otro.
