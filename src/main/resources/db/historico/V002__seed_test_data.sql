-- =============================================================================
--  V002__seed_test_data.sql
--  Datos de prueba para desarrollo y demo: 2 instituciones + 4 usuarios.
--
--  ATENCION: estos datos son de PRUEBA. Las contrasenas estan documentadas
--  en Documentacion/credenciales_proyecto.txt. En un despliegue real:
--    - eliminar/no aplicar esta migracion, o
--    - cambiar las contrasenas inmediatamente despues de instalar.
--
--  Hashes BCrypt (cost 10) generados con Python bcrypt - compatibles con
--  Spring Security BCryptPasswordEncoder (acepta $2a$, $2b$, $2y$).
-- =============================================================================

-- ============================================================
--  Instituciones
-- ============================================================
INSERT INTO instituciones (nombre, cuit, direccion, email_contacto, telefono_contacto) VALUES
    ('CENT35 Rio Grande',                   '30-99999991-1', 'Calle Falsa 123, Rio Grande',     'contacto@cent35.edu.ar', '+54 2964 555-0001'),
    ('Universidad de Tierra del Fuego',     '30-99999992-2', 'Av. de los Andes 100, Ushuaia',   'info@utf.edu.ar',        '+54 2901 555-0002');


-- ============================================================
--  Usuarios
--  CENT35:
--    superadmin.cent35 / super123
--    admin.cent35      / admin123
--  UTF:
--    superadmin.utf    / super123
--    admin.utf         / admin123
-- ============================================================

-- CENT35 - Superadmin
INSERT INTO usuarios (institucion_id, rol_id, username, email, password_hash, nombre, apellido)
VALUES (
    (SELECT id FROM instituciones WHERE nombre = 'CENT35 Rio Grande'),
    (SELECT id FROM roles         WHERE codigo = 'SUPERADMIN_INSTITUCION'),
    'superadmin.cent35',
    'superadmin@cent35.edu.ar',
    '$2b$10$o2u5kWHggcQOKSulWioEuedskkNgh4.HHUDU0dZ5mdqUl9HXIKk1a', -- super123
    'Super',
    'Admin CENT35'
);

-- CENT35 - Admin
INSERT INTO usuarios (institucion_id, rol_id, username, email, password_hash, nombre, apellido)
VALUES (
    (SELECT id FROM instituciones WHERE nombre = 'CENT35 Rio Grande'),
    (SELECT id FROM roles         WHERE codigo = 'ADMIN'),
    'admin.cent35',
    'admin@cent35.edu.ar',
    '$2b$10$ySFtTrDnI13UjvNqNrbHSu1HKHEoxwapAtxxlk2VEw3Ft2seJOSCe', -- admin123
    'Admin',
    'CENT35'
);

-- UTF - Superadmin
INSERT INTO usuarios (institucion_id, rol_id, username, email, password_hash, nombre, apellido)
VALUES (
    (SELECT id FROM instituciones WHERE nombre = 'Universidad de Tierra del Fuego'),
    (SELECT id FROM roles         WHERE codigo = 'SUPERADMIN_INSTITUCION'),
    'superadmin.utf',
    'superadmin@utf.edu.ar',
    '$2b$10$o2u5kWHggcQOKSulWioEuedskkNgh4.HHUDU0dZ5mdqUl9HXIKk1a', -- super123
    'Super',
    'Admin UTF'
);

-- UTF - Admin
INSERT INTO usuarios (institucion_id, rol_id, username, email, password_hash, nombre, apellido)
VALUES (
    (SELECT id FROM instituciones WHERE nombre = 'Universidad de Tierra del Fuego'),
    (SELECT id FROM roles         WHERE codigo = 'ADMIN'),
    'admin.utf',
    'admin@utf.edu.ar',
    '$2b$10$ySFtTrDnI13UjvNqNrbHSu1HKHEoxwapAtxxlk2VEw3Ft2seJOSCe', -- admin123
    'Admin',
    'UTF'
);
