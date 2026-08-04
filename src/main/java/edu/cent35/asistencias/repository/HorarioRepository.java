package edu.cent35.asistencias.repository;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Horario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Repositorio de franjas horarias, con las consultas que sostienen el pase de asistencia y la
 * detección de superposiciones. El tenant viaja como parámetro porque se resuelve por JOIN.
 */
@Repository
public interface HorarioRepository extends JpaRepository<Horario, Long> {

    // Horarios de una comision, ordenados por dia y hora.
    List<Horario> findByComisionIdOrderByDiaSemanaAscHoraInicioAsc(Long comisionId);

    // Cuenta horarios activos de una comision (para validar baja).
    long countByComisionIdAndActivoTrue(Long comisionId);

    // Horarios activos de una carrera, para dibujar la grilla semanal.
    @Query("""
        SELECT h
          FROM Horario h
          JOIN FETCH h.comision c
          JOIN FETCH c.materia m
          LEFT JOIN FETCH c.docenteAsignado d
         WHERE m.carrera.id    = :carreraId
           AND m.institucionId = :tenantId
           AND h.activo = true
           AND c.activo = true
           AND m.activo = true
        ORDER BY h.diaSemana, h.horaInicio
        """)
    List<Horario> findActivosPorCarrera(@Param("carreraId") Long carreraId,
                                        @Param("tenantId")  Long tenantId);

    // Horarios del dia con docente asignado; los que no tengan marca y ya terminaron son AUSENTE.
    @Query("""
        SELECT h FROM Horario h
        JOIN FETCH h.comision c
        JOIN FETCH c.materia m
        JOIN FETCH c.docenteAsignado d
        WHERE h.diaSemana = :dia
          AND h.activo    = true
          AND c.activo    = true
          AND m.institucionId = :tenantId
        ORDER BY h.horaInicio, c.codigo
        """)
    List<Horario> findActivosDelDiaConDocente(
        @Param("dia") Byte diaSemana,
        @Param("tenantId") Long tenantId);

    // Clases de hoy del docente. Si esta corriendo o no se decide en Java, porque la tolerancia
    // es propia de cada horario y no un valor global.
    @Query("""
        SELECT h FROM Horario h
        JOIN FETCH h.comision c
        JOIN FETCH c.materia m
        WHERE c.docenteAsignado.id = :docenteId
          AND h.diaSemana = :dia
          AND h.activo    = true
          AND c.activo    = true
          AND m.institucionId = :tenantId
        """)
    List<Horario> findHoyParaDocente(
        @Param("docenteId") Long docenteId,
        @Param("dia") Byte diaSemana,
        @Param("tenantId") Long tenantId);

    // Franjas de la misma comision que pisan a la propuesta; excludeId evita compararse consigo misma.
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
