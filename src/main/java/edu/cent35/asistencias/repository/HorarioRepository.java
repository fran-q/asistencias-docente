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

@Repository
public interface HorarioRepository extends JpaRepository<Horario, Long> {

    // Horarios de una comision, ordenados por dia y hora.
    List<Horario> findByComisionIdOrderByDiaSemanaAscHoraInicioAsc(Long comisionId);

    // Cuenta horarios activos de una comision (para validar baja).
    long countByComisionIdAndActivoTrue(Long comisionId);

    /**
     * Horarios activos de una carrera, para la grilla semanal.
     * <p>
     * Filtra explicitamente por {@code institucionId} - el filtro Hibernate
     * sobre Materia/Carrera no se propaga al JOIN en JPQL.
     */
    @Query("""
        SELECT h
          FROM Horario h
          JOIN h.comision c
          JOIN c.materia m
         WHERE m.carrera.id    = :carreraId
           AND m.institucionId = :tenantId
           AND h.activo = true
           AND c.activo = true
           AND m.activo = true
        ORDER BY h.diaSemana, h.horaInicio
        """)
    List<Horario> findActivosPorCarrera(@Param("carreraId") Long carreraId,
                                        @Param("tenantId")  Long tenantId);

    /**
     * Horarios activos del tenant en un día puntual, con docente asignado.
     * Sirve para calcular los AUSENTE del listado: cada uno de estos
     * horarios deberia tener una marca para la fecha; los que no la tengan
     * y cuya hora_fin ya pasó cuentan como AUSENTE.
     */
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

    /**
     * Horarios de hoy en los que el docente está asignado a la comisión.
     * <p>
     * El filtro fino de "está corriendo ahora" (ventana
     * {@code [hora_inicio - tolerancia, hora_fin]}) se hace en Java —
     * la tolerancia depende de cada Horario, no de un valor global.
     */
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
