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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
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

    /** 1=Lunes ... 7=Domingo (ISO 8601). Persistido como TINYINT. */
    @Column(name = "dia_semana", nullable = false)
    private Byte diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "tolerancia_min", nullable = false)
    @Builder.Default
    private Short toleranciaMin = (short) 15;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    /** Helper de conveniencia: devuelve el enum DiaSemana correspondiente. */
    public DiaSemana getDia() {
        return diaSemana == null ? null : DiaSemana.fromNumero(diaSemana);
    }

    public void setDia(DiaSemana d) {
        this.diaSemana = d == null ? null : d.getNumero();
    }
}
