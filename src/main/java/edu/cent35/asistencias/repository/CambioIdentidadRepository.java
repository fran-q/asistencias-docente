package edu.cent35.asistencias.repository;

import edu.cent35.asistencias.model.CambioIdentidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Historial de cambios sobre los datos de identidad (ADR-0016).
 * Solo se escribe y se lee: una fila de este historial no se edita ni se borra desde la
 * aplicación, porque su valor es justamente que nadie la haya tocado después.
 */
@Repository
public interface CambioIdentidadRepository extends JpaRepository<CambioIdentidad, Long> {

    // Historial de una persona, del cambio más reciente al más viejo.
    @Query("""
        SELECT c FROM CambioIdentidad c
        WHERE c.institucionId = :tenantId
          AND c.personaId = :personaId
        ORDER BY c.fecha DESC, c.id DESC
        """)
    List<CambioIdentidad> historialDe(@Param("tenantId") Long tenantId,
                                      @Param("personaId") Long personaId);
}
