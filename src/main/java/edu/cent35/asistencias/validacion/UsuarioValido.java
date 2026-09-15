package edu.cent35.asistencias.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * La regla del nombre de usuario, en un solo lugar.
 *
 * <p><b>Por qué existe.</b> Estaba escrita dos veces —el alta de institución y la creación de
 * usuarios— con el mismo patrón y dos mensajes distintos, y pedía 3 <i>caracteres</i> de
 * cualquier tipo: {@code ...} o {@code 1-2} eran usuarios válidos. El usuario es lo que alguien
 * teclea en cada ingreso y lo que queda en el historial de lo que hizo: tiene que poder leerse.
 *
 * <p><b>La regla.</b> Hasta 60 caracteres, solo letras sin tilde ni eñe, números, punto, guion
 * y guion bajo, y al menos 3 letras. Sin {@code @} a propósito: es lo que permite distinguir, en
 * un mismo campo, un usuario de un correo.
 *
 * <p><b>Rige para los usuarios que se escriben de nuevo:</b> el alta y el cambio que cada
 * persona hace del suyo desde Mi cuenta. Las cuentas anteriores siguen entrando como están —el
 * ingreso no valida el formato— y pueden guardar sus otros datos sin tocar el usuario viejo.
 */
@Documented
@Constraint(validatedBy = UsuarioValidoValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface UsuarioValido {

    /** Mínimo de letras. Tres caracteres cualesquiera dejaban pasar "..." o "1-2". */
    int MIN_LETRAS = 3;

    /** Máximo de caracteres: el largo de la columna. */
    int MAX = 60;

    /**
     * La misma regla para el atributo {@code pattern} del navegador, que la ancla solo. El guion
     * va escapado: los navegadores compilan el patrón con la bandera {@code v}, y ahí un guion
     * suelto entre corchetes invalida la expresión entera, que entonces se ignora sin avisar.
     */
    String PATRON_HTML = "(?=(?:[^A-Za-z]*[A-Za-z]){3})[A-Za-z0-9._\\-]{3,60}";

    /** Texto para debajo del campo. */
    String AYUDA = "Al menos 3 letras. También números, punto, guion y guion bajo; sin tildes, eñes ni @.";

    /** Lo que dice el navegador antes de enviar, si el patrón no se cumple. */
    String AVISO = "Tiene que tener al menos 3 letras, y solo letras sin tilde, números, punto, guion "
                 + "y guion bajo.";

    String message() default "El usuario no cumple los requisitos";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
