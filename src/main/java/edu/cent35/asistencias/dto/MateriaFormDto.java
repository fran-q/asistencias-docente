package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Materia;
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
 * Datos que viajan entre el formulario de la materia y el controlador. Lleva las anotaciones de
 * validación, así que los errores se detectan antes de llegar al service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MateriaFormDto {

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

    @NotNull(message = "Hay que elegir una carrera")
    private Long carreraId;

    // El tope real lo pone la duracion de la carrera elegida; se valida en el service,
    // porque aca todavia no sabemos cual es.
    @NotNull(message = "Hay que indicar de qué año es la materia")
    @Min(value = 1, message = "El año tiene que ser 1 o mayor")
    @Max(value = 10, message = "El año no puede pasar de 10")
    private Short anio;

    // Opcional: id del docente titular (puede ser null).
    private Long docenteTitularId;

    // Precarga el formulario con los datos actuales de la entidad, para el modo edición.
    public static MateriaFormDto from(Materia m) {
        return MateriaFormDto.builder()
            .codigo(m.getCodigo())
            .nombre(m.getNombre())
            .carreraId(m.getCarrera().getId())
            .anio(m.getAnio())
            .docenteTitularId(m.getDocenteTitular() != null ? m.getDocenteTitular().getId() : null)
            .build();
    }
}
