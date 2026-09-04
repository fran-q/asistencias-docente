package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.RolCodigo;
import edu.cent35.asistencias.validacion.PasswordSegura;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form para crear un usuario nuevo en la institucion del tenant actual.
 * Incluye password en claro (se hashea en el service con BCrypt).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioCreateFormDto {

    @NotBlank(message = "El username es obligatorio")
    @Size(min = 3, max = 60, message = "El username debe tener entre 3 y 60 caracteres")
    @Pattern(
        regexp = "^[a-zA-Z0-9._-]+$",
        message = "El username solo puede contener letras, numeros, puntos, guiones y guion bajo"
    )
    private String username;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email debe ser valido")
    @Size(max = 120)
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @PasswordSegura
    private String password;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 80)
    private String nombre;

    // Opcional: una cuenta de institución no tiene apellido. Las que se crean acá son
    // siempre de administrador y sí lo llevan, pero el DTO no es el lugar donde imponerlo.
    @Size(max = 80)
    private String apellido;

    @NotBlank(message = "Repetí la contraseña")
    private String confirmacion;

    /**
     * Si las dos contraseñas coinciden.
     *
     * <p>Se pide dos veces porque quien la escribe no la ve —el campo está enmascarado— y es
     * la contraseña con la que otra persona va a entrar por primera vez. Un error de tipeo no
     * se descubre acá: se descubre cuando esa persona no puede iniciar sesión y no hay forma
     * de saber si el problema es la contraseña o la cuenta.
     */
    public boolean coincide() {
        return password != null && password.equals(confirmacion);
    }
}
