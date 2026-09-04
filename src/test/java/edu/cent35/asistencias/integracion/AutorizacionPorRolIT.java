package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.interceptor.VerificacionInterceptor;
import edu.cent35.asistencias.interceptor.TenantInterceptor;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.InstitucionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que las reglas de @PreAuthorize de los controladores se cumplan de verdad sobre
 * peticiones HTTP: que un ADMIN no alcance las pantallas reservadas al rol INSTITUCION, que el
 * anónimo caiga al login y que un POST sin token CSRF sea rechazado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AutorizacionPorRolIT {

    // Pantallas compartidas por INSTITUCION y ADMIN. Todas exigen sesion, asi que esta lista
    // es la que usa el caso del anonimo.
    private static final String[] PANTALLAS_COMPARTIDAS = {
        "/docentes", "/carreras", "/materias", "/comisiones",
        "/horarios", "/grilla", "/asistencias", "/reportes", "/asistencia/pase"
    };

    // Las que se abren con solo tener el rol. El pase quedo afuera: desde ADR-0015 tambien
    // exige que el equipo sea un puesto autorizado, asi que un 200 ahi ya no depende del rol
    // y mezclarlo haria que este test midiera dos controles a la vez. El del puesto vive en
    // PuestoCapturaIT, incluido que el pase siga alcanzable con el equipo designado.
    private static final String[] ABREN_SOLO_CON_ROL = {
        "/docentes", "/carreras", "/materias", "/comisiones",
        "/horarios", "/grilla", "/asistencias", "/reportes"
    };

    @Autowired private MockMvc mockMvc;
    @Autowired private InstitucionRepository institucionRepository;

    private Long tenantId;

    // Hace falta una institucion real: las pantallas del rol INSTITUCION la leen desde el tenant.
    @BeforeEach
    void sembrar() {
        TenantContext.clear();
        institucionRepository.deleteAll();
        Institucion i = institucionRepository.save(
            Institucion.builder().nombre("Instituto de prueba").activo(true).build());
        tenantId = i.getId();
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
        institucionRepository.deleteAll();
    }

    // ========================================================================
    //  Pantallas reservadas al rol INSTITUCION
    // ========================================================================

    @Test
    @DisplayName("Un ADMIN no puede entrar a la administracion de usuarios")
    void adminNoAccedeAUsuarios() throws Exception {
        mockMvc.perform(get("/usuarios").with(user(principal("ADMIN"))))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un ADMIN no puede entrar a los datos de la institucion")
    void adminNoAccedeAMiInstitucion() throws Exception {
        mockMvc.perform(get("/mi-institucion").with(user(principal("ADMIN"))))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El rol INSTITUCION si entra a usuarios y a su institucion")
    void institucionAccedeASusPantallas() throws Exception {
        mockMvc.perform(get("/usuarios").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk());
        mockMvc.perform(get("/mi-institucion").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk());
    }

    // ========================================================================
    //  Pantallas compartidas
    // ========================================================================

    @ParameterizedTest(name = "ADMIN accede a {0}")
    @ValueSource(strings = {
        "/docentes", "/carreras", "/materias", "/comisiones",
        "/horarios", "/grilla", "/asistencias", "/reportes"
    })
    @DisplayName("El ADMIN accede a las pantallas compartidas")
    void adminAccedeALasCompartidas(String ruta) throws Exception {
        mockMvc.perform(get(ruta).with(user(principal("ADMIN"))))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("El rol INSTITUCION tambien accede a las pantallas compartidas")
    void institucionAccedeALasCompartidas() throws Exception {
        for (String ruta : ABREN_SOLO_CON_ROL) {
            mockMvc.perform(get(ruta).with(user(principal("INSTITUCION"))))
                .andExpect(status().isOk());
        }
    }

    // ========================================================================
    //  Sin autenticar
    // ========================================================================

    @Test
    @DisplayName("El anonimo es mandado al login en vez de ver datos")
    void anonimoVaAlLogin() throws Exception {
        for (String ruta : PANTALLAS_COMPARTIDAS) {
            mockMvc.perform(get(ruta))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        }
        mockMvc.perform(get("/usuarios"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("El login y los estaticos quedan abiertos")
    void recursosPublicosSiguenAbiertos() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
    }

    // ========================================================================
    //  CSRF
    // ========================================================================

    @Test
    @DisplayName("Un POST sin token CSRF se rechaza aunque el rol sea correcto")
    void postSinCsrfSeRechaza() throws Exception {
        mockMvc.perform(post("/carreras/nueva")
                .with(user(principal("INSTITUCION")))
                .param("codigo", "X-1")
                .param("nombre", "Carrera X"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El mismo POST con token CSRF ya no se rechaza por seguridad")
    void postConCsrfPasaElControlDeSeguridad() throws Exception {
        mockMvc.perform(post("/carreras/nueva")
                .with(user(principal("INSTITUCION")))
                .with(csrf())
                .param("codigo", "X-1")
                .param("nombre", "Carrera X")
                // Obligatoria desde V010: sin ella el alta vuelve al formulario con
                // errores de validacion y el test dejaria de probar lo que dice probar.
                .param("duracionAnios", "3"))
            .andExpect(status().is3xxRedirection());
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    // Principal de la aplicacion (no el de Spring): TenantInterceptor necesita un
    // UsuarioAutenticado para poder publicar la institucion en el TenantContext.
    private UsuarioAutenticado principal(String rol) {
        Rol r = new Rol();
        r.setId((short) 1);
        r.setCodigo(rol);
        r.setDescripcion(rol);

        Usuario u = Usuario.builder()
            .id(99L)
            .username("test." + rol.toLowerCase())
            .passwordHash("no-se-usa")
            .persona(DatosDePrueba.persona("Test", rol))
            .activo(true)
            .rol(r)
            // Verificada: estos tests miran los permisos por rol, y una cuenta sin verificar
            // queda retenida antes por VerificacionInterceptor, que es otra regla distinta.
            .emailVerificadoEn(java.time.LocalDateTime.now())
            .build();
        u.setInstitucionId(tenantId);
        return new UsuarioAutenticado(u);
    }
}
