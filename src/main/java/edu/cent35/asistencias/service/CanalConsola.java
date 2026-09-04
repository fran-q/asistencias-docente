package edu.cent35.asistencias.service;

import edu.cent35.asistencias.model.PropositoCodigo;
import edu.cent35.asistencias.model.Usuario;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Canal de prototipo: el código se escribe en la consola del servidor en vez de enviarse.
 *
 * <p><b>Para qué existe.</b> Todo el flujo de códigos —alta de institución, verificación de
 * cuenta, recuperación de contraseña— depende de tener un servidor SMTP levantado. Sin él, la
 * pantalla dice "revisá tu correo" y no llega nada: no es que falle visiblemente, es que se
 * queda esperando. Durante el desarrollo eso vuelve imposible probar tres flujos enteros por
 * una dependencia de infraestructura que no aporta nada a lo que se está probando.
 *
 * <p><b>Qué NO hace.</b> No cambia cómo se genera ni cómo se guarda el código: eso sigue
 * pasando en {@code CodigoVerificacionService}, con el mismo hash y el mismo vencimiento. Lo
 * único que cambia es por dónde sale. Un código que se lee en la consola es tan válido y tan
 * de un solo uso como uno que llega por correo.
 *
 * <p><b>El riesgo, dicho de frente.</b> Con este canal activo, cualquiera que vea la consola
 * del servidor puede tomar el código de recuperación de cualquier cuenta y entrar. Es
 * aceptable en una máquina de desarrollo y es una puerta abierta en producción. Por eso el
 * arranque escribe una advertencia imposible de pasar por alto: el modo tiene que doler a la
 * vista para que nadie lo deje puesto sin darse cuenta.
 */
@Service
@ConditionalOnProperty(name = "app.mail.canal", havingValue = "consola")
@Slf4j
public class CanalConsola implements CanalDeCodigos {

    @PostConstruct
    void advertir() {
        log.warn("""

            ***************************************************************************
            *                                                                         *
            *   CANAL DE CODIGOS EN MODO CONSOLA                                      *
            *                                                                         *
            *   Los codigos de verificacion y recuperacion NO se envian por correo:   *
            *   se escriben en esta consola. Cualquiera que la vea puede entrar a     *
            *   cualquier cuenta.                                                     *
            *                                                                         *
            *   Solo para desarrollo. En produccion:  app.mail.canal=correo           *
            *                                                                         *
            ***************************************************************************
            """);
    }

    /**
     * Escribe el código en la terminal en vez de enviarlo por correo.
     *
     * <p><b>El recuadro va por {@code System.out} y no por el log.</b> El logger le antepone
     * marca de tiempo, hilo y nombre de clase a cada línea, que es justo lo que rompe el marco;
     * y además queda a merced del nivel configurado. Este canal existe para una sola cosa —que
     * alguien lea el código en su terminal— así que se imprime directo.
     *
     * <p>Al log va una sola línea, para que quede constancia en el archivo sin repetir el
     * recuadro entero por duplicado en la pantalla.
     *
     * <p>Si {@code hibernate.format_sql} está activo el recuadro se pierde igual entre las
     * consultas: cada una ocupa treinta líneas. Conviene tenerlo apagado en el perfil local.
     */
    @Override
    public void enviarCodigo(Usuario usuario, PropositoCodigo proposito, String email, String codigo) {
        // Marco en ASCII puro y sin acentos, a proposito: la consola de Windows no usa UTF-8,
        // asi que los bordes de caja y las tildes salen como signos de pregunta y el recuadro
        // queda ilegible justo donde tiene que leerse.
        System.out.println("""

            +---------------------------------------------------------------+
            |   CODIGO DE UN SOLO USO - modo consola, no se envio correo     |
            +---------------------------------------------------------------+
            |   Para    : %s <%s>
            |   Motivo  : %s
            |
            |   CODIGO  :  %s
            |
            +---------------------------------------------------------------+
            """.formatted(usuario.getNombreParaMostrar(), email, describir(proposito), codigo));
        System.out.flush();

        log.warn("Codigo de un solo uso emitido por consola para {} ({})",
                 email, describir(proposito));
    }

    /**
     * Escribe en la terminal que el pedido no emitió ningún código, y por qué.
     *
     * <p>Es el complemento del recuadro de arriba: sin esto, un pedido para una cuenta que no
     * existe y un canal roto se ven exactamente igual desde la consola —no aparece nada— y no
     * hay forma de saber cuál de los dos pasó. Sale con el mismo ancho y el mismo marco para
     * que se lea como parte de la misma conversación.
     *
     * <p>Lo que ve el navegador no cambia: sigue diciendo lo mismo exista o no la cuenta. Esa
     * propiedad es de la respuesta HTTP y se sostiene en {@code RecuperacionController}; acá
     * solo se está contando lo que pasó del lado del servidor, en una consola que en desarrollo
     * ya muestra los códigos en claro.
     */
    @Override
    public void noSeEmitio(String identificador, String motivo) {
        // Mismo criterio que enviarCodigo: ASCII puro y por System.out, para que el marco no
        // quede roto por el prefijo del logger ni por la codificacion de la consola de Windows.
        System.out.println("""

            +---------------------------------------------------------------+
            |   NO SE EMITIO NINGUN CODIGO - modo consola                   |
            +---------------------------------------------------------------+
            |   Se pidio para : %s
            |   Motivo        : %s
            |
            |   La pantalla igual dice "si la cuenta existe...": contesta lo
            |   mismo exista o no, a proposito (ADR-0009). Este aviso sale
            |   solo por consola.
            +---------------------------------------------------------------+
            """.formatted(vacioSiEnBlanco(identificador), motivo));
        System.out.flush();

        log.warn("No se emitio ningun codigo para '{}': {}", identificador, motivo);
    }

    // Nombre corto del canal, para los logs y el aviso de arranque.
    @Override
    public String nombre() {
        return "consola";
    }

    // Un identificador vacio impreso como nada deja la linea muda; conviene nombrarlo.
    private String vacioSiEnBlanco(String identificador) {
        return identificador == null || identificador.isBlank() ? "(vacio)" : identificador.trim();
    }

    private String describir(PropositoCodigo proposito) {
        return switch (proposito) {
            case VERIFICACION_EMAIL -> "confirmar la direccion de correo";
            case RECUPERACION_PASSWORD -> "recuperar la contrasena";
        };
    }
}
