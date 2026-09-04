package edu.cent35.asistencias.seguridad;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Carga el usuario desde la tabla usuarios para que Spring Security lo autentique. Busca por
 * username global aunque el username solo sea único por institución: si dos instituciones
 * llegaran a repetirlo, el login falla con mensaje genérico en vez de elegir uno al azar, y
 * la solución definitiva sería sumar un selector de institución en la pantalla de login.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CargadorDeUsuarios implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    // Devuelve el principal, o falla con el mismo mensaje si no existe, hay ambigüedad o está inactivo.
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

        return new UsuarioAutenticado(usuario);
    }
}
