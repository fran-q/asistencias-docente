package edu.cent35.asistencias.repository;

import edu.cent35.asistencias.model.JustificacionAusencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de las justificaciones adjuntas a una ausencia (RF-25, RF-26). La relación con
 * la asistencia es 1:1, así que la búsqueda por asistencia devuelve a lo sumo una.
 */
public interface JustificacionAusenciaRepository extends JpaRepository<JustificacionAusencia, Long> {

    // Justificación adjunta a una ausencia (relación 1:1).
    Optional<JustificacionAusencia> findByAsistenciaId(Long asistenciaId);

    // Trae solo las filas de las asistencias pedidas. El reporte antes hacia findAll() y
    // filtraba en Java: traia la tabla entera a memoria para quedarse con un punado, y el
    // filtro era un List.contains dentro de un stream, o sea cuadratico sobre esa tabla.
    List<JustificacionAusencia> findByAsistenciaIdIn(Collection<Long> asistenciaIds);
}
