package edu.cent35.asistencias.repository;

import edu.cent35.asistencias.model.BloquePresencia;
import edu.cent35.asistencias.model.EstadoCierre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a {@code bloques_presencia} (ADR-0017).
 * <p>
 * <b>Multi-tenant</b>: la entidad es tenant-scoped y lleva su propio {@code @Filter}, así que
 * las derived queries sobre la raíz ya salen filtradas. Las que hacen JOIN llevan el
 * {@code WHERE institucionId} explícito igual, porque el filtro de Hibernate no se propaga a
 * las entidades JOINeadas (ADR-0004, TD-003).
 * <p>
 * Las dos consultas que traen el docente le hacen {@code JOIN FETCH} también a su
 * {@code Persona}: desde V016 el nombre no vive en el docente sino ahí, y con
 * {@code open-in-view=false} la pantalla explota al pedirlo fuera de la transacción.
 */
public interface BloquePresenciaRepository extends JpaRepository<BloquePresencia, Long> {

    /**
     * El bloque que el docente todavía no cerró, si tiene uno.
     *
     * <p>Devuelve {@code Optional} y no una lista porque la base garantiza que haya como
     * máximo uno: el UNIQUE sobre la columna generada {@code bloque_abierto_de} de V019. Si
     * alguna vez devolviera dos, el problema no está acá sino en que ese invariante se rompió.
     */
    Optional<BloquePresencia> findByDocenteIdAndEstadoCierre(Long docenteId, EstadoCierre estado);

    // Bloques del docente en una fecha, del primero al último.
    List<BloquePresencia> findByDocenteIdAndFechaOrderByHoraEntradaAsc(Long docenteId, LocalDate fecha);

    /**
     * Bloques que quedaron sin que nadie registrara la salida, del más viejo al más nuevo
     * (RF-79).
     *
     * <p>Sin tope de fecha a propósito: los pendientes se arrastran entre días hasta que un
     * administrador los resuelva. Limpiarlos al cambiar el día convertiría la obligatoriedad
     * de la salida en una formalidad.
     */
    @Query("""
        SELECT b FROM BloquePresencia b
        JOIN FETCH b.docente d
        JOIN FETCH d.persona
        WHERE b.institucionId = :tenantId
          AND b.estadoCierre  = edu.cent35.asistencias.model.EstadoCierre.SIN_CIERRE
        ORDER BY b.fecha ASC, b.horaEntrada ASC
        """)
    List<BloquePresencia> findPendientesDeCierre(@Param("tenantId") Long tenantId);

    /**
     * Bloques todavía abiertos de una fecha, para que el job los cierre (RF-80).
     *
     * <p>Incluye los de fechas anteriores a la de hoy: un bloque abierto de ayer no se cierra
     * solo, y si el job solo mirara el día en curso quedaría abierto para siempre bloqueando
     * al docente —el UNIQUE de un solo bloque abierto le impediría entrar de nuevo—.
     */
    @Query("""
        SELECT b FROM BloquePresencia b
        JOIN FETCH b.docente d
        JOIN FETCH d.persona
        WHERE b.institucionId = :tenantId
          AND b.estadoCierre  = edu.cent35.asistencias.model.EstadoCierre.ABIERTO
          AND b.fecha        <= :hasta
        ORDER BY b.fecha ASC, b.horaEntrada ASC
        """)
    List<BloquePresencia> findAbiertosHasta(@Param("tenantId") Long tenantId,
                                            @Param("hasta") LocalDate hasta);

    /**
     * Cuántas salidas quedaron sin registrar (RF-79).
     *
     * <p>Cuenta en vez de traer las filas: el panel de inicio solo muestra el número, y el
     * listado completo tiene su propia pantalla.
     */
    @Query("""
        SELECT COUNT(b) FROM BloquePresencia b
        WHERE b.institucionId = :tenantId
          AND b.estadoCierre  = edu.cent35.asistencias.model.EstadoCierre.SIN_CIERRE
        """)
    long countPendientesDeCierre(@Param("tenantId") Long tenantId);
}
