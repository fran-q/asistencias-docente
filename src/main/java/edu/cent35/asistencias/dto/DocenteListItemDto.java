package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.EstadoConsentimiento;
import edu.cent35.asistencias.model.Docente;
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
    // Estado del consentimiento biometrico (Sprint 3 Fase D).
    EstadoConsentimiento estadoConsentimiento;

    public static DocenteListItemDto from(Docente d, EstadoConsentimiento estadoConsentimiento) {
        return DocenteListItemDto.builder()
            .id(d.getId())
            .dni(d.getDni())
            .legajo(d.getLegajo())
            .nombreCompleto(d.getNombreCompleto())
            .email(d.getEmail())
            .telefono(d.getTelefono())
            .fechaAlta(d.getFechaAlta())
            .activo(Boolean.TRUE.equals(d.getActivo()))
            .estadoConsentimiento(estadoConsentimiento)
            .build();
    }
}
