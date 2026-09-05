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
    /**
     * Horarios activos de una carrera dentro de un ciclo, para la grilla semanal.
     *
     * <p><b>El ciclo entró como parámetro desde V023.</b> Sin él la grilla mezclaba la oferta
     * de todos los años en la misma cuadrícula: en cuanto existiera 2027, el mismo casillero
     * mostraría la clase de 2026 y la de 2027 superpuestas.
     *
     * <p><b>Dónde queda el WHERE del tenant.</b> En tres lugares, uno por cada entidad
     * tenant-scoped que toca el JOIN: {@code m.institucionId} por la materia,
     * {@code p.institucionId} por el período y {@code cl.institucionId} por el ciclo. El filtro
     * de Hibernate no se propaga a los JOINs (TD-003), así que cada uno se acota solo.
     */
    @Query("""
        SELECT h
          FROM Horario h
          JOIN FETCH h.comision c
          JOIN FETCH c.materia m
          LEFT JOIN FETCH c.docenteAsignado d
          JOIN c.periodo p
          JOIN p.ciclo cl
         WHERE m.carrera.id     = :carreraId
           AND m.institucionId  = :tenantId
           AND p.institucionId  = :tenantId
           AND cl.institucionId = :tenantId
           AND cl.id = :cicloId
           AND h.activo = true
           AND c.activo = true
           AND m.activo = true
        ORDER BY h.diaSemana, h.horaInicio
        """)
    List<Horario> findActivosPorCarreraYCiclo(@Param("carreraId") Long carreraId,
                                              @Param("cicloId")  Long cicloId,
                                              @Param("tenantId") Long tenantId);

    /**
     * Horarios de ese día con docente asignado, y que además <b>corren en esa fecha</b>: los que
     * no tengan marca y ya terminaron son AUSENTE.
     *
     * <p><b>La fecha entró como parámetro desde V023, y es lo que arregla un error viejo.</b>
     * Antes la consulta traía todo horario activo de ese día de la semana, sin ningún límite de
     * calendario. Con eso el job generaba ausencias en enero y en el receso, y en marzo de 2027
     * habría seguido generándolas con los horarios de 2026 hasta que alguien los diera de baja a
     * mano, uno por uno.
     *
     * <p>Ahora el horario tiene que caer dentro de su período y su ciclo tiene que estar ACTIVO.
     * Una materia cuatrimestral deja de generar ausencias cuando termina su cuatrimestre, sin
     * que nadie tenga que acordarse de darla de baja.
     *
     * <p><b>Dónde queda el WHERE del tenant.</b> {@code m.institucionId} para la materia,
     * {@code p.institucionId} para el período y {@code cl.institucionId} para el ciclo: las tres
     * son tenant-scoped y el filtro de Hibernate no llega a los JOINs (TD-003).
     */
    @Query("""
        SELECT h FROM Horario h
        JOIN FETCH h.comision c
        JOIN FETCH c.materia m
        JOIN FETCH c.docenteAsignado d
        JOIN c.periodo p
        JOIN p.ciclo cl
        WHERE h.diaSemana = :dia
          AND h.activo    = true
          AND c.activo    = true
          AND m.institucionId  = :tenantId
          AND p.institucionId  = :tenantId
          AND cl.institucionId = :tenantId
          AND cl.estado = edu.cent35.asistencias.model.EstadoCiclo.ACTIVO
          AND :fecha BETWEEN p.fechaInicio AND p.fechaFin
        ORDER BY h.horaInicio, c.codigo
        """)
    List<Horario> findActivosDelDiaConDocente(
        @Param("dia") Byte diaSemana,
        @Param("fecha") LocalDate fecha,
        @Param("tenantId") Long tenantId);

    /**
     * Clases de hoy del docente. Si está corriendo o no se decide en Java, porque la tolerancia
     * es propia de cada horario y no un valor global.
     *
     * <p>Acotada por fecha desde V023, igual que la del job: sin eso, el pase le habría abierto
     * un bloque de presencia a un docente por una clase de un cuatrimestre ya terminado o de un
     * ciclo del año pasado.
     *
     * <p><b>Dónde queda el WHERE del tenant.</b> {@code m.institucionId}, {@code p.institucionId}
     * y {@code cl.institucionId}, una por cada entidad tenant-scoped del JOIN (TD-003).
     */
    @Query("""
        SELECT h FROM Horario h
        JOIN FETCH h.comision c
        JOIN FETCH c.materia m
        JOIN c.periodo p
        JOIN p.ciclo cl
        WHERE c.docenteAsignado.id = :docenteId
          AND h.diaSemana = :dia
          AND h.activo    = true
          AND c.activo    = true
          AND m.institucionId  = :tenantId
          AND p.institucionId  = :tenantId
          AND cl.institucionId = :tenantId
          AND cl.estado = edu.cent35.asistencias.model.EstadoCiclo.ACTIVO
          AND :fecha BETWEEN p.fechaInicio AND p.fechaFin
        """)
    List<Horario> findHoyParaDocente(
        @Param("docenteId") Long docenteId,
        @Param("dia") Byte diaSemana,
        @Param("fecha") LocalDate fecha,
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
