package edu.cent35.asistencias.repository;

import edu.cent35.asistencias.model.ModeloFacial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a {@code modelos_faciales}.
 * <p>
 * <b>Multi-tenant</b>: la entidad no es tenant-scoped directamente; el
 * tenant lo da el {@code Docente} padre. Las queries con JOIN a docente
 * filtran explícitamente por {@code institucionId} (ADR-0004).
 */
public interface ModeloFacialRepository extends JpaRepository<ModeloFacial, Long> {

    /** Modelo facial activo de un docente, si tiene. Solo deberia haber uno. */
    Optional<ModeloFacial> findByDocenteIdAndActivoTrue(Long docenteId);

    /** Historial completo de modelos del docente, del más nuevo al más viejo. */
    List<ModeloFacial> findByDocenteIdOrderByFechaRegistroDescIdDesc(Long docenteId);

    /**
     * Modelos faciales activos de todos los docentes activos del tenant.
     * Es la base de la identificación (Fase D): se compara una cara contra
     * todos estos modelos.
     */
    @Query("""
        SELECT m FROM ModeloFacial m
        JOIN FETCH m.docente d
        WHERE m.activo = true
          AND d.activo = true
          AND d.institucionId = :tenantId
    """)
    List<ModeloFacial> findActivosDelTenant(@Param("tenantId") Long tenantId);
}
