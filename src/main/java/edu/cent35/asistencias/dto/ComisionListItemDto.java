package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Comision;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Fila ya preparada de las comisiones para la tabla del listado. Se arma dentro de la transacción
 * para que el template no tenga que tocar entidades ni disparar consultas perezosas.
 */
@Value
@Builder
public class ComisionListItemDto {
    Long id;
    String codigo;
    Long materiaId;
    String materiaCodigo;
    String materiaNombre;
    String carreraCodigo;
    // La pantalla muestra el nombre; el codigo queda para uso interno.
    String carreraNombre;
    String docenteNombre;       // null si no tiene docente asignado
    boolean docenteActivo;      // true si no hay docente o si está activo
    boolean activo;
    boolean materiaActiva;
    LocalDateTime actualizadoEn;
    // A que ano y a que tramo pertenece esta oferta (V023). Sin esto el listado mezcla los
    // anos sin decirlo, que es exactamente el problema que los ciclos vinieron a resolver.
    Short cicloAnio;
    String periodoNombre;
    boolean cicloCerrado;

    // Arma la fila del listado a partir de la entidad, resolviendo lo que el template va a mostrar.
    public static ComisionListItemDto from(Comision c) {
        return ComisionListItemDto.builder()
            .id(c.getId())
            .codigo(c.getCodigo())
            .materiaId(c.getMateria().getId())
            .materiaCodigo(c.getMateria().getCodigo())
            .materiaNombre(c.getMateria().getNombre())
            .carreraCodigo(c.getMateria().getCarrera() != null ? c.getMateria().getCarrera().getCodigo() : null)
            .carreraNombre(c.getMateria().getCarrera() != null ? c.getMateria().getCarrera().getNombre() : null)
            .docenteNombre(c.getDocenteAsignado() != null ? c.getDocenteAsignado().getNombreCompleto() : null)
            .docenteActivo(c.getDocenteAsignado() == null || Boolean.TRUE.equals(c.getDocenteAsignado().getActivo()))
            .activo(Boolean.TRUE.equals(c.getActivo()))
            .materiaActiva(Boolean.TRUE.equals(c.getMateria().getActivo()))
            .actualizadoEn(c.getActualizadoEn())
            .cicloAnio(c.getPeriodo() != null ? c.getPeriodo().getCiclo().getAnio() : null)
            .periodoNombre(c.getPeriodo() != null ? c.getPeriodo().getNombre() : null)
            .cicloCerrado(c.getPeriodo() != null
                && !c.getPeriodo().getCiclo().getEstado().admiteCambiosDeEstructura())
            .build();
    }
}
