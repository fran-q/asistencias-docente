package edu.cent35.asistencias.repository;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Comision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de comisiones. Comisión no tiene institucion_id propio, así que las consultas
 * del tenant van por JOIN contra materia y llevan el institucionId como parámetro explícito.
 */
@Repository
public interface ComisionRepository extends JpaRepository<Comision, Long> {

    // Comisiones de una materia; el aislamiento lo garantiza el service, que valida la materia antes.
    List<Comision> findByMateriaIdOrderByActivoDescCodigoAsc(Long materiaId);

    // Comisiones del tenant por JOIN con materia. El institucionId va explicito porque el filtro
    // de Hibernate no se propaga a las entidades JOINeadas en JPQL (TD-003).
    /**
     * Todas las comisiones de la institución, de todos los ciclos.
     *
     * <p>Trae el período y su ciclo con JOIN FETCH porque el listado los muestra en cada fila:
     * sin el fetch serían dos consultas por comisión, y con {@code open-in-view=false} ni
     * siquiera eso —serían una LazyInitializationException al renderizar—.
     *
     * <p>Ordena por año descendente primero: lo que se está mirando casi siempre es el ciclo en
     * curso, y dejarlo mezclado por nombre de materia obligaría a buscarlo.
     *
     * <p><b>Dónde queda el WHERE del tenant.</b> {@code m.institucionId} por la materia,
     * {@code p.institucionId} por el período y {@code cl.institucionId} por el ciclo: las tres
     * entidades del JOIN son tenant-scoped y el filtro de Hibernate no se propaga (TD-003).
     */
    @Query("""
        SELECT c FROM Comision c
        JOIN c.materia m
        JOIN FETCH c.periodo p
        JOIN FETCH p.ciclo cl
        WHERE m.institucionId  = :tenantId
          AND p.institucionId  = :tenantId
          AND cl.institucionId = :tenantId
        ORDER BY cl.anio DESC, p.orden ASC, c.activo DESC, m.nombre, c.codigo
        """)
    List<Comision> findAllDelTenant(@Param("tenantId") Long tenantId);

    /**
     * Las comisiones de un ciclo concreto. Es la consulta que usa la pantalla cuando se elige
     * un año, y la que lee el copiado para armar la oferta del año siguiente.
     *
     * <p><b>Dónde queda el WHERE del tenant.</b> Igual que arriba, uno por entidad: materia,
     * período y ciclo.
     */
    @Query("""
        SELECT c FROM Comision c
        JOIN FETCH c.materia m
        JOIN FETCH c.periodo p
        JOIN FETCH p.ciclo cl
        LEFT JOIN FETCH c.docenteAsignado d
        WHERE m.institucionId  = :tenantId
          AND p.institucionId  = :tenantId
          AND cl.institucionId = :tenantId
          AND cl.id = :cicloId
        ORDER BY p.orden ASC, c.activo DESC, m.nombre, c.codigo
        """)
    List<Comision> findDelCiclo(@Param("cicloId") Long cicloId,
                                @Param("tenantId") Long tenantId);

    // Busca una comisión por su código dentro de la materia y el período. Desde V023 hacen
    // falta los tres: el mismo código convive entre años a proposito.
    Optional<Comision> findByMateriaIdAndCodigoAndPeriodoId(Long materiaId, String codigo,
                                                            Long periodoId);

    // Indica si el código ya está tomado en esa materia dentro de ese período. Desde V023 el
    // período entra en la clave: el mismo código se repite entre años a proposito.
    boolean existsByMateriaIdAndCodigoAndPeriodoId(Long materiaId, String codigo, Long periodoId);

    // Cuenta comisiones activas de una materia, para bloquear su baja.
    long countByMateriaIdAndActivoTrue(Long materiaId);

    // Cuenta comisiones activas asignadas a un docente - para bloquear su baja.
    long countByDocenteAsignadoIdAndActivoTrue(Long docenteId);

    /**
     * Comisiones activas de una materia que todavía cuelgan de un ciclo abierto.
     *
     * <p>Es lo que decide si la materia se puede sacar del plan. Antes de V023 alcanzaba con
     * contar las comisiones activas, y eso dejaba a la materia atada para siempre: una comisión
     * de 2026 seguía siendo "activa" en 2030 e impedía sacarla. Ahora las de ciclos cerrados no
     * cuentan — son historia, no oferta vigente.
     *
     * <p><b>Dónde queda el WHERE del tenant.</b> {@code m.institucionId}, {@code p.institucionId}
     * y {@code cl.institucionId} (TD-003).
     */
    @Query("""
        SELECT COUNT(c) FROM Comision c
        JOIN c.materia m
        JOIN c.periodo p
        JOIN p.ciclo cl
        WHERE m.institucionId  = :tenantId
          AND p.institucionId  = :tenantId
          AND cl.institucionId = :tenantId
          AND m.id = :materiaId
          AND c.activo = true
          AND cl.estado <> edu.cent35.asistencias.model.EstadoCiclo.CERRADO
        """)
    long contarActivasEnCiclosAbiertos(@Param("materiaId") Long materiaId,
                                       @Param("tenantId") Long tenantId);

    /**
     * Las activas con materia activa, para los combos de los formularios.
     *
     * <p>Acotadas al ciclo que contiene la fecha desde V023: un combo que ofreciera las
     * comisiones de 2026 al cargar una asistencia de 2027 deja elegir una clase que ese año no
     * existe, y el error recién aparecería al guardar.
     *
     * <p><b>Dónde queda el WHERE del tenant.</b> {@code m.institucionId}, {@code p.institucionId}
     * y {@code cl.institucionId} (TD-003).
     */
    @Query("""
        SELECT c FROM Comision c
        JOIN FETCH c.materia m
        JOIN c.periodo p
        JOIN p.ciclo cl
        WHERE m.institucionId  = :tenantId
          AND p.institucionId  = :tenantId
          AND cl.institucionId = :tenantId
          AND c.activo = true
          AND m.activo = true
          AND :fecha BETWEEN p.fechaInicio AND p.fechaFin
        ORDER BY m.nombre, c.codigo
        """)
    List<Comision> findActivasEnFecha(@Param("fecha") LocalDate fecha,
                                      @Param("tenantId") Long tenantId);
}
