package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.RolCodigo;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.service.ClaveRecuperacionService;
import edu.cent35.asistencias.service.InstalacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La clave de recuperación de una instalación autónoma (ADR-0022, decisión 5).
 *
 * <p><b>Qué cuida.</b> Que una instalación sin correo tenga camino de vuelta cuando se olvida
 * la contraseña de la cuenta institucional —que es TD-009, y sin esto una contraseña olvidada
 * deja la instalación inservible— y que ese camino no se abra con cualquier cosa.
 *
 * <p>Lo que hace que la clave sirva de verdad es que nazca sola con la institución: si hubiera
 * que acordarse de generarla, la instalación que la necesita es justamente la que no la tiene.
 */
@SpringBootTest(properties = "app.instalacion.autonoma=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Clave de recuperación")
class ClaveRecuperacionIT {

    @Autowired MockMvc mvc;
    @Autowired InstitucionRepository institucionRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RolRepository rolRepository;
    @Autowired InstalacionService instalacion;
    @Autowired ClaveRecuperacionService claveService;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void dejarLaBaseComoReciénInstalada() {
        TenantContext.clear();
        usuarioRepository.deleteAll();
        institucionRepository.deleteAll();
        ReflectionTestUtils.setField(instalacion, "yaHayInstitucion", false);
        rolRepository.findByCodigo(RolCodigo.INSTITUCION.name())
            .orElseGet(() -> rolRepository.save(
                Rol.builder().codigo(RolCodigo.INSTITUCION.name()).descripcion("Institución").build()));
    }

    @Test
    @DisplayName("la configuración inicial la genera sola, sin que nadie se acuerde")
    void naceConLaInstitucion() throws Exception {
        configurarLaInstalacion();

        Institucion creada = institucionRepository.findAll().get(0);
        assertThat(creada.getClaveRecuperacionHash()).isNotNull();
        assertThat(creada.getClaveRecuperacionCreadaEn()).isNotNull();
    }

    @Test
    @DisplayName("con la clave correcta se pone una contraseña nueva")
    void restableceConLaClave() throws Exception {
        String clave = configurarLaInstalacion();

        mvc.perform(post("/recuperar/clave").with(csrf())
                .param("username", "instituto")
                .param("clave", clave)
                .param("nuevaPassword", "Nueva456")
                .param("confirmacion", "Nueva456"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));

        assertThat(passwordEncoder.matches("Nueva456", cuenta().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("se acepta como haya quedado anotada: sin guiones y en minúscula")
    void aceptaLaClaveComoLaCopiaron() throws Exception {
        String clave = configurarLaInstalacion();
        String comoLaCopiaron = clave.replace("-", "").toLowerCase();

        mvc.perform(post("/recuperar/clave").with(csrf())
                .param("username", "instituto")
                .param("clave", comoLaCopiaron)
                .param("nuevaPassword", "Nueva456")
                .param("confirmacion", "Nueva456"))
            .andExpect(redirectedUrl("/login"));

        assertThat(passwordEncoder.matches("Nueva456", cuenta().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("con la clave equivocada no cambia nada")
    void noRestableceConUnaClaveEquivocada() throws Exception {
        configurarLaInstalacion();
        String hashOriginal = cuenta().getPasswordHash();

        mvc.perform(post("/recuperar/clave").with(csrf())
                .param("username", "instituto")
                .param("clave", "ZZZZZ-ZZZZZ-ZZZZZ-ZZZZZ")
                .param("nuevaPassword", "Nueva456")
                .param("confirmacion", "Nueva456"))
            .andExpect(status().isOk());

        assertThat(cuenta().getPasswordHash()).isEqualTo(hashOriginal);
    }

    @Test
    @DisplayName("generar una nueva desde Mi institución deja sin valor a la anterior")
    void regenerarInvalidaLaAnterior() throws Exception {
        String vieja = configurarLaInstalacion();

        mvc.perform(post("/mi-institucion/clave-recuperacion").with(csrf())
                .with(user(principalDeLaInstitucion())))
            .andExpect(redirectedUrl("/mi-institucion"));

        // La clave que alguien tenía anotada deja de abrir la puerta en el mismo acto: si no,
        // regenerarla no serviría para el caso que más importa, que es que la vio quien no debía.
        mvc.perform(post("/recuperar/clave").with(csrf())
                .param("username", "instituto")
                .param("clave", vieja)
                .param("nuevaPassword", "Nueva456")
                .param("confirmacion", "Nueva456"))
            .andExpect(status().isOk());

        assertThat(passwordEncoder.matches("Nueva456", cuenta().getPasswordHash())).isFalse();
    }

    @Test
    @DisplayName("el formulario de recuperación se ofrece desde el login")
    void elLoginOfreceLaRecuperacionPorClave() throws Exception {
        configurarLaInstalacion();
        mvc.perform(get("/recuperar/clave")).andExpect(status().isOk());
    }

    // ---- helpers -------------------------------------------------------------------------

    /** Corre la configuración inicial y devuelve la clave que se mostró una sola vez. */
    private String configurarLaInstalacion() throws Exception {
        MvcResult res = mvc.perform(post("/instalacion").with(csrf())
                .param("nombreInstitucion", "Instituto con clave")
                .param("cuit", "")
                .param("username", "instituto")
                .param("email", "instalacion@ejemplo.test")
                .param("password", "Prueba123")
                .param("confirmacion", "Prueba123"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

        Object clave = res.getFlashMap().get("claveRecuperacion");
        assertThat(clave).as("la configuración inicial tiene que entregar la clave").isNotNull();
        return (String) clave;
    }

    private Usuario cuenta() {
        return usuarioRepository.findByUsername("instituto").get(0);
    }

    private UsuarioAutenticado principalDeLaInstitucion() {
        Usuario real = cuenta();
        Rol r = new Rol();
        r.setId((short) 1);
        r.setCodigo(RolCodigo.INSTITUCION.name());
        r.setDescripcion("Institución");
        Usuario u = Usuario.builder()
            .id(real.getId()).username(real.getUsername()).passwordHash("no-se-usa")
            .activo(true).rol(r).emailVerificadoEn(LocalDateTime.now()).build();
        u.setInstitucionId(real.getInstitucionId());
        return new UsuarioAutenticado(u);
    }
}
