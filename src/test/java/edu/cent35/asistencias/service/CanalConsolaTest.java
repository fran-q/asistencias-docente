package edu.cent35.asistencias.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import edu.cent35.asistencias.model.PropositoCodigo;
import edu.cent35.asistencias.model.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del canal de consola.
 *
 * <p>Se lee lo que efectivamente sale por el log y no solo que el método no explote: el único
 * propósito de este canal es que el código se pueda leer, así que si no aparece impreso no
 * sirve para nada aunque la llamada haya terminado bien.
 */
class CanalConsolaTest {

    private CanalConsola canal;
    private ListAppender<ILoggingEvent> capturado;
    private Logger logger;

    @BeforeEach
    void setUp() {
        canal = new CanalConsola();
        logger = (Logger) LoggerFactory.getLogger(CanalConsola.class);
        capturado = new ListAppender<>();
        capturado.start();
        logger.addAppender(capturado);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(capturado);
    }

    // Junta el mensaje ya con los argumentos reemplazados, que es lo que se ve en la consola.
    private String salida() {
        return capturado.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    @DisplayName("El código queda impreso y se puede leer")
    void imprimeElCodigo() {
        canal.enviarCodigo(usuario(), PropositoCodigo.RECUPERACION_PASSWORD,
                           "ana@ejemplo.com", "482913");

        assertThat(salida())
            .as("si el codigo no sale impreso, este canal no sirve para nada")
            .contains("482913")
            .contains("ana@ejemplo.com")
            .contains("Ana")
            .contains("recuperar la contraseña");
    }

    @Test
    @DisplayName("Sale como advertencia, no como info")
    void saleComoAdvertencia() {
        canal.enviarCodigo(usuario(), PropositoCodigo.VERIFICACION_EMAIL,
                           "ana@ejemplo.com", "111222");

        assertThat(capturado.list)
            .as("con nivel info se pierde entre las lineas de Hibernate, y el punto es "
                + "poder encontrarlo de un vistazo")
            .allMatch(e -> e.getLevel() == Level.WARN);
    }

    @Test
    @DisplayName("El arranque avisa que los códigos no se están enviando")
    void avisaAlArrancar() {
        canal.advertir();

        assertThat(salida())
            .as("dejar este modo puesto en produccion abre cualquier cuenta a quien vea la "
                + "consola: el aviso tiene que ser imposible de pasar por alto")
            .contains("MODO CONSOLA")
            .contains("NO se envian por correo")
            .contains("app.mail.canal=correo");
    }

    private Usuario usuario() {
        return Usuario.builder().id(1L).nombre("Ana").apellido("Pérez")
            .username("ana.perez").passwordHash("x").activo(true).build();
    }
}
