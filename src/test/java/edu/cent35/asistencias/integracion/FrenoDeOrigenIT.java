package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.EscenarioDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.institucion.Usuario;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.service.FrenoDeOrigenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El freno de intentos en el recorrido real: el filtro, la cadena de seguridad y la pantalla.
 *
 * <p>Va con su propio contexto y sus propios topes: el perfil de test los deja altos para que los
 * demás IT, que entran y fallan desde la misma dirección, no se bloqueen entre ellos.
 */
@SpringBootTest(properties = {
    "app.seguridad.login.max-por-cuenta-e-ip=3",
    "app.seguridad.login.max-por-cuenta=100",
    "app.seguridad.login.max-por-ip=100",
    "app.seguridad.publico.max-por-ip-por-hora=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FrenoDeOrigenIT {

    private static final String IP = "203.0.113.9";
    private static final String USUARIO = "ana.freno";
    private static final String CLAVE = "ClaveDePrueba1";

    @Autowired private MockMvc mvc;
    @Autowired private EscenarioDePrueba escenario;
    @Autowired private FrenoDeOrigenService freno;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long tenantId;

    @BeforeEach
    void preparar() {
        TenantContext.clear();
        vaciarFreno();
        tenantId = escenario.institucion("Instituto del freno");
        Usuario cuenta = escenario.cuenta(tenantId, "ADMIN", USUARIO,
            DatosDePrueba.persona("Ana", "Freno"));
        cuenta.setPasswordHash(passwordEncoder.encode(CLAVE));
        usuarios.save(cuenta);
    }

    @AfterEach
    void limpiar() {
        escenario.borrarTodoDe(tenantId);
        vaciarFreno();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Al llegar al tope, ni la contraseña correcta entra desde esa dirección")
    void elTopeFrenaAntesDeMirarLaContrasena() throws Exception {
        for (int i = 0; i < 3; i++) {
            entrar(USUARIO, "ClaveEquivocada1", IP).andExpect(redirectedUrl("/login?error"));
        }

        entrar(USUARIO, CLAVE, IP).andExpect(redirectedUrl("/login?bloqueado"));
    }

    @Test
    @DisplayName("Desde otra dirección el dueño de la cuenta sigue entrando")
    void otraDireccionEntra() throws Exception {
        for (int i = 0; i < 3; i++) {
            entrar(USUARIO, "ClaveEquivocada1", IP);
        }

        entrar(USUARIO, CLAVE, "198.51.100.1").andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("Una cuenta que no existe se comporta igual: el freno no delata cuáles existen")
    void cuentaInexistenteSeComportaIgual() throws Exception {
        for (int i = 0; i < 3; i++) {
            entrar("nadie.se.llama.asi", "ClaveEquivocada1", IP)
                .andExpect(redirectedUrl("/login?error"));
        }

        entrar("nadie.se.llama.asi", "ClaveEquivocada1", IP)
            .andExpect(redirectedUrl("/login?bloqueado"));
    }

    @Test
    @DisplayName("Quien viene del kiosco conserva la vuelta aunque quede frenado")
    void conservaLaVueltaAlKiosco() throws Exception {
        for (int i = 0; i < 3; i++) {
            entrar(USUARIO, "ClaveEquivocada1", IP);
        }

        mvc.perform(post("/login").with(csrf()).with(desde(IP))
                .param("username", USUARIO).param("password", CLAVE)
                .param("desde", "kiosco"))
            .andExpect(redirectedUrl("/login?bloqueado&desde=kiosco"));
    }

    @Test
    @DisplayName("La pantalla de login explica que hay que esperar")
    void laPantallaLoExplica() throws Exception {
        mvc.perform(get("/login").param("bloqueado", ""))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("demasiados intentos")));
    }

    @Test
    @DisplayName("Las pantallas públicas frenan por dirección, aunque cambie la cuenta (TD-008)")
    void lasPantallasPublicasFrenanPorDireccion() throws Exception {
        for (String cuenta : new String[] {"cuenta.uno", "cuenta.dos"}) {
            mvc.perform(post("/recuperar").with(csrf()).with(desde(IP)).session(new MockHttpSession())
                    .param("usuarioOEmail", cuenta))
                .andExpect(redirectedUrl("/recuperar/codigo"));
        }

        mvc.perform(post("/recuperar").with(csrf()).with(desde(IP)).session(new MockHttpSession())
                .param("usuarioOEmail", "cuenta.tres"))
            .andExpect(redirectedUrl("/login?bloqueado"));
    }

    private ResultActions entrar(String usuario, String clave, String ip) throws Exception {
        return mvc.perform(post("/login").with(csrf()).with(desde(ip)).session(new MockHttpSession())
            .param("username", usuario).param("password", clave));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor desde(String ip) {
        return pedido -> {
            pedido.setRemoteAddr(ip);
            return pedido;
        };
    }

    // El freno es un bean compartido por los casos de esta clase.
    private void vaciarFreno() {
        ((Map<?, ?>) ReflectionTestUtils.getField(freno, "fallidos")).clear();
        ((Map<?, ?>) ReflectionTestUtils.getField(freno, "publicos")).clear();
    }
}
