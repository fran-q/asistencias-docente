package edu.cent35.asistencias.model.persona;

/**
 * Por dónde entró un cambio de identidad (ADR-0016). Importa porque el mismo cambio se explica
 * distinto según el camino.
 * <p>
 * Coincide con el CHECK {@code ck_cambios_origen} de V017.
 */
public enum OrigenCambioIdentidad {

    // La ficha del docente.
    DOCENTE,

    // La cuenta: la edita un administrador o la propia persona, y eso lo dice usuario_id.
    USUARIO,

    // Un alta que cayó sobre una persona que ya existía, y se confirmó con datos distintos.
    REINGRESO
}
