package edu.cent35.asistencias.docente.web;

import edu.cent35.asistencias.docente.domain.Docente;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class DocenteListItemDto {
    Long id;
    String dni;
    String legajo;
    String nombreCompleto;
    String email;
    String telefono;
    LocalDate fechaAlta;
    boolean activo;

    public static DocenteListItemDto from(Docente d) {
        return DocenteListItemDto.builder()
            .id(d.getId())
            .dni(d.getDni())
            .legajo(d.getLegajo())
            .nombreCompleto(d.getNombreCompleto())
            .email(d.getEmail())
            .telefono(d.getTelefono())
            .fechaAlta(d.getFechaAlta())
            .activo(Boolean.TRUE.equals(d.getActivo()))
            .build();
    }
}
