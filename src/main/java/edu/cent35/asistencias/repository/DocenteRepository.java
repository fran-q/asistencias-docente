package edu.cent35.asistencias.repository;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Docente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocenteRepository extends JpaRepository<Docente, Long> {

    /** Todos los docentes del tenant, ordenados por apellido / nombre. */
    List<Docente> findAllByOrderByActivoDescApellidoAscNombreAsc();

    /** Solo docentes activos, para selectores de UI. */
    List<Docente> findByActivoTrueOrderByApellidoAscNombreAsc();

    Optional<Docente> findByDni(String dni);

    Optional<Docente> findByLegajo(String legajo);

    boolean existsByDni(String dni);

    boolean existsByLegajo(String legajo);
}
