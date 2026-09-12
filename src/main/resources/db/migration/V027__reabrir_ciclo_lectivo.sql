-- =============================================================================
--  V027 - Reabrir un ciclo lectivo cerrado
-- =============================================================================
--  QUE RESUELVE
--  Cerrar un ciclo era definitivo, y cerrar por error el ciclo en curso dejaba
--  a toda la institucion sin tomar asistencia el resto del ano: el pase solo
--  busca clases en el ciclo ACTIVO, un ciclo cerrado no se podia activar, y
--  tampoco se podia crear otro del mismo ano por uq_ciclos_inst_anio. La unica
--  salida era tocar la base a mano.
--
--  Ahora se puede reabrir el ULTIMO ciclo cerrado, siempre que no haya otro
--  activo. Lo segundo impide dejar editable la oferta de un ano terminado
--  mientras corre el siguiente; lo primero, reabrir un ano de hace cinco.
--
--  POR QUE VUELVE A PREPARACION Y NO A ACTIVO
--  El ciclo pudo haberse cerrado sin llegar a correr nunca. Reabierto a ACTIVO
--  quedaria tomando asistencia sin que nadie lo pidiera, y de ACTIVO no se
--  vuelve a PREPARACION. Desde PREPARACION se activa como siempre, con su
--  propia confirmacion: es un paso mas en el caso del cierre por error, y
--  ninguna sorpresa en el otro.
--
--  POR QUE cerrado_en NO SE BORRA AL REABRIR
--  Es el registro de que alguien lo cerro y cuando. Si se reabrio porque el
--  cierre fue un error, ese dato es justamente lo que alguien va a querer
--  mirar. Si el ciclo esta cerrado lo dice la columna estado, no cerrado_en:
--  por eso se corrige su comentario, que decia "NULL = sigue abierto".
--
--  PARA VOLVER ATRAS
--    ALTER TABLE ciclos_lectivos
--      DROP FOREIGN KEY fk_ciclos_reabierto_por,
--      DROP COLUMN reabierto_por,
--      DROP COLUMN reabierto_en;
-- =============================================================================

ALTER TABLE ${esquema}.ciclos_lectivos
  MODIFY COLUMN `cerrado_en` timestamp NULL DEFAULT NULL
    COMMENT 'Ultimo cierre. Se conserva al reabrir: si esta cerrado lo dice estado, no esta columna.',
  ADD COLUMN `reabierto_en` timestamp NULL DEFAULT NULL
    COMMENT 'Ultima vez que se reabrio despues de cerrado. NULL = nunca.' AFTER `cerrado_por`,
  ADD COLUMN `reabierto_por` bigint(20) DEFAULT NULL
    COMMENT 'Quien lo reabrio. NULL si nunca se reabrio o si esa cuenta ya no existe.' AFTER `reabierto_en`,
  ADD CONSTRAINT `fk_ciclos_reabierto_por` FOREIGN KEY (`reabierto_por`)
    REFERENCES ${esquema}.usuarios (`id`) ON DELETE SET NULL;
