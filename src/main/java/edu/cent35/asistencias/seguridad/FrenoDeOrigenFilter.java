package edu.cent35.asistencias.seguridad;

import edu.cent35.asistencias.service.FrenoDeOrigenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Corta los POST del login y de las pantallas públicas cuando ese origen ya pidió demasiado, antes
 * de que lleguen a comparar un BCrypt o a mandar un correo. Es un filtro y no un chequeo en cada
 * controlador porque el login no pasa por ningún controlador: lo atiende Spring Security.
 * <p>
 * No lleva {@code @Component} a propósito: Spring Boot registraría también como filtro del
 * contenedor a cualquier bean de este tipo, y correría dos veces. Lo agrega {@code SecurityConfig}.
 */
public class FrenoDeOrigenFilter extends OncePerRequestFilter {

    private static final String LOGIN = "/login";

    // Las pantallas sin sesión que mandan correo o prueban códigos. No hay otra forma de limitar a
    // quien recorre cuentas distintas: cada una tiene su propio contador (TD-008).
    private static final Set<String> PUBLICAS = Set.of(
        "/recuperar", "/recuperar/codigo", "/recuperar/clave",
        "/alta-institucion", "/alta-institucion/codigo");

    private final FrenoDeOrigenService freno;

    public FrenoDeOrigenFilter(FrenoDeOrigenService freno) {
        this.freno = freno;
    }

    // Solo mira los POST de esas rutas; el resto sigue su camino.
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain cadena) throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod()) && frenar(request)) {
            boolean desdeKiosco = "kiosco".equals(request.getParameter("desde"));
            response.sendRedirect(request.getContextPath() + LOGIN + "?bloqueado"
                + (desdeKiosco ? "&desde=kiosco" : ""));
            return;
        }
        cadena.doFilter(request, response);
    }

    // Decide según la ruta; en la pública, además, anota el pedido.
    private boolean frenar(HttpServletRequest request) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        String ip = request.getRemoteAddr();
        if (LOGIN.equals(ruta)) {
            return freno.loginBloqueado(request.getParameter("username"), ip);
        }
        return PUBLICAS.contains(ruta) && !freno.permitirPedidoPublico(ip);
    }
}
