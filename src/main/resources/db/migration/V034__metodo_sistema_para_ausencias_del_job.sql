-- =============================================================================
--  V034 - Las ausencias que genera el job tienen metodo propio: SISTEMA
-- =============================================================================
--  QUE RESUELVE
--  metodo decia dos cosas con un mismo valor. AUTOMATICO era "la camara
--  reconocio al docente" y tambien "el job genero la ausencia al terminar la
--  clase sin marca" (RF-19). El filtro de metodo del reporte mezclaba las dos,
--  y la columna Metodo mostraba como automatica una ausencia que no midio
--  nadie.
--
--  Desde esta migracion cada valor dice una sola cosa:
--    AUTOMATICO  la camara reconocio al docente. Nunca es una ausencia.
--    MANUAL      un admin la cargo, despues o en el momento (ADR-0021).
--    SISTEMA     la genero el job. Siempre es una ausencia, sin jornada.
--
--  COMO SE RECONOCEN LAS QUE YA ESTABAN
--  Toda ausencia AUTOMATICO la escribio el job: el estado de una marca de la
--  camara sale de AsistenciaService.calcularEstado, que solo devuelve
--  PRESENTE o TARDE. Y el job nunca les puso modelo, confianza ni jornada. Si
--  alguna los tuviera, seria un dato corrupto: los CHECK nuevos la rechazan y
--  la migracion se frena, a proposito, para que alguien la mire.
--
--  LOS CHECK
--  ck_asistencias_metodo suma SISTEMA. ck_asistencias_metodo_modelo se
--  reescribe para decir lo mismo con tres valores: solo AUTOMATICO puede tener
--  modelo y confianza. Y dos nuevos atan el metodo al estado, para que no
--  vuelvan a chocar:
--    ck_asistencias_sistema          SISTEMA es ausencia, sin jornada.
--    ck_asistencias_camara_presente  AUTOMATICO nunca es ausencia.
--
--  El orden importa: el CHECK viejo rechaza SISTEMA, asi que se cae antes del
--  UPDATE, y los nuevos se agregan despues, cuando los datos ya los cumplen.
--
--  PARA VOLVER ATRAS
--    ALTER TABLE asistencias
--      DROP CONSTRAINT ck_asistencias_camara_presente,
--      DROP CONSTRAINT ck_asistencias_sistema,
--      DROP CONSTRAINT ck_asistencias_metodo_modelo,
--      DROP CONSTRAINT ck_asistencias_metodo;
--    UPDATE asistencias SET metodo = 'AUTOMATICO' WHERE metodo = 'SISTEMA';
--  y volver a agregar los dos CHECK de V001.
-- =============================================================================

ALTER TABLE ${esquema}.asistencias
  DROP CONSTRAINT `ck_asistencias_metodo`,
  DROP CONSTRAINT `ck_asistencias_metodo_modelo`;

UPDATE ${esquema}.asistencias
   SET metodo = 'SISTEMA'
 WHERE metodo = 'AUTOMATICO'
   AND estado = 'AUSENTE';

ALTER TABLE ${esquema}.asistencias
  ADD CONSTRAINT `ck_asistencias_metodo`
    CHECK (`metodo` in ('AUTOMATICO','MANUAL','SISTEMA')),
  ADD CONSTRAINT `ck_asistencias_metodo_modelo` CHECK (
    `metodo` = 'AUTOMATICO'
    or (`modelo_facial_id` is null and `confianza` is null)
  ),
  ADD CONSTRAINT `ck_asistencias_sistema` CHECK (
    `metodo` <> 'SISTEMA'
    or (`estado` = 'AUSENTE' and `bloque_id` is null)
  ),
  ADD CONSTRAINT `ck_asistencias_camara_presente` CHECK (
    `metodo` <> 'AUTOMATICO' or `estado` <> 'AUSENTE'
  );
