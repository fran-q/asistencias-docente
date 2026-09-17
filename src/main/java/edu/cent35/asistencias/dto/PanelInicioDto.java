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
 * @param enCurso         clases con la ventana horaria abierta en este momento
 * @param proximas        las que arrancan mas tarde hoy; solo importan si no hay ninguna en curso
 * @param resumen         como viene el dia en numeros
 * @param pendientes      cosas cargadas a medias que impiden que el sistema funcione
 * @param motivoSinClases por que no hay nada en curso ni por venir; null si hay algo
 */
public record PanelInicioDto(
    List<ClaseEnCurso> enCurso,
    List<ProximaClase> proximas,
    ResumenDelDia resumen,
    List<Pendiente> pendientes,
    MotivoSinClases motivoSinClases
) {

    // true si no hay ninguna clase corriendo ahora mismo.
    public boolean sinClasesAhora() {
        return enCurso.isEmpty();
    }

    // true si no hay nada corriendo pero si algo mas tarde.
    public boolean hayProximas() {
        return enCurso.isEmpty() && !proximas.isEmpty();
    }

    // true si no hay nada corriendo ni por venir: el dia ya termino o no habia clases.
    public boolean nadaMasHoy() {
        return enCurso.isEmpty() && proximas.isEmpty();
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
     * Una clase que todavia no empezo.
     *
     * <p>Solo se muestran cuando no hay ninguna en curso: el bloque "Ahora mismo" pasaba la
     * mayor parte del dia diciendo que no habia nada y ocupando un tercio del ancho igual.
     * Decir a que hora arranca la proxima usa ese espacio para algo.
     */
    public record ProximaClase(
        LocalTime horaInicio,
        LocalTime horaFin,
        String comisionCodigo,
        String materiaNombre,
        String docenteNombre
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
     * @param cantidad        cuántos casos hay; null cuando no se cuentan: un ciclo sin activar
     *                        es uno solo, y un "1" adelante no dice nada
     * @param url             a donde se va a resolverlo
     * @param soloInstitucion si esa pantalla es solo de la cuenta de la institución. Para otro
     *                        rol el pendiente se muestra sin enlace: un enlace a un acceso
     *                        denegado no ayuda a resolver nada, y decir quién lo resuelve sí
     * @param cuales          los casos por nombre --docentes, comisiones--: el listado al que
     *                        lleva el enlace no los distingue, y había que revisarlo fila por fila
     */
    public record Pendiente(Long cantidad, String titulo, String detalle, String url,
                            boolean soloInstitucion, List<String> cuales) {

        // Cuantos casos se nombran; el resto se resume en "y N mas".
        private static final int NOMBRADOS = 3;

        public Pendiente(long cantidad, String titulo, String detalle, String url) {
            this(cantidad, titulo, detalle, url, false, List.of());
        }

        // Los primeros casos por nombre, o null si no hay nombres que mostrar.
        public String cualesResumidos() {
            if (cuales == null || cuales.isEmpty()) return null;
            String primeros = String.join(" · ", cuales.subList(0, Math.min(NOMBRADOS, cuales.size())));
            int resto = cuales.size() - NOMBRADOS;
            return resto > 0 ? primeros + " y " + resto + " más" : primeros;
        }
    }

    /**
     * Por qué no hay ninguna clase en curso ni por venir hoy.
     *
     * <p>"No hay clases" era la respuesta a todo: a un feriado, a un día que ya terminó y a un
     * ciclo sin activar, que en pantalla se veían iguales y se resuelven de formas muy
     * distintas. Sin la causa, quien toma asistencia tenía que revisar el calendario entero.
     *
     * @param problema        si hay algo que resolver, y se destaca, o es solo cómo viene el día
     * @param enlace          el texto del enlace a donde se resuelve; null si no hay nada que hacer
     * @param soloInstitucion ver {@link Pendiente}
     */
    public record MotivoSinClases(String texto, boolean problema, String enlace, String url,
                                  boolean soloInstitucion) {

        public static MotivoSinClases informativo(String texto) {
            return new MotivoSinClases(texto, false, null, null, false);
        }
    }
}
