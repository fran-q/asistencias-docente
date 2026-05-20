package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Comision;
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
    @Size(min = 1, max = 8, message = "El código debe tener entre 1 y 8 caracteres")
    @Pattern(
        // A diferencia de los codigos de Carrera/Materia (identificadores tipo "MAT-101"),
        // el codigo de comision suele ser texto: "Mañana", "Noche", "Atención". Por eso
        // aceptamos letras acentuadas, ñ/Ñ y diéresis (ü/Ü). Si en el futuro se quiere
        // unificar con Unicode general, evaluar @Pattern.Flag.UNICODE_CHARACTER_CLASS.
        regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ0-9 ._-]+$",
        message = "El código solo puede contener letras, números, espacios, puntos, guiones y guion bajo"
    )
    private String codigo;

    @NotNull(message = "Hay que elegir una materia")
    private Long materiaId;

    @Min(value = 1, message = "El cupo debe ser un número positivo")
    private Integer cupo;

    /** Opcional: docente asignado a la comisión. Null = sin asignar. */
    private Long docenteAsignadoId;

    public static ComisionFormDto from(Comision c) {
        return ComisionFormDto.builder()
            .codigo(c.getCodigo())
            .materiaId(c.getMateria().getId())
            .cupo(c.getCupo())
            .docenteAsignadoId(c.getDocenteAsignado() != null ? c.getDocenteAsignado().getId() : null)
            .build();
    }
}
