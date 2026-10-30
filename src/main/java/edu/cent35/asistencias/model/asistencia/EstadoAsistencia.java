package edu.cent35.asistencias.model.asistencia;

/**
 * Cómo llegó el docente a una clase (RF-19 a RF-21).
 * <p>
 * Coincide con el CHECK {@code ck_asistencias_estado} de V001, que admite los tres. Las
 * ausencias se graban: las genera el job al terminar una clase sin marca (RF-19) o las carga un
 * admin. Además, el listado del día calcula al vuelo la de una clase terminada que el job
 * todavía no procesó, sin grabarla.
 */
public enum EstadoAsistencia {

    // Llegó dentro de la tolerancia del horario, contada hacia los dos lados del inicio
    // (ADR-0018).
    PRESENTE("Presente"),

    // Llegó pasada esa tolerancia (se registra la hora exacta).
    TARDE("Tarde"),

    // No vino: la generó el job, la cargó un admin, o el listado la calcula al vuelo.
    AUSENTE("Ausente");

    private final String etiqueta;

    EstadoAsistencia(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    // Nombre para mostrar en pantalla y en el PDF. El CSV lleva el valor, que es el dato crudo.
    public String getEtiqueta() {
        return etiqueta;
    }
}
