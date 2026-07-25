package edu.cent35.asistencias.model;

/**
 * Cómo se generó la marca de asistencia.
 * <p>
 * Coincide con el CHECK {@code ck_asistencias_metodo} de V001.
 */
public enum MetodoAsistencia {

    // El sistema reconoció al docente por reconocimiento facial.
    AUTOMATICO,

    // Un admin cargó la asistencia a mano (con motivo del catálogo).
    MANUAL
}
