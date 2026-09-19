package edu.cent35.asistencias.seguridad;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Las sesiones abiertas de una cuenta: cuáles hay y cómo se cierran.
 *
 * <p>Una cuenta se usa en un equipo por vez. No es una restricción técnica sino de uso: la
 * cuenta de la institución es una sola y la comparten, así que sin esto no hay forma de saber
 * quién está adentro ni de sacar a alguien que quedó con la sesión abierta en una máquina que
 * ya no controla. Con el control puesto, entrar desde otro equipo avisa qué se va a cerrar y
 * lo decide quien está entrando (ADR-0020).
 *
 * <p><b>Por qué se busca por nombre de usuario y no por el principal.</b>
 * {@link SessionRegistry} indexa por el objeto del principal, y {@link UsuarioAutenticado} no
 * define {@code equals}: dos ingresos de la misma cuenta producen dos instancias distintas y
 * el registro las guarda como si fueran dos personas. Comparando el nombre de usuario, que es
 * único en el sistema, las sesiones de una cuenta se encuentran siempre.
 *
 * <p>Las sesiones se cierran con {@code expireNow()}: no se borran acá. El pedido siguiente de
 * ese equipo lo atiende {@code ConcurrentSessionFilter}, que lo saca y lo manda al login con el
 * motivo. Invalidar la sesión desde otro hilo dejaría a esa pantalla sin ninguna explicación.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SesionesActivasService {

    private final SessionRegistry registro;

    /**
     * Las demás sesiones abiertas de esa cuenta, de la más usada recientemente a la más vieja.
     *
     * @param sesionActual el id de la sesión que hace el pedido, que no se devuelve
     */
    public List<SessionInformation> otrasDe(String username, String sesionActual) {
        return registro.getAllPrincipals().stream()
            .filter(p -> esDe(p, username))
            // false: las vencidas no cuentan. Son las que ya fueron desplazadas y todavía no
            // volvieron a pedir nada, y anunciarlas mandaria a cerrar algo que ya esta cerrado.
            .flatMap(p -> registro.getAllSessions(p, false).stream())
            .filter(s -> !s.getSessionId().equals(sesionActual))
            .sorted(Comparator.comparing(SessionInformation::getLastRequest).reversed())
            .toList();
    }

    /**
     * Cierra las demás sesiones de esa cuenta y devuelve cuántas cerró.
     *
     * <p>Lo pide quien está entrando desde otro equipo, después de ver qué se va a cerrar.
     */
    public int desplazarOtras(String username, String sesionActual) {
        List<SessionInformation> otras = otrasDe(username, sesionActual);
        otras.forEach(SessionInformation::expireNow);
        if (!otras.isEmpty()) {
            log.info("Sesiones desplazadas: cuenta={}, cerradas={}", username, otras.size());
        }
        return otras.size();
    }

    /**
     * Cierra todas las sesiones de esa cuenta.
     *
     * <p>Lo usa la baja de un usuario: la cuenta ya no puede entrar, pero la sesión que tenía
     * abierta seguiría funcionando hasta vencer, que es media hora de alguien operando con una
     * cuenta que la institución acaba de dar de baja.
     */
    public int cerrarTodasDe(String username) {
        int cerradas = desplazarOtras(username, null);
        if (cerradas > 0) {
            log.info("Sesiones cerradas por baja de la cuenta {}: {}", username, cerradas);
        }
        return cerradas;
    }

    // El principal del registro es el UserDetails con el que se entro; se compara su usuario.
    private static boolean esDe(Object principal, String username) {
        return principal instanceof UserDetails u && u.getUsername().equals(username);
    }
}
