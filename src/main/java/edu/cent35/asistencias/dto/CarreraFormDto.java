package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Carrera;
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
 * Datos que viajan entre el formulario de la carrera y el controlador. Lleva las anotaciones de
 * validación, así que los errores se detectan antes de llegar al service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarreraFormDto {

    @NotBlank(message = "El código es obligatorio")
    @Size(min = 1, max = 30, message = "El código debe tener entre 1 y 30 caracteres")
    @Pattern(
        regexp = "^[a-zA-Z0-9._-]+$",
        message = "El código solo puede contener letras, números, puntos, guiones y guion bajo (sin espacios)"
    )
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 150, message = "El nombre debe tener entre 3 y 150 caracteres")
    private String nombre;

    @NotNull(message = "Hay que indicar cuántos años dura la carrera")
    @Min(value = 1, message = "La carrera tiene que durar al menos un año")
    @Max(value = 10, message = "La duración no puede pasar de 10 años")
    private Short duracionAnios;

    // Precarga el formulario con los datos actuales de la entidad, para el modo edición.
    public static CarreraFormDto from(Carrera c) {
        return CarreraFormDto.builder()
            .codigo(c.getCodigo())
            .nombre(c.getNombre())
            .duracionAnios(c.getDuracionAnios())
            .build();
    }
}
