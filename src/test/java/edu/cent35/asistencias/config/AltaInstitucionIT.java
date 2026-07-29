package edu.cent35.asistencias.config;

import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre el alta de una institución nueva, que es la única operación que corre sin institución
 * en contexto y sin sesión. Lo que se prueba no es que el formulario funcione, sino que la
 * clave de instalación sea una barrera real y que la cuenta inicial nazca utilizable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AltaInstitucionIT {

    private static final String CLAVE_BUENA = "clave-de-prueba";

    @Autowired private MockMvc mockMvc;
    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;

    @BeforeEach
    void preparar() {
        TenantContext.clear();
        limpiar();
        // El alta busca el rol INSTITUCION: sin el, la base no esta inicializada.
        Rol rol = new Rol();
        rol.setCodigo("INSTITUCION");
        rol.setDescripcion("Cuenta institucional");
        rolRepository.save(rol);
    }

    @AfterEach
    void limpiarDespues() {
        TenantContext.clear();
        limpiar();
    }

    @Test
    @DisplayName("La pantalla se abre sin estar logueado")
    void esPublica() throws Exception {
        mockMvc.perform(get("/alta-institucion")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Sin la clave de instalacion no se crea nada")
    void sinClaveNoCreaNada() throws Exception {
        mockMvc.perform(post("/alta-institucion").with(csrf()).params(datos("clave-equivocada")))
            .andExpect(status().isOk());          // vuelve al formulario, no redirige

        assertThat(institucionRepository.count())
            .as("una clave incorrecta no puede dejar ninguna institucion creada")
            .isZero();
        assertThat(usuarioRepository.count()).isZero();
    }

    @Test
    @DisplayName("Con la clave correcta crea la institucion y su primera cuenta")
    void creaInstitucionYCuenta() throws Exception {
        mockMvc.perform(post("/alta-institucion").with(csrf()).params(datos(CLAVE_BUENA)))
            .andExpect(status().is3xxRedirection());

        assertThat(institucionRepository.count()).isEqualTo(1);

        Optional<Usuario> creado = usuarioRepository.findByUsername("jefe.nuevo").stream().findFirst();
        assertThat(creado).isPresent();
        assertThat(creado.get().getRol().getCodigo()).isEqualTo("INSTITUCION");
        assertThat(creado.get().getInstitucionId())
            .isEqualTo(institucionRepository.findAll().get(0).getId());
    }

    @Test
    @DisplayName("La cuenta inicial nace verificada, para que la institucion no quede sin acceso")
    void laCuentaInicialNaceVerificada() throws Exception {
        mockMvc.perform(post("/alta-institucion").with(csrf()).params(datos(CLAVE_BUENA)))
            .andExpect(status().is3xxRedirection());

        Usuario creado = usuarioRepository.findByUsername("jefe.nuevo").get(0);
        assertThat(creado.getEmailVerificadoEn())
            .as("si tuviera que verificar por correo y el SMTP fallara, la institucion "
                + "quedaria sin acceso a su propia cuenta de gestion")
            .isNotNull();
    }

    @Test
    @DisplayName("No se puede repetir el nombre de una institucion ya registrada")
    void elNombreEsUnicoEnTodoElSistema() throws Exception {
        mockMvc.perform(post("/alta-institucion").with(csrf()).params(datos(CLAVE_BUENA)))
            .andExpect(status().is3xxRedirection());

        MultiValueMap<String, String> repetida = datos(CLAVE_BUENA);
        repetida.set("username", "otro.jefe");
        repetida.set("email", "otro@ejemplo.edu.ar");

        mockMvc.perform(post("/alta-institucion").with(csrf()).params(repetida))
            .andExpect(status().isOk());          // vuelve al formulario con el error

        assertThat(institucionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Sin token CSRF el alta se rechaza igual que en el resto del sistema")
    void exigeCsrf() throws Exception {
        mockMvc.perform(post("/alta-institucion").params(datos(CLAVE_BUENA)))
            .andExpect(status().isForbidden());
        assertThat(institucionRepository.count()).isZero();
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    private MultiValueMap<String, String> datos(String clave) {
        MultiValueMap<String, String> p = new LinkedMultiValueMap<>();
        p.add("claveInstalacion", clave);
        p.add("nombreInstitucion", "Instituto Nuevo");
        p.add("cuit", "");
        p.add("username", "jefe.nuevo");
        p.add("email", "jefe@ejemplo.edu.ar");
        p.add("password", "clave12345");
        p.add("confirmacion", "clave12345");
        p.add("nombre", "Jefa");
        p.add("apellido", "Nueva");
        return p;
    }

    private void limpiar() {
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        institucionRepository.deleteAll();
    }
}
