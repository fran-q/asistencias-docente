package edu.cent35.asistencias.interceptor;

import edu.cent35.asistencias.service.InstalacionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * En una instalación sin configurar, cualquier pantalla lleva al asistente de primer arranque
 * (ADR-0022).
 *
 * <p>Sin esto, abrir el acceso directo recién instalado termina en el login: una pantalla que
 * pide un usuario que todavía no existe y no ofrece ningún camino para crearlo, porque el alta
 * pública está apagada en modo local. El sistema quedaría instalado y sin forma de entrar, y la
 * salida sería un instructivo — que es justo lo que este despliegue no puede pedir.
 *
 * <p>Se apaga solo: apenas existe una institución, {@code disponible()} responde que no y este
 * interceptor deja de redirigir para siempre. En desarrollo nunca se enciende, así que no
 * cuesta ni una consulta.
 */
@Component
@RequiredArgsConstructor
public class InstalacionInterceptor implements HandlerInterceptor {

    private final InstalacionService instalacion;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!instalacion.disponible()) {
            return true;
        }
        response.sendRedirect(request.getContextPath() + "/instalacion");
        return false;
    }
}
