package edu.cent35.asistencias.repository;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Carrera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de carreras. Todas las consultas quedan acotadas a la institución actual por el
 * filtro de Hibernate, así que no hace falta pasar el tenant como parámetro.
 */
@Repository
public interface CarreraRepository extends JpaRepository<Carrera, Long> {

    // Listado ordenado por nombre, limitado al tenant via filtro Hibernate.
    List<Carrera> findAllByOrderByActivoDescNombreAsc();

    // Busca por código dentro de la institución.
    Optional<Carrera> findByCodigo(String codigo);

    // Indica si el código ya está tomado, para rechazar duplicados.
    boolean existsByCodigo(String codigo);

    // Cuenta las carreras activas.
    long countByActivoTrue();

    // Solo carreras activas, ordenadas por nombre. Para selectores de UI.
    List<Carrera> findByActivoTrueOrderByNombreAsc();
}
