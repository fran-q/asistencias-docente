package edu.cent35.asistencias.model;

/**
 * Estado de una marca de asistencia (RF-19 a RF-21).
 * <p>
 * Coincide con el CHECK constraint {@code ck_asistencias_estado} de V001:
 * sólo se persisten {@link #PRESENTE} y {@link #TARDE}. {@link #AUSENTE}
 * se usa como estado <i>calculado</i> al listar horarios sin marca; rara
 * vez se inserta (sólo si el admin lo carga manualmente).
 */
public enum EstadoAsistencia {

    // Llegó dentro de la tolerancia del horario, contada hacia los dos lados del inicio
    // (ADR-0018).
    PRESENTE,

    // Llegó pasada esa tolerancia (se registra la hora exacta).
    TARDE,

    // No marcó dentro del horario. Se calcula al listar o lo carga el admin manualmente.
    AUSENTE
}
