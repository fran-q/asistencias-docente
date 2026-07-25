package edu.cent35.asistencias.dto;

import edu.cent35.asistencias.model.EstadoAsistencia;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Datos que viajan entre el formulario de la asistencia y el controlador. Lleva las anotaciones de
 * validación, así que los errores se detectan antes de llegar al service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsistenciaManualFormDto {

    @NotNull(message = "Elegí un docente")
    private Long docenteId;

    @NotNull(message = "Elegí un horario")
    private Long horarioId;

    @NotNull(message = "Indicá la fecha")
    @PastOrPresent(message = "La fecha no puede ser futura")
    private LocalDate fecha;

    @NotNull(message = "Indicá la hora")
    private LocalTime horaRegistrada;

    @NotNull(message = "Elegí el estado a registrar")
    private EstadoAsistencia estado;

    @NotNull(message = "Elegí un motivo")
    private Short motivoId;

    @Size(max = 2000, message = "El detalle no puede superar los 2000 caracteres")
    private String detalleAdicional;
}
