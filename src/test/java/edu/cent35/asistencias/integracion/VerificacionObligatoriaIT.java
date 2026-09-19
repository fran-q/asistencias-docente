package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que una cuenta que todavía no confirmó su correo no pueda operar el sistema, y —
 * tanto o más importante— que sí pueda llegar a la pantalla donde se desbloquea. Un bloqueo
 * mal puesto acá no es un fallo menor: deja a la persona encerrada fuera de su propia cuenta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificacionObligatoriaIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SessionRegistry sessionRegistry;

    private Long institucionId;
    private Rol rolInstitucion;

    @BeforeEach
    void preparar() {
        TenantContext.clear();
        limpiar();

        institucionId = institucionRepository.save(
            Institucion.builder().nombre("Instituto de prueba").activo(true).build()).getId();

        Rol r = new Rol();
        r.setCodigo("INSTITUCION");
        r.setDescripcion("Cuenta institucional");
        rolInstitucion = rolRepository.save(r);
    }

    @AfterEach
    void limpiarDespues() {
        TenantContext.clear();
        limpiar();
    }

    @ParameterizedTest(name = "sin verificar no entra a {0}")
    @ValueSource(strings = {"/", "/docentes", "/carreras", "/asistencias", "/reportes", "/usuarios"})
    @DisplayName("Una cuenta sin verificar no puede operar el sistema")
    void sinVerificarNoOpera(String ruta) throws Exception {
        var respuesta = mockMvc.perform(get(ruta).with(user(principalDe(cuenta("sin.verificar", null)))))
            .andExpect(status().is3xxRedirection())
            .andReturn().getResponse();

        assertThat(respuesta.getRedirectedUrl())
            .as("tiene que mandar a la pantalla donde se desbloquea, no a cualquier lado")
            .startsWith("/mi-cuenta");
    }

    @Test
    @DisplayName("Sin verificar SI puede llegar a su cuenta, que es donde se desbloquea")
    void sinVerificarLlegaASuCuenta() throws Exception {
        mockMvc.perform(get("/mi-cuenta").with(user(principalDe(cuenta("sin.verificar", null)))))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Una cuenta verificada opera con normalidad")
    void verificadaOperaNormal() throws Exception {
        Usuario u = cuenta("ya.verificada", LocalDateTime.now());
        mockMvc.perform(get("/docentes").with(user(principalDe(u))))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Quien verifica durante la sesion deja de estar bloqueado sin volver a entrar")
    void alVerificarSeDesbloqueaEnLaMismaSesion() throws Exception {
        // El principal se arma al iniciar sesion, asi que nace con la marca en falso. Si el
        // bloqueo se apoyara solo en esa foto, la persona verificaria y seguiria encerrada
        // hasta cerrar y volver a abrir sesion.
        Usuario u = cuenta("verifica.ahora", null);
        UsuarioAutenticado principal = principalDe(u);

        mockMvc.perform(get("/docentes").with(user(principal)))
            .andExpect(status().is3xxRedirection());

        u.setEmailVerificadoEn(LocalDateTime.now());
        usuarioRepository.save(u);

        mockMvc.perform(get("/docentes").with(user(principal)))
            .andExpect(status().isOk());
    }

    // ========================================================================
    //  Entrar con el usuario o con el correo
    // ========================================================================

    @Test
    @DisplayName("Se entra con el usuario o con el correo, sin importar las mayúsculas del correo")
    void seEntraConUsuarioOCorreo() throws Exception {
        cuenta("ana.perez", LocalDateTime.now());

        mockMvc.perform(get("/login"))
            .andExpect(content().string(containsString("Usuario o correo")));

        mockMvc.perform(post("/login").with(csrf())
                .param("username", "ana.perez").param("password", "Clave12345"))
            .andExpect(redirectedUrl("/"))
            .andExpect(authenticated().withUsername("ana.perez"));

        // Entre un ingreso y el otro, este test simula a la misma persona entrando de nuevo, no
        // a dos equipos a la vez: se olvida la sesion anterior. En el servidor la olvida el
        // contenedor al destruirla; MockMvc no destruye ninguna, asi que la de arriba seguiria
        // contando y el segundo ingreso terminaria en el paso de "ya hay una sesion abierta"
        // (ADR-0020), que es de lo que se ocupa SesionUnicaIT.
        olvidarLasSesiones();

        // La sesion queda a nombre del usuario aunque se haya entrado con el correo: es lo que
        // se muestra y lo que queda en el historial de lo que hizo.
        mockMvc.perform(post("/login").with(csrf())
                .param("username", "Ana.Perez@Ejemplo.edu.ar").param("password", "Clave12345"))
            .andExpect(redirectedUrl("/"))
            .andExpect(authenticated().withUsername("ana.perez"));

        mockMvc.perform(post("/login").with(csrf())
                .param("username", "ana.perez@ejemplo.edu.ar").param("password", "equivocada"))
            .andExpect(redirectedUrl("/login?error"))
            .andExpect(unauthenticated());
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    // Vacia el registro de sesiones abiertas, que vive en el contexto que los tests comparten.
    private void olvidarLasSesiones() {
        sessionRegistry.getAllPrincipals().stream()
            .flatMap(p -> sessionRegistry.getAllSessions(p, true).stream())
            .map(SessionInformation::getSessionId)
            .toList()
            .forEach(sessionRegistry::removeSessionInformation);
    }

    private Usuario cuenta(String username, LocalDateTime verificadoEn) {
        Usuario u = Usuario.builder().persona(DatosDePrueba.persona("Cuenta", "Prueba")).username(username).email(username + "@ejemplo.edu.ar").passwordHash(passwordEncoder.encode("Clave12345")).rol(rolInstitucion).activo(true).emailVerificadoEn(verificadoEn).build();
        u.setInstitucionId(institucionId);
        return usuarioRepository.save(u);
    }

    private UsuarioAutenticado principalDe(Usuario u) {
        return new UsuarioAutenticado(u);
    }

    private void limpiar() {
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        institucionRepository.deleteAll();
    }
}
