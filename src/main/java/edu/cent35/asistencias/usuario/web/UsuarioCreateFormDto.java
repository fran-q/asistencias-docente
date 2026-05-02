package edu.cent35.asistencias.usuario.web;

import edu.cent35.asistencias.usuario.domain.RolCodigo;
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

    @NotBlank(message = "La contraseña es obligatoria al crear")
    @Size(min = 6, max = 60, message = "La contraseña debe tener entre 6 y 60 caracteres")
    private String password;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 80)
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 80)
    private String apellido;

    @NotNull(message = "Hay que asignar un rol")
    private RolCodigo rol;
}
