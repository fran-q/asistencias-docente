package edu.cent35.asistencias.model;

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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un día dentro del ciclo en el que no se dicta clase: un feriado, el receso, una jornada
 * institucional, un paro.
 *
 * <p><b>Qué evita.</b> El job de ausencias materializa una fila AUSENTE por cada horario que
 * nadie marcó. Sin esta tabla, un feriado produce una ausencia automática para cada docente que
 * tenía clase ese día: no es un dato incompleto, es un dato falso —dice que alguien faltó un
 * día en que la institución estaba cerrada— y limpiarlo después cuesta más que no generarlo.
 *
 * <p><b>Qué no hace.</b> No impide tomar asistencia. Si alguien viene a trabajar un feriado, la
 * cámara lo registra igual y esa marca vale como cualquier otra. Lo que el día dice es "no
 * esperes que vengan", no "no pueden venir".
 *
 * <p>No lleva baja lógica, al revés que el resto del sistema: nada referencia a un día no
 * laborable, así que uno cargado por error se borra en vez de quedar como fila inactiva
 * ensuciando el listado.
 */
@Entity
@Table(
    name = "dias_no_laborables",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_dias_inst_fecha", columnNames = {"institucion_id", "fecha"})
    }
)
@Filter(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "fecha", "motivo"})
public class DiaNoLaborable extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 120)
    private String motivo;

    // Quien lo cargo. NULL si esa cuenta ya no existe (ON DELETE SET NULL).
    @Column(name = "creado_por")
    private Long creadoPor;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;
}
