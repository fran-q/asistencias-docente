package edu.cent35.asistencias.model;

import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * Materia academica perteneciente a una carrera. RF-12.
 * Tenant-scoped: pertenece a una unica institucion.
 * <p>
 * El campo {@code docenteTitularId} se completa en Sprint 3 cuando
 * existan docentes gestionables. Por ahora se persiste null.
 */
@Entity
@Table(
    name = "materias",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_materias_inst_codigo", columnNames = {"institucion_id", "codigo"})
    }
)
@Filter(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "codigo", "nombre", "activo"})
public class Materia extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrera_id", nullable = false)
    private Carrera carrera;

    @Column(nullable = false, length = 30)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    /**
     * Anio de la carrera en el que se cursa.
     *
     * <p>Va en la materia y no en la comision porque es una propiedad del plan de estudios:
     * que Analisis I sea de primero no depende de si se dicta a la manana o a la noche. En la
     * comision habria que repetirlo en cada una y dos podrian contradecirse.
     */
    @Column(nullable = false)
    @Builder.Default
    private Short anio = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "docente_titular_id")
    private Docente docenteTitular;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    // Cuando se dio de baja. NULL = no fue dada de baja.
    @Column(name = "fecha_baja")
    private LocalDate fechaBaja;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    // Quien ejecuto la baja logica. NULL mientras la fila siga activa, y tambien en las bajas
    // anteriores a V017, que no lo registraban. Ver ADR-0016.
    @Column(name = "dado_de_baja_por")
    private Long dadoDeBajaPor;
}
