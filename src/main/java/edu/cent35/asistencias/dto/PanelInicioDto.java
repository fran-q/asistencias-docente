package edu.cent35.asistencias.dto;

import java.time.LocalTime;
import java.util.List;

/**
 * Lo que se muestra en la pantalla de inicio.
 *
 * <p>La home no repite los accesos del menu: eso ya lo contesta la barra de navegacion. Lo
 * que contesta aca es que esta pasando ahora y que necesita que alguien haga algo, que es
 * lo unico que justifica una pantalla de inicio propia.
 *
 * @param enCurso    clases con la ventana horaria abierta en este momento
 * @param resumen    como viene el dia en numeros
 * @param pendientes cosas cargadas a medias que impiden que el sistema funcione
 */
public record PanelInicioDto(
    List<ClaseEnCurso> enCurso,
    ResumenDelDia resumen,
    List<Pendiente> pendientes
) {

    // true si no hay ninguna clase corriendo ahora mismo.
    public boolean sinClasesAhora() {
        return enCurso.isEmpty();
    }

    // true si hay alguna clase en curso con el docente todavia sin marcar.
    public boolean hayAlguienSinMarcar() {
        return enCurso.stream().anyMatch(c -> !c.marcada());
    }

    // true si no quedo nada pendiente de cargar.
    public boolean todoEnOrden() {
        return pendientes.isEmpty();
    }

    /**
     * Una clase corriendo ahora, con el estado de su docente.
     *
     * @param estado     PRESENTE o TARDE si ya marco; null si todavia no
     * @param horaMarca  cuando marco, o null si todavia no
     */
    public record ClaseEnCurso(
        LocalTime horaInicio,
        LocalTime horaFin,
        String comisionCodigo,
        String materiaNombre,
        String docenteNombre,
        boolean marcada,
        String estado,
        LocalTime horaMarca
    ) {}

    /**
     * El dia en numeros.
     *
     * <p>Las ausencias se cuentan solo sobre clases que ya terminaron: una clase que todavia
     * no empezo no es una ausencia, es una clase que falta. Mezclarlas daria un tablero en
     * rojo a primera hora de la manana todos los dias.
     *
     * @param docentesQueMarcaron docentes con al menos una marca hoy
     * @param docentesConClase    docentes con al menos una clase hoy
     */
    public record ResumenDelDia(
        long presentes,
        long tarde,
        long ausentes,
        long pendientesDeMarcar,
        long docentesQueMarcaron,
        long docentesConClase
    ) {

        // Porcentaje de docentes del dia que ya marcaron, para la barra de seguimiento.
        public int porcentajeCobertura() {
            if (docentesConClase == 0) return 0;
            return (int) Math.round(docentesQueMarcaron * 100.0 / docentesConClase);
        }

        // true si hoy no hay ninguna clase programada.
        public boolean sinClasesHoy() {
            return docentesConClase == 0;
        }
    }

    /**
     * Algo que falta cargar y que impide que el sistema funcione.
     *
     * @param url a donde se va a resolverlo
     */
    public record Pendiente(long cantidad, String titulo, String detalle, String url) {}
}
