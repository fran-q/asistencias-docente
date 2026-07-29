package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.EstadoConsentimiento;
import edu.cent35.asistencias.model.Docente;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

/**
 * Fila ya preparada de los docentes para la tabla del listado. Se arma dentro de la transacción
 * para que el template no tenga que tocar entidades ni disparar consultas perezosas.
 */
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
    // NULL cuando el docente sigue activo, o cuando fue dado de baja antes de que el
    // sistema registrara la fecha.
    LocalDate fechaBaja;
    boolean activo;
    // Estado del consentimiento biometrico (Sprint 3 Fase D).
    EstadoConsentimiento estadoConsentimiento;

    // Arma la fila del listado a partir de la entidad, resolviendo lo que el template va a mostrar.
    public static DocenteListItemDto from(Docente d, EstadoConsentimiento estadoConsentimiento) {
        return DocenteListItemDto.builder()
            .id(d.getId())
            .dni(d.getDni())
            .legajo(d.getLegajo())
            .nombreCompleto(d.getNombreCompleto())
            .email(d.getEmail())
            .telefono(d.getTelefono())
            .fechaAlta(d.getFechaAlta())
            .fechaBaja(d.getFechaBaja())
            .activo(Boolean.TRUE.equals(d.getActivo()))
            .estadoConsentimiento(estadoConsentimiento)
            .build();
    }
}
