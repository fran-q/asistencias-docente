# 03 — Modelo de datos

> **Versión:** 1.0 · **Actualizado:** 2026-08-14 · **Estado:** vigente
> **Se actualiza cuando:** se agrega una migración Flyway.

Motor: MariaDB 10.4, InnoDB, `utf8mb4` / `utf8mb4_unicode_ci`.
Esquema gestionado exclusivamente por Flyway. Hibernate solo valida.

---

## Tablas vigentes: 19

El consolidado `V001` crea 16. Después: `personas` (V016), `cambios_identidad` (V017) y
`bloques_presencia` (V019).

> Nota: el `CHANGELOG.md` dice "esquema completo de 16 tablas" en el Sprint 0. En ese
> momento eran 15 — `auditoria` existía y `codigos_verificacion` y `puestos_captura`
> todavía no. Es un error del changelog, no del esquema.

### Núcleo tenant

| Tabla | Qué guarda | Tenant-scoped |
|---|---|---|
| `instituciones` | Tenant root. Nombre, CUIT, contacto. | — (es la raíz) |
| `roles` | Catálogo global: `INSTITUCION`, `ADMIN`. | No |
| `usuarios` | Login del sistema. Solo institución y admins. **El docente no es usuario.** | Sí |
| `codigos_verificacion` | Códigos de un solo uso para verificar correo y recuperar contraseña. | Vía usuario |
| `puestos_captura` | Equipos autorizados a capturar datos biométricos. Guarda el hash del token, no el token. Ver ADR-0015. | Sí |

### Personas

| Tabla | Qué guarda | Tenant-scoped |
|---|---|---|
| `docentes` | Sujetos pasivos. DNI, legajo, contacto, fecha de alta y de baja. | Sí |
| `consentimientos_biometricos` | Consentimiento versionado, con método, fecha, revocación y auditoría forense (IP, User-Agent). | Vía docente |
| `modelos_faciales` | Modelo biométrico cifrado. **Nunca imágenes.** | Vía docente |

### Estructura académica

| Tabla | Qué guarda | Tenant-scoped |
|---|---|---|
| `carreras` | Programas académicos. Código único por institución. | Sí |
| `materias` | Asociadas a carrera y opcionalmente a docente titular. | Sí (denormalizado) |
| `comisiones` | Varias por materia, cada una con su docente asignado y cupo. | Vía materia |
| `horarios` | Día ISO (1=lunes), hora inicio/fin, tolerancia por horario. | Vía comisión |

### Asistencia

| Tabla | Qué guarda | Tenant-scoped |
|---|---|---|
| `asistencias` | El núcleo. Fecha, hora, estado, método, confianza. | Sí (denormalizado) |
| `bloques_presencia` | El lapso continuo de permanencia de un docente, con su marca de entrada y de salida. Abarca todos los horarios que el umbral de la institución mantenga juntos (V019). | Sí (denormalizado) |
| `motivos_carga_manual` | Catálogo global: `FALLA_CAMARA`, `FALLA_RECONOCIMIENTO`, `NO_REGISTRADO`, `OTRO`. | No |
| `asistencias_manuales` | Detalle 1:1 de las marcas manuales: admin responsable, motivo, detalle. | Vía asistencia |
| `justificaciones_ausencia` | 1:1 sobre una ausencia: motivo y documento opcional. | Vía asistencia |

---

## Invariantes

Reglas que el esquema garantiza y que no hay que revalidar desde cero cada vez:

**`asistencias`**
- `UNIQUE (docente_id, horario_id, fecha)` — una marca por docente, horario y día. Es la
  base de la idempotencia del pase.
- `estado ∈ {PRESENTE, TARDE, AUSENTE}`.
- `metodo ∈ {AUTOMATICO, MANUAL}`.
- Si `metodo = MANUAL`, entonces `modelo_facial_id` y `confianza` son `NULL`. Está
  forzado por CHECK.
- `confianza` entre 0 y 1, `DECIMAL(5,4)`.
- FK a `modelos_faciales` con `ON DELETE SET NULL`: si el docente ejerce ARCO y se borra
  su modelo, **el historial de asistencias se conserva**.

**`bloques_presencia`** (V019)
- `UNIQUE (bloque_abierto_de)` sobre una **columna generada** que vale `docente_id`
  mientras `estado_cierre = 'ABIERTO'` y `NULL` cuando no. Es lo que garantiza que un
  docente no tenga dos bloques abiertos a la vez, aprovechando que un UNIQUE admite
  repetir NULL. La columna no se mapea en la entidad: la calcula la base.
