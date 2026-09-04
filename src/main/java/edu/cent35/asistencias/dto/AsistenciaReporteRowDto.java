package edu.cent35.asistencias.dto;

import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.OrigenMarca;
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

    LocalTime horaSalida;          // del bloque de presencia; null si no hay o sigue adentro
    boolean salidaPresumida;       // la hora la completó el sistema, no la observó nadie

    /** Cuánto dura la clase según la grilla. Es contra esto que se compara lo efectivo. */
    int minutosProgramados;

    /**
     * Minutos de la clase que el docente efectivamente cubrió (RF-27 a RF-29).
     *
     * <p>Es la <b>intersección</b> entre su permanencia y la franja de la clase, no su
     * permanencia total: llegar media hora antes no agrega minutos dictados, y quedarse
     * después tampoco. Cero cuando estuvo ausente.
     *
     * <p><b>Null cuando no se puede saber</b>: marcas anteriores a la marca de salida, cargas
     * manuales sin bloque, o un docente que todavía está adentro. Null y cero son cosas
     * distintas y el reporte no las puede mostrar igual — una dice "no dio la clase" y la
     * otra "no tenemos el dato".
     */
    Integer minutosEfectivos;

    // Arma la fila del reporte sumando, si los hay, el detalle manual y el de la justificación.
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
            .docenteDni(a.getDocente().getPersona().getDni())
            .docenteApellido(a.getDocente().getPersona().getApellido())
            .docenteNombre(a.getDocente().getPersona().getNombre())
            .horaRegistrada(a.getHoraRegistrada())
            .estado(a.getEstado().name())
            .metodo(a.getMetodo().name())
            .confianza(a.getConfianza())
            .horaSalida(a.getBloque() == null ? null : a.getBloque().getHoraSalida())
            .salidaPresumida(a.getBloque() != null
                && a.getBloque().getOrigenSalida() == OrigenMarca.PRESUNTO)
            .minutosProgramados(minutosEntre(
                a.getHorario().getHoraInicio(), a.getHorario().getHoraFin()))
            .minutosEfectivos(minutosEfectivos(a))
            .motivoManual(manualOrNull != null && manualOrNull.getMotivo() != null
                ? manualOrNull.getMotivo().getDescripcion() : null)
            .detalleManual(manualOrNull != null ? manualOrNull.getDetalleAdicional() : null)
            .usuarioRegistrador(manualOrNull != null && manualOrNull.getUsuario() != null
                ? manualOrNull.getUsuario().getUsername() : null)
            .justificada(motivoJustOrNull != null)
            .motivoJustificacion(motivoJustOrNull)
            .build();
    }

    // Día que acompaña a la fecha. Sale de la fecha y no del día programado del horario: si por
    // un error de carga no coinciden, tiene que mostrarse el que de verdad corresponde a la fecha.
    private static String labelDia(LocalDate fecha) {
        if (fecha == null) return "";
        return DiaSemana.deLaFecha(fecha).getEtiqueta();
    }

    /**
     * Los minutos de la clase que quedaron cubiertos por la permanencia del docente.
     *
     * <p>Se recorta la permanencia contra la franja de la clase por los dos lados. Sin ese
     * recorte, un docente con una jornada de cuatro horas sumaría cuatro horas en cada una de
     * sus tres clases, y el reporte diría que dictó doce.
     */
    private static Integer minutosEfectivos(Asistencia a) {
        if ("AUSENTE".equals(a.getEstado().name())) {
            return 0;
        }
        if (a.getBloque() == null || a.getBloque().getHoraSalida() == null) {
            return null;   // no hay dato, que no es lo mismo que cero
        }
        LocalTime desde = maximo(a.getBloque().getHoraEntrada(), a.getHorario().getHoraInicio());
        LocalTime hasta = minimo(a.getBloque().getHoraSalida(), a.getHorario().getHoraFin());
        return Math.max(0, minutosEntre(desde, hasta));
    }

    private static int minutosEntre(LocalTime desde, LocalTime hasta) {
        if (desde == null || hasta == null) return 0;
        return (int) java.time.Duration.between(desde, hasta).toMinutes();
    }

    private static LocalTime maximo(LocalTime a, LocalTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalTime minimo(LocalTime a, LocalTime b) {
        return a.isBefore(b) ? a : b;
    }
}
