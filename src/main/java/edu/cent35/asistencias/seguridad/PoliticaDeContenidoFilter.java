package edu.cent35.asistencias.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Manda la política de contenido (CSP) y genera el nonce de los pocos scripts que tienen que ir
 * escritos en la página. Con ella, un texto malicioso que llegara a colarse en una pantalla no se
 * ejecuta: el navegador solo corre los archivos del propio sistema y lo que lleve este nonce.
 * <p>
 * El nonce cambia en cada pedido, así que no se puede fijar desde {@code SecurityConfig} como un
 * encabezado más. Las plantillas lo toman con {@code th:nonce="${cspNonce}"}, y
 * {@code PlantillasTest} falla si un script en línea no lo lleva: sin él, el navegador lo ignora
 * sin que nada se rompa a la vista.
 */
public class PoliticaDeContenidoFilter extends OncePerRequestFilter {

    /** El nombre con el que el nonce queda disponible para las plantillas. */
    public static final String ATRIBUTO_NONCE = "cspNonce";

    private static final SecureRandom ALEATORIO = new SecureRandom();

    // style-src-attr admite el atributo style: los valores que calcula el servidor, como el ancho
    // de una barra, van con th:style (ver 04-convenciones). Las hojas de estilo siguen siendo solo
    // las propias. blob: en imágenes y medios es para la cámara y lo que se dibuja en el canvas.
    private static final String POLITICA = String.join("; ",
        "default-src 'self'",
        "script-src 'self' 'nonce-%s'",
        "style-src 'self'",
        "style-src-attr 'unsafe-inline'",
        "img-src 'self' data: blob:",
        "media-src 'self' blob:",
        "font-src 'self'",
        "connect-src 'self'",
        "object-src 'none'",
        "base-uri 'self'",
        "form-action 'self'",
        "frame-ancestors 'none'");

    // Deja el nonce en el pedido y la política en la respuesta.
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain cadena) throws ServletException, IOException {
        byte[] crudo = new byte[16];
        ALEATORIO.nextBytes(crudo);
        String nonce = Base64.getEncoder().encodeToString(crudo);

        request.setAttribute(ATRIBUTO_NONCE, nonce);
        response.setHeader("Content-Security-Policy", POLITICA.formatted(nonce));
        cadena.doFilter(request, response);
    }
}
