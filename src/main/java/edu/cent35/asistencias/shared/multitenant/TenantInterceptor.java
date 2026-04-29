package edu.cent35.asistencias.shared.multitenant;

import edu.cent35.asistencias.shared.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Setea el {@link TenantContext} al inicio de cada request HTTP a partir
 * del usuario autenticado (si lo hay), y lo limpia al finalizar.
 * <p>
 * Tambien populariza el MDC con {@code tenantId} y {@code userId} para
 * que los logs traceen automaticamente a quien correspondia cada accion
 * (RNF-10 sobre trazabilidad multi-tenant).
 * <p>
 * <b>Importante</b>: este interceptor corre incluso para endpoints
 * publicos (ej: {@code /login}), pero solo setea el contexto si hay
 * un {@link CustomUserDetails} en la {@code SecurityContext}. Para
 * requests anonimos, los campos quedan en N/A y el filtro de Hibernate
 * no se activa.
 */
@Component
@Slf4j
public class TenantInterceptor implements HandlerInterceptor {

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
