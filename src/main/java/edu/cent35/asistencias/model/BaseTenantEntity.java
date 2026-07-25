package edu.cent35.asistencias.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Superclase de las entidades que pertenecen a una institución: aporta la columna
 * institucion_id sobre la que actúa el filtro de Hibernate. Cada entidad concreta suma su
 * propio @Filter("tenant"), y TenantFilterAspect lo activa por transacción.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseTenantEntity {

    @Column(name = "institucion_id", nullable = false, updatable = false)
    private Long institucionId;
}
