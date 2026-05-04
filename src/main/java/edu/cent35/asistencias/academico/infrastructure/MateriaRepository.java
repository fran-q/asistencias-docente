package edu.cent35.asistencias.academico.infrastructure;

import edu.cent35.asistencias.academico.domain.Materia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MateriaRepository extends JpaRepository<Materia, Long> {

    /** Lista todas las materias del tenant (filtro Hibernate aplicado). */
    List<Materia> findAllByOrderByActivoDescNombreAsc();

    /** Lista las materias de una carrera (filtro tenant aplicado por estar la carrera misma filtrada). */
    List<Materia> findByCarreraIdOrderByActivoDescNombreAsc(Long carreraId);

    Optional<Materia> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    long countByCarreraIdAndActivoTrue(Long carreraId);
}
