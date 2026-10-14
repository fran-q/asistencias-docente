package edu.cent35.asistencias.model;

/**
 * Estado actual del consentimiento biométrico de un docente.
 * <p>
 * Se calcula a partir del registro más reciente en
 * {@code consentimientos_biometricos} para ese docente.
 */
public enum EstadoConsentimiento {

    // El docente nunca otorgó consentimiento (no hay filas para él).
    NUNCA_OTORGADO,

    // Hay un consentimiento vigente: fecha_revocacion IS NULL.
    ACTIVO,

    // El consentimiento más reciente fue revocado por el docente o el admin.
    REVOCADO
}
