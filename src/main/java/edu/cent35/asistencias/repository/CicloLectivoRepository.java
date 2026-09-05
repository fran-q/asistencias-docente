package edu.cent35.asistencias.repository;

import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.model.EstadoCiclo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de ciclos lectivos. Como el resto, recibe el institucionId de forma explícita
 * aunque el filtro de Hibernate ya acote: es la segunda capa de la defensa multi-tenant y no
 * depende de que el filtro esté activo (ADR-0004).
 */
@Repository
public interface CicloLectivoRepository extends JpaRepository<CicloLectivo, Long> {

    /**
     * Los ciclos de la institución, del más nuevo al más viejo.
     *
     * <p>Trae los períodos con LEFT JOIN FETCH: el listado los muestra en la misma fila, y sin
     * el fetch serían una consulta por ciclo. LEFT porque un ciclo recién creado todavía no
     * tiene ninguno y un JOIN común lo dejaría afuera de su propia pantalla.
     *
     * <p>DISTINCT porque el fetch de una colección multiplica la raíz por cada hijo.
     */
    @Query("""
        SELECT DISTINCT c FROM CicloLectivo c
        LEFT JOIN FETCH c.periodos p
        WHERE c.institucionId = :institucionId
        ORDER BY c.anio DESC
        """)
    List<CicloLectivo> listarDelTenant(@Param("institucionId") Long institucionId);

    // Un ciclo con sus periodos ya cargados, validando el tenant en la misma consulta.
    @Query("""
        SELECT DISTINCT c FROM CicloLectivo c
        LEFT JOIN FETCH c.periodos p
        WHERE c.institucionId = :institucionId
          AND c.id = :id
        """)
    Optional<CicloLectivo> porIdEnTenant(@Param("institucionId") Long institucionId,
                                        @Param("id") Long id);

    // El ciclo de un ano concreto; lo usa el alta para no permitir dos del mismo ano.
    Optional<CicloLectivo> findByInstitucionIdAndAnio(Long institucionId, Short anio);

    /**
     * El ciclo activo que contiene esa fecha.
     *
     * <p>Es la consulta del pase y del job de ausencias, así que corre seguido. Devuelve
     * {@code Optional} y no falla: fuera del ciclo —enero, un año sin abrir— la respuesta
     * correcta es "no hay clases", no un error.
     */
    @Query("""
        SELECT c FROM CicloLectivo c
        WHERE c.institucionId = :institucionId
          AND c.estado = edu.cent35.asistencias.model.EstadoCiclo.ACTIVO
          AND :fecha BETWEEN c.fechaInicio AND c.fechaFin
        """)
    Optional<CicloLectivo> activoEnFecha(@Param("institucionId") Long institucionId,
                                         @Param("fecha") LocalDate fecha);

    // Cuantos ciclos hay en ese estado; evita que queden dos activos a la vez.
    long countByInstitucionIdAndEstado(Long institucionId, EstadoCiclo estado);
}
