package edu.cent35.asistencias.academico.infrastructure;

import edu.cent35.asistencias.academico.domain.Comision;
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
     * Util para listados globales (ej: "todas las comisiones de la institucion").
     */
    @Query("SELECT c FROM Comision c JOIN c.materia m ORDER BY c.activo DESC, m.nombre, c.codigo")
    List<Comision> findAllDelTenant();

    Optional<Comision> findByMateriaIdAndCodigo(Long materiaId, String codigo);

    boolean existsByMateriaIdAndCodigo(Long materiaId, String codigo);

    long countByMateriaIdAndActivoTrue(Long materiaId);

    /** Cuenta comisiones del tenant - chequea pertenencia via materia. */
    @Query("SELECT COUNT(c) FROM Comision c JOIN c.materia m WHERE c.id = :id")
    long countByIdEnTenant(@Param("id") Long id);
}
