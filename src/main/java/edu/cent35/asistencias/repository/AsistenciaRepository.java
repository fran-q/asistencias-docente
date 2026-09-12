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

    // Marca ya existente para (docente, horario, fecha); es la base de la idempotencia.
    Optional<Asistencia> findByDocenteIdAndHorarioIdAndFecha(
        Long docenteId, Long horarioId, LocalDate fecha);

    // Asistencias del día (en el tenant actual, gracias al @Filter).
    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.docente d
        JOIN FETCH d.persona per
        JOIN FETCH a.comision c
        JOIN FETCH c.materia m
        JOIN FETCH a.horario h
        LEFT JOIN FETCH a.bloque b
        WHERE per.institucionId = :tenantId
          AND a.fecha = :fecha
        ORDER BY a.horaRegistrada DESC, a.id DESC
    """)
    List<Asistencia> findDelDia(@Param("tenantId") Long tenantId,
                                @Param("fecha") LocalDate fecha);

    // Filas del reporte: solo el rango de fechas es obligatorio, el resto de filtros son opcionales.
    @Query("""
        SELECT a FROM Asistencia a
        JOIN FETCH a.docente d
        JOIN FETCH d.persona per
        JOIN FETCH a.comision c
        JOIN FETCH c.materia m
        LEFT JOIN FETCH m.carrera
        JOIN FETCH a.horario h
        LEFT JOIN FETCH a.bloque b
        WHERE per.institucionId = :tenantId
          AND a.fecha BETWEEN :desde AND :hasta
          AND (:docenteId IS NULL OR d.id = :docenteId)
          AND (:materiaId IS NULL OR m.id = :materiaId)
          AND (:carreraId IS NULL OR m.carrera.id = :carreraId)
          AND (:estado    IS NULL OR a.estado = :estado)
          AND (:metodo    IS NULL OR a.metodo = :metodo)
        ORDER BY a.fecha DESC, a.horaRegistrada DESC, a.id DESC
    """)
    List<Asistencia> findParaReporte(
        @Param("tenantId")  Long tenantId,
        @Param("desde")     LocalDate desde,
        @Param("hasta")     LocalDate hasta,
        @Param("docenteId") Long docenteId,
        @Param("materiaId") Long materiaId,
        @Param("carreraId") Long carreraId,
        @Param("estado")    edu.cent35.asistencias.model.EstadoAsistencia estado,
        @Param("metodo")    edu.cent35.asistencias.model.MetodoAsistencia metodo);

    // Cuantas filas daria el reporte sin el tope. Se cuenta en la base en vez de traerlas
    // y medir la lista: contar es justamente lo que hay que poder hacer sin traer nada.
    @Query("""
        SELECT COUNT(a) FROM Asistencia a
        JOIN a.docente d
        JOIN d.persona per
        JOIN a.comision c
        JOIN c.materia m
        WHERE per.institucionId = :tenantId
          AND a.fecha BETWEEN :desde AND :hasta
          AND (:docenteId IS NULL OR d.id = :docenteId)
          AND (:materiaId IS NULL OR m.id = :materiaId)
          AND (:carreraId IS NULL OR m.carrera.id = :carreraId)
          AND (:estado    IS NULL OR a.estado = :estado)
          AND (:metodo    IS NULL OR a.metodo = :metodo)
    """)
    long contarParaReporte(
        @Param("tenantId")  Long tenantId,
        @Param("desde")     LocalDate desde,
        @Param("hasta")     LocalDate hasta,
        @Param("docenteId") Long docenteId,
        @Param("materiaId") Long materiaId,
        @Param("carreraId") Long carreraId,
        @Param("estado")    edu.cent35.asistencias.model.EstadoAsistencia estado,
        @Param("metodo")    edu.cent35.asistencias.model.MetodoAsistencia metodo);

    /**
     * La primera asistencia de un período entre dos fechas, o null si no hay ninguna.
     *
     * <p>La usa la edición del calendario (V027): achicar un período no puede dejar afuera días
     * que ya tienen asistencias, porque quedarían registradas en un período que dice que ese
     * día no hubo clase. Devuelve la fecha y no un sí o no porque el mensaje tiene que decir
     * cuál es.
     *
     * <p><b>Dónde queda el WHERE del tenant.</b> {@code a.institucionId} por la asistencia,
     * {@code m.institucionId} por la materia —comisiones no tiene columna propia— y
     * {@code p.institucionId} por el período: el filtro de Hibernate no se propaga a los
     * JOINs (TD-003).
     */
    @Query("""
        SELECT MIN(a.fecha) FROM Asistencia a
        JOIN a.comision c
        JOIN c.materia m
        JOIN c.periodo p
        WHERE a.institucionId = :tenantId
          AND m.institucionId = :tenantId
          AND p.institucionId = :tenantId
          AND p.id = :periodoId
          AND a.fecha BETWEEN :desde AND :hasta
    """)
    LocalDate primeraDelPeriodoEntre(@Param("tenantId")  Long tenantId,
                                     @Param("periodoId") Long periodoId,
                                     @Param("desde")     LocalDate desde,
                                     @Param("hasta")     LocalDate hasta);

    // Lo mismo para el ciclo entero. Los mismos tres WHERE del tenant; el ciclo se compara por
    // la FK del periodo, sin sumar otro JOIN que tambien habria que acotar.
    @Query("""
        SELECT MIN(a.fecha) FROM Asistencia a
        JOIN a.comision c
        JOIN c.materia m
        JOIN c.periodo p
        WHERE a.institucionId = :tenantId
          AND m.institucionId = :tenantId
          AND p.institucionId = :tenantId
          AND p.ciclo.id = :cicloId
          AND a.fecha BETWEEN :desde AND :hasta
    """)
    LocalDate primeraDelCicloEntre(@Param("tenantId") Long tenantId,
                                   @Param("cicloId")  Long cicloId,
                                   @Param("desde")    LocalDate desde,
                                   @Param("hasta")    LocalDate hasta);

    /**
     * Las asistencias imputadas a un bloque, de la primera clase a la última.
     *
     * <p>Se usa al corregir una salida: si la hora nueva deja clases afuera, hay que poder
     * decir cuáles quedaron marcadas por un rango que ya no existe.
     */
    List<Asistencia> findByBloqueIdOrderByHoraRegistradaAsc(Long bloqueId);
}
