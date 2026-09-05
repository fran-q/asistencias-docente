package edu.cent35.asistencias.repository;

import edu.cent35.asistencias.model.DiaNoLaborable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repositorio de días sin clase. Lo consulta el job de ausencias una vez por corrida, así que
 * la pregunta que importa —¿hoy hay clases?— se resuelve por el índice único
 * {@code (institucion_id, fecha)}.
 */
@Repository
public interface DiaNoLaborableRepository extends JpaRepository<DiaNoLaborable, Long> {

    // Si ese dia esta marcado como sin clases. Es lo que consulta el job antes de generar nada.
    boolean existsByInstitucionIdAndFecha(Long institucionId, LocalDate fecha);

    // El listado de la pantalla, acotado a un rango para no traer todos los anos cargados.
    @Query("""
        SELECT d FROM DiaNoLaborable d
        WHERE d.institucionId = :institucionId
          AND d.fecha BETWEEN :desde AND :hasta
        ORDER BY d.fecha ASC
        """)
    List<DiaNoLaborable> entreFechas(@Param("institucionId") Long institucionId,
                                     @Param("desde") LocalDate desde,
                                     @Param("hasta") LocalDate hasta);

    // Para validar el alta sin depender de que la FK explote con un error de base.
    boolean existsByInstitucionIdAndFechaAndIdNot(Long institucionId, LocalDate fecha, Long id);
}
