package edu.cent35.asistencias.dto;

import edu.cent35.asistencias.validacion.PasswordSegura;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * La contraseña nueva, pedida dos veces.
 *
 * <p>Es el <b>segundo</b> paso del cambio: para llegar hasta acá hay que haber validado el
 * código que llegó al correo de la cuenta. Por eso ya no incluye la contraseña actual —el
 * código es una prueba más fuerte que ella— ni el código en sí, que se consumió antes.
 *
 * <p>Se pide dos veces porque el campo va enmascarado: quien la escribe no la ve, y un error
 * de tipeo se descubriría recién en el próximo inicio de sesión, cuando ya no hay forma de
 * saber qué se escribió.
 */
@Data
public class CambioPasswordDto {

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @PasswordSegura
    private String nuevaPassword;

    @NotBlank(message = "Repetí la contraseña nueva")
    private String confirmacion;

    /** Si las dos coinciden. */
    public boolean coincide() {
        return nuevaPassword != null && nuevaPassword.equals(confirmacion);
    }
}
