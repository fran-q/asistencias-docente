package edu.cent35.asistencias.academico.infrastructure;

import edu.cent35.asistencias.academico.domain.Horario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface HorarioRepository extends JpaRepository<Horario, Long> {

    /** Horarios de una comision, ordenados por dia y hora. */
    List<Horario> findByComisionIdOrderByDiaSemanaAscHoraInicioAsc(Long comisionId);

    /** Horarios activos de una carrera, para la grilla semanal. */
    @Query("""
        SELECT h
          FROM Horario h
          JOIN h.comision c
          JOIN c.materia m
         WHERE m.carrera.id = :carreraId
           AND h.activo = true
           AND c.activo = true
           AND m.activo = true
        ORDER BY h.diaSemana, h.horaInicio
        """)
    List<Horario> findActivosPorCarrera(@Param("carreraId") Long carreraId);

    /**
     * Detecta superposicion de horarios para una misma comision.
     * Devuelve los horarios existentes que solapan con la franja propuesta
     * (excluyendo el id pasado en {@code excludeId} si se esta editando).
     */
    @Query("""
        SELECT h
          FROM Horario h
         WHERE h.comision.id = :comisionId
           AND h.activo      = true
           AND h.diaSemana   = :dia
           AND (:excludeId IS NULL OR h.id <> :excludeId)
           AND h.horaInicio  < :horaFin
           AND h.horaFin     > :horaInicio
        """)
    List<Horario> findSolapamientos(
        @Param("comisionId") Long comisionId,
        @Param("dia") Byte diaSemana,
        @Param("horaInicio") LocalTime horaInicio,
        @Param("horaFin") LocalTime horaFin,
        @Param("excludeId") Long excludeId);
}
