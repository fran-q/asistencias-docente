-- =============================================================================
--  V026 - La camara con la que captura cada puesto
-- =============================================================================
--  QUE RESUELVE
--  Las tres pantallas que usan la camara -el pase, el kiosco y el registro del
--  rostro- la pedian sin decir cual, y el navegador tomaba la predeterminada del
--  sistema. En una PC con la camara integrada y una webcam USB enchufada no habia
--  forma de elegir la USB.
--
--  POR QUE EN EL PUESTO Y NO EN LA CUENTA
--  El identificador de la camara lo genera el navegador (MediaDeviceInfo.deviceId)
--  y vale solo para ESE navegador en ESA maquina: exactamente el alcance de la
--  cookie del puesto (ADR-0015). En la cuenta de una persona dejaria de significar
--  algo en cuanto esa persona entrara desde otra computadora, y en el modo kiosco
--  no hay ninguna cuenta: la eleccion tiene que funcionar sin sesion.
--
--  QUE CUIDAR
--  1. NULL significa "la predeterminada del sistema", que es el comportamiento de
--     siempre. Ningun puesto existente cambia de camara por esta migracion.
--
--  2. La etiqueta se guarda aparte porque el identificador es un hash ilegible, y
--     la pantalla de puestos tiene que poder decir que camara usa el equipo. El
--     CHECK impide un identificador sin nombre o un nombre sin identificador.
--
--  3. camara_elegida_en se conserva aunque se vuelva a la predeterminada. El
--     reconocimiento se calibro con una camara concreta: si empieza a fallar, lo
--     primero que conviene saber es si alguien la cambio, y cuando.
--
--  PARA VOLVER ATRAS
--    ALTER TABLE puestos_captura
--      DROP CONSTRAINT ck_puestos_camara_con_nombre,
--      DROP COLUMN camara_elegida_en,
--      DROP COLUMN camara_etiqueta,
--      DROP COLUMN camara_dispositivo_id;
-- =============================================================================

ALTER TABLE ${esquema}.puestos_captura
  ADD COLUMN `camara_dispositivo_id` varchar(255) DEFAULT NULL
    COMMENT 'deviceId que da el navegador. Vale solo en esa maquina. NULL = la predeterminada del sistema.'
    AFTER `kiosco_habilitado_por`,
  ADD COLUMN `camara_etiqueta` varchar(120) DEFAULT NULL
    COMMENT 'Nombre que reporto el dispositivo, para poder mostrar que camara usa el puesto.'
    AFTER `camara_dispositivo_id`,
  ADD COLUMN `camara_elegida_en` timestamp NULL DEFAULT NULL
    COMMENT 'Ultimo cambio de camara. Se conserva al volver a la predeterminada.'
    AFTER `camara_etiqueta`,
  ADD CONSTRAINT `ck_puestos_camara_con_nombre` CHECK (
    (`camara_dispositivo_id` IS NULL AND `camara_etiqueta` IS NULL)
    OR
    (`camara_dispositivo_id` IS NOT NULL AND `camara_etiqueta` IS NOT NULL)
  );
