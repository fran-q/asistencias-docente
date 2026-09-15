package edu.cent35.asistencias.validacion;

import edu.cent35.asistencias.dto.AltaInstitucionFormDto;
import edu.cent35.asistencias.dto.UsuarioCreateFormDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La regla del nombre de usuario, y que los dos formularios que crean cuentas apliquen la misma.
 *
 * <p>Estaba copiada en los dos, con mensajes distintos, y pedía 3 caracteres cualesquiera.
 * Comprobar los dos DTO contra los mismos casos es lo que impide que vuelvan a separarse; y
 * comparar el patrón del navegador con el validador, que la pantalla diga una cosa y el
 * servidor otra.
 */
class UsuarioValidoTest {

    private static Validator validator;

    @BeforeAll
    static void iniciar() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = { "cent35", "ifd", "utn-frtdf", "ana.perez", "abc", "ies_2", "a1b2c3" })
    @DisplayName("acepta los que tienen al menos 3 letras y solo caracteres admitidos")
    void aceptaLosValidos(String usuario) {
        assertThat(errores(usuario)).as("'%s' tendría que pasar", usuario).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "...", "1-2", "ab", "ab12", "a.b" })
    @DisplayName("rechaza los que tienen menos de 3 letras, aunque tengan 3 caracteres o más")
    void rechazaPocasLetras(String usuario) {
        assertThat(errores(usuario)).singleElement().asString().contains("al menos 3 letras");
    }

    @Test
    @DisplayName("dice qué falla: el @, la eñe o el espacio, y el largo")
    void diceQueFalla() {
        assertThat(errores("ana@x.com")).containsExactly("No puede llevar @: el usuario no es el correo.");
        assertThat(errores("peña")).singleElement().asString().contains("sin tilde ni eñe");
        assertThat(errores("ana perez")).singleElement().asString().contains("sin tilde ni eñe");
        assertThat(errores("a".repeat(61))).singleElement().asString().contains("máximo son 60");
        assertThat(errores("ab12")).containsExactly("Tiene que tener al menos 3 letras, y tiene 2.");
    }

    @Test
    @DisplayName("el campo vacío lo reporta solo @NotBlank, no dos veces")
    void vacioUnSoloError() {
        assertThat(errores("")).containsExactly("El usuario es obligatorio");
    }

    @ParameterizedTest
    @ValueSource(strings = { "...", "ana@x.com", "peña", "cent35" })
    @DisplayName("los dos formularios que crean cuentas dicen lo mismo")
    void losDosFormulariosCoinciden(String usuario) {
        AltaInstitucionFormDto alta = new AltaInstitucionFormDto();
        alta.setUsername(usuario);
        UsuarioCreateFormDto nuevo = new UsuarioCreateFormDto();
        nuevo.setUsername(usuario);

        assertThat(mensajes(validator.validateProperty(alta, "username")))
            .isEqualTo(mensajes(validator.validateProperty(nuevo, "username")));
    }

    @Test
    @DisplayName("el patrón del navegador acepta y rechaza lo mismo que el servidor")
    void elPatronDelNavegadorCoincide() {
        // El atributo pattern se ancla solo; acá se ancla a mano para compararlo igual.
        Pattern html = Pattern.compile("^(?:" + UsuarioValido.PATRON_HTML + ")$");
        for (String u : List.of("cent35", "ifd", "utn-frtdf", "a1b2c3", "ies_2",
                                "...", "1-2", "ab12", "ana@x.com", "peña", "ana perez", "a".repeat(61))) {
            assertThat(html.matcher(u).matches())
                .as("'%s'", u)
                .isEqualTo(UsuarioValidoValidator.problema(u) == null);
        }
    }

    @Test
    @DisplayName("las dos pantallas repiten el patrón y el aviso tal cual")
    void lasPantallasUsanLaMismaRegla() throws Exception {
        // Las plantillas escriben el patrón a mano, como la ayuda de la contraseña. Esto es lo
        // que impide que se separen de la anotación sin que nadie lo note.
        for (String plantilla : List.of("templates/usuario/form-nuevo.html",
                                        "templates/auth/alta-institucion.html")) {
            String html;
            try (var in = getClass().getClassLoader().getResourceAsStream(plantilla)) {
                assertThat(in).as(plantilla).isNotNull();
                html = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            assertThat(html).as(plantilla)
                .contains("pattern=\"" + UsuarioValido.PATRON_HTML + "\"")
                .contains("data-error-patron=\"" + UsuarioValido.AVISO + "\"");
        }
    }

    private List<String> errores(String usuario) {
        UsuarioCreateFormDto f = new UsuarioCreateFormDto();
        f.setUsername(usuario);
        return mensajes(validator.validateProperty(f, "username"));
    }

    private static <T> List<String> mensajes(Set<ConstraintViolation<T>> violaciones) {
        return violaciones.stream().map(ConstraintViolation::getMessage).sorted().toList();
    }
}
