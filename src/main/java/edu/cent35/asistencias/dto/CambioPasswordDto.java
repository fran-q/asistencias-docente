package edu.cent35.asistencias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Cambio de la propia contraseña desde "Mi cuenta".
 *
 * <p>Pide la contraseña actual, a diferencia del reseteo que hace el rol INSTITUCION sobre
 * otra cuenta. El motivo es que acá ya hay una sesión abierta: si alguien se sienta frente a
 * una computadora desatendida, sin este campo podría cambiarle la contraseña al dueño y
 * dejarlo afuera de su propia cuenta. Con él, necesita saber la que ya estaba.
 */
@Data
public class CambioPasswordDto {

    @NotBlank(message = "Ingresá tu contraseña actual")
    private String actual;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 10, message = "La contraseña nueva debe tener al menos 10 caracteres")
    private String nuevaPassword;

    @NotBlank(message = "Repetí la contraseña nueva")
    private String confirmacion;

    // true si la contrasena nueva y su repeticion son iguales.
    public boolean coincide() {
        return nuevaPassword != null && nuevaPassword.equals(confirmacion);
    }

    // true si la nueva es igual a la actual: cambiarla por la misma no cambia nada.
    public boolean esLaMisma() {
        return nuevaPassword != null && nuevaPassword.equals(actual);
    }
}
