package edu.cent35.asistencias.seguridad;

import edu.cent35.asistencias.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Sella usuarios.ultimo_login cada vez que alguien se autentica (RNF-10). Escucha el evento
 * de Spring Security en lugar de enganchar un AuthenticationSuccessHandler para no tocar la
 * redirección post-login, y si el UPDATE falla solo lo registra: nadie se queda afuera del
 * sistema porque no se pudo escribir un dato de auditoría.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RegistroUltimoLoginListener {

    private final UsuarioRepository usuarioRepository;

    // Reloj inyectable para tests (Clock.systemDefaultZone() por default).
    private Clock clock = Clock.systemDefaultZone();

    // Corre una sola vez por login real; los requests siguientes ya viajan con la sesión hecha.
    @EventListener
    public void registrarUltimoLogin(AuthenticationSuccessEvent evento) {
        Object principal = evento.getAuthentication().getPrincipal();
        if (!(principal instanceof UsuarioAutenticado detalle)) {
            return;
        }
        try {
            usuarioRepository.registrarUltimoLogin(detalle.getUsuarioId(), LocalDateTime.now(clock));
            log.debug("Ultimo login sellado para usuario id={}", detalle.getUsuarioId());
        } catch (RuntimeException ex) {
            log.warn("No se pudo registrar el ultimo login de '{}': {}",
                     detalle.getUsername(), ex.toString());
        }
    }

    // Solo para tests: permite fijar el reloj.
    void setClock(Clock clock) {
        this.clock = clock;
    }
}
