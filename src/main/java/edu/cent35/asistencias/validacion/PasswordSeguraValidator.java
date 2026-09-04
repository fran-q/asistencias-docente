package edu.cent35.asistencias.validacion;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Comprueba {@link PasswordSegura} y, sobre todo, <b>dice qué falta</b>.
 *
 * <p>Un mensaje genérico —"la contraseña no cumple los requisitos"— obliga a adivinar cuál de
 * las cinco condiciones se incumplió, y quien está creando una cuenta prueba variantes a
 * ciegas hasta que entra. Por eso el validador arma el texto con lo que efectivamente falta:
 * "Le falta una mayúscula y un número".
 *
 * <p>No revela nada sensible: son las reglas, que además están escritas debajo del campo.
 */
public class PasswordSeguraValidator implements ConstraintValidator<PasswordSegura, String> {

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        // El campo vacío lo reporta @NotBlank. Si acá también se quejara, la persona vería
        // dos errores por el mismo hueco.
        if (valor == null || valor.isEmpty()) {
            return true;
        }

        List<String> faltantes = new ArrayList<>();
        if (valor.chars().noneMatch(Character::isLowerCase)) faltantes.add("una minúscula");
        if (valor.chars().noneMatch(Character::isUpperCase)) faltantes.add("una mayúscula");
        if (valor.chars().noneMatch(Character::isDigit))     faltantes.add("un número");

        boolean largoOk = valor.length() >= PasswordSegura.MIN
                       && valor.length() <= PasswordSegura.MAX;

        if (largoOk && faltantes.isEmpty()) {
            return true;
        }

        contexto.disableDefaultConstraintViolation();
        contexto.buildConstraintViolationWithTemplate(explicar(valor, largoOk, faltantes))
                .addConstraintViolation();
        return false;
    }

    // Arma el mensaje con lo que realmente falta, en castellano y sin jerga.
    private String explicar(String valor, boolean largoOk, List<String> faltantes) {
        StringBuilder sb = new StringBuilder();

        if (!largoOk) {
            sb.append(valor.length() < PasswordSegura.MIN
                ? "Es muy corta: tiene que tener al menos " + PasswordSegura.MIN + " caracteres"
                : "Es muy larga: el máximo son " + PasswordSegura.MAX + " caracteres");
        }

        if (!faltantes.isEmpty()) {
            sb.append(sb.length() > 0 ? ", y le falta " : "Le falta ");
            for (int i = 0; i < faltantes.size(); i++) {
                if (i > 0) sb.append(i == faltantes.size() - 1 ? " y " : ", ");
                sb.append(faltantes.get(i));
            }
        }

        return sb.append('.').toString();
    }
}
