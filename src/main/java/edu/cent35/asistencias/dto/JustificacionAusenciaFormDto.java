package edu.cent35.asistencias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos que viajan entre el formulario de la justificación y el controlador. Lleva las anotaciones de
 * validación, así que los errores se detectan antes de llegar al service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JustificacionAusenciaFormDto {

    @NotBlank(message = "Indicá el motivo de la justificación")
    @Size(max = 2000, message = "El motivo no puede superar los 2000 caracteres")
    private String motivo;

    @Size(max = 255, message = "La URL no puede superar los 255 caracteres")
    private String documentoUrl;
}
