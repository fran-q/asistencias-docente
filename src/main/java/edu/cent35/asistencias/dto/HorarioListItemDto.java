package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.DiaSemana;
import edu.cent35.asistencias.model.Horario;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalTime;

@Value
@Builder
public class HorarioListItemDto {
    Long id;
    Byte diaSemanaNum;
    String diaEtiqueta;
    LocalTime horaInicio;
    LocalTime horaFin;
    Short toleranciaMin;
    LocalDate vigenteDesde;
    LocalDate vigenteHasta;
    Long comisionId;
    String comisionCodigo;
    String materiaCodigo;
    String materiaNombre;
    String carreraCodigo;
    boolean activo;
    boolean comisionActiva;

    public static HorarioListItemDto from(Horario h) {
        DiaSemana dia = h.getDia();
        return HorarioListItemDto.builder()
            .id(h.getId())
            .diaSemanaNum(h.getDiaSemana())
            .diaEtiqueta(dia != null ? dia.getEtiqueta() : "—")
            .horaInicio(h.getHoraInicio())
            .horaFin(h.getHoraFin())
            .toleranciaMin(h.getToleranciaMin())
            .vigenteDesde(h.getVigenteDesde())
            .vigenteHasta(h.getVigenteHasta())
            .comisionId(h.getComision().getId())
            .comisionCodigo(h.getComision().getCodigo())
            .materiaCodigo(h.getComision().getMateria().getCodigo())
            .materiaNombre(h.getComision().getMateria().getNombre())
            .carreraCodigo(h.getComision().getMateria().getCarrera() != null
                           ? h.getComision().getMateria().getCarrera().getCodigo() : null)
            .activo(Boolean.TRUE.equals(h.getActivo()))
            .comisionActiva(Boolean.TRUE.equals(h.getComision().getActivo()))
            .build();
    }
}
