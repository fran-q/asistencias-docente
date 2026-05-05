package edu.cent35.asistencias.academico.web;

import edu.cent35.asistencias.academico.domain.Carrera;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class CarreraListItemDto {
    Long id;
    String codigo;
    String nombre;
    boolean activo;
    LocalDateTime creadoEn;
    LocalDateTime actualizadoEn;

    public static CarreraListItemDto from(Carrera c) {
        return CarreraListItemDto.builder()
            .id(c.getId())
            .codigo(c.getCodigo())
            .nombre(c.getNombre())
            .activo(Boolean.TRUE.equals(c.getActivo()))
            .creadoEn(c.getCreadoEn())
            .actualizadoEn(c.getActualizadoEn())
            .build();
    }
}
