package edu.cent35.asistencias.usuario.web;

import edu.cent35.asistencias.usuario.domain.RolCodigo;
import edu.cent35.asistencias.usuario.domain.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form para edicion de un usuario existente. NO incluye username
 * (inmutable) ni password (se cambia con un flujo aparte).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioEditFormDto {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email debe ser valido")
    @Size(max = 120)
    private String email;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 80)
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 80)
    private String apellido;

    @NotNull(message = "Hay que asignar un rol")
    private RolCodigo rol;

    @NotNull
    private Boolean activo;

    public static UsuarioEditFormDto from(Usuario u) {
        return UsuarioEditFormDto.builder()
            .email(u.getEmail())
            .nombre(u.getNombre())
            .apellido(u.getApellido())
            .rol(RolCodigo.valueOf(u.getRol().getCodigo()))
            .activo(Boolean.TRUE.equals(u.getActivo()))
            .build();
    }
}
