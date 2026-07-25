package edu.cent35.asistencias.dto;

import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.AsistenciaManual;
import edu.cent35.asistencias.model.DiaSemana;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Fila de reporte de asistencias (Sprint 6 Fase A).
 * Se usa tanto para mostrar en la pantalla de reportes como para
 * exportar a CSV.
 */
@Value
@Builder
public class AsistenciaReporteRowDto {

    Long asistenciaId;
    LocalDate fecha;
    String diaSemana;
    LocalTime horaInicio;
    LocalTime horaFin;
    String carreraCodigo;
    String materiaCodigo;
    String materiaNombre;
    String comisionCodigo;
    String docenteDni;
    String docenteApellido;
    String docenteNombre;
    LocalTime horaRegistrada;
    String estado;
    String metodo;
    BigDecimal confianza;
    String motivoManual;           // motivo del catálogo si la marca es MANUAL
    String detalleManual;          // detalle adicional opcional del cargador
    String usuarioRegistrador;     // username del admin que la cargó manualmente
    boolean justificada;           // true si la AUSENTE tiene justificación adjunta
    String motivoJustificacion;    // motivo de la justificación, si la hay

    public static AsistenciaReporteRowDto from(Asistencia a,
                                               AsistenciaManual manualOrNull,
                                               String motivoJustOrNull) {
        return AsistenciaReporteRowDto.builder()
            .asistenciaId(a.getId())
            .fecha(a.getFecha())
            .diaSemana(labelDia(a.getFecha()))
            .horaInicio(a.getHorario().getHoraInicio())
            .horaFin(a.getHorario().getHoraFin())
            .carreraCodigo(a.getComision().getMateria().getCarrera() != null
                ? a.getComision().getMateria().getCarrera().getCodigo() : null)
            .materiaCodigo(a.getComision().getMateria().getCodigo())
            .materiaNombre(a.getComision().getMateria().getNombre())
            .comisionCodigo(a.getComision().getCodigo())
            .docenteDni(a.getDocente().getDni())
            .docenteApellido(a.getDocente().getApellido())
            .docenteNombre(a.getDocente().getNombre())
            .horaRegistrada(a.getHoraRegistrada())
            .estado(a.getEstado().name())
            .metodo(a.getMetodo().name())
            .confianza(a.getConfianza())
            .motivoManual(manualOrNull != null && manualOrNull.getMotivo() != null
                ? manualOrNull.getMotivo().getDescripcion() : null)
            .detalleManual(manualOrNull != null ? manualOrNull.getDetalleAdicional() : null)
            .usuarioRegistrador(manualOrNull != null && manualOrNull.getUsuario() != null
                ? manualOrNull.getUsuario().getUsername() : null)
            .justificada(motivoJustOrNull != null)
            .motivoJustificacion(motivoJustOrNull)
            .build();
    }

    /**
     * Dia que se muestra junto a la fecha. Sale de la <b>fecha</b> de la marca,
     * no del dia programado del horario: si por un error de carga no coinciden,
     * el reporte tiene que mostrar el dia que realmente corresponde a la fecha.
     */
    private static String labelDia(LocalDate fecha) {
        if (fecha == null) return "";
        return DiaSemana.deLaFecha(fecha).getEtiqueta();
    }
}
