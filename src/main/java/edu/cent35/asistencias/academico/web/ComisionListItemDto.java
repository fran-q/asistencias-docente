package edu.cent35.asistencias.academico.web;

import edu.cent35.asistencias.academico.domain.Comision;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class ComisionListItemDto {
    Long id;
    String codigo;
    Long materiaId;
    String materiaCodigo;
    String materiaNombre;
    String carreraCodigo;
    Integer cupo;
    boolean activo;
    boolean materiaActiva;
    LocalDateTime actualizadoEn;

    public static ComisionListItemDto from(Comision c) {
        return ComisionListItemDto.builder()
            .id(c.getId())
            .codigo(c.getCodigo())
            .materiaId(c.getMateria().getId())
            .materiaCodigo(c.getMateria().getCodigo())
            .materiaNombre(c.getMateria().getNombre())
            .carreraCodigo(c.getMateria().getCarrera() != null ? c.getMateria().getCarrera().getCodigo() : null)
            .cupo(c.getCupo())
            .activo(Boolean.TRUE.equals(c.getActivo()))
            .materiaActiva(Boolean.TRUE.equals(c.getMateria().getActivo()))
            .actualizadoEn(c.getActualizadoEn())
            .build();
    }
}
