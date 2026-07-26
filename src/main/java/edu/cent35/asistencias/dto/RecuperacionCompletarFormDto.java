package edu.cent35.asistencias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form que cierra la recuperación: el código recibido más la contraseña nueva. Mantiene la
 * misma política de largo que el reset que hace el superadmin, para no tener dos reglas.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecuperacionCompletarFormDto {

    @NotBlank(message = "Ingresá el código que te llegó por correo")
    private String codigo;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 6, max = 60, message = "La contraseña debe tener entre 6 y 60 caracteres")
    private String nuevaPassword;

    @NotBlank(message = "Repetí la contraseña")
    private String confirmacion;

    // Indica si la contraseña y su repetición son iguales.
    public boolean coincide() {
        return nuevaPassword != null && nuevaPassword.equals(confirmacion);
    }
}
