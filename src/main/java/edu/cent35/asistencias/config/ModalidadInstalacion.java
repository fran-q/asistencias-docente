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

    public ModalidadInstalacion(@Value("${app.instalacion.autonoma:false}") boolean autonoma) {
        this.autonoma = autonoma;
    }

    public boolean esAutonoma() {
        return autonoma;
    }
}
