package edu.cent35.asistencias.interceptor;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.PuestoCaptura;
import edu.cent35.asistencias.seguridad.CookiePuesto;
import edu.cent35.asistencias.service.PuestoCapturaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Resuelve la institución desde el equipo autorizado cuando no hay ninguna sesión abierta,
 * para que el kiosco pueda tomar asistencia en los turnos sin personal (RF-84, ADR-0019).
 * <p>
 * <b>La sesión siempre gana.</b> Si hay usuario autenticado este interceptor no toca nada:
 * corre antes que {@link TenantInterceptor} y le deja el terreno libre. Sin esa regla, un
 * administrador de la institución A trabajando en la máquina de la institución B —que tiene
 * la cookie de B— podría terminar operando sobre los datos de B. Es la fuga que este orden
 * evita, y por eso la condición es "no hay sesión" y no "hay cookie de puesto".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KioscoTenantInterceptor implements HandlerInterceptor {

    private final PuestoCapturaService puestoService;

    /**
     * Antes del controller: si nadie inició sesión, publica en contexto la institución del
     * equipo.
     *
     * <p>Devuelve {@code true} siempre. Este interceptor <b>resuelve</b>, no autoriza: quien
     * rechaza al equipo sin credencial válida es {@link PuestoCapturaInterceptor}, que corre
     * después y ya sabe contra qué institución validar. Mezclar las dos cosas acá dejaría el
     * control de acceso repartido en dos lugares que hay que leer juntos para entenderlo.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if (haySesion()) {
            return true;
        }

        Optional<PuestoCaptura> puesto = CookiePuesto.leer(request)
            .flatMap(puestoService::resolverKiosco);

        if (puesto.isEmpty()) {
            // Sin sesion y sin credencial valida no hay institucion que publicar. El pedido
            // sigue su curso y muere en el control de acceso, que es donde corresponde.
            return true;
        }

        PuestoCaptura p = puesto.get();
        TenantContext.set(p.getInstitucionId());
        MDC.put("tenantId", String.valueOf(p.getInstitucionId()));
        MDC.put("puestoId", String.valueOf(p.getId()));

        log.debug("Tenant resuelto desde el puesto {}: institucion={}",
                  p.getId(), p.getInstitucionId());
        return true;
    }

    // Al cerrar el request: limpia lo propio, porque el hilo vuelve al pool de Tomcat.
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // TenantContext lo limpia TenantInterceptor en su propio afterCompletion, que corre
        // para todas las rutas. Repetirlo aca no haria dano, pero tener dos lugares que
        // limpian el mismo ThreadLocal invita a que alguien saque el equivocado.
        MDC.remove("puestoId");
    }

    // Si hay un usuario realmente autenticado, y no el anonimo que Spring pone por defecto.
    private boolean haySesion() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
            && auth.isAuthenticated()
            && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()));
    }
}
