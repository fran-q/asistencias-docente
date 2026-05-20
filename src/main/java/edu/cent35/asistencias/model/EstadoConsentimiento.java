package edu.cent35.asistencias.model;

/**
 * Estado actual del consentimiento biometrico de un docente.
 * <p>
 * Se calcula a partir del registro mas reciente en
 * {@code consentimientos_biometricos} para ese docente.
 */
public enum EstadoConsentimiento {

    /** El docente nunca otorgo consentimiento (no hay filas para el). */
    NUNCA_OTORGADO,

    /** Hay un consentimiento vigente: {@code fecha_revocacion IS NULL}. */
    ACTIVO,

    /** El consentimiento mas reciente fue revocado por el docente o el admin. */
    REVOCADO
}
