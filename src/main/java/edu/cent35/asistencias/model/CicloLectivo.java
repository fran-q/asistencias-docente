package edu.cent35.asistencias.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Un año calendario de cursada. Es lo que permite que la misma materia se ofrezca todos los
 * años sin que cada oferta pise a la anterior (V023).
 *
 * <p><b>No confundir con {@code materias.anio}</b>, que es el año del plan —primero, segundo,
 * tercero— y no cambia cuando cambia el almanaque. Acá "año" es 2026 o 2027.
 *
 * <p>El ciclo no se cuelga de la materia sino de la comisión, a través del período: lo que se
 * vuelve a ofrecer cada año es la comisión con su docente y sus horarios. La carrera y la
 * materia son el plan, y el plan sobrevive a los años.
 */
@Entity
@Table(
    name = "ciclos_lectivos",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_ciclos_inst_anio", columnNames = {"institucion_id", "anio"})
    }
)
@Filter(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "anio", "estado"})
public class CicloLectivo extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Short anio;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoCiclo estado = EstadoCiclo.PREPARACION;

    /**
     * Los tramos en los que corre la oferta.
     *
     * <p><b>{@code CascadeType.ALL} y {@code orphanRemoval}</b>, al revés que casi todo el
     * resto del sistema: un período no tiene sentido fuera de su ciclo —no es una entidad que
     * alguien vaya a consultar por su cuenta— y editarlos como una lista es exactamente cómo
     * se los arma en la pantalla. Es la única relación del proyecto donde el hijo es
     * verdaderamente parte del padre.
     *
     * <p>Aun así, borrar un período que tenga comisiones lo impide la FK: la base rechaza el
     * huérfano antes de que el cascade lo intente.
     */
    @OneToMany(mappedBy = "ciclo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC, fechaInicio ASC")
    @Builder.Default
    private List<PeriodoLectivo> periodos = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Column(name = "cerrado_en")
    private LocalDateTime cerradoEn;

    // Quien cerro el ciclo. Mismo criterio que dadoDeBajaPor: el id pelado, no la relacion.
    @Column(name = "cerrado_por")
    private Long cerradoPor;

    /** Si esa fecha cae dentro del ciclo. No dice si hay clases ese día: eso lo dice el período. */
    public boolean contiene(LocalDate fecha) {
        return fecha != null && !fecha.isBefore(fechaInicio) && !fecha.isAfter(fechaFin);
    }

    /**
     * Suma un período manteniendo los dos lados de la relación.
     *
     * <p>Con {@code mappedBy}, quien manda es el lado del período: agregarlo solo a la lista
     * deja el {@code ciclo_id} en null y la fila se rechaza al guardar. Es un olvido que no
     * se nota hasta que explota el INSERT.
     */
    public void agregarPeriodo(PeriodoLectivo periodo) {
        periodo.setCiclo(this);
        periodo.setInstitucionId(getInstitucionId());
        periodos.add(periodo);
    }

    /** El período de este ciclo que contiene esa fecha, o vacío si ninguno la cubre. */
    public java.util.Optional<PeriodoLectivo> periodoDe(LocalDate fecha) {
        return periodos.stream().filter(p -> p.contiene(fecha)).findFirst();
    }
}
