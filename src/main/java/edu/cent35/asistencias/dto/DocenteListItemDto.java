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
    // Las dos letras del avatar del listado. Se resuelven en Persona y no en la plantilla:
    // desde el template solo se ve "Apellido, Nombre", y recortarlo daria las dos primeras
    // del apellido en lugar de las iniciales.
    String iniciales;
    String email;
    String telefono;
    LocalDate fechaAlta;
    // NULL cuando el docente sigue activo, o cuando fue dado de baja antes de que el
    // sistema registrara la fecha.
    LocalDate fechaBaja;
    boolean activo;
    // Estado del consentimiento biometrico (Sprint 3 Fase D).
    EstadoConsentimiento estadoConsentimiento;

    /**
     * Por qué no se le puede dar de baja, o null si se puede.
     *
     * <p>Viaja hasta la pantalla para que el botón sepa si abrir el cuadro de confirmación o
     * avisar de una vez. Confirmar algo que va a fallar es pedirle a alguien que decida sobre
     * una operación imposible.
     */
    String motivoQueImpideLaBaja;

    // Arma la fila del listado a partir de la entidad, resolviendo lo que el template va a mostrar.
    // La persona se lee acá dentro, con la transacción todavía abierta: es LAZY, así que el
    // docente tiene que venir de una consulta con JOIN FETCH.
    public static DocenteListItemDto from(Docente d, EstadoConsentimiento estadoConsentimiento,
                                          String motivoQueImpideLaBaja) {
        Persona p = d.getPersona();
        return DocenteListItemDto.builder()
            .id(d.getId())
            .dni(p.getDni())
            .legajo(d.getLegajo())
            .nombreCompleto(p.getNombreCompleto())
            .iniciales(p.getIniciales())
            .email(p.getEmail())
            .telefono(p.getTelefono())
            .fechaAlta(d.getFechaAlta())
            .fechaBaja(d.getFechaBaja())
            .activo(Boolean.TRUE.equals(d.getActivo()))
            .estadoConsentimiento(estadoConsentimiento)
            .motivoQueImpideLaBaja(motivoQueImpideLaBaja)
            .build();
    }
}
