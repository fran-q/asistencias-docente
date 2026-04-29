package edu.cent35.asistencias.institucion.infrastructure;

import edu.cent35.asistencias.institucion.domain.Institucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio JPA de instituciones.
 * Esta tabla NO se filtra por tenant (es la tabla de tenants, justamente).
 */
@Repository
public interface InstitucionRepository extends JpaRepository<Institucion, Long> {

    Optional<Institucion> findByNombre(String nombre);

    Optional<Institucion> findByCuit(String cuit);

    boolean existsByNombre(String nombre);

    boolean existsByCuit(String cuit);
}
