package edu.cent35.asistencias.institucion.web;

import edu.cent35.asistencias.institucion.domain.Institucion;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para el formulario de edicion de la institucion propia.
 * <p>
 * Solo expone los campos que el usuario INSTITUCION puede editar.
 * No incluye {@code id}, {@code activo}, ni timestamps - esos los
 * mantiene el sistema.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstitucionFormDto {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 150, message = "El nombre debe tener entre 3 y 150 caracteres")
    private String nombre;

    @Pattern(
        regexp = "^$|^\\d{2}-\\d{8}-\\d{1}$",
        message = "El CUIT debe tener el formato XX-XXXXXXXX-X (ej: 30-12345678-9)"
    )
    @Size(max = 13, message = "El CUIT no puede superar 13 caracteres")
    private String cuit;

    @Size(max = 200, message = "La direccion no puede superar 200 caracteres")
    private String direccion;

    @Email(message = "El email de contacto debe ser valido")
    @Size(max = 120, message = "El email no puede superar 120 caracteres")
    private String emailContacto;

    @Size(max = 30, message = "El telefono no puede superar 30 caracteres")
    private String telefonoContacto;

    /**
     * Construye el DTO a partir de una entidad para pre-rellenar el form.
     */
    public static InstitucionFormDto from(Institucion entidad) {
        return InstitucionFormDto.builder()
            .nombre(entidad.getNombre())
            .cuit(entidad.getCuit())
            .direccion(entidad.getDireccion())
            .emailContacto(entidad.getEmailContacto())
            .telefonoContacto(entidad.getTelefonoContacto())
            .build();
    }
}
