-- =============================================================================
--  V033 - Las asistencias de una entrada sin camara son MANUAL
-- =============================================================================
--  QUE RESUELVE
--  V029 dejo registrar la entrada sin camara (ADR-0021): el bloque queda con
--  origen_entrada = MANUAL, su autor y su motivo. Pero las asistencias que ese
--  bloque imputa se grababan con metodo = AUTOMATICO, que dice "la reconocio la
--  camara". El reporte, el listado y el PDF las mostraban asi, y el filtro de
--  metodo MANUAL no las encontraba.
--
--  Desde el 2026-10-28 el codigo las graba MANUAL
--  (AsistenciaService.imputarDelBloque). Esta migracion corrige las que ya
--  estaban grabadas.
--
--  POR QUE MANUAL Y NO UN VALOR NUEVO
--  MANUAL es lo que MetodoAsistencia define: la cargo un admin, con un motivo
--  del catalogo. Lo que la separa de una carga a mano posterior es que tiene
--  bloque, y eso ya esta en la base: no hace falta otro valor ni tocar los
--  CHECK de V001.
--
--  POR QUE EL JOIN COMPARA LA INSTITUCION
--  bloque_id siempre apunta a un bloque de la misma institucion, porque lo
--  arma el codigo y no un formulario. Se compara igual: si alguna fila
--  estuviera mal, queda como esta en vez de corregirse con el dato de otra
--  institucion.
--
--  POR QUE SOLO LAS QUE NO TIENEN MODELO NI CONFIANZA
--  ck_asistencias_metodo_modelo (V001) prohibe que una MANUAL los tenga. Las de
--  un bloque abierto a mano nunca los tienen: los copian del bloque, y
--  ck_bloques_entrada_modelo (V019) no los admite en una entrada a mano. La
--  condicion esta para que una fila imposible no frene el arranque. Quedaria
--  AUTOMATICO, que con modelo y confianza es lo coherente.
--
--  actualizado_en queda con la fecha de esta migracion, que es cuando el dato
--  cambio.
--
--  PARA VOLVER ATRAS
--  No se contempla. Volver atras es volver al dato equivocado, y despues de
--  esta migracion una fila corregida no se distingue de las que graba el
--  codigo nuevo.
-- =============================================================================

UPDATE ${esquema}.asistencias a
  JOIN ${esquema}.bloques_presencia b
    ON b.id = a.bloque_id
   AND b.institucion_id = a.institucion_id
   SET a.metodo = 'MANUAL'
 WHERE b.origen_entrada = 'MANUAL'
   AND a.metodo = 'AUTOMATICO'
   AND a.modelo_facial_id IS NULL
   AND a.confianza IS NULL;
