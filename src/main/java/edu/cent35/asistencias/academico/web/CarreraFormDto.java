package edu.cent35.asistencias.academico.web;

import edu.cent35.asistencias.academico.domain.Carrera;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    public static CarreraFormDto from(Carrera c) {
        return CarreraFormDto.builder()
            .codigo(c.getCodigo())
            .nombre(c.getNombre())
            .build();
    }
}
