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
 * Carga el usuario desde la tabla usuarios para que Spring Security lo autentique. Acepta el
 * nombre de usuario o el correo: un usuario no puede llevar "@" (UsuarioValido), así que si lo
 * tiene es un correo, y se lo busca sin distinguir mayúsculas.
 *
 * <p>La búsqueda es global aunque el usuario y el correo solo sean únicos por institución: si
 * dos instituciones llegaran a repetirlo, el login falla con mensaje genérico en vez de elegir
 * uno al azar, y la solución definitiva sería sumar un selector de institución en la pantalla de
 * login. Con el correo es más fácil que pase —una persona que administra dos institutos— y la
 * salida es la misma: entrar con el usuario.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CargadorDeUsuarios implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    // Devuelve el principal, o falla con el mismo mensaje si no existe, hay ambigüedad o está inactivo.
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String ingresado) throws UsernameNotFoundException {
        String limpio = ingresado == null ? "" : ingresado.trim();
        boolean esCorreo = limpio.indexOf('@') >= 0;
        String que = esCorreo ? "correo" : "username";

        List<Usuario> matches = esCorreo
            ? usuarioRepository.findByEmailIgnoreCase(limpio)
            : usuarioRepository.findByUsername(limpio);

        if (matches.isEmpty()) {
            log.debug("Login fallido: {} '{}' no existe en la BD", que, limpio);
            throw new UsernameNotFoundException("Usuario o contraseña incorrectos");
        }

        if (matches.size() > 1) {
            log.warn("{} '{}' encontrado en {} instituciones distintas - ambiguedad. {}",
                     que, limpio, matches.size(),
                     esCorreo ? "Tiene que entrar con el usuario." : "Se requiere selector de tenant.");
            throw new UsernameNotFoundException("Usuario o contraseña incorrectos");
        }

        Usuario usuario = matches.get(0);

        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            log.debug("Login fallido: usuario '{}' esta inactivo", usuario.getUsername());
            throw new UsernameNotFoundException("Usuario o contraseña incorrectos");
        }

        log.debug("Usuario encontrado por {}: id={}, institucion_id={}, rol={}",
                  que, usuario.getId(), usuario.getInstitucionId(), usuario.getRol().getCodigo());

        return new UsuarioAutenticado(usuario);
    }
}
