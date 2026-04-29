package edu.cent35.asistencias.shared.multitenant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Superclase para entidades tenant-scoped: aporta la columna
 * {@code institucion_id} compartida por todas las tablas que pertenecen
 * a una institucion.
 * <p>
 * En la <b>Fase B del Sprint 1</b> se sumara aqui (o en
 * {@code package-info.java}) el {@code @FilterDef} de Hibernate y
 * cada entidad concreta llevara {@code @Filter("tenant")}, junto con
 * el aspecto que activa el filtro por request a partir del
 * {@code TenantContext}.
 * <p>
 * Por ahora la columna queda mapeada y la entidad la persiste, pero
 * no hay filtrado automatico (las queries devolveran datos de todos
 * los tenants si se ejecutan sin un WHERE manual). El aislamiento se
 * activa cuando llegue la Fase B.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseTenantEntity {

    @Column(name = "institucion_id", nullable = false, updatable = false)
    private Long institucionId;
}
