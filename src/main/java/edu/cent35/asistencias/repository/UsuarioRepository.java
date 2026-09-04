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

    // Variante global por email, para la recuperacion de contrasena, que ocurre antes del login.
    // Devuelve lista por el mismo motivo: el email tampoco es unico entre instituciones.
    List<Usuario> findByEmailIgnoreCase(String email);

    // Indica si el username ya está tomado en esa institución.
    boolean existsByUsernameAndInstitucionId(String username, Long institucionId);

    // Indica si el email ya está tomado en esa institución.
    boolean existsByEmailAndInstitucionId(String email, Long institucionId);

    // La cuenta de acceso de una persona, si la tiene. Se usa para avisar, antes de editar una
    // identidad desde la pantalla de docentes, que el cambio tambien alcanza a su cuenta.
    @Query("""
        SELECT u FROM Usuario u
        JOIN FETCH u.persona p
        WHERE p.institucionId = :institucionId
          AND p.id = :personaId
        """)
    Optional<Usuario> cuentaDe(@Param("institucionId") Long institucionId,
                               @Param("personaId") Long personaId);

    /**
     * Lista los usuarios activos de una institucion, ordenados por el nombre de su persona.
     *
     * <p><b>LEFT JOIN y no JOIN, desde V018.</b> La cuenta institucional no tiene persona, y un
     * JOIN comun la dejaria afuera del listado: la unica cuenta que administra el sistema
     * desapareceria de la pantalla que administra las cuentas.
     *
     * <p><b>Y por eso el WHERE del tenant se corrio a la raiz.</b> Antes filtraba por
     * {@code p.institucionId}, que ademas de acotar hacia de JOIN implicito. Con cuentas sin
     * persona ese filtro nunca se cumpliria para ellas, asi que ahora acota por
     * {@code u.institucionId}: Usuario es tenant-scoped por si mismo y no depende de que haya
     * alguien del otro lado.
     */
    @Query("""
        SELECT u FROM Usuario u
        LEFT JOIN FETCH u.persona p
        WHERE u.institucionId = :institucionId
          AND u.activo = true
        ORDER BY p.apellido ASC, p.nombre ASC
        """)
    List<Usuario> listarActivosDelTenant(@Param("institucionId") Long institucionId);

    // Lista todos los usuarios de una institucion, activos e inactivos. Mismo criterio que el
    // anterior: LEFT JOIN para no perder la cuenta institucional y el tenant acotado en la raiz.
    @Query("""
        SELECT u FROM Usuario u
        LEFT JOIN FETCH u.persona p
        WHERE u.institucionId = :institucionId
        ORDER BY u.activo DESC, p.apellido ASC, p.nombre ASC
        """)
    List<Usuario> listarDelTenant(@Param("institucionId") Long institucionId);

    // Cuenta usuarios activos de un rol; evita que la institucion se quede sin cuenta INSTITUCION.
    long countByInstitucionIdAndRolCodigoAndActivoTrue(Long institucionId, String rolCodigo);

    // Sella el ultimo login exitoso (RNF-10). Va por id porque el username no es unico global, y
    // lleva su propia transaccion porque el listener lo dispara fuera de una abierta.
    @Modifying
    @Transactional
    @Query("UPDATE Usuario u SET u.ultimoLogin = :momento WHERE u.id = :id")
    int registrarUltimoLogin(@Param("id") Long id, @Param("momento") LocalDateTime momento);
}
