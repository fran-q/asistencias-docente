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

    @Override
    public void enviarCodigo(Usuario usuario, PropositoCodigo proposito, String email, String codigo) {
        // Se escribe con warn y con marco para que no se pierda entre las lineas de Hibernate.
        // El punto de este canal es que el codigo se pueda encontrar de un vistazo.
        log.warn("""

            ┌─────────────────────────────────────────────────────────────┐
            │  CODIGO DE UN SOLO USO  (modo consola, no se envio correo)  │
            ├─────────────────────────────────────────────────────────────┤
            │  Para      : {} <{}>
            │  Motivo    : {}
            │  CODIGO    : {}
            └─────────────────────────────────────────────────────────────┘
            """, usuario.getNombre(), email, describir(proposito), codigo);
    }

    @Override
    public String nombre() {
        return "consola";
    }

    private String describir(PropositoCodigo proposito) {
        return switch (proposito) {
            case VERIFICACION_EMAIL -> "confirmar la dirección de correo";
            case RECUPERACION_PASSWORD -> "recuperar la contraseña";
        };
    }
}
