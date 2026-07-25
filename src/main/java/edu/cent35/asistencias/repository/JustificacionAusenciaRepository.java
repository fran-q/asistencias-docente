package edu.cent35.asistencias.repository;

import edu.cent35.asistencias.model.JustificacionAusencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositorio de las justificaciones adjuntas a una ausencia (RF-25, RF-26). La relación con
 * la asistencia es 1:1, así que la búsqueda por asistencia devuelve a lo sumo una.
 */
public interface JustificacionAusenciaRepository extends JpaRepository<JustificacionAusencia, Long> {

    // Justificación adjunta a una ausencia (relación 1:1).
    Optional<JustificacionAusencia> findByAsistenciaId(Long asistenciaId);
}
