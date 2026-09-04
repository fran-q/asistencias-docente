package edu.cent35.asistencias.dto;

import lombok.Builder;
import lombok.Value;

/**
 * A quién y a qué alcanza una operación sobre una identidad, para poder avisarlo antes de
 * ejecutarla (ADR-0016).
 * Existe porque una persona puede tener cuenta de administrador y vínculo docente a la vez: sin
 * este aviso, editar un nombre desde la pantalla de docentes cambia también el de la cuenta, y
 * un DNI mal tipeado en un alta le reescribe la identidad a otra persona sin que nadie lo note.
 */
@Value
@Builder
public class ImpactoIdentidadDto {

    /** Por qué hace falta confirmar. */
    public enum Motivo {
        /** El DNI ingresado ya pertenece a alguien: o es un reingreso, o está mal tipeado. */
        ALTA_SOBRE_PERSONA_EXISTENTE,
        /** La persona que se está editando tiene más de un rol en la institución. */
        EDICION_ALCANZA_VARIOS_ROLES
    }

    Motivo motivo;

    Long personaId;
    String dni;

    /** Cómo figura hoy en el sistema. */
    String nombreRegistrado;

    /** Cómo quedaría si se confirma. Igual al anterior cuando el alta no cambia los datos. */
    String nombrePropuesto;

    /** Tiene cuenta de acceso al sistema. */
    boolean tieneCuenta;
    String usernameCuenta;

    /** Tiene un vínculo docente abierto. */
    boolean tieneVinculoVigente;

    /** Cuántos períodos docentes acumula, vigentes o cerrados. */
    int periodosDocentes;

    // true si la persona cumple los dos roles: es lo que hay que advertir con más énfasis,
    // porque el cambio se ve en pantallas que quien edita no está mirando.
    public boolean isAlcanzaVariosRoles() {
        return tieneCuenta && periodosDocentes > 0;
    }

    // true si los datos del formulario difieren de los guardados. Cuando no difieren, confirmar
    // es inofensivo y el aviso puede ser más liviano.
    public boolean isCambiaLosDatos() {
        return nombrePropuesto != null && !nombrePropuesto.equals(nombreRegistrado);
    }
}
