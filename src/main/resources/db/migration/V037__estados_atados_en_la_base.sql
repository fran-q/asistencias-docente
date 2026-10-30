-- =============================================================================
--  V037 - Los estados que dicen lo mismo quedan atados en la base
-- =============================================================================
--  QUE RESUELVE
--  Varios estados viven en mas de una columna, o suponen que hay uno solo por
--  docente o por institucion. El acuerdo lo sostenia solo el codigo: una fila
--  escrita por otro camino, o dos pedidos simultaneos, los dejaban
--  contradiciendose sin que nada avisara. Cada seccion cierra uno.
--
--  QUE PASA SI YA HAY FILAS QUE LOS VIOLAN
--  La migracion falla, a proposito: un estado contradictorio alguien lo tiene
--  que mirar, no corregir a ciegas. Antes de escribirla se conto en la base de
--  desarrollo que no hay ninguna. La excepcion es la que produjo el propio
--  sistema, y se corrige aca: las cuentas dadas de baja sin fecha (seccion 5).
--
--  LO QUE QUEDA AFUERA A PROPOSITO
--  Un equipo revocado conserva kiosco_habilitado = 1: dice que estaba operando
--  solo cuando se revoco, y el codigo cierra la puerta mirando las dos columnas
--  (PuestoCaptura.operaDesatendido). Es historia, no una contradiccion.
--
--  LAS COLUMNAS GENERADAS
--  Secciones 2 a 4. Mismo mecanismo que bloque_abierto_de (V019): valen el
--  docente o la institucion mientras la fila este activa y NULL cuando no. Un
--  UNIQUE admite repetir NULL, asi que el historial se conserva. No se mapean en
--  las entidades: las calcula la base.
--
--  PARA VOLVER ATRAS
--    ALTER TABLE bloques_presencia DROP CONSTRAINT ck_bloques_cierre_origen;
--    ALTER TABLE consentimientos_biometricos
--      DROP CONSTRAINT ck_consentimientos_vigencia,
--      DROP INDEX uq_consentimientos_un_vigente_por_docente,
--      DROP COLUMN consentimiento_vigente_de;
--    ALTER TABLE modelos_faciales
--      DROP INDEX uq_modelos_un_activo_por_docente,
--      DROP COLUMN modelo_activo_de;
--    ALTER TABLE ciclos_lectivos
--      DROP INDEX uq_ciclos_un_activo_por_institucion,
--      DROP COLUMN ciclo_activo_de;
--  y DROP CONSTRAINT ck_<tabla>_baja_coherente en las diez tablas de la seccion 5.
--  Las fechas de baja que se completaron no se pueden distinguir de las demas.
-- =============================================================================

-- -----------------------------------------------------------------------------
--  1. La salida de un bloque
-- -----------------------------------------------------------------------------
--  estado_cierre, origen_salida y estado_salida describen la misma salida desde
--  tres lados. ck_bloques_cierre_coherente (V019) exige que esten los tres, pero
--  no que coincidan: admitia un SIN_CIERRE con salida AUTOMATICO.
ALTER TABLE ${esquema}.bloques_presencia
  ADD CONSTRAINT `ck_bloques_cierre_origen` CHECK (
    `estado_cierre` = 'ABIERTO'
    or (`estado_cierre` = 'CERRADO_POR_ROSTRO' and `origen_salida` = 'AUTOMATICO'
        and `estado_salida` in ('EN_HORA','ANTICIPADA'))
    or (`estado_cierre` = 'CERRADO_POR_ADMIN' and `origen_salida` = 'MANUAL'
        and `estado_salida` in ('EN_HORA','ANTICIPADA'))
    or (`estado_cierre` = 'SIN_CIERRE' and `origen_salida` = 'PRESUNTO'
        and `estado_salida` = 'SIN_MARCA')
  );

-- -----------------------------------------------------------------------------
--  2. El consentimiento vigente
-- -----------------------------------------------------------------------------
--  vigente y fecha_revocacion dicen lo mismo (ADR-0005 lo dejo como atajo para
--  los listados). Y que haya uno solo vigente por docente lo cuidaba el servicio,
--  que no frena dos pedidos simultaneos. Este es un dato legal: el pase decide
--  por el, y dos vigentes dejarian sin respuesta cual se revoca.
ALTER TABLE ${esquema}.consentimientos_biometricos
  ADD COLUMN `consentimiento_vigente_de` bigint(20)
    AS (IF(`vigente` = 1, `docente_id`, NULL)) VIRTUAL,
  ADD UNIQUE KEY `uq_consentimientos_un_vigente_por_docente` (`consentimiento_vigente_de`),
  ADD CONSTRAINT `ck_consentimientos_vigencia` CHECK (
    (`vigente` = 1 and `fecha_revocacion` is null)
    or (`vigente` = 0 and `fecha_revocacion` is not null)
  );

