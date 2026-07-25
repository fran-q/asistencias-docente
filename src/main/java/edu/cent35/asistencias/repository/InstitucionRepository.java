package edu.cent35.asistencias.repository;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Institucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio JPA de instituciones.
 * Esta tabla NO se filtra por tenant (es la tabla de tenants, justamente).
 */
@Repository
public interface InstitucionRepository extends JpaRepository<Institucion, Long> {

    // Busca una institución por nombre.
    Optional<Institucion> findByNombre(String nombre);

    // Busca una institución por CUIT.
    Optional<Institucion> findByCuit(String cuit);

    // Indica si el nombre ya está tomado por otra institución.
    boolean existsByNombre(String nombre);

    // Indica si el CUIT ya está tomado por otra institución.
    boolean existsByCuit(String cuit);
}
