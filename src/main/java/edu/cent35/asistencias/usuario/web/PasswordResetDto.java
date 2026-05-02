package edu.cent35.asistencias.usuario.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form para resetear la contrasena de un usuario.
 * El superadmin no necesita conocer la actual - simplemente la sobrescribe.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetDto {

    @NotBlank(message = "La nueva contrasena es obligatoria")
    @Size(min = 6, max = 60, message = "La contrasena debe tener entre 6 y 60 caracteres")
    private String nuevaPassword;

    @NotBlank(message = "Repeti la contrasena")
    private String confirmacion;

    public boolean coincide() {
        return nuevaPassword != null && nuevaPassword.equals(confirmacion);
    }
}
