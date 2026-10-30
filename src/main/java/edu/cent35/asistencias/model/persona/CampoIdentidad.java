package edu.cent35.asistencias.model.persona;

/**
 * Qué dato de la identidad de una persona cambió (ADR-0016).
 * <p>
 * Coincide con el CHECK {@code ck_cambios_campo} (V036). Es el valor con el que queda asentado
 * en {@code cambios_identidad}, y no el nombre de un atributo de {@link Persona}: renombrar uno
 * no puede cambiar lo que dice el historial.
 */
public enum CampoIdentidad {
    DNI,
    NOMBRE,
    APELLIDO,
    EMAIL,
    TELEFONO
}
