# ADR-0005: Diseño del consentimiento biométrico

**Estado**: Aceptada
**Fecha**: 2026-05-19
**Decisor**: Francisco Quiroga (fran-q)

## Contexto

La aplicación trata datos biométricos del docente (vector facial derivado de su rostro) para automatizar el registro de asistencia. Bajo el marco normativo argentino:

- **Ley N° 25.326 de Protección de Datos Personales**: exige consentimiento del titular *libre, expreso, informado*; con derecho a revocación (Derechos ARCO).
- **Resolución AAIP N° 255/2022**: clasifica los datos biométricos como **sensibles**. Refuerza la exigencia de consentimiento expreso y suma criterios técnicos: medidas razonables de seguridad y *auditabilidad demostrable* del consentimiento.

Sprint 3 introduce la gestión efectiva de docentes (Fase A) y, con eso, queda habilitado modelar el consentimiento que va a permitir registrar el modelo facial en Sprint 4.

Hay varias decisiones de diseño que conviene fijar y justificar antes de que la solución se vuelva difícil de revertir.

## Decisión

### 1. Tabla independiente con histórico, no flag en `docentes`

El consentimiento vive en su propia tabla `consentimientos_biometricos` (creada en V001, ampliada en V005), con FK a `docentes`. Cada operación de otorgamiento o revocación es una **fila nueva** o una **actualización del estado** de una fila existente: nunca se borra ni se reemplaza el historial.

- El estado actual del docente se calcula con el registro más reciente (`MAX(id)` del docente).
- Sólo puede haber un registro `vigente = true` por docente (garantizado a nivel aplicación; MariaDB no soporta índices únicos parciales tipo `WHERE vigente = true`).

**Alternativa descartada**: columnas `consentimiento_otorgado_en` / `consentimiento_revocado_en` en `docentes`. Se descartó porque pierde el historial (otorga → revoca → otorga de nuevo) y dificulta la auditoría AAIP.

### 2. Versionado del texto del consentimiento

`TextoConsentimiento.VERSION_ACTUAL = "2026-05-v1"` + cuerpo legal completo en una constante Java. Cada registro guarda `version_terminos` con la versión que **estaba al momento de aceptar**.

- Si el texto cambia, se incrementa la versión (`2026-06-v2`). Los docentes existentes siguen vigentes con la versión anterior — su aceptación fue legal en su momento.
- A futuro, la UI puede sugerir re-aceptar la versión nueva (no obligatorio).

**Alternativa descartada**: hash del texto en lugar de versión semántica. Se descartó porque el version semántico es legible para operadores no técnicos y permite enlazarse con un changelog del texto.

### 3. Auditoría forense en otorgamiento y revocación

V005 agrega `ip_otorgamiento`, `user_agent_otorgamiento`, `revocado_por_usuario_id`, `ip_revocacion`, `user_agent_revocacion`, `motivo_revocacion`. Se captura automáticamente la IP y el User-Agent del **admin que ejecuta la acción** (no del docente: en Sprint 3 el docente aún no tiene login).

- IP: prioriza `X-Forwarded-For` si existe (despliegue tras proxy), fallback a `request.getRemoteAddr()`.
- User-Agent: header crudo.
- Quién: el `usuarioId` del `CustomUserDetails` del principal autenticado.

**Justificación AAIP**: ante una eventual auditoría se puede demostrar *cuándo* y *desde qué sesión HTTP concreta* se cargó/revocó el consentimiento. Bajo costo (varias columnas opcionales), alto beneficio defensivo.

**Alternativa descartada**: bitácora externa en otra tabla (`audit_logs`). Se postergó: por ahora, mantener todo en la misma fila simplifica las queries de auditoría y evita duplicación. Si el volumen crece, se puede migrar.

### 4. Quién otorga / revoca en Sprint 3: admin en representación

Los docentes todavía **no tienen login propio** (eso es Sprint 4). En Sprint 3 el admin de la institución (`INSTITUCION` o `ADMIN`) carga el consentimiento *en representación del docente que firmó el documento físicamente*.

