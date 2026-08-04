package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.DiaSemana;
import edu.cent35.asistencias.model.Horario;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Fila ya preparada de los horarios para la tabla del listado. Se arma dentro de la transacción
 * para que el template no tenga que tocar entidades ni disparar consultas perezosas.
 */
@Value
@Builder
public class HorarioListItemDto {
    Long id;
    Byte diaSemanaNum;
    String diaEtiqueta;
    LocalTime horaInicio;
    LocalTime horaFin;
    Short toleranciaMin;
    Long comisionId;
    String comisionCodigo;
    String materiaCodigo;
    String materiaNombre;
    String carreraCodigo;
    boolean activo;
    boolean comisionActiva;

    // Arma la fila del listado a partir de la entidad, resolviendo lo que el template va a mostrar.
    public static HorarioListItemDto from(Horario h) {
        DiaSemana dia = h.getDia();
        return HorarioListItemDto.builder()
            .id(h.getId())
            .diaSemanaNum(h.getDiaSemana())
            .diaEtiqueta(dia != null ? dia.getEtiqueta() : "—")
            .horaInicio(h.getHoraInicio())
            .horaFin(h.getHoraFin())
            .toleranciaMin(h.getToleranciaMin())
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
