package edu.cent35.asistencias.model;

/**
 * En qué momento de su vida está un ciclo lectivo. Lo que decide es qué se puede editar, no
 * qué se puede leer: un ciclo cerrado se sigue consultando entero desde los reportes.
 */
public enum EstadoCiclo {

    // Se está armando la oferta del año que viene mientras el actual sigue corriendo. Se
    // editan comisiones y horarios, pero no se toma asistencia ni se generan ausencias.
    PREPARACION("En preparación"),

    // El ciclo en curso. Es el único contra el que se registra asistencia.
    ACTIVO("Activo"),

    // Terminó. La estructura queda congelada --comisiones, horarios, períodos-- pero las
    // asistencias se pueden seguir corrigiendo: una inspección o un reclamo llegan casi
    // siempre después de cerrado el año, y no poder justificar una ausencia de marzo en
    // febrero del año siguiente convertiría el cierre en una trampa.
    CERRADO("Cerrado");

    private final String etiqueta;

    EstadoCiclo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    // Nombre para mostrar en pantalla.
    public String getEtiqueta() {
        return etiqueta;
    }

    /** Si la estructura académica de este ciclo se puede seguir tocando. */
    public boolean admiteCambiosDeEstructura() {
        return this != CERRADO;
    }
}
