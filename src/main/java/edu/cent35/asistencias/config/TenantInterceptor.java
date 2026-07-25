package edu.cent35.asistencias.config;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.config.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Setea el TenantContext al inicio de cada request a partir del usuario autenticado y lo
 * limpia al terminar, además de dejar tenantId y userId en el MDC para que los logs sean
 * trazables (RNF-10). Corre también en endpoints públicos como /login, pero ahí no hay
 * principal y el filtro de Hibernate simplemente no se activa.
 */
@Component
@Slf4j
public class TenantInterceptor implements HandlerInterceptor {

    // Antes del controller: si hay usuario logueado, publica su tenant e id en contexto y MDC.
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof CustomUserDetails user) {

            TenantContext.set(user.getInstitucionId());
            MDC.put("tenantId", String.valueOf(user.getInstitucionId()));
            MDC.put("userId", String.valueOf(user.getUsuarioId()));

            log.debug("TenantContext seteado: tenantId={}, userId={}",
                      user.getInstitucionId(), user.getUsuarioId());
        }
        return true;
    }

    // Al cerrar el request: limpia todo, porque el hilo vuelve al pool de Tomcat.
    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        TenantContext.clear();
        MDC.remove("tenantId");
        MDC.remove("userId");
    }
}
