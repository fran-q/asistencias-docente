package edu.cent35.asistencias.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El control que impide arrancar con la configuración de desarrollo. Lo que se prueba es que corte
 * por omisión: el caso que importa es el despliegue al que se le olvidó algo.
 */
class ControlDeArranqueConfigTest {

    private static final String CLAVE_BUENA = "una-clave-larga-generada-al-azar-para-esta-prueba";
    private static final String SALT_BUENO = "a1b2c3d4e5f60718";

    @Test
    @DisplayName("Sin perfil y con la clave de desarrollo, el sistema no arranca")
    void sinPerfilNoArrancaConLaClaveDeDesarrollo() {
        assertThatThrownBy(() -> control(new MockEnvironment(),
                ControlDeArranqueConfig.CLAVE_DE_DESARROLLO, ControlDeArranqueConfig.SALT_DE_DESARROLLO,
                "correo", false).verificar())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("BIOMETRIA_CLAVE")
            .hasMessageContaining("BIOMETRIA_SALT");
    }

    @Test
    @DisplayName("El mensaje no repite ningún valor: termina en el log")
    void elMensajeNoRepiteElSecreto() {
        assertThatThrownBy(() -> control(new MockEnvironment(), "corta", "no-es-hexa",
                "correo", false).verificar())
            .hasMessageNotContaining("corta")
            .hasMessageNotContaining("no-es-hexa");
    }

    @Test
    @DisplayName("Desarrollo y tests no se revisan, y un despliegue bien configurado arranca")
    void desarrolloYBienConfiguradoArrancan() {
        MockEnvironment local = new MockEnvironment();
        local.setActiveProfiles("local");
        assertThatCode(() -> control(local, ControlDeArranqueConfig.CLAVE_DE_DESARROLLO,
                ControlDeArranqueConfig.SALT_DE_DESARROLLO, "consola", false).verificar())
            .doesNotThrowAnyException();

        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatCode(() -> control(prod, CLAVE_BUENA, SALT_BUENO, "correo", false).verificar())
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Cada problema se detecta por separado")
    void cadaProblema() {
        assertThat(ControlDeArranqueConfig.problemas(CLAVE_BUENA, SALT_BUENO, "correo", false, true))
            .isEmpty();
        assertThat(ControlDeArranqueConfig.problemas("corta", SALT_BUENO, "correo", false, true))
            .singleElement().asString().contains("BIOMETRIA_CLAVE");
        assertThat(ControlDeArranqueConfig.problemas(CLAVE_BUENA, "zzzz", "correo", false, true))
            .singleElement().asString().contains("BIOMETRIA_SALT");
        assertThat(ControlDeArranqueConfig.problemas(CLAVE_BUENA, SALT_BUENO, "consola", false, true))
            .singleElement().asString().contains("MAIL_CANAL");
        assertThat(ControlDeArranqueConfig.problemas(CLAVE_BUENA, SALT_BUENO, "correo", true, true))
            .singleElement().asString().contains("autonoma");
        // La instalación autónoma sin el perfil de internet sigue siendo válida.
        assertThat(ControlDeArranqueConfig.problemas(CLAVE_BUENA, SALT_BUENO, "correo", true, false))
            .isEmpty();
    }

    @Test
    @DisplayName("Los valores que se rechazan son los mismos que trae application.properties")
    void losValoresDeDesarrolloSiguenIguales() throws IOException {
        Properties base = new Properties();
        try (InputStream entrada = new ClassPathResource("application.properties").getInputStream()) {
            base.load(entrada);
        }

        // Si alguien cambia el valor por defecto y no la constante, el control deja de servir
        // sin que falle nada.
        assertThat(base.getProperty("app.biometria.clave-cifrado"))
            .contains(ControlDeArranqueConfig.CLAVE_DE_DESARROLLO);
        assertThat(base.getProperty("app.biometria.salt"))
            .contains(ControlDeArranqueConfig.SALT_DE_DESARROLLO);
    }

    @Test
    @DisplayName("El perfil de internet no trae ningún secreto escrito")
    void elPerfilProdNoTraeSecretos() throws IOException {
        Properties prod = new Properties();
        try (InputStream entrada = new ClassPathResource("application-prod.properties").getInputStream()) {
            prod.load(entrada);
        }

        assertThat(prod.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
        assertThat(prod.getProperty("spring.mail.password")).isEqualTo("${MAIL_PASS}");
        // Y las tres cosas que hacen falta detrás del proxy.
        assertThat(prod.getProperty("server.forward-headers-strategy")).isEqualTo("native");
        assertThat(prod.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
        assertThat(prod.getProperty("app.alta.publica")).endsWith(":false}");
    }

    private ControlDeArranqueConfig control(MockEnvironment entorno, String clave, String salt,
                                            String canal, boolean autonoma) {
        return new ControlDeArranqueConfig(entorno, clave, salt, canal, autonoma);
    }
}
