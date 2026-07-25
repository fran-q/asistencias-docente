package edu.cent35.asistencias.repository;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Materia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de materias, con los conteos que usan las bajas lógicas para no dejar hijos
 * huérfanos. El filtro de Hibernate ya acota todo a la institución actual.
 */
@Repository
public interface MateriaRepository extends JpaRepository<Materia, Long> {

    // Lista todas las materias del tenant (filtro Hibernate aplicado).
    List<Materia> findAllByOrderByActivoDescNombreAsc();

    // Lista las materias de una carrera (filtro tenant aplicado por estar la carrera misma filtrada).
    List<Materia> findByCarreraIdOrderByActivoDescNombreAsc(Long carreraId);

    // Busca por código dentro de la institución.
    Optional<Materia> findByCodigo(String codigo);

    // Indica si el código ya está tomado, para rechazar duplicados.
    boolean existsByCodigo(String codigo);

    // Cuenta materias activas de una carrera, para bloquear su baja.
    long countByCarreraIdAndActivoTrue(Long carreraId);

    // Solo materias activas, ordenadas por nombre. Para selectores de UI.
    List<Materia> findByActivoTrueOrderByNombreAsc();

    // Cuenta materias activas donde un docente es titular - para bloquear su baja.
    long countByDocenteTitularIdAndActivoTrue(Long docenteId);
}
