package edu.cent35.asistencias.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Comprueba que el CUIT sea real, no solo que tenga la forma correcta. Se resuelve como una
 * anotación y no dentro del servicio para que el error salga marcado en el propio campo, junto
 * al resto de las validaciones del formulario.
 */
@Documented
@Constraint(validatedBy = CuitValidoValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface CuitValido {

    String message() default "El CUIT no es válido: revisá el último dígito";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
