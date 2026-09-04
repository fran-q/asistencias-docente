package edu.cent35.asistencias.repository;

import edu.cent35.asistencias.model.Docente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de vínculos docentes (ADR-0016): cada fila es un período laboral, no una persona.
 * Desde V016 el nombre y el documento viven en {@code Persona}, así que las consultas hacen
 * JOIN contra una entidad tenant-scoped y llevan el {@code WHERE institucionId} explícito, más
 * el JOIN FETCH que evita que la persona quede sin cargar al renderizar la vista.
 */
@Repository
public interface DocenteRepository extends JpaRepository<Docente, Long> {

    // Todos los vínculos de la institución, ordenados por apellido y nombre de la persona.
    @Query("""
        SELECT d FROM Docente d
        JOIN FETCH d.persona p
        WHERE p.institucionId = :tenantId
        ORDER BY d.activo DESC, p.apellido ASC, p.nombre ASC
        """)
    List<Docente> listarDelTenant(@Param("tenantId") Long tenantId);

    // Solo los vínculos vigentes, para los selectores que asignan materias y comisiones.
    @Query("""
        SELECT d FROM Docente d
        JOIN FETCH d.persona p
        WHERE p.institucionId = :tenantId
          AND d.activo = true
        ORDER BY p.apellido ASC, p.nombre ASC
        """)
    List<Docente> listarVigentesDelTenant(@Param("tenantId") Long tenantId);

    // Un vínculo con su persona ya cargada. Reemplaza al findById cuando hace falta el nombre:
    // findById no pasa por el filtro de tenant y además dejaría la persona sin traer.
    @Query("""
        SELECT d FROM Docente d
        JOIN FETCH d.persona p
        WHERE p.institucionId = :tenantId
          AND d.id = :id
        """)
    Optional<Docente> buscarDelTenant(@Param("tenantId") Long tenantId,
                                      @Param("id") Long id);

    // Los períodos de una persona, del más reciente al más viejo. Es lo que permite responder
    // desde cuándo y hasta cuándo trabajó, incluidas las idas y vueltas.
    @Query("""
        SELECT d FROM Docente d
        JOIN d.persona p
        WHERE p.institucionId = :tenantId
          AND p.id = :personaId
        ORDER BY d.fechaAlta DESC
        """)
    List<Docente> periodosDe(@Param("tenantId") Long tenantId,
                             @Param("personaId") Long personaId);

    // El vínculo vigente de una persona, si lo tiene. Con varios períodos posibles, "el
    // docente" sin más deja de estar definido: hay que preguntar por el que está abierto.
    @Query("""
        SELECT d FROM Docente d
        JOIN FETCH d.persona p
        WHERE p.institucionId = :tenantId
          AND p.id = :personaId
          AND d.activo = true
        """)
    Optional<Docente> vinculoVigenteDe(@Param("tenantId") Long tenantId,
                                       @Param("personaId") Long personaId);

    // Indica si el legajo ya está tomado por un vínculo vigente. El legajo dejó de ser único
    // en la base porque un reingreso reutiliza el del período anterior; lo que no puede haber
    // son dos vínculos abiertos con el mismo.
    @Query("""
        SELECT COUNT(d) > 0 FROM Docente d
        JOIN d.persona p
        WHERE p.institucionId = :tenantId
          AND d.legajo = :legajo
          AND d.activo = true
        """)
    boolean existeLegajoVigente(@Param("tenantId") Long tenantId,
                                @Param("legajo") String legajo);

    // Igual que el anterior pero ignorando un vínculo, para la edición.
    @Query("""
        SELECT COUNT(d) > 0 FROM Docente d
        JOIN d.persona p
        WHERE p.institucionId = :tenantId
          AND d.legajo = :legajo
          AND d.activo = true
          AND d.id <> :idExcluido
        """)
    boolean existeLegajoVigenteEnOtro(@Param("tenantId") Long tenantId,
                                      @Param("legajo") String legajo,
                                      @Param("idExcluido") Long idExcluido);
}
