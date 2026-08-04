package edu.cent35.asistencias.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
import java.time.LocalTime;

/**
 * Franja horaria semanal de una comision. RF-14.
 * <p>
 * El dia de la semana se persiste como TINYINT (1=Lunes ... 7=Domingo
 * segun ISO 8601). El enum {@link DiaSemana} provee conversion segura.
 * <p>
 * La tolerancia (default 15 min) se usa para clasificar Presente vs Tarde
 * en la asistencia automatica (RF-19) - se evalua al cruzar la hora del
 * registro con {@code hora_inicio + tolerancia_min}.
 * <p>
 * Tenant: lo determina la comision -> materia. Validacion en service.
 */
@Entity
@Table(name = "horarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "diaSemana", "horaInicio", "horaFin", "activo"})
public class Horario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comision_id", nullable = false)
    private Comision comision;

    // 1=Lunes ... 7=Domingo (ISO 8601). Persistido como TINYINT.
    @Column(name = "dia_semana", nullable = false)
    private Byte diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "tolerancia_min", nullable = false)
    @Builder.Default
    private Short toleranciaMin = (short) 15;



    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    // Cuando se dio de baja. NULL = no fue dado de baja. Con actualizado_en no alcanza:
    // cambia con cualquier edicion posterior y deja de servir como constancia.
    @Column(name = "fecha_baja")
    private LocalDate fechaBaja;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    // Helper de conveniencia: devuelve el enum DiaSemana correspondiente.
    public DiaSemana getDia() {
        return diaSemana == null ? null : DiaSemana.fromNumero(diaSemana);
    }

    // Guarda el dia como su numero ISO, que es el tipo real de la columna.
    public void setDia(DiaSemana d) {
        this.diaSemana = d == null ? null : d.getNumero();
    }

    /**
     * Indica si {@code ahora} cae dentro de [hora_inicio - tolerancia, hora_fin], que es la
     * ventana en la que el pase acepta una marca para esta clase.
     *
     * <p>Vive en el modelo y no en un servicio porque la responden dos pantallas distintas:
     * el pase, para decidir a que clase imputar la marca, y el panel de inicio, para decir
     * que clases estan corriendo. Con una copia en cada lado alcanzaba con tocar una para
     * que la home mostrara como en curso algo que el pase se negaba a marcar.
     */
    public boolean estaEnCurso(LocalTime ahora) {
        if (ahora == null || horaInicio == null || horaFin == null) return false;
        short tol = toleranciaMin == null ? 0 : toleranciaMin;
        return !ahora.isBefore(horaInicio.minusMinutes(tol)) && !ahora.isAfter(horaFin);
    }
}
