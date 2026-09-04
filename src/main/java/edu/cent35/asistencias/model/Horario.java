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
 * La tolerancia (default 15 min) es el margen alrededor del horario: se
 * clasifica Presente vs Tarde cruzando la hora del registro con
 * {@code hora_inicio + tolerancia_min}, y En hora vs Anticipada cruzandola
 * con {@code hora_fin - tolerancia_min}. Es el mismo valor de los dos lados
 * y en los dos extremos (RF-19, RF-78, ADR-0018).
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
     * Cuántos minutos puede estirarse la tolerancia hacia cada lado, como máximo.
     *
     * <p>Media hora es lo que tarda alguien en llegar, dejar sus cosas y pasar por
     * secretaría. Más que eso deja de ser "llegó a dar su clase" y pasa a ser cualquier
     * momento del día: con dos horas de anticipación, un docente que viene a la mañana
     * marca la clase de la tarde sin haberla dado.
     *
     * <p>Desde ADR-0018 el tope rige <b>de los dos lados</b>, no solo antes del inicio: una
     * salida aceptada mucho después del fin acredita una permanencia que no ocurrió, que es
     * el riesgo espejo del anterior.
     */
    public static final int MINUTOS_MAXIMOS_DE_TOLERANCIA = 30;

    /**
     * Indica si {@code ahora} cae dentro de [hora_inicio - tolerancia, hora_fin], que es la
     * ventana en la que el pase acepta una marca de entrada para esta clase.
     *
     * <p>Vive en el modelo y no en un servicio porque la responden tres lugares distintos:
     * el pase, para decidir a que clase imputar la marca; el panel de inicio, para decir
     * que clases estan corriendo; y el resolutor de bloques, para saber cual esta en curso.
     * Con una copia en cada lado alcanzaba con tocar una para que la home mostrara como en
     * curso algo que el pase se negaba a marcar.
     */
    public boolean estaEnCurso(LocalTime ahora) {
        if (ahora == null || horaInicio == null || horaFin == null) return false;
        return !ahora.isBefore(sinDarLaVuelta(horaInicio, -toleranciaEfectiva()))
            && !ahora.isAfter(horaFin);
    }

    /**
     * Los minutos de tolerancia que realmente se aplican: la cargada, pero nunca más de
     * {@link #MINUTOS_MAXIMOS_DE_TOLERANCIA}.
     *
     * <p>El tope vive acá y no solo en la validación del formulario porque las franjas
     * cargadas antes de esta regla pueden tener tolerancias mayores, y esas también tienen
     * que quedar acotadas sin necesidad de corregirlas una por una.
     */
    public int toleranciaEfectiva() {
        short tol = toleranciaMin == null ? 0 : toleranciaMin;
        return Math.min(tol, MINUTOS_MAXIMOS_DE_TOLERANCIA);
    }

    /**
     * Indica si llegar a esa hora cuenta como PRESENTE en vez de TARDE (RF-19, ADR-0018).
     *
     * <p>La tolerancia perdona <b>hacia los dos lados</b> del inicio: llegar antes siempre
     * estuvo bien, y llegar hasta {@code hora_inicio + tolerancia} también. Hasta ADR-0018
     * el código clasificaba TARDE apenas pasaba la hora de inicio, contra lo que dicen el
     * RF-19, el glosario y el javadoc de esta misma clase.
     */
    public boolean llegadaEnHora(LocalTime hora) {
        if (hora == null || horaInicio == null) return false;
        return !hora.isAfter(sinDarLaVuelta(horaInicio, toleranciaEfectiva()));
    }

    /**
     * Indica si irse a esa hora cuenta como salida en hora en vez de anticipada (RF-78).
     *
     * <p>Es el espejo exacto de {@link #llegadaEnHora}, con el mismo número de minutos y
     * alrededor de {@code hora_fin}. Sin la tolerancia de este lado, irse 19:58 de una clase
     * que termina 20:00 quedaría registrado como retiro anticipado.
     */
    public boolean salidaEnHora(LocalTime hora) {
        if (hora == null || horaFin == null) return false;
        return !hora.isBefore(sinDarLaVuelta(horaFin, -toleranciaEfectiva()));
    }

    // Hasta cuándo se acepta una salida para esta clase: hora_fin más la tolerancia (RF-78).
    public LocalTime limiteDeSalida() {
        return sinDarLaVuelta(horaFin, toleranciaEfectiva());
    }

    /**
     * Corre una hora tantos minutos, sin dejar que cruce la medianoche.
     *
     * <p>{@link LocalTime} es circular: restarle 15 minutos a las 00:10 da 23:55, y a partir
     * de ahí toda comparación se invierte y la ventana queda al revés. Una clase a las 00:10
     * es rara pero el esquema la permite, así que la resta se topea en el borde del día en
     * vez de dar la vuelta. Es coherente con que los bloques no crucen la medianoche
     * (ADR-0017).
     */
    private static LocalTime sinDarLaVuelta(LocalTime base, int minutos) {
        if (base == null) return null;
        LocalTime corrida = base.plusMinutes(minutos);
        if (minutos < 0 && corrida.isAfter(base)) return LocalTime.MIN;
        if (minutos > 0 && corrida.isBefore(base)) return LocalTime.MAX;
        return corrida;
    }

    // Quien ejecuto la baja logica. NULL mientras la fila siga activa, y tambien en las bajas
    // anteriores a V017, que no lo registraban. Ver ADR-0016.
    @Column(name = "dado_de_baja_por")
    private Long dadoDeBajaPor;
}
