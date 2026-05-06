package edu.cent35.asistencias.academico.web;

import edu.cent35.asistencias.academico.domain.Comision;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class ComisionFormDto {

    @NotBlank(message = "El código de la comisión es obligatorio")
    @Size(min = 1, max = 30, message = "El código debe tener entre 1 y 30 caracteres")
    @Pattern(
        regexp = "^[a-zA-Z0-9 ._-]+$",
        message = "El código solo puede contener letras, números, espacios, puntos, guiones y guion bajo"
    )
    private String codigo;

    @NotNull(message = "Hay que elegir una materia")
    private Long materiaId;

    @Min(value = 1, message = "El cupo debe ser un número positivo")
    private Integer cupo;

    public static ComisionFormDto from(Comision c) {
        return ComisionFormDto.builder()
            .codigo(c.getCodigo())
            .materiaId(c.getMateria().getId())
            .cupo(c.getCupo())
            .build();
    }
}
