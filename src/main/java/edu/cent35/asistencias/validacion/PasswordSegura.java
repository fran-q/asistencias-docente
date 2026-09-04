package edu.cent35.asistencias.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * La política de contraseñas del sistema, en un solo lugar.
 *
 * <p><b>Por qué existe.</b> La regla estaba escrita cuatro veces, con tres valores distintos:
 * el alta de institución pedía entre 6 y 60 caracteres, la creación de usuarios lo mismo, la
 * recuperación también, y el cambio desde "Mi cuenta" pedía 10 como mínimo. La misma persona
 * veía un requisito al crear la cuenta y otro al actualizarla, sin ninguna razón. Peor: el
 * formulario del cambio mostraba "al menos 10" y rechazaba con un mensaje que hablaba de otra
 * cosa.
 *
 * <p>Repetir una regla es garantizar que se desincronice. Ahora vive acá, y cualquier campo de
 * contraseña la toma anotándose con {@code @PasswordSegura}: si mañana cambia, cambia en un
 * archivo y en todas las pantallas a la vez.
 *
 * <p><b>La regla.</b> Entre 6 y 20 caracteres, con al menos una minúscula, una mayúscula y un
 * número. El mínimo de 6 es el piso razonable para un sistema con tope de intentos; el máximo
 * de 20 evita que alguien pegue un texto entero por accidente. Las tres clases de carácter
 * obligan a salir de las contraseñas que se adivinan solas.
 *
 * <p><b>No exige símbolos.</b> Agregan poco frente a las tres clases ya pedidas y en cambio
 * generan la contraseña anotada en un papel al lado del teclado, que es un problema peor.
 */
@Documented
@Constraint(validatedBy = PasswordSeguraValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordSegura {

    /** Mínimo de caracteres. */
    int MIN = 6;

    /** Máximo de caracteres. */
    int MAX = 20;

    /** Texto para las pantallas, para no repetirlo en cada plantilla. */
    String AYUDA = "Entre 6 y 20 caracteres, con al menos una minúscula, una mayúscula y un número.";

    String message() default "La contraseña no cumple los requisitos";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
