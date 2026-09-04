package edu.cent35.asistencias.interceptor;

import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.Set;

/**
 * Deja a las cuentas sin verificar únicamente en la pantalla de su propia cuenta, que es donde
 * pueden pedir y cargar el código. Cualquier otra ruta las devuelve ahí, de modo que nadie
 * opere el sistema con un correo que todavía no demostró controlar.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VerificacionInterceptor implements HandlerInterceptor {

    // Lo unico accesible sin verificar. /mi-cuenta es donde se pide y se carga el codigo, y
    // logout tiene que seguir disponible: encerrar a alguien sin poder salir seria peor.
    private static final Set<String> RUTAS_PERMITIDAS = Set.of(
        "/mi-cuenta", "/mi-cuenta/enviar-codigo", "/mi-cuenta/verificar", "/logout"
    );

    private final UsuarioRepository usuarioRepository;

    // Deja pasar solo si la cuenta ya confirmo su correo; si no, la manda a verificarlo.
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof UsuarioAutenticado principal)) {
            return true;                       // sin sesion: lo resuelve Spring Security
        }

        String ruta = request.getRequestURI();
        if (RUTAS_PERMITIDAS.contains(ruta)) {
            return true;
        }

        // El principal se arma al iniciar sesion, asi que su dato de verificacion envejece.
        // Se usa como via rapida: si YA figuraba verificado no puede haberse desverificado, y
        // se evita la consulta. Si figura sin verificar, se relee de la base, porque puede
        // haberse verificado en esta misma sesion y seria un encierro dejarlo afuera.
        if (principal.isEmailVerificado()) {
            return true;
        }
        Optional<Usuario> usuario = usuarioRepository.findById(principal.getUsuarioId());
        if (usuario.isPresent() && usuario.get().getEmailVerificadoEn() != null) {
            principal.marcarEmailVerificado();
            return true;
        }

        log.debug("Acceso a {} bloqueado: la cuenta {} todavia no verifico su correo",
                  ruta, principal.getUsuarioId());
        response.sendRedirect(request.getContextPath() + "/mi-cuenta?verificacion-requerida");
        return false;
    }
}
