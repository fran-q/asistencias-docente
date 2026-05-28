package edu.cent35.asistencias.repository;

import edu.cent35.asistencias.model.Asistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a {@code asistencias}. La entidad es tenant-scoped por
 * {@code @Filter("tenant")}, por lo que los {@code findAll}/derived queries
 * sobre la raíz Asistencia ya aplican el filtro automáticamente.
 */
public interface AsistenciaRepository extends JpaRepository<Asistencia, Long> {

    /**
     * Asistencia ya registrada para el triple (docente, horario, fecha).
     * Usado para garantizar idempotencia: si existe, no se vuelve a marcar.
     */
    Optional<Asistencia> findByDocenteIdAndHorarioIdAndFecha(
        Long docenteId, Long horarioId, LocalDate fecha);

    /** Asistencias del día (en el tenant actual, gracias al @Filter). */
    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.docente d
        JOIN FETCH a.comision c
        JOIN FETCH c.materia m
        JOIN FETCH a.horario h
        WHERE a.fecha = :fecha
        ORDER BY a.horaRegistrada DESC, a.id DESC
    """)
    List<Asistencia> findDelDia(@Param("fecha") LocalDate fecha);
}
