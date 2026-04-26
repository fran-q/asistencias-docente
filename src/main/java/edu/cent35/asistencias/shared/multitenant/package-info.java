/**
 * Multi-tenancy por discriminator (institucion_id).
 * <p>
 * Componentes principales:
 * <ul>
 *   <li>{@code TenantContext}: ThreadLocal con el id de institucion del request actual.</li>
 *   <li>{@code TenantInterceptor}: setea el contexto al inicio de cada request.</li>
 *   <li>{@code BaseTenantEntity}: superclase JPA con la columna institucion_id y @Filter activado.</li>
 * </ul>
 * <p>
 * RF-04 y RNF-10: aislamiento total entre instituciones.
 */
package edu.cent35.asistencias.shared.multitenant;
