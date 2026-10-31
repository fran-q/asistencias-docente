package edu.cent35.asistencias.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.ArrayList;
import java.util.List;

/**
 * Impide que el sistema arranque con la configuración de desarrollo fuera de desarrollo. La clave
 * biométrica tiene un valor por defecto que está escrito en un repositorio público, así que un
 * despliegue al que le faltara la variable de entorno andaría igual y cifraría con una clave que
 * cualquiera puede leer.
 * <p>
 * Corta por omisión: solo los perfiles {@code local} y {@code test} quedan afuera. Cualquier otro
 * caso, con perfil o sin él, se revisa, porque el error que hay que evitar es justamente olvidarse
 * de configurar algo. Falla al arrancar y no con un aviso en el log, que nadie lee a tiempo.
 */
@Configuration
public class ControlDeArranqueConfig {

    /** La clave por defecto de application.properties. Un test comprueba que sigan iguales. */
    static final String CLAVE_DE_DESARROLLO = "dev-clave-biometrica-no-usar-en-produccion";

    /** El salt por defecto de application.properties. */
    static final String SALT_DE_DESARROLLO = "5c0d1e2f3a4b5c6d";

    // El mismo piso que exige instalar.ps1 para la clave de la instalación autónoma.
    private static final int LARGO_MINIMO_CLAVE = 32;

    private final Environment entorno;
    private final String clave;
    private final String salt;
    private final String canalDeCorreo;
    private final boolean autonoma;

    public ControlDeArranqueConfig(Environment entorno,
                                   @Value("${app.biometria.clave-cifrado}") String clave,
                                   @Value("${app.biometria.salt}") String salt,
                                   @Value("${app.mail.canal}") String canalDeCorreo,
                                   @Value("${app.instalacion.autonoma:false}") boolean autonoma) {
        this.entorno = entorno;
        this.clave = clave;
        this.salt = salt;
        this.canalDeCorreo = canalDeCorreo;
        this.autonoma = autonoma;
    }

    // Revisa la configuración y aborta el arranque si algo es de desarrollo.
    @PostConstruct
    void verificar() {
        if (entorno.acceptsProfiles(Profiles.of("local", "test"))) {
            return;
        }
        boolean perfilProd = entorno.acceptsProfiles(Profiles.of("prod"));
        List<String> problemas = problemas(clave, salt, canalDeCorreo, autonoma, perfilProd);
        if (!problemas.isEmpty()) {
            throw new IllegalStateException("El sistema no arranca con esta configuracion: "
                + String.join("; ", problemas));
        }
    }

    /**
     * Lo que está mal en la configuración, sin repetir ningún valor: este texto termina en el log.
     *
     * <p>Una clave cambiada después de haber cifrado deja ilegibles los modelos ya guardados,
     * pero eso se ve al identificar y no compromete nada. Lo que no se puede detectar más tarde
     * es haber usado la clave pública, por eso esa es la que se frena acá.
     */
    static List<String> problemas(String clave, String salt, String canalDeCorreo,
                                  boolean autonoma, boolean perfilProd) {
        List<String> problemas = new ArrayList<>();
        if (clave == null || clave.length() < LARGO_MINIMO_CLAVE
                || CLAVE_DE_DESARROLLO.equals(clave)) {
            problemas.add("BIOMETRIA_CLAVE falta, es la de desarrollo o tiene menos de "
                + LARGO_MINIMO_CLAVE + " caracteres");
        }
        // Encryptors.stronger exige hexadecimal, y con menos de 16 dígitos no agrega nada.
        if (salt == null || !salt.matches("[0-9a-fA-F]{16,}") || salt.length() % 2 != 0
                || SALT_DE_DESARROLLO.equalsIgnoreCase(salt)) {
            problemas.add("BIOMETRIA_SALT falta, es el de desarrollo o no es hexadecimal");
        }
        if ("consola".equalsIgnoreCase(canalDeCorreo)) {
            problemas.add("MAIL_CANAL esta en consola: quien vea el log entra a cualquier cuenta");
        }
        if (perfilProd && autonoma) {
            problemas.add("la instalacion autonoma no se puede exponer a internet (ADR-0022)");
        }
        return problemas;
    }
}
