package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Materia;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Fila ya preparada de las materias para la tabla del listado. Se arma dentro de la transacción
 * para que el template no tenga que tocar entidades ni disparar consultas perezosas.
 */
@Value
@Builder
public class MateriaListItemDto {
    Long id;
    String codigo;
    String nombre;
    Long carreraId;
    String carreraCodigo;
    String carreraNombre;
    Short anio;
    String titularNombre;     // null si no tiene titular asignado
    boolean titularActivo;
    boolean activo;
    boolean carreraActiva;
    LocalDateTime actualizadoEn;

    // Arma la fila del listado a partir de la entidad, resolviendo lo que el template va a mostrar.
    public static MateriaListItemDto from(Materia m) {
        return MateriaListItemDto.builder()
            .id(m.getId())
            .codigo(m.getCodigo())
            .nombre(m.getNombre())
            .carreraId(m.getCarrera().getId())
            .carreraCodigo(m.getCarrera().getCodigo())
            .carreraNombre(m.getCarrera().getNombre())
            .anio(m.getAnio())
            .titularNombre(m.getDocenteTitular() != null ? m.getDocenteTitular().getNombreCompleto() : null)
            .titularActivo(m.getDocenteTitular() == null || Boolean.TRUE.equals(m.getDocenteTitular().getActivo()))
            .activo(Boolean.TRUE.equals(m.getActivo()))
            .carreraActiva(Boolean.TRUE.equals(m.getCarrera().getActivo()))
            .actualizadoEn(m.getActualizadoEn())
            .build();
    }
}
