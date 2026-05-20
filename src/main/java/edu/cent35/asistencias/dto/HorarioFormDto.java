package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.DiaSemana;
import edu.cent35.asistencias.model.Horario;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HorarioFormDto {

    @NotNull(message = "Hay que elegir una comisión")
    private Long comisionId;

    @NotNull(message = "Elegí un día de la semana")
    private DiaSemana dia;

    @NotNull(message = "La hora de inicio es obligatoria")
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime horaInicio;

    @NotNull(message = "La hora de fin es obligatoria")
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime horaFin;

    @NotNull(message = "La tolerancia es obligatoria")
    @Min(value = 0, message = "La tolerancia no puede ser negativa")
    @Max(value = 120, message = "La tolerancia no puede superar los 120 minutos")
    private Short toleranciaMin;

    @NotNull(message = "La fecha de inicio de vigencia es obligatoria")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate vigenteDesde;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate vigenteHasta;

    public static HorarioFormDto from(Horario h) {
        return HorarioFormDto.builder()
            .comisionId(h.getComision().getId())
            .dia(h.getDia())
            .horaInicio(h.getHoraInicio())
            .horaFin(h.getHoraFin())
            .toleranciaMin(h.getToleranciaMin())
            .vigenteDesde(h.getVigenteDesde())
            .vigenteHasta(h.getVigenteHasta())
            .build();
    }
}
