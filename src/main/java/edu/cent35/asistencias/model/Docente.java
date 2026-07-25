package edu.cent35.asistencias.model;

import edu.cent35.asistencias.model.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Docente - sujeto pasivo del sistema (no se loguea, RF-07).
 * Tenant-scoped: pertenece a una unica institucion.
 * <p>
 * Los datos personales y biometricos que se persistan estan sujetos a
 * la Ley 25.326 y la Resolucion AAIP 255/2022. El consentimiento
 * informado se modela aparte ({@code ConsentimientoBiometrico}).
 */
@Entity
@Table(
    name = "docentes",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_docentes_inst_dni",    columnNames = {"institucion_id", "dni"}),
        @UniqueConstraint(name = "uq_docentes_inst_legajo", columnNames = {"institucion_id", "legajo"})
    }
)
@Filter(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "dni", "apellido", "nombre", "activo"})
public class Docente extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 15)
    private String dni;

    @Column(length = 30)
    private String legajo;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(nullable = false, length = 80)
    private String apellido;

    @Column(length = 120)
    private String email;

    @Column(length = 30)
    private String telefono;

    @Column(name = "fecha_alta", nullable = false)
    private LocalDate fechaAlta;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    // Helper: nombre + apellido en un solo string.
    public String getNombreCompleto() {
        return apellido + ", " + nombre;
    }
}
