package edu.cent35.asistencias.dto;

import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.DiaSemana;
import edu.cent35.asistencias.model.EstadoAsistencia;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.OrigenMarca;
import edu.cent35.asistencias.model.MetodoAsistencia;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Fila del listado de asistencias del día (Sprint 5 Fase C).
 * <p>
 * Puede representar tanto una asistencia <i>persistida</i> (PRESENTE, TARDE,
 * o un MANUAL/AUSENTE cargado a mano) como una asistencia <b>calculada como
 * AUSENTE</b>: cuando un horario del día ya terminó y no hay fila para
 * ese (docente, horario, fecha). En el caso AUSENTE calculado, {@link #id}
 * es {@code null} y {@link #horaRegistrada} también.
 */
@Value
@Builder
public class AsistenciaListItemDto {

    // null si es una fila AUSENTE calculada.
    Long id;

    Long docenteId;
    String docenteNombre;

    Long comisionId;
    String comisionCodigo;
    String materiaNombre;

    Long horarioId;
    Byte diaSemana;
    String diaLabel;
    LocalTime horaInicio;
    LocalTime horaFin;

    LocalDate fecha;
    // null si AUSENTE calculada.
    LocalTime horaRegistrada;

    EstadoAsistencia estado;
    // null si AUSENTE calculada (no hay método).
    MetodoAsistencia metodo;
    // Sólo presente si metodo == AUTOMATICO.
    BigDecimal confianza;

    /**
     * Hora en que el docente se retiró, tomada de su bloque de presencia (RF-74).
     *
     * <p>Null en las marcas anteriores a V019, en las cargas manuales sin bloque y mientras
     * el docente siga adentro. Que esté vacía no es un error: significa que todavía no se fue
     * o que ese registro es de antes de que existiera la marca de salida.
     */
    LocalTime horaSalida;

    /**
     * Si esa hora la completó el sistema en vez de observarla (RF-80).
     *
     * <p>Se muestra distinto a propósito. Un cierre por reconocimiento, uno cargado por un
     * admin y una hora presumida tienen distinto valor probatorio, y verlos iguales en el
     * listado es exactamente lo que hace que después nadie sepa cuál es cuál.
     */
    boolean salidaPresumida;

    // Arma la fila del listado a partir de la entidad, resolviendo lo que el template va a mostrar.
    public static AsistenciaListItemDto from(Asistencia a) {
        Horario h = a.getHorario();
        return AsistenciaListItemDto.builder()
            .id(a.getId())
            .docenteId(a.getDocente().getId())
            .docenteNombre(a.getDocente().getNombreCompleto())
            .comisionId(a.getComision().getId())
            .comisionCodigo(a.getComision().getCodigo())
            .materiaNombre(a.getComision().getMateria().getNombre())
            .horarioId(h.getId())
            .diaSemana(h.getDiaSemana())
            .diaLabel(labelDia(h.getDiaSemana()))
            .horaInicio(h.getHoraInicio())
            .horaFin(h.getHoraFin())
            .fecha(a.getFecha())
            .horaSalida(a.getBloque() == null ? null : a.getBloque().getHoraSalida())
            .salidaPresumida(a.getBloque() != null
                && a.getBloque().getOrigenSalida() == OrigenMarca.PRESUNTO)
            .horaRegistrada(a.getHoraRegistrada())
            .estado(a.getEstado())
            .metodo(a.getMetodo())
            .confianza(a.getConfianza())
            .build();
    }

    // Fila AUSENTE que no está en la base: se calcula al vuelo para un horario ya terminado sin marca.
    public static AsistenciaListItemDto ausenteCalculada(Horario h, LocalDate fecha) {
        return AsistenciaListItemDto.builder()
            .id(null)
            .docenteId(h.getComision().getDocenteAsignado().getId())
            .docenteNombre(h.getComision().getDocenteAsignado().getNombreCompleto())
            .comisionId(h.getComision().getId())
            .comisionCodigo(h.getComision().getCodigo())
            .materiaNombre(h.getComision().getMateria().getNombre())
            .horarioId(h.getId())
            .diaSemana(h.getDiaSemana())
            .diaLabel(labelDia(h.getDiaSemana()))
            .horaInicio(h.getHoraInicio())
            .horaFin(h.getHoraFin())
            .fecha(fecha)
            .horaRegistrada(null)
            .estado(EstadoAsistencia.AUSENTE)
            .metodo(null)
            .confianza(null)
            .build();
    }

    // true si esta fila no se persistió (es AUSENTE calculada).
    public boolean isCalculada() {
        return id == null;
    }

    // Nombre del día para mostrar en la fila.
    private static String labelDia(Byte numero) {
        if (numero == null) return "";
        DiaSemana d = DiaSemana.fromNumero(numero);
        return d == null ? "" : capitalizar(d.name());
    }

    // Deja la primera letra en mayúscula y el resto en minúscula.
    private static String capitalizar(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
