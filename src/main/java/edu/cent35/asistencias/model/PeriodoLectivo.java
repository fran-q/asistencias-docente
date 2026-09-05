package edu.cent35.asistencias.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
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
 * El tramo del ciclo en el que corre una comisión: anual, primer cuatrimestre, segundo.
 *
 * <p><b>Por qué es una entidad y no un enum.</b> "Anual" y "1er cuatrimestre" son la misma
 * clase de cosa —un nombre y dos fechas— y tratarlos como casos distintos obligaría a cada
 * consumidor a ramificar por tipo para averiguar si una fecha cae adentro. Siendo tabla, todos
 * preguntan lo mismo: {@code fecha BETWEEN fechaInicio AND fechaFin}. De paso admite
 * trimestres o un período de verano sin tocar el esquema.
 *
 * <p>Es tenant-scoped por columna propia y no por su ciclo: el filtro de Hibernate actúa sobre
 * {@code institucion_id}, y sin esa columna el período dependería de un JOIN para acotarse.
 */
@Entity
@Table(
    name = "periodos_lectivos",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_periodos_ciclo_nombre", columnNames = {"ciclo_id", "nombre"})
    }
)
@Filter(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "nombre", "fechaInicio", "fechaFin"})
public class PeriodoLectivo extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ciclo_id", nullable = false)
    private CicloLectivo ciclo;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    // Para listarlos en el orden en que ocurren y no por alfabeto, que pondria "2do
    // cuatrimestre" antes que "Anual".
    @Column(nullable = false)
    @Builder.Default
    private Short orden = 1;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    /** Si esa fecha cae dentro del período. Es la única pregunta que le hace el resto del sistema. */
    public boolean contiene(LocalDate fecha) {
        return fecha != null && !fecha.isBefore(fechaInicio) && !fecha.isAfter(fechaFin);
    }

    /**
     * Deja el período en la misma institución que su ciclo.
     *
     * <p>Mismo invariante que {@code Usuario.alinearInstitucionDeLaPersona}: la columna está
     * denormalizada, así que nada en la base impide guardarla desalineada, y un período en otra
     * institución que su ciclo desaparecería de las consultas sin dar ningún error.
     */
    @PrePersist
    private void alinearInstitucionDelCiclo() {
        if (ciclo != null && getInstitucionId() == null) {
            setInstitucionId(ciclo.getInstitucionId());
        }
    }
}
