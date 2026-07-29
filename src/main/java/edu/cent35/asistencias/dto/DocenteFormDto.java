package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Docente;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos que viajan entre el formulario de el docente y el controlador. Lleva las anotaciones de
 * validación, así que los errores se detectan antes de llegar al service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocenteFormDto {

    @NotBlank(message = "El DNI es obligatorio")
    @Pattern(regexp = "^[0-9]{7,12}$", message = "El DNI debe tener entre 7 y 12 dígitos numéricos")
    @Size(max = 15)
    private String dni;

    @Size(max = 30, message = "El legajo no puede superar 30 caracteres")
    private String legajo;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 80)
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 80)
    private String apellido;

    @Email(message = "El email debe ser válido")
    @Size(max = 120)
    private String email;

    // Patrón permisivo a propósito: acepta prefijos, paréntesis y separadores, pero no letras.
    @Size(max = 30)
    @Pattern(regexp = "^$|^[0-9+()\\s-]{6,30}$",
             message = "El teléfono solo admite números, espacios y los signos + ( ) -")
    private String telefono;

    // La fecha de alta no viaja en el formulario: la fija el sistema al crear el docente y
    // despues no se edita. Ver DocenteService.crear.

    // Precarga el formulario con los datos actuales de la entidad, para el modo edición.
    public static DocenteFormDto from(Docente d) {
        return DocenteFormDto.builder()
            .dni(d.getDni())
            .legajo(d.getLegajo())
            .nombre(d.getNombre())
            .apellido(d.getApellido())
            .email(d.getEmail())
            .telefono(d.getTelefono())
            .build();
    }
}
