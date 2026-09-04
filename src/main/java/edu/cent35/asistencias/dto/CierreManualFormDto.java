package edu.cent35.asistencias.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalTime;

/**
 * Datos del formulario de cierre manual de un bloque de presencia (RF-83). Lleva las
 * anotaciones de validación para que lo que se puede detectar en el formulario no llegue al
 * service.
 * <p>
 * Lo que <b>no</b> se valida acá es que el detalle sea obligatorio cuando el motivo es "Otro":
 * eso depende del código del motivo, que vive en la base, así que lo resuelve el service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CierreManualFormDto {

    @NotNull(message = "Indicá a qué hora se retiró el docente")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime horaSalida;

    @NotNull(message = "Elegí por qué hay que cerrarlo a mano")
    private Short motivoId;

    @Size(max = 2000, message = "El detalle no puede superar los 2000 caracteres")
    private String detalle;
}
