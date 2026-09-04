-- =============================================================================
--  V014__usuario_apellido_opcional.sql
--
--  El apellido de un usuario pasa a ser opcional.
--
--  Por que. La tabla usuarios guarda dos cosas distintas bajo la misma forma:
--  las cuentas de las personas que operan el sistema (rol ADMIN), que tienen
--  nombre y apellido como cualquier persona, y la cuenta de la institucion
--  misma (rol INSTITUCION), que no es una persona. Un colegio no tiene
--  apellido: tiene un nombre, y punto.
--
--  Hasta ahora las dos columnas eran obligatorias, asi que la cuenta de una
--  institucion se cargaba partiendo su nombre al medio --"Direccion" /
--  "UTN FRTDF"-- o repitiendolo. Ninguna de las dos cosas es el dato: son
--  maneras de esquivar una restriccion que no correspondia.
--
--  Que significa NULL aca. Exactamente "esta cuenta no tiene apellido porque
--  no es una persona". No es un dato que falta ni que se desconoce. Por eso
--  NULL y no cadena vacia: la cadena vacia diria "tiene apellido y es el vacio",
--  que no quiere decir nada, y ademas obliga a que cada consulta se acuerde de
--  distinguir '' de un apellido de verdad.
--
--  El nombre completo se arma juntando lo que haya, asi que una cuenta sin
--  apellido muestra su nombre solo, sin espacios colgando.
--
--  Que NO cambia. El nombre sigue siendo obligatorio para todos: una cuenta sin
--  ninguna forma de nombrarla no se puede administrar. Y las filas existentes
--  quedan como estan: nadie pierde su apellido por esta migracion.
-- =============================================================================

ALTER TABLE usuarios
    MODIFY COLUMN apellido VARCHAR(80) NULL
        COMMENT 'Apellido de la persona. NULL en las cuentas de rol INSTITUCION, que no son personas.';
