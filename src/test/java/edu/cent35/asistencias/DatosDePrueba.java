package edu.cent35.asistencias;

import edu.cent35.asistencias.model.Persona;

/**
 * Fábricas de datos para los tests, para no repetir el armado de una {@code Persona} en cada uno.
 * Existe desde ADR-0016: al separarse la identidad del vínculo, construir un docente o un usuario
 * pasó a ser dos objetos en vez de uno, y sin esto cada test cargaba con ese detalle.
 */
public final class DatosDePrueba {

    private DatosDePrueba() {
    }

    // Persona con documento, para los tests que arman docentes.
    public static Persona personaConDni(String dni, String nombre, String apellido) {
        return Persona.builder()
            .dni(dni)
            .nombre(nombre)
            .apellido(apellido)
            .build();
    }

    // Persona sin documento, para los tests que arman cuentas de acceso: el alta de usuario no
    // pide DNI, así que en la práctica esas personas nacen sin él.
    public static Persona persona(String nombre, String apellido) {
        return Persona.builder()
            .nombre(nombre)
            .apellido(apellido)
            .build();
    }

    // Persona de un solo nombre, como las cuentas institucionales, que no son una persona física.
    public static Persona persona(String nombre) {
        return persona(nombre, null);
    }

    // Igual que personaConDni pero fijando la institución, para los tests de integración que
    // persisten de verdad y necesitan que la fila quede en el tenant correcto.
    public static Persona personaDelTenant(Long institucionId, String dni,
                                           String nombre, String apellido) {
        Persona p = personaConDni(dni, nombre, apellido);
        p.setInstitucionId(institucionId);
        return p;
    }
}