- El campo `metodo` se setea como `ESCRITO` (constraint `CHECK (metodo IN ('ESCRITO','DIGITAL'))`).
- Cuando llegue el login docente en Sprint 4, se habilitará `DIGITAL` con aceptación directa del propio docente.
- El form simplificado de Sprint 3 expone solo un checkbox; método y fecha de firma se hardcodean en el controller (decisión UX para reducir fricción del operador). Si más adelante hace falta cargar consentimientos retroactivos con fecha pasada, basta sumar campos al DTO sin tocar service.

### 5. Defensa multi-tenant via docente padre

`ConsentimientoBiometrico` **no extiende `BaseTenantEntity`** y no tiene su propia columna `institucion_id`. El tenant lo determina el `Docente` padre — mismo patrón que `Comision` (via `Materia`).

El service valida explícitamente que el docente pertenezca al `TenantContext` actual antes de cualquier operación. La query agregada `findUltimoEstadoPorDocenteEnTenant` aplica el filtro explícito `c.docente.institucionId = :tenantId` siguiendo ADR-0004.

**Alternativa descartada**: denormalizar `institucion_id` en `consentimientos_biometricos`. Se descartó por las mismas razones de ADR-0004 (duplicación, riesgo de inconsistencia, no agrega valor).

### 6. Lazy explícito por `open-in-view=false`

`spring.jpa.open-in-view=false` (decisión arquitectónica heredada): expone N+1 ocultos en lugar de tolerarlos silenciosamente. Como Thymeleaf renderiza fuera de la transacción, las asociaciones LAZY que el template lee (`registradoPor.username`, `revocadoPor.username`) **deben** inicializarse explícitamente dentro del service.

Concretamente: `ConsentimientoBiometricoService` define un helper `touchAsociacionesLazy(...)` que se llama en `vigenteDe` y `historialDe`. Bug capturado durante el desarrollo de D.2 (un 500 al volver de `/otorgar` a `/editar`).

### 7. Manejo de bajas de docente

Si un docente con consentimiento `ACTIVO` se da de baja lógica:

- **El consentimiento queda intacto.** No se revoca automáticamente.
- Si el docente se reactiva, el consentimiento sigue vigente con su versión original.
- Si la versión del texto cambió mientras tanto, el sistema puede sugerir re-aceptar la nueva versión, pero la aceptación previa **sigue siendo válida**.

**Alternativa descartada**: revocación automática al inactivar. Se descartó porque convierte una baja temporal en obligación de re-firma — fricción innecesaria y potencial pérdida de validez retroactiva en caso de bajas/altas operativas.

## Consecuencias

**Positivas**:
- Cumplimiento demostrable de Ley 25.326 + AAIP 255/2022.
- Historial completo: cualquier auditoría puede reconstruir la línea temporal de aceptaciones y revocaciones por docente.
- Versionado del texto permite cambios legales sin invalidar consentimientos previos.
- Auditoría forense (IP/UA) sin costo operativo significativo.
- Multi-tenant consistente con el resto del sistema (mismo patrón que Comisión).

**Negativas**:
- Garantía de "un único vigente por docente" depende del service, no de la BD (MariaDB sin índice único parcial). Mitigación: cubierto por tests unitarios (`otorgar_yaHayVigente`); si en producción se observa contención de escritura concurrente, sumar bloqueo pesimista o constraint a nivel app.
- El `metodo = 'ESCRITO'` se hardcodea en el controller en Sprint 3 — cuando entre Sprint 4 hay que extender el DTO para distinguir entre ESCRITO y DIGITAL según quién acepta.
- `vigente BOOLEAN` es redundante con `fecha_revocacion IS NULL`. Se mantiene como atajo para queries de listado; sincronización a nivel aplicación.

## Referencias

- [ADR-0002](./0002-multi-tenant-discriminator.md) — Diseño multi-tenant base.
- [ADR-0004](./0004-tenant-filter-en-joins.md) — Defensa en JOINs.
- `docs/legal/` — Documentación legal interna del proyecto.
- Sprint 3 Fase D commits `cb791a8` (D.1-D.3) y el de cierre que incluye este ADR.
- Ley N° 25.326 — Texto oficial: https://www.argentina.gob.ar/normativa/nacional/ley-25326-64790
- Resolución AAIP N° 255/2022: https://www.boletinoficial.gob.ar/detalleAviso/primera/277183
