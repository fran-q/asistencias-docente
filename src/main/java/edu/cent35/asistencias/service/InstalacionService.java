package edu.cent35.asistencias.service;

import edu.cent35.asistencias.dto.AltaInstitucionFormDto;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.InstitucionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El asistente de primer arranque: cómo se crea la primera cuenta en una instalación autónoma,
 * donde no hay un servidor de correo por el cual mandar el código que pide el alta (ADR-0022).
 *
 * <p><b>Viene apagado.</b> {@code app.instalacion.autonoma} es {@code false} salvo que
 * el perfil de instalación diga lo contrario, así que el entorno de desarrollo y cualquier
 * despliegue expuesto a internet no se enteran de que esta pantalla existe. Sin eso, una base
 * recién creada en un servidor público sería una institución gratis para el primero que pase.
 *
 * <p><b>Y se apaga solo.</b> Aun encendido, el asistente existe únicamente mientras no haya
 * ninguna institución. En cuanto hay una, la pantalla deja de responder para siempre — que es
 * lo que hace que una actualización del instalador sobre una instalación con datos no vuelva a
 * ofrecerla.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InstalacionService {

    private final InstitucionRepository institucionRepository;
    private final AltaInstitucionService altaService;
    private final ClaveRecuperacionService claveRecuperacion;

    @Value("${app.instalacion.autonoma:false}")
    private boolean autonoma;

    /**
     * Una institución que ya existe no deja de existir, así que esto solo va de false a true y
     * ahí se queda. Sirve para no pagar un {@code count()} por request durante toda la vida de
     * la instalación: apenas se crea la primera, la pregunta se responde en memoria.
     */
    private volatile boolean yaHayInstitucion = false;

    /**
     * Si esta es una instalación autónoma: la que corre en la máquina de la institución, sin
     * internet y sin servidor de correo (ADR-0022).
     *
     * <p>De acá cuelga todo lo que cambia en esa modalidad —el asistente de primer arranque y la
     * recuperación por clave— y por eso es una sola propiedad y no una por función: dos podrían
     * quedar en desacuerdo, y la combinación mala es silenciosa. Una instalación con asistente y
     * sin recuperación se entrega andando y se rompe recién el día que alguien olvida la
     * contraseña.
     *
     * <p><b>No confundir con el perfil {@code local}</b>, que es el entorno de desarrollo contra
     * XAMPP. Son cosas distintas y por eso no comparten nombre.
     */
    public boolean autonoma() {
        return autonoma;
    }

    /** Si corresponde mostrar el asistente. Falso en desarrollo, y falso apenas hay datos. */
    public boolean disponible() {
        if (!autonoma || yaHayInstitucion) {
            return false;
        }
        if (institucionRepository.count() > 0) {
            yaHayInstitucion = true;
            return false;
        }
        return true;
    }

    /**
     * Crea la institución y su cuenta administradora, ya verificada.
     *
     * <p>{@code synchronized} y con la condición revisada adentro: es una operación que ocurre
     * una sola vez en la vida de la instalación, y sin eso dos envíos simultáneos del mismo
     * formulario —un doble clic alcanza— entrarían los dos. El UNIQUE del nombre frenaría al
     * segundo, pero con un error de integridad en la cara en vez de una pantalla que dice qué
     * pasó.
     */
    @Transactional
    public synchronized PrimeraInstitucion crearPrimeraInstitucion(AltaInstitucionFormDto form) {
        if (!disponible()) {
            throw new IllegalStateException(
                "La configuración inicial ya se completó. Entrá con la cuenta que creaste.");
        }
        Usuario creado = altaService.crearInstitucionConCuenta(form);

        // En la misma transacción que la institución, y no como un paso que quien llama puede
        // olvidarse: una instalación autónoma sin clave de recuperación es una instalación a la
        // que se entra hasta que alguien olvide la contraseña, y ahí se terminó. Si esto falla,
        // no queda una institución a medias.
        String clave = claveRecuperacion.generar(creado.getInstitucionId());

        yaHayInstitucion = true;
        log.info("Configuracion inicial completada: usuario_id={}", creado.getId());
        return new PrimeraInstitucion(creado, clave);
    }

    /**
     * Lo que deja la configuración inicial: la cuenta creada y su clave de recuperación.
     *
     * <p>La clave viaja acá y no se guarda en ningún lado porque de ella solo queda el hash. Es
     * la única vez que se puede leer, así que quien llama tiene que mostrarla.
     */
    public record PrimeraInstitucion(Usuario usuario, String claveRecuperacion) {}
}
