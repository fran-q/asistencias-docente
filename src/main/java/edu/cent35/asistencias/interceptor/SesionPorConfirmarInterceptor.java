package edu.cent35.asistencias.interceptor;

import edu.cent35.asistencias.config.SecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Mientras no se decida qué hacer con la otra sesión abierta, esta no sirve para nada más.
 *
 * <p>Quien entra desde un segundo equipo queda autenticado --la contraseña era correcta-- pero
 * con la sesión marcada: hasta que confirme, cualquier pantalla lo devuelve al paso donde ve
 * qué se va a cerrar. Sin esto, la marca sería un cartel que se saltea escribiendo otra
 * dirección, y la sesión anterior seguiría abierta mientras las dos trabajan a la vez.
 *
 * <p>No corre sobre {@code /sesion/**} --ahí vive el propio paso-- ni sobre los estáticos: lo
 * decide {@code WebMvcConfig}, que es donde ya se nombra ese conjunto de rutas.
 *
 * <p>Cerrar sesión sí funciona: el logout lo atiende un filtro de Spring Security, antes de
 * que los interceptores lleguen a ver la petición. Es la salida de quien prefiere no desplazar
 * a nadie.
 */
@Component
public class SesionPorConfirmarInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        HttpSession sesion = request.getSession(false);
        if (sesion == null || sesion.getAttribute(SecurityConfig.SESION_POR_CONFIRMAR) == null) {
            return true;
        }
        response.sendRedirect(request.getContextPath() + "/sesion/otra-abierta");
        return false;
    }
}
