package edu.cent35.asistencias.repository;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio JPA de roles. Catalogo global, no tenant-scoped.
 */
@Repository
public interface RolRepository extends JpaRepository<Rol, Short> {

    // Busca un rol por su código (ADMIN, INSTITUCION).
    Optional<Rol> findByCodigo(String codigo);
}
