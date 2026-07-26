# ADR-0002: Multi-tenancy por discriminator (institucion_id)

**Estado**: Aceptada
**Fecha**: 2026-04-26
**Decisor**: Francisco Quiroga (fran-q)

## Contexto

El sistema debe servir a múltiples instituciones educativas desde una sola instalación, con aislamiento total de datos entre ellas (RF-04, RNF-10). El alcance inicial es de 200 a 400 docentes por institución.

Las tres estrategias clásicas de multi-tenancy son:

| Estrategia | Aislamiento | Complejidad operativa | Adecuación al proyecto |
|---|---|---|---|
| Database per tenant | Físico, total | Alta (N bases, N migraciones) | Excesiva |
| Schema per tenant | Lógico, alto | Media (1 base, N esquemas) | Media |
| Discriminator por columna | Lógico | Baja (1 base, 1 esquema, columna `institucion_id`) | **Adecuada** |

## Decisión

Se adopta **multi-tenancy por discriminator**: cada tabla tenant-scoped incluye una columna `institucion_id BIGINT NOT NULL` que actúa como FK a `instituciones(id)`.

El aislamiento se garantiza con **defensa en profundidad** en cinco capas:

1. **Sesión**: `TenantContext` (ThreadLocal) almacena el `institucion_id` del usuario logueado.
2. **Hibernate**: un `@Filter` activado por interceptor agrega automáticamente `WHERE institucion_id = :tenantId` a toda query JPA.
3. **Service**: validación explícita de que el recurso accedido pertenece al tenant del contexto.
4. **Base de datos**: índices compuestos `(institucion_id, ...)` y FK con `ON DELETE RESTRICT`.
5. **Logs**: MDC con `tenantId` en cada línea para trazabilidad.

## Excepciones

Tres tablas son globales (sin `institucion_id`):
- `roles`: catálogo de roles del sistema.
- `motivos_carga_manual`: catálogo de motivos.
- `auditoria`: incluye `institucion_id` pero como columna nullable.

## Consecuencias

**Positivas:**
- Una sola base de datos: backup, monitoreo y migraciones simples.
- Onboarding de nueva institución = inserción de fila en `instituciones`.
- Bajo overhead operativo, apropiado para despliegue local.

**Negativas:**
- Una falla en el filtro de Hibernate puede exponer datos cruzados (mitigado con validación adicional en service).
- Compartir la misma base entre tenants requiere disciplina en queries nativas (no usar SQL raw sin filtrar por tenant).

## Cumplimiento legal

El aislamiento es requisito para cumplir con la Ley 25.326 y la Resolución AAIP 255/2022 sobre datos biométricos sensibles. Una fuga entre tenants compromete el cumplimiento legal.

## Referencias

- Sección 7 de la Guía del Proyecto.
- Hibernate ORM documentation - Filters.
