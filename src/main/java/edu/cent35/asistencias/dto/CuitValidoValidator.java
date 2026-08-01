package edu.cent35.asistencias.dto;

import edu.cent35.asistencias.model.Cuit;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Aplica la comprobación del dígito verificador a un campo anotado con {@link CuitValido}.
 * Delega en {@code Cuit} para que la regla viva en un solo lugar y valga igual desde el
 * formulario que desde cualquier otro punto que necesite verificar un CUIT.
 */
public class CuitValidoValidator implements ConstraintValidator<CuitValido, String> {

    // Un campo vacio se da por valido: si el CUIT es obligatorio lo dice @NotBlank, no esto.
    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        return Cuit.esValido(valor);
    }
}
