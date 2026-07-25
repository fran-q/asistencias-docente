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

    // Variante por email.
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

    // Lista los usuarios activos de una institucion.
    List<Usuario> findByInstitucionIdAndActivoTrueOrderByApellidoAscNombreAsc(Long institucionId);

    // Lista todos los usuarios de una institucion (activos e inactivos).
    List<Usuario> findByInstitucionIdOrderByActivoDescApellidoAscNombreAsc(Long institucionId);

    /**
     * Cuenta cuantos usuarios con un rol especifico estan activos en una institucion.
     * Se usa para evitar que la institucion se quede sin cuenta INSTITUCION
     * (ej: la ultima cuenta INSTITUCION no puede desactivarse a si misma).
     */
    long countByInstitucionIdAndRolCodigoAndActivoTrue(Long institucionId, String rolCodigo);

    /**
     * Sella la fecha/hora del ultimo login exitoso (dato de auditoria, RNF-10).
     * <p>
     * Se hace por <b>id</b> y no por username porque el username solo es unico
     * dentro de una institucion. Lleva su propia transaccion porque lo dispara
     * {@link edu.cent35.asistencias.config.RegistroUltimoLoginListener} durante
     * el login, fuera de cualquier transaccion abierta.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Usuario u SET u.ultimoLogin = :momento WHERE u.id = :id")
    int registrarUltimoLogin(@Param("id") Long id, @Param("momento") LocalDateTime momento);
}
