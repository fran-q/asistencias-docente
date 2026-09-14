package edu.cent35.asistencias.model;

/**
 * Qué clase de día sin clase es (V028). Es por lo que se filtra el listado; el motivo sigue
 * siendo la descripción libre de por qué ese día no hay clases.
 *
 * <p>No cambia lo que el día hace: cualquiera de los cinco saltea las ausencias y apaga el pase.
 * Sirve para revisar el año —qué feriados se cargaron, cuándo fue el receso— sin leer los
 * motivos uno por uno.
 */
public enum TipoDiaNoLaborable {

    NACIONAL("Feriado nacional"),

    // Los de la provincia, que el calendario nacional no trae.
    PROVINCIAL("Feriado provincial"),

    // Una jornada, un acto o un cierre que decide la propia institucion.
    INSTITUCIONAL("Institucional"),

    // Invierno o verano: varios dias seguidos, que se marcan de una vez con un rango.
    RECESO("Receso"),

    // Lo que no entra en los demas, y los dias cargados antes de V028, que no tenian tipo.
    OTRO("Otro");

    private final String etiqueta;

    TipoDiaNoLaborable(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    // Nombre para mostrar en pantalla.
    public String getEtiqueta() {
        return etiqueta;
    }
}
