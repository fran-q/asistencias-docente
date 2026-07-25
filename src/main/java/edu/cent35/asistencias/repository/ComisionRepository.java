package edu.cent35.asistencias.repository;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Comision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de comisiones. Comisión no tiene institucion_id propio, así que las consultas
 * del tenant van por JOIN contra materia y llevan el institucionId como parámetro explícito.
 */
@Repository
public interface ComisionRepository extends JpaRepository<Comision, Long> {

    // Comisiones de una materia; el aislamiento lo garantiza el service, que valida la materia antes.
    List<Comision> findByMateriaIdOrderByActivoDescCodigoAsc(Long materiaId);

    // Comisiones del tenant por JOIN con materia. El institucionId va explicito porque el filtro
    // de Hibernate no se propaga a las entidades JOINeadas en JPQL (TD-003).
    @Query("""
        SELECT c FROM Comision c
        JOIN c.materia m
        WHERE m.institucionId = :tenantId
        ORDER BY c.activo DESC, m.nombre, c.codigo
        """)
    List<Comision> findAllDelTenant(@Param("tenantId") Long tenantId);

    // Busca una comisión por su código dentro de la materia.
    Optional<Comision> findByMateriaIdAndCodigo(Long materiaId, String codigo);

    // Indica si el código ya está tomado en esa materia.
    boolean existsByMateriaIdAndCodigo(Long materiaId, String codigo);

    // Cuenta comisiones activas de una materia, para bloquear su baja.
    long countByMateriaIdAndActivoTrue(Long materiaId);

    // Cuenta comisiones activas asignadas a un docente - para bloquear su baja.
    long countByDocenteAsignadoIdAndActivoTrue(Long docenteId);

    // Cuenta comisiones del tenant - chequea pertenencia via materia.
    @Query("SELECT COUNT(c) FROM Comision c JOIN c.materia m WHERE c.id = :id")
    long countByIdEnTenant(@Param("id") Long id);

    // Solo las activas con materia activa, para los combos de los formularios.
    @Query("""
        SELECT c FROM Comision c
        JOIN c.materia m
        WHERE m.institucionId = :tenantId
          AND c.activo = true
          AND m.activo = true
        ORDER BY m.nombre, c.codigo
        """)
    List<Comision> findActivasDelTenant(@Param("tenantId") Long tenantId);
}
