package edu.cent35.asistencias.academico.web;

import edu.cent35.asistencias.academico.domain.Materia;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class MateriaListItemDto {
    Long id;
    String codigo;
    String nombre;
    Long carreraId;
    String carreraCodigo;
    String carreraNombre;
    boolean activo;
    boolean carreraActiva;
    LocalDateTime actualizadoEn;

    public static MateriaListItemDto from(Materia m) {
        return MateriaListItemDto.builder()
            .id(m.getId())
            .codigo(m.getCodigo())
            .nombre(m.getNombre())
            .carreraId(m.getCarrera().getId())
            .carreraCodigo(m.getCarrera().getCodigo())
            .carreraNombre(m.getCarrera().getNombre())
            .activo(Boolean.TRUE.equals(m.getActivo()))
            .carreraActiva(Boolean.TRUE.equals(m.getCarrera().getActivo()))
            .actualizadoEn(m.getActualizadoEn())
            .build();
    }
}
