package edu.cent35.asistencias.dto;

import edu.cent35.asistencias.validacion.PasswordSegura;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Restablecer la contraseña de la cuenta institucional con la clave de recuperación que entregó
 * la configuración inicial (ADR-0022, decisión 5).
 *
 * <p>Es el camino de una instalación autónoma, donde no hay correo por el cual mandar un código.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecuperacionPorClaveFormDto {

    @NotBlank(message = "Ingresá el usuario de la institución")
    @Size(max = 60, message = "El usuario no puede superar los 60 caracteres")
    private String username;

    // Sin @Pattern: la clave se acepta como la haya copiado la persona --con guiones, con
    // espacios, en minuscula-- y el servicio la normaliza antes de comparar. Un formato exigido
    // acá rechazaría una clave correcta por cómo quedó pegada desde un papel.
    @NotBlank(message = "Ingresá la clave de recuperación")
    @Size(max = 60, message = "La clave de recuperación es más corta que eso")
    private String clave;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @PasswordSegura
    private String nuevaPassword;

    @NotBlank(message = "Repetí la contraseña")
    private String confirmacion;

    // Indica si la contraseña y su repetición son iguales.
    public boolean coincide() {
        return nuevaPassword != null && nuevaPassword.equals(confirmacion);
    }
}
