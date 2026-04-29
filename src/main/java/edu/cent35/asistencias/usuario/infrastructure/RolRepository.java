package edu.cent35.asistencias.usuario.infrastructure;

import edu.cent35.asistencias.usuario.domain.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio JPA de roles. Catalogo global, no tenant-scoped.
 */
@Repository
public interface RolRepository extends JpaRepository<Rol, Short> {

    Optional<Rol> findByCodigo(String codigo);
}
