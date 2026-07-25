package edu.cent35.asistencias.repository;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Comision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComisionRepository extends JpaRepository<Comision, Long> {

    /**
     * Lista las comisiones de una materia.
     * Como Comision NO esta tenant-scoped directamente, el aislamiento
     * se logra a nivel service: solo se busca por materia ya validada.
     */
    List<Comision> findByMateriaIdOrderByActivoDescCodigoAsc(Long materiaId);

    /**
     * Lista todas las comisiones del tenant actual via JOIN con materia.
     * <p>
     * <b>IMPORTANTE</b>: el filtro Hibernate {@code "tenant"} NO se propaga
     * automaticamente a entidades JOINeadas en JPQL - hay que filtrar
     * explicitamente por {@code institucionId}. Por eso el parametro.
     */
    @Query("""
        SELECT c FROM Comision c
        JOIN c.materia m
        WHERE m.institucionId = :tenantId
        ORDER BY c.activo DESC, m.nombre, c.codigo
        """)
    List<Comision> findAllDelTenant(@Param("tenantId") Long tenantId);

    Optional<Comision> findByMateriaIdAndCodigo(Long materiaId, String codigo);

    boolean existsByMateriaIdAndCodigo(Long materiaId, String codigo);

    long countByMateriaIdAndActivoTrue(Long materiaId);

    // Cuenta comisiones activas asignadas a un docente - para bloquear su baja.
    long countByDocenteAsignadoIdAndActivoTrue(Long docenteId);

    // Cuenta comisiones del tenant - chequea pertenencia via materia.
    @Query("SELECT COUNT(c) FROM Comision c JOIN c.materia m WHERE c.id = :id")
    long countByIdEnTenant(@Param("id") Long id);

    /**
     * Comisiones activas del tenant con materia activa, para selectores de UI.
     * <p>
     * Filtra explicitamente por institucionId (TD-003: el filtro Hibernate
     * sobre Materia no se propaga al JOIN en JPQL).
     */
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
