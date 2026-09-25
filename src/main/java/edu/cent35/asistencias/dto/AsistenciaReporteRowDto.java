package edu.cent35.asistencias.dto;

import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.BloquePresencia;
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

    /**
     * Nombre del equipo desde el que se registró la entrada, y desde el que se registró la
     * salida (RF-89, V031). Null cuando no hubo ninguno.
     *
     * <p>Van los dos porque desde V030 puede haber una cámara por entrada: el docente entra
     * por una puerta y sale por la otra, y una sola columna diría que salió por donde entró.
     * La salida queda vacía cuando la cerró el job por vencimiento o un admin desde la
     * pantalla de pendientes, que no exige equipo.
     */
    String equipoEntrada;
    String equipoSalida;

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

    /**
     * Minutos que el docente llegó tarde a esta clase: del inicio de la clase a su entrada.
     *
     * <p>Cero si llegó a horario o antes, y nunca más que la clase entera. <b>Null cuando no
     * hay jornada</b>, por el mismo motivo que los minutos efectivos: sin una hora de entrada
     * observada no se puede afirmar un retraso, y una carga manual guarda la hora en que se
     * cargó, no la de llegada.
     */
    Integer minutosTarde;

    /** Minutos que faltaron al final: de la salida al fin de la clase. Mismo criterio de null. */
    Integer minutosSalidaAnticipada;

    /**
     * Minutos de permanencia fuera de la franja de esta clase: el "estuvo de más".
     *
     * <p>Se cuentan <b>una sola vez por jornada</b> y contra su primera y su última clase, que
     * es lo que los hace sumables. Una jornada que cubre tres clases seguidas no estuvo de más
     * entre la primera y la segunda: estuvo dando la segunda. Lo calcula el servicio del
     * reporte, que es quien conoce la jornada entera.
     */
    Integer minutosFueraDeClase;

    /**
     * Si el retraso entra en la tolerancia del horario (ADR-0018).
     *
     * <p>True cuando no hay dato: lo que la pantalla marca es el desvío que se pasó del margen,
     * y una fila sin jornada no tiene nada que marcar.
     */
    boolean llegadaDentroDelMargen;

    /** Si la salida entra en la tolerancia del horario (RF-78). Mismo criterio que la llegada. */
    boolean salidaDentroDelMargen;

    // Arma la fila del reporte sumando, si los hay, el detalle manual y el de la justificación.
    public static AsistenciaReporteRowDto from(Asistencia a,
                                               AsistenciaManual manualOrNull,
                                               String motivoJustOrNull) {
        return from(a, manualOrNull, motivoJustOrNull, null);
    }

    /**
     * La misma fila, con los minutos que la jornada pasó fuera de la franja de clase.
     *
     * <p>Ese dato no sale de la asistencia sola: depende de cuál es la primera y la última
     * clase de su jornada, y eso lo sabe el servicio del reporte.
     */
    public static AsistenciaReporteRowDto from(Asistencia a,
                                               AsistenciaManual manualOrNull,
                                               String motivoJustOrNull,
                                               Integer minutosFueraDeClase) {
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
            .equipoEntrada(nombreDelEquipo(a.getBloque() == null
                ? null : a.getBloque().getPuesto()))
            .equipoSalida(nombreDelEquipo(a.getBloque() == null
                ? null : a.getBloque().getPuestoSalida()))
            .salidaPresumida(a.getBloque() != null
                && a.getBloque().getOrigenSalida() == OrigenMarca.PRESUNTO)
            .minutosProgramados(minutosEntre(
                a.getHorario().getHoraInicio(), a.getHorario().getHoraFin()))
            .minutosEfectivos(minutosEfectivos(a))
            .minutosTarde(minutosTarde(a))
            .minutosSalidaAnticipada(minutosSalidaAnticipada(a))
            .minutosFueraDeClase(minutosFueraDeClase)
            .llegadaDentroDelMargen(llegadaDentroDelMargen(a))
            .salidaDentroDelMargen(salidaDentroDelMargen(a))
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

    /**
     * Cuántos minutos de la clase se perdieron por llegar tarde.
     *
     * <p>Se tope contra la clase entera para que valga el reparto completo:
     * <b>efectivos + tarde + salida anticipada = programados</b>. Sin ese tope, una jornada
     * que ni toca la clase daría cifras más grandes que la clase.
     */
    private static Integer minutosTarde(Asistencia a) {
        BloquePresencia b = a.getBloque();
        if (b == null) {
            return null;   // sin jornada no hay hora de llegada observada
        }
        int programados = minutosEntre(a.getHorario().getHoraInicio(), a.getHorario().getHoraFin());
        int tarde = minutosEntre(a.getHorario().getHoraInicio(), b.getHoraEntrada());
        return Math.min(programados, Math.max(0, tarde));
    }

    // Cuantos minutos de la clase se perdieron por irse antes. Mismo tope que el retraso.
    private static Integer minutosSalidaAnticipada(Asistencia a) {
        BloquePresencia b = a.getBloque();
        if (b == null || b.getHoraSalida() == null) {
            return null;
        }
        int programados = minutosEntre(a.getHorario().getHoraInicio(), a.getHorario().getHoraFin());
        int antes = minutosEntre(b.getHoraSalida(), a.getHorario().getHoraFin());
        return Math.min(programados, Math.max(0, antes));
    }

    // Si ese retraso lo perdona la tolerancia del horario, que es la misma que decide
    // PRESENTE o TARDE (ADR-0018).
    private static boolean llegadaDentroDelMargen(Asistencia a) {
        BloquePresencia b = a.getBloque();
        return b == null || a.getHorario().llegadaEnHora(b.getHoraEntrada());
    }

    private static boolean salidaDentroDelMargen(Asistencia a) {
        BloquePresencia b = a.getBloque();
        return b == null || b.getHoraSalida() == null
            || a.getHorario().salidaEnHora(b.getHoraSalida());
    }

    // El nombre con el que la institucion reconoce ese equipo ("Entrada norte"), o null.
    private static String nombreDelEquipo(edu.cent35.asistencias.model.PuestoCaptura puesto) {
        return puesto == null ? null : puesto.getNombre();
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
