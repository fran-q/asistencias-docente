package edu.cent35.asistencias.repository;

import edu.cent35.asistencias.model.PeriodoLectivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de períodos lectivos. Todas las consultas llevan el WHERE del tenant explícito
 * sobre {@code p.institucionId}, que es columna propia del período y no del ciclo: el filtro de
 * Hibernate no se propaga a los JOINs (TD-003).
 */
@Repository
public interface PeriodoLectivoRepository extends JpaRepository<PeriodoLectivo, Long> {

    /**
     * Los períodos que se pueden elegir al armar una comisión: los de ciclos que todavía
     * admiten cambios de estructura.
     *
     * <p>Un ciclo cerrado queda afuera a propósito. Ofrecerlo en el combo sería ofrecer algo
     * que el servicio va a rechazar, y quien lo elija se entera recién al guardar.
     */
    @Query("""
        SELECT p FROM PeriodoLectivo p
        JOIN FETCH p.ciclo c
        WHERE p.institucionId = :institucionId
          AND c.estado <> edu.cent35.asistencias.model.EstadoCiclo.CERRADO
        ORDER BY c.anio DESC, p.orden ASC
        """)
    List<PeriodoLectivo> seleccionablesDelTenant(@Param("institucionId") Long institucionId);

    // Un periodo con su ciclo, validando el tenant en la misma consulta.
    @Query("""
        SELECT p FROM PeriodoLectivo p
        JOIN FETCH p.ciclo c
        WHERE p.institucionId = :institucionId
          AND p.id = :id
        """)
    Optional<PeriodoLectivo> porIdEnTenant(@Param("institucionId") Long institucionId,
                                           @Param("id") Long id);

    /**
     * Los períodos de la institución que contienen esa fecha, dentro de un ciclo activo.
     *
     * <p>Devuelve lista y no uno solo porque nada impide que se solapen: "Anual" y "1er
     * cuatrimestre" cubren los dos el mes de abril, y esa superposición es normal —una materia
     * anual y una cuatrimestral conviven en abril—. Quien pregunta suele querer saber si el
     * período de una comisión está entre estos, no cuál es "el" período del día.
     */
    @Query("""
        SELECT p FROM PeriodoLectivo p
        JOIN p.ciclo c
        WHERE p.institucionId = :institucionId
          AND c.estado = edu.cent35.asistencias.model.EstadoCiclo.ACTIVO
          AND :fecha BETWEEN p.fechaInicio AND p.fechaFin
        """)
    List<PeriodoLectivo> vigentesEnFecha(@Param("institucionId") Long institucionId,
                                         @Param("fecha") LocalDate fecha);

    // Cuantas comisiones cuelgan de este periodo; sin esto, borrarlo lo rechazaria la FK con
    // un error de base en vez de un mensaje que se entienda.
    @Query("""
        SELECT COUNT(co) FROM Comision co
        JOIN co.materia m
        WHERE m.institucionId = :institucionId
          AND co.periodo.id = :periodoId
        """)
    long contarComisiones(@Param("institucionId") Long institucionId,
                          @Param("periodoId") Long periodoId);
}
