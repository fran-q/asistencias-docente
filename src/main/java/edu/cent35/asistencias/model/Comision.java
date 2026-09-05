package edu.cent35.asistencias.model;

import edu.cent35.asistencias.model.Docente;
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

import java.time.LocalDate;
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
 * <p>
 * Desde V023 la comision pertenece ademas a un periodo lectivo, que es lo que
 * la ubica en un ano calendario concreto.
 */
@Entity
@Table(
    name = "comisiones",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_comisiones_materia_codigo_periodo",
                          columnNames = {"materia_id", "codigo", "periodo_id"})
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

    @Column(nullable = false, length = 8)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "docente_asignado_id")
    private Docente docenteAsignado;

    /**
     * El tramo del ciclo en el que corre esta comisión (V023).
     *
     * <p>Es lo que la ata a un año concreto, y por eso entró al UNIQUE: "Comisión A" de 2026 y
     * "Comisión A" de 2027 son dos ofertas distintas de la misma materia. Antes de V023 esa
     * repetición estaba prohibida y dar la misma materia el año siguiente obligaba a pisar la
     * comisión anterior, con lo que el año viejo dejaba de poder reconstruirse.
     *
     * <p>LAZY: los listados que necesitan el período lo traen con JOIN FETCH, y los que no
     * —el pase, la grilla— no tienen por qué pagar la consulta.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "periodo_id", nullable = false)
    private PeriodoLectivo periodo;

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
