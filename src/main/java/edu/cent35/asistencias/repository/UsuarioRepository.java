package edu.cent35.asistencias.repository;

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

    // Busca por username dentro de una institución concreta.
    Optional<Usuario> findByUsernameAndInstitucionId(String username, Long institucionId);

    // Variante por email.
    Optional<Usuario> findByEmailAndInstitucionId(String email, Long institucionId);

    // Búsqueda global para el login, cuando todavía no se sabe la institución. Devuelve lista
    // porque el username solo es único dentro de cada institución.
    List<Usuario> findByUsername(String username);

    // Variante global por email, para la recuperación de contraseña, que ocurre antes del login.
    // Devuelve lista por el mismo motivo: el email tampoco es único entre instituciones.
    List<Usuario> findByEmailIgnoreCase(String email);

    // Indica si el username ya está tomado en esa institución.
    boolean existsByUsernameAndInstitucionId(String username, Long institucionId);

    /**
     * Cuántas cuentas usan ese nombre de usuario en <b>cualquier</b> institución, sin contar una.
     *
     * <p>Nativa a propósito: el filtro de tenant la limitaría a la institución actual, y el login
     * busca el usuario en todas ({@code CargadorDeUsuarios}). Sin JOIN no hay nada más que
     * filtrar, así que la regla de oro del multi-tenant no aplica acá.
     */
    @Query(value = "SELECT COUNT(*) FROM usuarios WHERE username = :username AND id <> :excepto",
           nativeQuery = true)
    long contarUsernameEnOtrasCuentas(@Param("username") String username,
                                      @Param("excepto") Long excepto);

    // Indica si el email ya está tomado en esa institución.
    boolean existsByEmailAndInstitucionId(String email, Long institucionId);

    // La cuenta de acceso de una persona, si la tiene. Se usa para avisar, antes de editar una
    // identidad desde la pantalla de docentes, que el cambio también alcanza a su cuenta.
    @Query("""
        SELECT u FROM Usuario u
        JOIN FETCH u.persona p
        WHERE p.institucionId = :institucionId
          AND p.id = :personaId
        """)
    Optional<Usuario> cuentaDe(@Param("institucionId") Long institucionId,
                               @Param("personaId") Long personaId);

    /**
     * Lista los usuarios activos de una institución, ordenados por el nombre de su persona.
     *
     * <p><b>LEFT JOIN y no JOIN, desde V018.</b> La cuenta institucional no tiene persona, y un
     * JOIN común la dejaría afuera del listado: la única cuenta que administra el sistema
     * desaparecería de la pantalla que administra las cuentas.
     *
     * <p><b>Y por eso el WHERE del tenant se corrió a la raíz.</b> Antes filtraba por
     * {@code p.institucionId}, que además de acotar hacía de JOIN implícito. Con cuentas sin
     * persona ese filtro nunca se cumpliria para ellas, así que ahora acota por
     * {@code u.institucionId}: Usuario es tenant-scoped por sí mismo y no depende de que haya
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

    // Lista todos los usuarios de una institución, activos e inactivos. Mismo criterio que el
    // anterior: LEFT JOIN para no perder la cuenta institucional y el tenant acotado en la raíz.
    @Query("""
        SELECT u FROM Usuario u
        LEFT JOIN FETCH u.persona p
        WHERE u.institucionId = :institucionId
        ORDER BY u.activo DESC, p.apellido ASC, p.nombre ASC
        """)
    List<Usuario> listarDelTenant(@Param("institucionId") Long institucionId);

    // Cuenta usuarios activos de un rol; evita que la institución se quede sin cuenta INSTITUCION.
    long countByInstitucionIdAndRolCodigoAndActivoTrue(Long institucionId, String rolCodigo);

    // Sella el último login exitoso (RNF-10). Va por id porque el username no es único global, y
    // lleva su propia transacción porque el listener lo dispara fuera de una abierta.
    @Modifying
    @Transactional
    @Query("UPDATE Usuario u SET u.ultimoLogin = :momento WHERE u.id = :id")
    int registrarUltimoLogin(@Param("id") Long id, @Param("momento") LocalDateTime momento);
}
