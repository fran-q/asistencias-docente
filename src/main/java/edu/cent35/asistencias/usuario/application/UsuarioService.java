package edu.cent35.asistencias.usuario.application;

import edu.cent35.asistencias.shared.multitenant.TenantContext;
import edu.cent35.asistencias.usuario.domain.Rol;
import edu.cent35.asistencias.usuario.domain.RolCodigo;
import edu.cent35.asistencias.usuario.domain.Usuario;
import edu.cent35.asistencias.usuario.infrastructure.RolRepository;
import edu.cent35.asistencias.usuario.infrastructure.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Operaciones sobre los usuarios (admins) de la institucion del tenant
 * actual. Cubre RF-06.
 * <p>
 * <b>Aislamiento multi-tenant</b>: combina dos defensas:
 * <ol>
 *   <li>El filtro de Hibernate {@code "tenant"} (activado por
 *       {@code TenantFilterAspect}) hace que las queries automaticas
 *       de Spring Data filtren por institucion.</li>
 *   <li>{@link #ensureMismoTenant(Usuario)} valida explicitamente que
 *       cada usuario accedido pertenezca al tenant actual antes de
 *       devolver/modificar - protege casos donde el filtro no aplica
 *       (ej: {@code findById}).</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    /** Lista los usuarios de la institucion actual (activos + inactivos, ordenados). */
    @Transactional(readOnly = true)
    public List<Usuario> listarMiInstitucion() {
        Long tenantId = TenantContext.getRequired();
        return usuarioRepository.findByInstitucionIdOrderByActivoDescApellidoAscNombreAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorId(Long id) {
        Usuario u = usuarioRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + id));
        ensureMismoTenant(u);
        return u;
    }

    /**
     * Crea un usuario nuevo en la institucion del tenant actual.
     * El password viene en claro y se hashea con BCrypt antes de persistir.
     */
    @Transactional
    public Usuario crear(String username, String email, String passwordPlano,
                         String nombre, String apellido, RolCodigo rolCodigo) {

        Long tenantId = TenantContext.getRequired();

        if (usuarioRepository.existsByUsernameAndInstitucionId(username, tenantId)) {
            throw new IllegalArgumentException("El username '" + username + "' ya existe en esta institucion");
        }
        if (usuarioRepository.existsByEmailAndInstitucionId(email, tenantId)) {
            throw new IllegalArgumentException("El email '" + email + "' ya existe en esta institucion");
        }

        Rol rol = rolRepository.findByCodigo(rolCodigo.name())
            .orElseThrow(() -> new IllegalStateException("Rol no encontrado: " + rolCodigo));

        Usuario nuevo = Usuario.builder()
            .username(username.trim())
            .email(email.trim())
            .passwordHash(passwordEncoder.encode(passwordPlano))
            .nombre(nombre.trim())
            .apellido(apellido.trim())
            .rol(rol)
            .activo(true)
            .build();
        nuevo.setInstitucionId(tenantId);

        Usuario saved = usuarioRepository.save(nuevo);
        log.info("Usuario creado: id={}, username={}, rol={}, institucion_id={}",
                 saved.getId(), saved.getUsername(), rolCodigo, tenantId);
        return saved;
    }

    /**
     * Actualiza datos editables: nombre, apellido, email, rol, activo.
     * NO cambia username ni password (eso es separado).
     */
    @Transactional
    public Usuario actualizar(Long id, String nombre, String apellido, String email,
                              RolCodigo rolCodigo, boolean activo, Long usuarioActualId) {

        Usuario u = buscarPorId(id);

        // Si se cambia email, validar unicidad por institucion
        String emailNuevo = email.trim();
        if (!emailNuevo.equalsIgnoreCase(u.getEmail())
                && usuarioRepository.existsByEmailAndInstitucionId(emailNuevo, u.getInstitucionId())) {
            throw new IllegalArgumentException("El email '" + emailNuevo + "' ya existe en esta institucion");
        }

        // Reglas de negocio para evitar quedarse sin superadmin
        if (id.equals(usuarioActualId) && !activo) {
            throw new IllegalArgumentException(
                "No podes desactivarte a vos mismo. Pedile a otro superadmin que lo haga.");
        }
        if (id.equals(usuarioActualId)
                && RolCodigo.SUPERADMIN_INSTITUCION.name().equals(u.getRol().getCodigo())
                && rolCodigo != RolCodigo.SUPERADMIN_INSTITUCION) {
            throw new IllegalArgumentException(
                "No podes degradarte a vos mismo. Pedile a otro superadmin que lo haga.");
        }

        // Antes de cambiar a un usuario que es el ultimo superadmin activo
        boolean estaDesactivandoUltimoSuper =
            RolCodigo.SUPERADMIN_INSTITUCION.name().equals(u.getRol().getCodigo())
            && Boolean.TRUE.equals(u.getActivo())
            && (!activo || rolCodigo != RolCodigo.SUPERADMIN_INSTITUCION);

        if (estaDesactivandoUltimoSuper) {
            long superActivos = usuarioRepository.countByInstitucionIdAndRolCodigoAndActivoTrue(
                u.getInstitucionId(), RolCodigo.SUPERADMIN_INSTITUCION.name());
            if (superActivos <= 1) {
                throw new IllegalArgumentException(
                    "La institucion debe tener al menos un superadmin activo. " +
                    "Cambialo solo despues de promover a otro usuario a superadmin.");
            }
        }

        Rol rol = rolRepository.findByCodigo(rolCodigo.name())
            .orElseThrow(() -> new IllegalStateException("Rol no encontrado: " + rolCodigo));

        u.setNombre(nombre.trim());
        u.setApellido(apellido.trim());
        u.setEmail(emailNuevo);
        u.setRol(rol);
        u.setActivo(activo);

        Usuario saved = usuarioRepository.save(u);
        log.info("Usuario actualizado: id={}, username={}, rol={}, activo={}",
                 saved.getId(), saved.getUsername(), rolCodigo, activo);
        return saved;
    }

    /** Resetea la contrasena de un usuario. Solo el superadmin puede hacerlo. */
    @Transactional
    public void resetearPassword(Long id, String passwordPlanoNuevo) {
        Usuario u = buscarPorId(id);
        u.setPasswordHash(passwordEncoder.encode(passwordPlanoNuevo));
        usuarioRepository.save(u);
        log.info("Password reseteado para usuario id={}, username={}", u.getId(), u.getUsername());
    }

    /** Verifica que el usuario pertenezca al tenant actual; defensa en profundidad. */
    private void ensureMismoTenant(Usuario u) {
        Long tenantId = TenantContext.getRequired();
        if (!tenantId.equals(u.getInstitucionId())) {
            log.warn("Intento de acceso cross-tenant: tenantActual={}, usuarioInstitucion={}",
                     tenantId, u.getInstitucionId());
            // Camuflamos como "no encontrado" para no filtrar la existencia entre tenants
            throw new EntityNotFoundException("Usuario no encontrado");
        }
    }
}
