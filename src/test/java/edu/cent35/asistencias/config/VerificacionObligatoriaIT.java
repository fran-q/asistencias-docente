package edu.cent35.asistencias.config;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        CustomUserDetails principal = principalDe(u);

        mockMvc.perform(get("/docentes").with(user(principal)))
            .andExpect(status().is3xxRedirection());

        u.setEmailVerificadoEn(LocalDateTime.now());
        usuarioRepository.save(u);

        mockMvc.perform(get("/docentes").with(user(principal)))
            .andExpect(status().isOk());
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    private Usuario cuenta(String username, LocalDateTime verificadoEn) {
        Usuario u = Usuario.builder()
            .username(username)
            .email(username + "@ejemplo.edu.ar")
            .passwordHash(passwordEncoder.encode("clave12345"))
            .nombre("Cuenta").apellido("Prueba")
            .rol(rolInstitucion)
            .activo(true)
            .emailVerificadoEn(verificadoEn)
            .build();
        u.setInstitucionId(institucionId);
        return usuarioRepository.save(u);
    }

    private CustomUserDetails principalDe(Usuario u) {
        return new CustomUserDetails(u);
    }

    private void limpiar() {
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        institucionRepository.deleteAll();
    }
}
