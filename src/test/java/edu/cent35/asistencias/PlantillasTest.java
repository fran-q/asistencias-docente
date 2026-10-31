package edu.cent35.asistencias;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas de escritura de las plantillas, revisadas en los archivos y no en el HTML
 * renderizado: tienen que valer también para la parte de una pantalla que ningún test
 * llega a mostrar (un aviso de error, un estado vacío, la clave recién generada).
 */
class PlantillasTest {

    private static final Path BASE = Path.of("src/main/resources/templates");

    // Un style="..." suelto. No cuentan th:style, que es un valor que calcula el servidor
    // (el ancho de una barra), ni los data-*-style, que son datos para el JS y no CSS.
    private static final Pattern ESTILO_SUELTO = Pattern.compile("(?<![\\w:-])style=\"");
    // Un <script> sin src ni th:src es código en línea, y tiene que traer el nonce.
    private static final Pattern SCRIPT_EN_LINEA_SIN_NONCE =
        Pattern.compile("<script(?![^>]*src=)(?![^>]*th:nonce=)[^>]*>");
    private static final Pattern MANEJADOR_EN_LINEA =
        Pattern.compile("\\son(?:click|change|submit|input|load|keydown|keyup|blur|focus)=\"");
    private static final Pattern COMENTARIO = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    @Test
    @DisplayName("Ninguna plantilla trae un style suelto: el estilo va en main.css")
    void ningunaPlantillaTraeEstiloSuelto() throws IOException {
        List<String> conEstilo;
        try (Stream<Path> archivos = Files.walk(BASE)) {
            conEstilo = archivos
                .filter(p -> p.toString().endsWith(".html"))
                .flatMap(p -> estilosSueltos(p).stream())
                .toList();
        }

        assertThat(conEstilo)
            .as("un style=\"\" no se encuentra desde main.css, no usa los tokens y se ve bien en "
                + "un tema y mal en el otro; va como clase (04-convenciones, Frontend)")
            .isEmpty();
    }

    @Test
    @DisplayName("Ningún script en línea queda sin el nonce de la política de contenido")
    void losScriptsEnLineaLlevanNonce() throws IOException {
        assertThat(encontrar(SCRIPT_EN_LINEA_SIN_NONCE))
            .as("sin th:nonce el navegador no lo ejecuta, y no da ningún error a la vista "
                + "(PoliticaDeContenidoFilter)")
            .isEmpty();
    }

    @Test
    @DisplayName("Ninguna plantilla trae código en un atributo onclick u onchange")
    void ningunaPlantillaTraeManejadoresEnLinea() throws IOException {
        assertThat(encontrar(MANEJADOR_EN_LINEA))
            .as("la política de contenido no los deja correr: el botón se ve bien y no hace "
                + "nada. Va en un .js (form-al-cambiar.js, confirm-modal.js)")
            .isEmpty();
    }

    private static List<String> encontrar(Pattern patron) throws IOException {
        try (Stream<Path> archivos = Files.walk(BASE)) {
            return archivos
                .filter(p -> p.toString().endsWith(".html"))
                .flatMap(p -> coincidencias(p, patron).stream())
                .toList();
        }
    }

    // "carpeta/archivo.html:línea" de cada style suelto. Los comentarios se blanquean
    // conservando los saltos de línea, para que el número siga apuntando al renglón real.
    private static List<String> estilosSueltos(Path archivo) {
        return coincidencias(archivo, ESTILO_SUELTO);
    }

    // "carpeta/archivo.html:línea" de cada coincidencia, fuera de los comentarios.
    private static List<String> coincidencias(Path archivo, Pattern patron) {
        String texto;
        try {
            texto = Files.readString(archivo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Matcher comentario = COMENTARIO.matcher(texto);
        StringBuilder sinComentarios = new StringBuilder();
        while (comentario.find()) {
            comentario.appendReplacement(sinComentarios,
                Matcher.quoteReplacement(comentario.group().replaceAll("[^\\n]", " ")));
        }
        comentario.appendTail(sinComentarios);

        List<String> encontrados = new ArrayList<>();
        Matcher estilo = patron.matcher(sinComentarios);
        while (estilo.find()) {
            long linea = sinComentarios.substring(0, estilo.start()).chars()
                .filter(c -> c == '\n').count() + 1;
            encontrados.add(BASE.relativize(archivo).toString().replace('\\', '/') + ":" + linea);
        }
        return encontrados;
    }
}
