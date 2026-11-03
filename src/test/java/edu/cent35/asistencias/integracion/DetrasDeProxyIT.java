package edu.cent35.asistencias.integracion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El sistema detrás de un proxy con HTTPS, contra el servidor real y con la configuración del perfil
 * de producción. Tiene que ser así: quien lee los encabezados del proxy es Tomcat, y MockMvc no pasa
 * por Tomcat, así que ahí estos casos darían bien aunque la configuración estuviera mal.
 *
 * <p>Desde {@code localhost} Tomcat confía en el proxy, igual que confiará en el contenedor de Caddy
 * adentro de su red.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "server.forward-headers-strategy=native",
    "server.servlet.session.cookie.secure=true",
    "app.alta.publica=false",
    "app.seguridad.login.max-por-cuenta-e-ip=3",
    "app.seguridad.login.max-por-cuenta=100",
    "app.seguridad.login.max-por-ip=100"
})
@ActiveProfiles("test")
class DetrasDeProxyIT {

    private static final Pattern TOKEN_DEL_FORMULARIO =
        Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");
    private static final Pattern COOKIE_CSRF = Pattern.compile("XSRF-TOKEN=([^;]+)");

    @LocalServerPort private int puerto;

    private final HttpClient cliente = HttpClient.newHttpClient();

    @Test
    @DisplayName("Con HTTPS en el proxy, las cookies salen Secure y se manda HSTS")
    void conHttpsLasCookiesSonSecure() throws Exception {
        HttpResponse<String> respuesta = pedirLogin("203.0.113.5", "https");

        assertThat(cookie(respuesta, "XSRF-TOKEN")).contains("Secure").contains("SameSite=Lax");
        assertThat(respuesta.headers().firstValue("Strict-Transport-Security")).isPresent();
    }

    @Test
    @DisplayName("Inventar un X-Forwarded-For no esquiva el freno: la IP es la que anotó el proxy")
    void elEncabezadoInventadoNoEsquivaElFreno() throws Exception {
        String ipReal = "203.0.113.7";

        // Cada intento se presenta con un origen distinto escrito por el cliente. El proxy agrega
        // la dirección real al final, y esa es la única que cuenta.
        for (int i = 1; i <= 3; i++) {
            HttpResponse<String> fallido = intentarLogin("198.51.100." + i + ", " + ipReal);
            assertThat(fallido.headers().firstValue("Location").orElseThrow()).endsWith("/login?error");
        }

        HttpResponse<String> frenado = intentarLogin("198.51.100.99, " + ipReal);
        assertThat(frenado.headers().firstValue("Location").orElseThrow()).endsWith("/login?bloqueado");
    }

    @Test
    @DisplayName("La sesión viaja solo en la cookie: la primera visita no la escribe en la dirección")
    void laSesionNoQuedaEnLaDireccion() throws Exception {
        // Sin cookie todavía, Tomcat no sabe si el navegador las acepta: si la respuesta abre una sesión,
        // por defecto agrega ;jsessionid= a la redirección y a los enlaces de la página. Así el
        // identificador queda en el historial y en los registros. El alta cerrada lo hacía: su 404 pasa
        // por /error, que pide login y guarda la página pedida en una sesión nueva. MockMvc no reescribe
        // direcciones, así que hay que verlo en Tomcat.
        HttpResponse<String> redireccion = cliente.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/alta-institucion"))
                .header("X-Forwarded-Proto", "https")
                .GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(redireccion.headers().firstValue("Location").orElseThrow())
            .endsWith("/login")
            .doesNotContainIgnoringCase("jsessionid");

        assertThat(pedirLogin("203.0.113.9", "https").body()).doesNotContainIgnoringCase("jsessionid");
    }

    private HttpResponse<String> pedirLogin(String reenviadoPor, String protocolo) throws Exception {
        return cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/login"))
                .header("X-Forwarded-For", reenviadoPor)
                .header("X-Forwarded-Proto", protocolo)
                .GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> intentarLogin(String reenviadoPor) throws Exception {
        HttpResponse<String> pantalla = pedirLogin(reenviadoPor, "https");

        Matcher token = TOKEN_DEL_FORMULARIO.matcher(pantalla.body());
        assertThat(token.find()).isTrue();
        Matcher cookieCsrf = COOKIE_CSRF.matcher(cookie(pantalla, "XSRF-TOKEN"));
        assertThat(cookieCsrf.find()).isTrue();

        String cuerpo = "username=nadie.proxy&password=ClaveEquivocada1&_csrf="
            + URLEncoder.encode(token.group(1), StandardCharsets.UTF_8);
        return cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/login"))
                .header("X-Forwarded-For", reenviadoPor)
                .header("X-Forwarded-Proto", "https")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", "XSRF-TOKEN=" + cookieCsrf.group(1))
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private static String cookie(HttpResponse<?> respuesta, String nombre) {
        List<String> cookies = respuesta.headers().allValues("Set-Cookie");
        return cookies.stream().filter(c -> c.startsWith(nombre + "=")).findFirst().orElseThrow();
    }
}
