package edu.cent35.asistencias.repository;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Docente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de docentes, con búsquedas por DNI y legajo para validar que no se repitan.
 * El filtro de Hibernate ya acota todo a la institución actual.
 */
@Repository
public interface DocenteRepository extends JpaRepository<Docente, Long> {

    // Todos los docentes del tenant, ordenados por apellido / nombre.
    List<Docente> findAllByOrderByActivoDescApellidoAscNombreAsc();

    // Solo docentes activos, para selectores de UI.
    List<Docente> findByActivoTrueOrderByApellidoAscNombreAsc();

    // Busca por DNI dentro de la institución.
    Optional<Docente> findByDni(String dni);

    // Busca por legajo dentro de la institución.
    Optional<Docente> findByLegajo(String legajo);

    // Indica si el DNI ya está tomado, para rechazar duplicados.
    boolean existsByDni(String dni);

    // Indica si el legajo ya está tomado, para rechazar duplicados.
    boolean existsByLegajo(String legajo);
}
