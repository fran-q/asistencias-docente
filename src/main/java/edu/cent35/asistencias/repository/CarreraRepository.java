package edu.cent35.asistencias.repository;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Carrera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CarreraRepository extends JpaRepository<Carrera, Long> {

    /** Listado ordenado por nombre, limitado al tenant via filtro Hibernate. */
    List<Carrera> findAllByOrderByActivoDescNombreAsc();

    Optional<Carrera> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    long countByActivoTrue();

    /** Solo carreras activas, ordenadas por nombre. Para selectores de UI. */
    List<Carrera> findByActivoTrueOrderByNombreAsc();
}
