package edu.cent35.asistencias.usuario.infrastructure;

import edu.cent35.asistencias.usuario.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA de usuarios.
 * <p>
 * <b>IMPORTANTE</b>: en Fase B se activara el filtro de Hibernate por
 * tenant, lo que hara que <i>casi todos</i> los metodos {@code findXxx}
 * filtren automaticamente por la institucion del request actual.
 * <p>
 * Mientras tanto (Fase A), los metodos definidos a continuacion incluyen
 * <b>institucionId explicitamente</b> para evitar fugas entre tenants.
 * Cuando el filtro este activo podremos quitar el parametro y simplificar.
 */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     * Busca un usuario por username dentro de una institucion.
     * Util para el {@code UserDetailsService} de Spring Security.
     */
    Optional<Usuario> findByUsernameAndInstitucionId(String username, Long institucionId);

    /** Variante por email. */
    Optional<Usuario> findByEmailAndInstitucionId(String email, Long institucionId);

    /**
     * Busqueda global por username (sin tenant). Util en el login,
     * cuando todavia no sabemos a que institucion pertenece.
     * El username NO es unico globalmente (lo es por institucion),
     * por eso devuelve lista. En el login resolvemos por institucion
     * + username juntos cuando agreguemos el selector.
     */
    List<Usuario> findByUsername(String username);

    boolean existsByUsernameAndInstitucionId(String username, Long institucionId);

    boolean existsByEmailAndInstitucionId(String email, Long institucionId);

    /** Lista los usuarios activos de una institucion. */
    List<Usuario> findByInstitucionIdAndActivoTrueOrderByApellidoAscNombreAsc(Long institucionId);
}
