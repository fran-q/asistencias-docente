package edu.cent35.asistencias.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Si el sistema corre como instalación autónoma: en la máquina de la institución, sin internet
 * y sin servidor de correo (ADR-0022).
 *
 * <p><b>Por qué es un bean propio y no un {@code @Value} en cada servicio.</b> La respuesta la
 * necesitan servicios que dependen unos de otros —{@code InstalacionService} de
 * {@code ClaveRecuperacionService}, y ese de {@code UsuarioService}—, así que ponerla en
 * cualquiera de ellos y hacer que los demás lo consulten arma un ciclo de dependencias. Acá no
 * depende de nada y todos pueden pedirla.
 *
 * <p>La otra salida era repetir el {@code @Value} en cada uno, y el día que la propiedad cambie
 * de nombre alguno se olvida: quedaría un servicio creyendo que hay correo y otro que no.
 */
@Component
public class ModalidadInstalacion {

    private final boolean autonoma;
    private final boolean altaPublica;

    public ModalidadInstalacion(@Value("${app.instalacion.autonoma:false}") boolean autonoma,
                                @Value("${app.alta.publica:true}") boolean altaPublica) {
        this.autonoma = autonoma;
        this.altaPublica = altaPublica;
    }

    public boolean esAutonoma() {
        return autonoma;
    }

    /**
     * Si cualquiera puede registrar una institución desde el login.
     *
     * <p>En una instalación autónoma no, porque atiende a una sola. En internet lo decide
     * {@code app.alta.publica}, que el perfil de producción deja cerrada: abierta, cualquiera crea
     * instituciones y usa el correo del sistema para escribirle a direcciones ajenas.
     */
    public boolean altaPublicaAbierta() {
        return !autonoma && altaPublica;
    }
}