- `UNIQUE (docente_id, fecha, hora_entrada)` — idempotencia del pase, mismo rol que el
  UNIQUE de `asistencias`.
- Un bloque `ABIERTO` no tiene `hora_salida`, `origen_salida` ni `estado_salida`; uno
  cerrado los tiene los tres. Forzado por `ck_bloques_cierre_coherente`.
- `hora_salida > hora_entrada` cuando existe.
- Solo `origen = AUTOMATICO` lleva modelo facial y confianza, en cada extremo por
  separado. Mismo criterio que `ck_asistencias_metodo_modelo`: una hora cargada a mano o
  presumida por el sistema no tiene evidencia biométrica detrás.
- Las dos FK a `modelos_faciales` hacen `SET NULL`: el registro de permanencia sobrevive
  a una supresión ARCO.
- **Sin baja lógica**, igual que `asistencias`: es el registro de un hecho, no una entidad
  que se administre.
- **V020**: un cierre `CERRADO_POR_ADMIN` lleva motivo obligatorio; cualquier otro no puede
  tener motivo, autor ni detalle. El CHECK exige el **motivo** y no el usuario, porque la FK
  del usuario hace `SET NULL` y exigirlo rompería filas ya escritas al suprimir una cuenta.
  El motivo sale del **mismo** catálogo que la carga manual (`motivos_carga_manual`).

⚠ **Ninguno de estos CHECK se ejercita en los tests.** El perfil `test` corre sobre H2 con
`flyway.enabled=false` y `ddl-auto=create-drop`, así que el esquema de los tests sale de
las entidades y no de las migraciones. Un build verde no dice nada sobre estos
constraints. Es la misma clase de punto ciego que dejó pasar el problema de esquema de V015.

✅ **Verificados a mano el 2026-09-01 contra MariaDB 10.4.32**, aplicando V019 sobre una copia
estructural de la base real: la migración entra limpia, el UNIQUE sobre la columna generada
funciona —era el punto que estaba en duda—, los diez CHECK rechazan lo que deben, y el
`ON DELETE SET NULL` conserva el bloque tras borrar el modelo facial. **Como no está
automatizado, hay que repetirlo si se toca el esquema de la tabla.**

**`horarios`**
- `dia_semana` entre 1 y 7 (ISO 8601, 1 = lunes).
- `hora_fin > hora_inicio`.
- `tolerancia_min` entre 0 y 120, por defecto 15. **Es por horario, no global.**
- Las columnas `vigente_desde` / `vigente_hasta` se eliminaron en V012.

**`comisiones`**
- `UNIQUE (materia_id, codigo)`.
- `cupo` positivo o nulo.
- `docente_asignado_id` fue nullable durante la transición del Sprint 3 (V004).

**Unicidad por tenant**, no global: `usuarios (institucion_id, username)`,
`usuarios (institucion_id, email)`, `docentes (institucion_id, dni)`,
`docentes (institucion_id, legajo)`, `carreras (institucion_id, codigo)`,
`materias (institucion_id, codigo)`. Dos instituciones pueden tener un docente con el
mismo DNI sin colisionar.

**Denormalización deliberada:** `materias.institucion_id` y `asistencias.institucion_id`
existen aunque se podrían derivar por JOIN. Refuerzan el aislamiento y aceleran los
reportes.

**Todas las FK son `ON DELETE RESTRICT`**, salvo dos: `asistencias_manuales` y
`justificaciones_ausencia` cascadean desde `asistencias` (son su detalle 1:1), y
`asistencias.modelo_facial_id` hace `SET NULL`.

---

## Baja lógica

Regla general del sistema: nada se borra. Las entidades tienen `activo BOOLEAN` y, desde
V012, `fecha_baja DATE`.

**Una sola excepción: la supresión biométrica por derecho ARCO.**
`ModeloFacialService.suprimirDatosBiometricos` hace `DELETE` físico de todos los modelos
del docente. Una fila marcada como inactiva seguiría conteniendo el dato biométrico, que
es exactamente lo que la ley obliga a eliminar. Antes del borrado se evicta el
recognizer del cache, para que no siga reconociendo desde memoria.

---

## Historial de migraciones

