package edu.cent35.asistencias.seguridad;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * La cookie que identifica a un puesto de captura (ADR-0015). Los atributos viven acá y no
 * repartidos por los controladores, porque son parte del control: una cookie de estas escrita
 * sin {@code HttpOnly} o sin {@code SameSite} deja de ofrecer lo que se espera de ella.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CookiePuesto {

    public static final String NOMBRE = "VISUM_PUESTO";

    // Un ano. La cookie identifica una maquina, no una sesion: expirar cada pocas semanas
    // obligaria a redesignar el mismo equipo una y otra vez, y el tramite terminaria
    // haciendose de memoria y sin pensarlo, que es como se degrada un control.
    private static final Duration VIGENCIA = Duration.ofDays(365);

    /** El token guardado en este navegador, si hay alguno. */
    public static Optional<String> leer(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
            .filter(c -> NOMBRE.equals(c.getName()))
            .map(Cookie::getValue)
            .filter(v -> v != null && !v.isBlank())
            .findFirst();
    }

    /**
     * Deja el token en el navegador que designó el puesto.
     *
     * <p>{@code HttpOnly} porque ningún script de la aplicación necesita leerlo, y no siendo
     * legible desde JavaScript un XSS no alcanza para llevárselo. {@code SameSite=Strict}
     * porque la cookie no tiene que viajar en peticiones originadas en otro sitio: es lo que
     * evita que una página ajena dispare el pase contra esta sesión.
     */
    public static void escribir(HttpServletRequest request, HttpServletResponse response, String token) {
        response.addHeader("Set-Cookie", base(request, token)
            .maxAge(VIGENCIA)
            .build()
            .toString());
    }

    /** Borra la cookie de este navegador. Se usa al revocar el puesto desde el propio equipo. */
    public static void borrar(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader("Set-Cookie", base(request, "")
            .maxAge(Duration.ZERO)
            .build()
            .toString());
    }

    private static ResponseCookie.ResponseCookieBuilder base(HttpServletRequest request, String valor) {
        return ResponseCookie.from(NOMBRE, valor)
            .httpOnly(true)
            .sameSite("Strict")
            .path("/")
            // Secure se decide por como llego la peticion y no por una constante: en
            // desarrollo el sistema corre sobre http y una cookie Secure no se guardaria,
            // dejando el puesto imposible de designar. Al salir a la nube (rumbo 1) la
            // aplicacion pasa a https --getUserMedia lo exige-- y la marca se activa sola.
            .secure(request.isSecure());
    }
}
