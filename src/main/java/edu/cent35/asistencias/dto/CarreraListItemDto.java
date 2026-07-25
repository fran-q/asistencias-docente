package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Carrera;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Fila ya preparada de las carreras para la tabla del listado. Se arma dentro de la transacción
 * para que el template no tenga que tocar entidades ni disparar consultas perezosas.
 */
@Value
@Builder
public class CarreraListItemDto {
    Long id;
    String codigo;
    String nombre;
    boolean activo;
    LocalDateTime creadoEn;
    LocalDateTime actualizadoEn;

    // Arma la fila del listado a partir de la entidad, resolviendo lo que el template va a mostrar.
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
