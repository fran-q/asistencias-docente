package edu.cent35.asistencias.shared.security;

import edu.cent35.asistencias.usuario.domain.Usuario;
import edu.cent35.asistencias.usuario.infrastructure.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Carga el {@link UserDetails} desde la tabla {@code usuarios}.
 * Reemplaza al {@code InMemoryUserDetailsManager} provisorio del Sprint 0.
 * <p>
 * <b>Resolucion del usuario por username</b>: como el username solo es
 * unico dentro de una institucion (UQ {@code institucion_id, username}),
 * podriamos tener colisiones globales. En la primera version del login
 * (Sprint 1) los seeds de prueba usan usernames distintivos por
 * institucion ({@code superadmin.cent35} vs {@code superadmin.utf}), de
 * modo que la busqueda global devuelve un unico usuario.
 * <p>
 * Si en el futuro habilitamos un selector de institucion en el login
 * para soportar usernames repetidos, este service evoluciona para
 * recibir tambien el {@code institucionId}.
 * <p>
 * Si la query devuelve >1 usuarios (colision global), el login falla con
 * mensaje generico para no filtrar la existencia de tenants distintos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        List<Usuario> matches = usuarioRepository.findByUsername(username);

        if (matches.isEmpty()) {
            log.debug("Login fallido: username '{}' no existe en la BD", username);
            throw new UsernameNotFoundException("Usuario o contraseña incorrectos");
        }

        if (matches.size() > 1) {
            log.warn("Username '{}' encontrado en {} instituciones distintas - " +
                     "ambiguedad. Se requiere selector de tenant.", username, matches.size());
            throw new UsernameNotFoundException("Usuario o contraseña incorrectos");
        }

        Usuario usuario = matches.get(0);

        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            log.debug("Login fallido: usuario '{}' esta inactivo", username);
            throw new UsernameNotFoundException("Usuario o contraseña incorrectos");
        }

        log.debug("Usuario encontrado: id={}, institucion_id={}, rol={}",
                  usuario.getId(), usuario.getInstitucionId(), usuario.getRol().getCodigo());

        return new CustomUserDetails(usuario);
    }
}
