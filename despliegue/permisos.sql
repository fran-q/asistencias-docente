-- Los permisos del usuario de la aplicacion, solo sobre las dos bases del sistema. El usuario lo
-- crea la imagen de MariaDB antes de correr este archivo, con MARIADB_USER y MARIADB_PASSWORD.
GRANT ALL PRIVILEGES ON `asistenciautomatica`.* TO 'visum'@'%';
GRANT ALL PRIVILEGES ON `asistenciautomatica_meta`.* TO 'visum'@'%';
FLUSH PRIVILEGES;
