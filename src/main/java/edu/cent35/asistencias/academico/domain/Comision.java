package edu.cent35.asistencias.academico.domain;

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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Comision de una materia. Una materia puede tener varias comisiones
 * (turnos / divisiones). RF-13.
 * <p>
 * El tenant lo determina la materia padre - no se guarda explicitamente
 * un institucion_id en la tabla. La defensa multi-tenant se hace via
 * service-layer (validacion explicita de que la materia padre pertenece
 * al tenant actual).
 * <p>
 * El campo {@code docenteAsignadoId} se completa en Sprint 3 cuando
 * existan docentes gestionables. Por ahora puede ser null (V004 lo
 * habilito).
 */
@Entity
@Table(
    name = "comisiones",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_comisiones_materia_codigo", columnNames = {"materia_id", "codigo"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "codigo", "activo"})
public class Comision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "materia_id", nullable = false)
    private Materia materia;

    @Column(nullable = false, length = 30)
    private String codigo;

    @Column(name = "docente_asignado_id")
    private Long docenteAsignadoId;

    @Column
    private Integer cupo;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}
