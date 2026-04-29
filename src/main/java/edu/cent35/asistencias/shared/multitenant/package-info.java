/**
 * Multi-tenancy por discriminator (institucion_id).
 * <p>
 * Componentes principales:
 * <ul>
 *   <li>{@link edu.cent35.asistencias.shared.multitenant.TenantContext}:
 *       ThreadLocal con el id de institucion del request actual.</li>
 *   <li>{@code TenantInterceptor} (Fase C): setea el contexto al inicio
 *       de cada request a partir del usuario autenticado.</li>
 *   <li>{@link edu.cent35.asistencias.shared.multitenant.TenantFilterAspect}:
 *       activa el filtro de Hibernate por transaccion.</li>
 *   <li>{@link edu.cent35.asistencias.shared.multitenant.BaseTenantEntity}:
 *       superclase JPA con la columna {@code institucion_id}.</li>
 * </ul>
 * <p>
 * <b>El {@code @FilterDef} de abajo registra el filtro a nivel global
 * en el persistence unit.</b> Cualquier entidad anotada con
 * {@code @Filter(name = "tenant")} usa esta definicion.
 * <p>
 * RF-04 y RNF-10: aislamiento total entre instituciones.
 */
@FilterDef(
    name = "tenant",
    parameters = @ParamDef(name = "institucionId", type = Long.class),
    defaultCondition = "institucion_id = :institucionId"
)
package edu.cent35.asistencias.shared.multitenant;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
