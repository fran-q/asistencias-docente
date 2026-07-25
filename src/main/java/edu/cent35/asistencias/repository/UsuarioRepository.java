package edu.cent35.asistencias.repository;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de usuarios. Aunque el filtro de Hibernate ya acota por institución, casi todos
 * los métodos siguen recibiendo el institucionId de forma explícita: es la segunda capa de la
 * defensa multi-tenant y no depende de que el filtro esté activo (ADR-0004).
 */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Busca por username dentro de una institucion concreta.
    Optional<Usuario> findByUsernameAndInstitucionId(String username, Long institucionId);

    // Variante por email.
    Optional<Usuario> findByEmailAndInstitucionId(String email, Long institucionId);

    // Busqueda global para el login, cuando todavia no se sabe la institucion. Devuelve lista
    // porque el username solo es unico dentro de cada institucion.
    List<Usuario> findByUsername(String username);

    // Indica si el username ya está tomado en esa institución.
    boolean existsByUsernameAndInstitucionId(String username, Long institucionId);

    // Indica si el email ya está tomado en esa institución.
    boolean existsByEmailAndInstitucionId(String email, Long institucionId);

    // Lista los usuarios activos de una institucion.
    List<Usuario> findByInstitucionIdAndActivoTrueOrderByApellidoAscNombreAsc(Long institucionId);

    // Lista todos los usuarios de una institucion (activos e inactivos).
    List<Usuario> findByInstitucionIdOrderByActivoDescApellidoAscNombreAsc(Long institucionId);

    // Cuenta usuarios activos de un rol; evita que la institucion se quede sin cuenta INSTITUCION.
    long countByInstitucionIdAndRolCodigoAndActivoTrue(Long institucionId, String rolCodigo);

    // Sella el ultimo login exitoso (RNF-10). Va por id porque el username no es unico global, y
    // lleva su propia transaccion porque el listener lo dispara fuera de una abierta.
    @Modifying
    @Transactional
    @Query("UPDATE Usuario u SET u.ultimoLogin = :momento WHERE u.id = :id")
    int registrarUltimoLogin(@Param("id") Long id, @Param("momento") LocalDateTime momento);
}
