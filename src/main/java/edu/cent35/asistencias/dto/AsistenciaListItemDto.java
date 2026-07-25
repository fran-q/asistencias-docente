package edu.cent35.asistencias.dto;

import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.DiaSemana;
import edu.cent35.asistencias.model.EstadoAsistencia;
import edu.cent35.asistencias.model.Horario;
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
            .horaRegistrada(a.getHoraRegistrada())
            .estado(a.getEstado())
            .metodo(a.getMetodo())
            .confianza(a.getConfianza())
            .build();
    }

    /**
     * Construye una fila AUSENTE calculada para un horario que no tiene marca.
     * Se usa cuando la {@code hora_fin} del horario ya pasó (o la fecha es anterior).
     */
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

    private static String labelDia(Byte numero) {
        if (numero == null) return "";
        DiaSemana d = DiaSemana.fromNumero(numero);
        return d == null ? "" : capitalizar(d.name());
    }

    private static String capitalizar(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
