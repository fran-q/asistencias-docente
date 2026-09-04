-- =============================================================================
--  V006__modelos_faciales_mediumblob.sql
--
--  Agranda la columna que guarda el modelo biometrico cifrado.
--
--  Por que: en V001 la columna se definio como BLOB, que en MariaDB admite
--  hasta 64 KB. El modelo LBPH entrenado (Sprint 4) serializa los histogramas
--  de varias imagenes y, ya cifrado, supera holgadamente ese limite -
--  tipicamente cientos de KB a algunos MB.
--
--  Se usa LONGBLOB (y no MEDIUMBLOB) para que coincida con el tipo que
--  Hibernate espera para un atributo @Lob byte[] en el dialecto MariaDB;
--  asi el chequeo ddl-auto=validate no da falsos negativos. Ver ADR-0007.
-- =============================================================================

ALTER TABLE modelos_faciales
    MODIFY COLUMN embedding_cifrado LONGBLOB NOT NULL
        COMMENT 'Modelo LBPH entrenado y serializado, cifrado con AES (Spring Security Crypto)';
