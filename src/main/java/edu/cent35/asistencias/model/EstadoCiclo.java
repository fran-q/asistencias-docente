package edu.cent35.asistencias.model;

/**
 * En qué momento de su vida está un ciclo lectivo. Lo que decide es qué se puede editar, no
 * qué se puede leer: un ciclo cerrado se sigue consultando entero desde los reportes.
 */
public enum EstadoCiclo {

    // Se esta armando la oferta del ano que viene mientras el actual sigue corriendo. Se
    // editan comisiones y horarios, pero no se toma asistencia ni se generan ausencias.
    PREPARACION("En preparación"),

    // El ciclo en curso. Es el unico contra el que se registra asistencia.
    ACTIVO("Activo"),

    // Termino. La estructura queda congelada --comisiones, horarios, periodos-- pero las
    // asistencias se pueden seguir corrigiendo: una inspeccion o un reclamo llegan casi
    // siempre despues de cerrado el ano, y no poder justificar una ausencia de marzo en
    // febrero del ano siguiente convertiria el cierre en una trampa.
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
