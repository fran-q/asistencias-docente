package edu.cent35.asistencias.dto;

import edu.cent35.asistencias.model.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lo que la propia cuenta corrige desde Mi cuenta: el usuario, el nombre y el correo. El usuario
 * no lleva {@code @UsuarioValido} a propósito: la regla se pide solo si cambia
 * (DatosCuentaController), para que una cuenta anterior a ella pueda corregir su nombre sin que
 * la obliguen a cambiar también cómo entra.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuentaPropiaFormDto {

    @NotBlank(message = "El usuario es obligatorio")
    @Size(max = 60, message = "El usuario no puede superar los 60 caracteres")
    private String username;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo debe ser válido")
    @Size(max = 120)
    private String email;

    // Solo en las cuentas de una persona. La de la institución no tiene nombre propio (V018):
    // muestra el del establecimiento, que se cambia desde Mi institución.
    @Size(max = 80)
    private String nombre;

    @Size(max = 80)
    private String apellido;

    // Viene en true solo cuando el pedido vuelve desde la pantalla de aviso, ya confirmado por
    // alguien que vio a quién alcanza el cambio. Nunca lo tipea un usuario.
    private boolean confirmado;

    // Precarga el formulario con los datos actuales de la cuenta.
    public static CuentaPropiaFormDto from(Usuario u) {
        return CuentaPropiaFormDto.builder()
            .username(u.getUsername())
            .email(u.getEmail())
            .nombre(u.getPersona() == null ? null : u.getPersona().getNombre())
            .apellido(u.getPersona() == null ? null : u.getPersona().getApellido())
            .build();
    }
}
