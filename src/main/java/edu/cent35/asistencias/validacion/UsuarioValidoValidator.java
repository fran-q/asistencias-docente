package edu.cent35.asistencias.validacion;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Comprueba {@link UsuarioValido} y dice qué falla, de a una cosa.
 *
 * <p>Un usuario es una palabra: cuando no sirve, casi siempre es por una sola razón —una tilde,
 * un {@code @} de más, pocas letras— y el mensaje nombra esa. Tres avisos juntos sobre un campo
 * de una palabra se leen como un reto.
 */
public class UsuarioValidoValidator implements ConstraintValidator<UsuarioValido, String> {

    private static final Pattern PERMITIDOS = Pattern.compile("[A-Za-z0-9._-]+");

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        // El campo vacío lo reporta @NotBlank. Si acá también se quejara, la persona vería
        // dos errores por el mismo hueco.
        if (valor == null || valor.isEmpty()) {
            return true;
        }
        String problema = problema(valor);
        if (problema == null) {
            return true;
        }
        contexto.disableDefaultConstraintViolation();
        contexto.buildConstraintViolationWithTemplate(problema).addConstraintViolation();
        return false;
    }

    // Lo primero que falla, en castellano; null si el usuario sirve. Es publico porque Mi cuenta
    // pide la regla solo cuando el usuario cambia, y eso no se puede decir con una anotacion.
    public static String problema(String valor) {
        if (valor.length() > UsuarioValido.MAX) {
            return "Es muy largo: el máximo son " + UsuarioValido.MAX + " caracteres.";
        }
        if (valor.indexOf('@') >= 0) {
            return "No puede llevar @: el usuario no es el correo.";
        }
        if (!PERMITIDOS.matcher(valor).matches()) {
            return "Solo admite letras sin tilde ni eñe, números, punto, guion y guion bajo.";
        }
        long letras = valor.chars()
            .filter(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))
            .count();
        if (letras < UsuarioValido.MIN_LETRAS) {
            return "Tiene que tener al menos " + UsuarioValido.MIN_LETRAS + " letras"
                + (letras == 0 ? "." : ", y tiene " + letras + ".");
        }
        return null;
    }
}
