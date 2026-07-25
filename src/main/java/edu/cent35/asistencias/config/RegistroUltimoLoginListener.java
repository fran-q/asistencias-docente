package edu.cent35.asistencias.config;

import edu.cent35.asistencias.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Sella {@code usuarios.ultimo_login} cada vez que alguien se autentica.
 * <p>
 * Escucha el {@link AuthenticationSuccessEvent} que publica Spring Security en
 * vez de engancharse a un {@code AuthenticationSuccessHandler} del form login:
 * asi no se toca la cadena de redireccion post-login y el sello queda
 * desacoplado de la configuracion de seguridad.
 * <p>
 * El evento se dispara una sola vez, en el login real. Los requests
 * siguientes viajan con la sesion ya establecida y no re-autentican, con lo
 * cual no vuelven a disparar el evento.
 * <p>
 * Es un dato de auditoria (RNF-10): si el UPDATE falla, se loguea y se sigue.
 * Nadie se queda sin poder entrar al sistema porque no se pudo escribir el
 * sello.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RegistroUltimoLoginListener {

    private final UsuarioRepository usuarioRepository;

    /** Reloj inyectable para tests (Clock.systemDefaultZone() por default). */
    private Clock clock = Clock.systemDefaultZone();

    @EventListener
    public void registrarUltimoLogin(AuthenticationSuccessEvent evento) {
        Object principal = evento.getAuthentication().getPrincipal();
        if (!(principal instanceof CustomUserDetails detalle)) {
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

    /** Solo para tests: permite fijar el reloj. */
    void setClock(Clock clock) {
        this.clock = clock;
    }
}
