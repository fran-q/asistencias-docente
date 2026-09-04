package edu.cent35.asistencias.interceptor;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.seguridad.CookiePuesto;
import edu.cent35.asistencias.model.PuestoCaptura;
import edu.cent35.asistencias.service.PuestoCapturaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Deja pasar a las pantallas de captura biométrica solo si la petición viene de un equipo
 * designado como puesto (ADR-0015).
 *
 * <p>El control es contra el EQUIPO, no contra la persona ni contra el tamaño de la pantalla.
 * Un rol viaja con quien inicia sesión; el ancho de la ventana dice cuánto espacio hay para
 * dibujar y nada sobre qué máquina es. Lo que acá se verifica es una cookie que solo existe
 * en los navegadores que la institución autorizó.
 *
 * <p>Qué rutas alcanza lo decide {@link WebMvcConfig}. La supresión de datos biométricos
 * queda deliberadamente afuera: es un derecho ARCO, y condicionar su ejercicio a estar
 * frente a una máquina determinada sería ponerle una traba.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PuestoCapturaInterceptor implements HandlerInterceptor {

    /** Atributo con el que el controlador puede recuperar el puesto ya verificado. */
    public static final String ATRIBUTO_PUESTO = "puestoCaptura";

    // Cada cuanto se refresca "ultimo uso". El endpoint de reconocimiento recibe un cuadro
    // por segundo, y anotar la marca en cada uno seria un UPDATE por segundo para un dato
    // que solo sirve para reconocer que maquina es cual.
    private static final Duration REFRESCO_USO = Duration.ofMinutes(5);

    private final PuestoCapturaService puestoService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws IOException {

        // Sin tenant no hay contra que validar. Pasa de largo y lo resuelve la cadena de
        // seguridad, que para estas rutas ya exige sesion.
        Optional<Long> tenant = TenantContext.get();
        if (tenant.isEmpty()) {
            return true;
        }
        Long institucionId = tenant.get();

        Optional<PuestoCaptura> puesto = CookiePuesto.leer(request)
            .flatMap(token -> puestoService.verificar(token, institucionId));

        if (puesto.isPresent()) {
            request.setAttribute(ATRIBUTO_PUESTO, puesto.get());
            refrescarUso(puesto.get(), institucionId);
            return true;
        }

        log.info("Captura biometrica bloqueada: {} {} sin puesto autorizado (institucion={})",
                 request.getMethod(), request.getRequestURI(), institucionId);
        rechazar(request, response, handler);
        return false;
    }

    /**
     * El rechazo se adapta a lo que quien llama sabe interpretar.
     *
     * <p>La distinción sale de si el handler está anotado con {@code @ResponseBody}, y no de
     * una lista de URLs: las rutas se mueven, la anotación viaja con el método. Las que
     * responden JSON las invoca {@code fetch} desde la pantalla del pase o de la captura, y
     * un redirect ahí llegaría como el HTML de otra página metido en un {@code response.json()}.
     */
    private void rechazar(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        if (esEndpointJson(handler)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("""
                {"error":"PUESTO_NO_AUTORIZADO",\
                "mensaje":"Este equipo no está autorizado para capturar. \
                Usá la computadora de secretaría."}""");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/puesto-requerido");
    }

    private boolean esEndpointJson(Object handler) {
        if (!(handler instanceof HandlerMethod metodo)) {
            return false;
        }
        return metodo.hasMethodAnnotation(ResponseBody.class)
            || metodo.getBeanType().isAnnotationPresent(ResponseBody.class);
    }

    /** Anota que el puesto se usó, pero espaciado: sirve para identificar equipos, no para auditar cuadros. */
    private void refrescarUso(PuestoCaptura puesto, Long institucionId) {
        LocalDateTime ultimo = puesto.getUltimoUsoEn();
        if (ultimo != null && ultimo.isAfter(LocalDateTime.now().minus(REFRESCO_USO))) {
            return;
        }
        try {
            puestoService.registrarUso(puesto.getId(), institucionId);
        } catch (RuntimeException e) {
            // Es un dato de conveniencia: que no se pueda anotar no puede impedir el pase.
            log.warn("No se pudo registrar el uso del puesto {}: {}", puesto.getId(), e.toString());
        }
    }
}
