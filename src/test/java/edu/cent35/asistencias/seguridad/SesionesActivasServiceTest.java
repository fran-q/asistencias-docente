package edu.cent35.asistencias.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las sesiones abiertas de una cuenta.
 *
 * <p>Se usa el registro de verdad ({@link SessionRegistryImpl}) y no un mock: lo que hay que
 * probar es cómo responde ese registro, y un mock devolvería lo que el test le dicte. En
 * particular, que <b>dos ingresos de la misma cuenta producen dos principales distintos</b>
 * --{@link UsuarioAutenticado} no define {@code equals}-- y que el servicio los junta igual
 * porque compara el nombre de usuario. Con un mock, ese detalle, que es justamente el que
 * hacía fallar la búsqueda, no se vería.
 */
class SesionesActivasServiceTest {

    private final SessionRegistry registro = new SessionRegistryImpl();
    private final SesionesActivasService service = new SesionesActivasService(registro);

    @Test
    @DisplayName("otras: encuentra la sesión de la misma cuenta aunque el principal sea otro objeto")
    void encuentraLaOtraSesion() {
        registro.registerNewSession("sesion-a", principal("ana.perez"));
        registro.registerNewSession("sesion-b", principal("ana.perez"));

        assertThat(service.otrasDe("ana.perez", "sesion-b"))
            .extracting(SessionInformation::getSessionId)
            .containsExactly("sesion-a");
    }

    @Test
    @DisplayName("otras: no cuenta las de otras cuentas ni la que pregunta")
    void noMezclaCuentas() {
        registro.registerNewSession("sesion-a", principal("ana.perez"));
        registro.registerNewSession("sesion-otro", principal("juan.gomez"));

        assertThat(service.otrasDe("ana.perez", "sesion-a")).isEmpty();
    }

    @Test
    @DisplayName("otras: una sesión ya desplazada no vuelve a anunciarse")
    void noCuentaLasYaCerradas() {
        registro.registerNewSession("sesion-a", principal("ana.perez"));
        registro.registerNewSession("sesion-b", principal("ana.perez"));
        service.desplazarOtras("ana.perez", "sesion-b");

        // La sesion desplazada sigue en el registro hasta que su equipo vuelva a pedir algo.
        // Si se contara, quien entra despues veria un aviso por una sesion que ya esta cerrada.
        registro.registerNewSession("sesion-c", principal("ana.perez"));
        assertThat(service.otrasDe("ana.perez", "sesion-c"))
            .extracting(SessionInformation::getSessionId)
            .containsExactly("sesion-b");
    }

    @Test
    @DisplayName("desplazar: cierra las otras y deja viva la que pidió continuar")
    void desplazaLasOtras() {
        registro.registerNewSession("sesion-a", principal("ana.perez"));
        registro.registerNewSession("sesion-b", principal("ana.perez"));
        registro.registerNewSession("sesion-c", principal("ana.perez"));

        assertThat(service.desplazarOtras("ana.perez", "sesion-c")).isEqualTo(2);
        assertThat(registro.getSessionInformation("sesion-a").isExpired()).isTrue();
        assertThat(registro.getSessionInformation("sesion-b").isExpired()).isTrue();
        assertThat(registro.getSessionInformation("sesion-c").isExpired())
            .as("la que decidió continuar es la única que queda en pie")
            .isFalse();
    }

    @Test
    @DisplayName("cerrarTodas: la baja de la cuenta no deja ninguna abierta")
    void cierraTodasEnLaBaja() {
        registro.registerNewSession("sesion-a", principal("ana.perez"));
        registro.registerNewSession("sesion-b", principal("ana.perez"));

        assertThat(service.cerrarTodasDe("ana.perez")).isEqualTo(2);
        assertThat(registro.getSessionInformation("sesion-a").isExpired()).isTrue();
        assertThat(registro.getSessionInformation("sesion-b").isExpired()).isTrue();
    }

    @Test
    @DisplayName("sin sesiones abiertas no hay nada que cerrar")
    void sinSesiones() {
        assertThat(service.otrasDe("ana.perez", "sesion-a")).isEmpty();
        assertThat(service.desplazarOtras("ana.perez", "sesion-a")).isZero();
    }

    // Un principal nuevo por ingreso, como los que arma el login: misma cuenta, otro objeto.
    private UsuarioAutenticado principal(String username) {
        edu.cent35.asistencias.model.Rol rol = new edu.cent35.asistencias.model.Rol();
        rol.setCodigo("ADMIN");
        rol.setDescripcion("Administrador");
        edu.cent35.asistencias.model.Usuario u = edu.cent35.asistencias.model.Usuario.builder()
            .id(1L).username(username).email(username + "@x.test")
            .passwordHash("no-se-usa").activo(true).rol(rol)
            .build();
        u.setInstitucionId(1L);
        return new UsuarioAutenticado(u);
    }

    // Deja a la vista que dos ingresos no comparten identidad: es lo que obliga a buscar por
    // nombre de usuario en vez de por el principal.
    @Test
    @DisplayName("dos ingresos de la misma cuenta no son el mismo principal")
    void dosIngresosNoSonElMismoPrincipal() {
        assertThat(List.of(principal("ana.perez")))
            .doesNotContain(principal("ana.perez"));
    }
}
