package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.validacion.CuitValido;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    // Mismas dos formas que en el alta de institución, por la misma razón.
    @Pattern(
        regexp = "^$|^\\d{11}$|^\\d{2}-\\d{8}-\\d{1}$",
        message = "El CUIT tiene que ser 11 dígitos: 30-12345678-1 o 30123456781"
    )
    @CuitValido
    @Size(max = 13, message = "El CUIT no puede superar 13 caracteres")
    private String cuit;

    @Size(max = 200, message = "La direccion no puede superar 200 caracteres")
    private String direccion;

    @Email(message = "El email de contacto debe ser valido")
    @Size(max = 120, message = "El email no puede superar 120 caracteres")
    private String emailContacto;

    // Mismo criterio que el teléfono del docente: permisivo con el formato, pero sin letras.
    @Size(max = 30, message = "El telefono no puede superar 30 caracteres")
    @Pattern(regexp = "^$|^[0-9+()\\s-]{6,30}$",
             message = "El teléfono solo admite números, espacios y los signos + ( ) -")
    private String telefonoContacto;

    /**
     * Minutos de hueco entre clases que las mantienen en el mismo bloque de presencia (RF-76).
     * Los límites son los mismos que el CHECK {@code ck_instituciones_umbral_separacion} de
     * V019: si acá se aflojaran, el guardado fallaría con un error de integridad en vez de un
     * mensaje que la persona pueda leer y corregir.
     */
    @NotNull(message = "El umbral de separación es obligatorio")
    @Min(value = 0, message = "El umbral no puede ser negativo")
    @Max(value = 240, message = "El umbral no puede superar los 240 minutos")
    private Short umbralSeparacionMin;

    // Precarga el formulario con los datos actuales de la institución.
    public static InstitucionFormDto from(Institucion entidad) {
        return InstitucionFormDto.builder()
            .nombre(entidad.getNombre())
            .cuit(entidad.getCuit())
            .direccion(entidad.getDireccion())
            .emailContacto(entidad.getEmailContacto())
            .telefonoContacto(entidad.getTelefonoContacto())
            .umbralSeparacionMin(entidad.getUmbralSeparacionMin())
            .build();
    }
}