| Migración | Qué hizo |
|---|---|
| `V001__init` | Esquema inicial: 15 tablas, CHECK constraints, seed de roles y motivos |
| `V002__seed_test_data` | Datos de prueba (ficticios) |
| `V003__rename_rol_superadmin_to_institucion` | `SUPERADMIN_INSTITUCION` → `INSTITUCION` |
| `V004__comisiones_docente_nullable` | `docente_asignado_id` nullable durante la transición |
| `V005__consentimientos_biometricos_audit` | Columnas de auditoría forense: IP y User-Agent |
| `V006__modelos_faciales_mediumblob` | `embedding_cifrado`: `BLOB` → `LONGBLOB` |
| `V007__codigos_verificacion_email` | Tabla `codigos_verificacion` + columnas en `usuarios` |
| `V008__docentes_fecha_baja` | `fecha_baja` en docentes |
| `V009__baja_tabla_auditoria` | **DROP de `auditoria`.** Ver nota abajo |
| `V010__anio_materia_y_baja_cupo` | Año de materia, ajustes en carreras/materias/comisiones |
| `V011__horarios_timestamps` | Timestamps en horarios |
| `V012__baja_vigencia_y_fechas_de_baja` | Quita `vigente_desde`/`vigente_hasta`; agrega `fecha_baja` a seis tablas |
| `V013__quitar_creado_en_duplicado` | Limpieza de columna duplicada en `modelos_faciales` |
| `V014__usuario_apellido_opcional` | Apellido de usuario pasa a opcional |
| `V015__puestos_captura` | Tabla `puestos_captura`: equipos habilitados para el pase y el registro del rostro |
| `V016__personas_y_vinculos_docentes` | Tabla `personas`: la identidad se separa de la cuenta y del vínculo docente (ADR-0016) |
| `V017__trazabilidad_de_identidad_y_bajas` | Tabla `cambios_identidad` + columna `dado_de_baja_por` en ocho tablas |
| `V018__cuenta_institucional_sin_persona` | `usuarios.persona_id` pasa a admitir NULL: la cuenta institucional no es una persona física |
| `V019__bloques_de_presencia` | Tabla `bloques_presencia`, `asistencias.bloque_id` y `instituciones.umbral_separacion_min` (ADR-0017) |
| `V020__cierre_manual_del_bloque` | Quién cerró el bloque a mano y por qué: `cerrado_por_usuario_id`, `motivo_cierre_id`, `detalle_cierre` (RF-83) |

> ⚠ Las quince migraciones originales viven en `db/historico/`, fuera de
> `spring.flyway.locations`. Lo que se aplica es `V001__esquema_consolidado.sql` y de ahí
> en adelante. Ver la sección de migraciones en `04-convenciones.md`.

### Por qué se eliminó `auditoria`

La tabla se creó en V001 anticipando los RF-34 a RF-36. **Nunca se escribió una fila**:
ningún punto del código insertaba en ella. Quedó con el esquema completo y cero datos.

Una tabla vacía que nadie escribe es lo peor de los dos mundos: no aporta trazabilidad
—quien la consulte va a encontrarla en blanco y sacar la conclusión equivocada— y además
sugiere una funcionalidad que no existe. Cuando se retome el módulo de auditoría habrá
que diseñarlo con los requerimientos de ese momento.

**No confundir con la auditoría forense del consentimiento biométrico**
(`consentimientos_biometricos`: IP y User-Agent del otorgamiento y de la revocación).
Esa se queda intacta: la exige la Ley 25.326 para poder acreditar ante la AAIP que hubo
una sesión concreta en la que esa persona prestó su consentimiento. Lo mismo con
`codigos_verificacion.ip_solicitud`.

---

## Reglas al tocar el esquema

1. **Una migración aplicada no se edita nunca.** Modificar `V001` rompe el checksum de
   Flyway. Todo cambio es una migración nueva.
2. Numeración correlativa: `V0XX__descripcion_en_snake_case.sql`.
3. Cabecera comentada explicando **qué hace y por qué**, no solo qué.
4. Si el cambio afecta a una entidad, actualizar la `@Entity` en el mismo commit:
   `ddl-auto=validate` va a fallar el arranque si no coinciden.
5. Los nombres de PRIMARY KEY (`CONSTRAINT pk_xxx`) los ignora MariaDB — genera warnings
   informativos. Se dejan por si en algún momento se migra a un motor que sí los honre
   (TD-001).
