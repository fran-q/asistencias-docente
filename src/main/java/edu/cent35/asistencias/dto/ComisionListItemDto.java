package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Comision;
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
    String docenteNombre;       // null si no tiene docente asignado
    boolean docenteActivo;      // true si no hay docente o si está activo
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
            .docenteNombre(c.getDocenteAsignado() != null ? c.getDocenteAsignado().getNombreCompleto() : null)
            .docenteActivo(c.getDocenteAsignado() == null || Boolean.TRUE.equals(c.getDocenteAsignado().getActivo()))
            .activo(Boolean.TRUE.equals(c.getActivo()))
            .materiaActiva(Boolean.TRUE.equals(c.getMateria().getActivo()))
            .actualizadoEn(c.getActualizadoEn())
            .build();
    }
}
