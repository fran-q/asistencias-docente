/**
 * Modelo del dominio: entidades JPA, enums y superclase tenant.
 * <p>
 * Acá se declara con {@link org.hibernate.annotations.FilterDef} el filtro
 * {@code "tenant"} de Hibernate a nivel global del persistence unit.
 * Cualquier entidad anotada con {@code @Filter(name = "tenant")} usa esta
 * definición. El filtro se activa por transacción vía
 * {@link edu.cent35.asistencias.config.TenantFilterAspect} a partir del
 * {@link edu.cent35.asistencias.config.TenantContext}.
 * <p>
 * RF-04 y RNF-10: aislamiento total entre instituciones.
 */
@FilterDef(
    name = "tenant",
    parameters = @ParamDef(name = "institucionId", type = Long.class),
    defaultCondition = "institucion_id = :institucionId"
)
package edu.cent35.asistencias.model;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
