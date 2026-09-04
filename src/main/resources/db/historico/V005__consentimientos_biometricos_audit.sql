-- =============================================================================
--  V005__consentimientos_biometricos_audit.sql
--
--  Refuerza la auditoria forense sobre la tabla consentimientos_biometricos
--  (creada en V001) para cumplir mejor con la Resolucion AAIP 255/2022 y
--  el principio de "consentimiento demostrable" de la Ley 25.326.
--
--  Por que: la AAIP puede exigir prueba forense de QUIEN otorgo o revoco un
--  consentimiento biometrico, DESDE DONDE y CON QUE NAVEGADOR. La tabla
--  original solo guardaba "registrado_por_usuario_id" + fechas, lo cual es
--  insuficiente para una auditoria estricta.
--
--  Cambios:
--    1. ip_otorgamiento / user_agent_otorgamiento - audit forense de la
--       sesion HTTP que cargo el consentimiento (admin que firmo en
--       representacion del docente).
--    2. revocado_por_usuario_id + ip_revocacion / user_agent_revocacion -
--       audit forense paralelo para la revocacion.
--    3. motivo_revocacion - texto libre opcional (derechos ARCO RNF-14).
--    4. creado_en / actualizado_en - timestamps de housekeeping; distintos
--       de fecha_consentimiento porque el admin puede cargar el dato
--       retroactivamente.
--
--  Decisiones que ya estaban en V001 y se respetan:
--    - vigente BOOLEAN: redundante con (fecha_revocacion IS NULL) pero util
--      como atajo para queries; se mantiene sincronizado a nivel aplicacion.
--    - metodo IN ('ESCRITO','DIGITAL'): en Sprint 3 solo se usa 'ESCRITO'
--      (admin carga en representacion, docente firma en papel). 'DIGITAL'
--      queda reservado para Sprint 4 (login docente).
--    - documento_url: opcional, URL a un PDF escaneado del documento
--      firmado. Sin uso obligatorio por ahora.
-- =============================================================================

ALTER TABLE consentimientos_biometricos
    ADD COLUMN ip_otorgamiento            VARCHAR(45)     NULL  COMMENT 'IPv4 / IPv6 desde donde se cargo el consentimiento'
        AFTER registrado_por_usuario_id,
    ADD COLUMN user_agent_otorgamiento    VARCHAR(500)    NULL  COMMENT 'User-Agent del navegador que cargo el consentimiento'
        AFTER ip_otorgamiento,
    ADD COLUMN revocado_por_usuario_id    BIGINT          NULL  COMMENT 'Usuario que ejecuto la revocacion (NULL si no fue revocado)'
        AFTER user_agent_otorgamiento,
    ADD COLUMN ip_revocacion              VARCHAR(45)     NULL  COMMENT 'IP desde donde se ejecuto la revocacion'
        AFTER revocado_por_usuario_id,
    ADD COLUMN user_agent_revocacion      VARCHAR(500)    NULL  COMMENT 'User-Agent del navegador que revoco'
        AFTER ip_revocacion,
    ADD COLUMN motivo_revocacion          VARCHAR(500)    NULL  COMMENT 'Texto libre opcional - derecho ARCO (RNF-14)'
        AFTER user_agent_revocacion,
    ADD COLUMN creado_en                  TIMESTAMP       NOT NULL  DEFAULT CURRENT_TIMESTAMP
        COMMENT 'Cuando se inserto la fila en la BD (distinto de fecha_consentimiento si fue carga retroactiva)',
    ADD COLUMN actualizado_en             TIMESTAMP       NOT NULL  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT 'Ultima modificacion del registro';

-- FK de la revocacion: igual semantica que registrado_por_usuario_id
ALTER TABLE consentimientos_biometricos
    ADD CONSTRAINT fk_consentimientos_revocado_por
        FOREIGN KEY (revocado_por_usuario_id) REFERENCES usuarios (id) ON DELETE RESTRICT;
