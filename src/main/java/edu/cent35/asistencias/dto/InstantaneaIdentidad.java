package edu.cent35.asistencias.dto;

import edu.cent35.asistencias.model.Persona;

/**
 * Los datos de identidad de una persona en un momento dado, para poder compararlos después del
 * cambio y registrar solo lo que efectivamente se modificó (ADR-0016).
 * Es un record y no la entidad a propósito: la entidad es mutable y se edita en el lugar, así
 * que guardarse una referencia a ella para "el antes" devolvería el estado nuevo.
 */
public record InstantaneaIdentidad(
    String dni,
    String nombre,
    String apellido,
    String email,
    String telefono
) {

    // Copia el estado actual de la persona. Hay que llamarlo ANTES de tocarla.
    public static InstantaneaIdentidad de(Persona p) {
        return new InstantaneaIdentidad(
            p.getDni(), p.getNombre(), p.getApellido(), p.getEmail(), p.getTelefono());
    }
}
