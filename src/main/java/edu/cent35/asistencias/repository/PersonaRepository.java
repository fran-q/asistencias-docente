package edu.cent35.asistencias.repository;

import edu.cent35.asistencias.model.Persona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de personas (ADR-0016), la identidad detrás de las cuentas y de los vínculos
 * docentes.
 * Todas las consultas llevan el {@code institucionId} explícito aunque la entidad ya tenga
 * el filtro de Hibernate: es la misma persona física la que puede existir en dos
 * instituciones, así que una búsqueda por DNI sin acotar devolvería la fila de otra.
 */
@Repository
public interface PersonaRepository extends JpaRepository<Persona, Long> {

    // Busca por documento dentro de la institución. El DNI no es único a nivel sistema:
    // la misma persona en dos institutos son dos filas y cada una es de su institución.
    @Query("""
        SELECT p FROM Persona p
        WHERE p.institucionId = :tenantId
          AND p.dni = :dni
        """)
    Optional<Persona> buscarPorDni(@Param("tenantId") Long tenantId,
                                   @Param("dni") String dni);

    // Indica si el documento ya está tomado en esta institución, para rechazar duplicados.
    @Query("""
        SELECT COUNT(p) > 0 FROM Persona p
        WHERE p.institucionId = :tenantId
          AND p.dni = :dni
        """)
    boolean existeDni(@Param("tenantId") Long tenantId,
                      @Param("dni") String dni);

    // Igual que el anterior pero ignorando una persona, para la edición: al guardar sin
    // cambiar el documento, la propia fila no puede contar como duplicado.
    @Query("""
        SELECT COUNT(p) > 0 FROM Persona p
        WHERE p.institucionId = :tenantId
          AND p.dni = :dni
          AND p.id <> :idExcluido
        """)
    boolean existeDniEnOtra(@Param("tenantId") Long tenantId,
                            @Param("dni") String dni,
                            @Param("idExcluido") Long idExcluido);

    // Listado alfabético de la institución, para los selectores que eligen a quién vincular.
    @Query("""
        SELECT p FROM Persona p
        WHERE p.institucionId = :tenantId
        ORDER BY p.apellido ASC, p.nombre ASC
        """)
    List<Persona> listarDelTenant(@Param("tenantId") Long tenantId);
}
