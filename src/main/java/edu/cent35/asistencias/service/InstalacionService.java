package edu.cent35.asistencias.service;

import edu.cent35.asistencias.dto.AltaInstitucionFormDto;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.InstitucionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * El asistente de primer arranque: cómo se crea la primera cuenta en una instalación local,
 * donde no hay un servidor de correo por el cual mandar el código que pide el alta (ADR-0022).
 *
 * <p><b>Viene apagado.</b> {@code app.instalacion.asistente-inicial} es {@code false} salvo que
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

    @Value("${app.instalacion.asistente-inicial:false}")
    private boolean habilitado;

    /**
     * Una institución que ya existe no deja de existir, así que esto solo va de false a true y
     * ahí se queda. Sirve para no pagar un {@code count()} por request durante toda la vida de
     * la instalación: apenas se crea la primera, la pregunta se responde en memoria.
     */
    private volatile boolean yaHayInstitucion = false;

    /** Si corresponde mostrar el asistente. Falso en desarrollo, y falso apenas hay datos. */
    public boolean disponible() {
        if (!habilitado || yaHayInstitucion) {
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
    public synchronized Usuario crearPrimeraInstitucion(AltaInstitucionFormDto form) {
        if (!disponible()) {
            throw new IllegalStateException(
                "La configuración inicial ya se completó. Entrá con la cuenta que creaste.");
        }
        Usuario creado = altaService.crearInstitucionConCuenta(form);
        yaHayInstitucion = true;
        log.info("Configuracion inicial completada: usuario_id={}", creado.getId());
        return creado;
    }
}
