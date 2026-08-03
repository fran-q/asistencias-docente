package edu.cent35.asistencias.repository;

import edu.cent35.asistencias.model.AsistenciaManual;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio del detalle 1:1 que acompaña a cada asistencia cargada a mano. Guarda el motivo
 * del catálogo, el admin que la cargó y una nota libre, que es lo que da trazabilidad al RF-24.
 */
public interface AsistenciaManualRepository extends JpaRepository<AsistenciaManual, Long> {

    // Detalle de carga manual de una asistencia (relación 1:1).
    Optional<AsistenciaManual> findByAsistenciaId(Long asistenciaId);

    // Trae solo las filas de las asistencias pedidas. El reporte antes hacia findAll() y
    // filtraba en Java: traia la tabla entera a memoria para quedarse con un punado, y el
    // filtro era un List.contains dentro de un stream, o sea cuadratico sobre esa tabla.
    List<AsistenciaManual> findByAsistenciaIdIn(Collection<Long> asistenciaIds);
}
