package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.RolCodigo;
import edu.cent35.asistencias.model.Usuario;
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

    // Opcional: las cuentas de institución no son personas y no llevan apellido.
    @Size(max = 80)
    private String apellido;

    @NotNull
    private Boolean activo;

    /**
     * Precarga el formulario con los datos actuales de la cuenta.
     *
     * <p><b>Las cuentas de institución traen su nombre completo en un solo campo.</b> La
     * pantalla les muestra un único "Nombre de la institución", así que si el apellido se
     * cargara aparte quedaría fuera de la vista y el primer guardado lo borraría sin que nadie
     * lo note: una cuenta cargada como "Dirección" / "UTN FRTDF" pasaría a llamarse
     * "Dirección" a secas.
     *
     * <p>Juntándolo acá, lo que se ve es lo que se guarda. Y a partir de entonces el nombre
     * vive entero en una sola columna, que es como debería haber estado desde el principio.
     */
    public static UsuarioEditFormDto from(Usuario u) {
        boolean esInstitucion = u.getRol() != null
            && RolCodigo.INSTITUCION.name().equals(u.getRol().getCodigo());

        return UsuarioEditFormDto.builder()
            .email(u.getEmail())
            .nombre(u.esCuentaInstitucional() ? u.getNombreParaMostrar() : u.getPersona().getNombre())
            .apellido(u.esCuentaInstitucional() ? null : u.getPersona().getApellido())
            .activo(Boolean.TRUE.equals(u.getActivo()))
            .build();
    }

    // Viene en true solo cuando el pedido vuelve desde la pantalla de aviso, ya confirmado por
    // alguien que vio a quien alcanza el cambio. Nunca lo tipea un usuario.
    private boolean confirmado;

}