-- -----------------------------------------------------------------------------
--  3. El modelo facial activo
-- -----------------------------------------------------------------------------
--  El pase compara contra el modelo activo de cada docente, y V001 lo dejaba
--  "validado en aplicacion". ModeloFacialService da de baja el anterior con flush
--  antes de insertar el nuevo: Hibernate haria el INSERT primero.
ALTER TABLE ${esquema}.modelos_faciales
  ADD COLUMN `modelo_activo_de` bigint(20)
    AS (IF(`activo` = 1, `docente_id`, NULL)) VIRTUAL,
  ADD UNIQUE KEY `uq_modelos_un_activo_por_docente` (`modelo_activo_de`);

-- -----------------------------------------------------------------------------
--  4. El ciclo lectivo activo
-- -----------------------------------------------------------------------------
--  El pase y el job buscan "el" ciclo activo de la institucion. Con dos, la
--  asistencia dependeria de cual devuelve la consulta primero.
ALTER TABLE ${esquema}.ciclos_lectivos
  ADD COLUMN `ciclo_activo_de` bigint(20)
    AS (IF(`estado` = 'ACTIVO', `institucion_id`, NULL)) VIRTUAL,
  ADD UNIQUE KEY `uq_ciclos_un_activo_por_institucion` (`ciclo_activo_de`);

-- -----------------------------------------------------------------------------
--  5. La baja logica
-- -----------------------------------------------------------------------------
--  activo y fecha_baja dicen lo mismo en diez tablas, sin nada que los ate. Y ya
--  se habian separado: UsuarioService.darDeBaja ponia activo = 0 sin fecha. Esas
--  cuentas toman la fecha de su ultima modificacion, que es la baja misma salvo
--  que se hayan tocado despues: es lo mas cerca que se puede llegar.
UPDATE ${esquema}.usuarios
   SET fecha_baja = DATE(actualizado_en)
 WHERE activo = 0
   AND fecha_baja IS NULL;

ALTER TABLE ${esquema}.instituciones
  ADD CONSTRAINT `ck_instituciones_baja_coherente` CHECK (
    (`activo` = 1 and `fecha_baja` is null) or (`activo` = 0 and `fecha_baja` is not null)
  );
ALTER TABLE ${esquema}.motivos_carga_manual
  ADD CONSTRAINT `ck_motivos_baja_coherente` CHECK (
    (`activo` = 1 and `fecha_baja` is null) or (`activo` = 0 and `fecha_baja` is not null)
  );
ALTER TABLE ${esquema}.usuarios
  ADD CONSTRAINT `ck_usuarios_baja_coherente` CHECK (
    (`activo` = 1 and `fecha_baja` is null) or (`activo` = 0 and `fecha_baja` is not null)
  );
ALTER TABLE ${esquema}.docentes
  ADD CONSTRAINT `ck_docentes_baja_coherente` CHECK (
    (`activo` = 1 and `fecha_baja` is null) or (`activo` = 0 and `fecha_baja` is not null)
  );
ALTER TABLE ${esquema}.carreras
  ADD CONSTRAINT `ck_carreras_baja_coherente` CHECK (
    (`activo` = 1 and `fecha_baja` is null) or (`activo` = 0 and `fecha_baja` is not null)
  );
ALTER TABLE ${esquema}.materias
  ADD CONSTRAINT `ck_materias_baja_coherente` CHECK (
    (`activo` = 1 and `fecha_baja` is null) or (`activo` = 0 and `fecha_baja` is not null)
  );
ALTER TABLE ${esquema}.comisiones
  ADD CONSTRAINT `ck_comisiones_baja_coherente` CHECK (
    (`activo` = 1 and `fecha_baja` is null) or (`activo` = 0 and `fecha_baja` is not null)
  );
ALTER TABLE ${esquema}.horarios
  ADD CONSTRAINT `ck_horarios_baja_coherente` CHECK (
    (`activo` = 1 and `fecha_baja` is null) or (`activo` = 0 and `fecha_baja` is not null)
  );
ALTER TABLE ${esquema}.puestos_captura
  ADD CONSTRAINT `ck_puestos_baja_coherente` CHECK (
    (`activo` = 1 and `fecha_baja` is null) or (`activo` = 0 and `fecha_baja` is not null)
  );
ALTER TABLE ${esquema}.modelos_faciales
  ADD CONSTRAINT `ck_modelos_baja_coherente` CHECK (
    (`activo` = 1 and `fecha_baja` is null) or (`activo` = 0 and `fecha_baja` is not null)
  );

