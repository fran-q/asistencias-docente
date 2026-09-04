package edu.cent35.asistencias.service;

import java.nio.charset.StandardCharsets;
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;
import edu.cent35.asistencias.DatosDePrueba;
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

    // El recuadro con el codigo sale por System.out y no por el log: el logger le antepone
    // marca de tiempo y clase a cada linea y rompe el marco. El test lee de donde sale.
    private PrintStream salidaOriginal;
    private ByteArrayOutputStream consola;

    @BeforeEach
    void setUp() {
        canal = new CanalConsola();
        logger = (Logger) LoggerFactory.getLogger(CanalConsola.class);
        capturado = new ListAppender<>();
        capturado.start();
        logger.addAppender(capturado);

        salidaOriginal = System.out;
        consola = new ByteArrayOutputStream();
        System.setOut(new PrintStream(consola, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        System.setOut(salidaOriginal);
        logger.detachAppender(capturado);
    }

    // Lo que quedo escrito en la terminal.
    private String terminal() {
        return consola.toString(StandardCharsets.UTF_8);
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

        assertThat(terminal())
            .as("si el codigo no sale impreso en la terminal, este canal no sirve para nada")
            .contains("482913")
            .contains("ana@ejemplo.com")
            .contains("Ana")
            .contains("recuperar la contrasena");

        // El marco y las etiquetas van en ASCII: la consola de Windows no usa UTF-8 y convierte
        // los bordes de caja en signos de pregunta, justo alrededor del dato que hay que leer.
        // Los nombres propios pueden traer acentos —eso es un dato, no formato— asi que la
        // comprobacion es sobre los caracteres que introduce este canal, no sobre toda la salida.
        assertThat(terminal())
            .as("el marco tiene que ser legible en una consola que no sea UTF-8")
            .doesNotContain("╔").doesNotContain("║").doesNotContain("═")
            .doesNotContain("┌").doesNotContain("│").doesNotContain("·")
            .contains("+---")
            .contains("CODIGO  :");
    }

    @Test
    @DisplayName("Sale como advertencia, no como info")
    void saleComoAdvertencia() {
        canal.enviarCodigo(usuario(), PropositoCodigo.VERIFICACION_EMAIL,
                           "ana@ejemplo.com", "111222");

        // En el log queda una linea de constancia, sin repetir el recuadro entero.
        assertThat(capturado.list)
            .as("la constancia en el archivo de log va como advertencia, no como info")
            .isNotEmpty()
            .allMatch(e -> e.getLevel() == Level.WARN);
        assertThat(salida())
            .as("el recuadro no se duplica en el log: en la pantalla saldria dos veces")
            .doesNotContain("+------");
    }

    @Test
    @DisplayName("Cuando no se emite ningun codigo, la terminal dice por que")
    void explicaPorQueNoSalioCodigo() {
        canal.noSeEmitio("admin", "no existe ninguna cuenta con ese usuario ni con ese correo");

        // Sin esto, un identificador inexistente y un canal roto se ven igual desde la consola:
        // no aparece nada en ninguno de los dos casos y no hay forma de distinguirlos.
        assertThat(terminal())
            .as("hay que poder distinguir 'no existe esa cuenta' de 'el canal esta roto'")
            .contains("NO SE EMITIO")
            .contains("admin")
            .contains("no existe ninguna cuenta");
    }

    @Test
    @DisplayName("Un identificador vacio se nombra, no se imprime como un hueco")
    void nombraElIdentificadorVacio() {
        canal.noSeEmitio("   ", "no se ingreso ningun usuario ni correo");

        assertThat(terminal())
            .as("una linea muda no dice si el dato falto o si el aviso salio mal")
            .contains("(vacio)");
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
        return Usuario.builder().persona(DatosDePrueba.persona("Ana", "Pérez")).id(1L).username("ana.perez").passwordHash("x").activo(true).build();
    }
}
