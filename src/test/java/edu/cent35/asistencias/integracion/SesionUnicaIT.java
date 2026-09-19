package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.service.UsuarioService;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Una cuenta se usa en un equipo por vez (ADR-0020).
 *
 * <p>Va como IT y no como test de servicio porque lo que hay que probar es el recorrido
 * entero: el ingreso registra la sesión, el segundo ingreso queda en el paso de confirmación,
 * mientras tanto no llega a ninguna pantalla, y recién al confirmar se cierra la anterior. Con
 * el servicio solo se probaría el registro, que es la parte que ya no falla.
 *
 * <p>Se entra con usuario y contraseña de verdad: el mecanismo depende de la sesión que crea
 * el login, y un principal puesto a mano por el soporte de tests no la crea.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SesionUnicaIT {

    private static final String USUARIO = "ana.sesiones";
    private static final String CLAVE = "Clave12345";

    @Autowired private MockMvc mockMvc;
    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UsuarioService usuarioService;
    @Autowired private SessionRegistry sessionRegistry;

    private Long institucionId;
    private Long usuarioId;

    @BeforeEach
    void sembrar() {
        TenantContext.clear();
        vaciarElRegistroDeSesiones();
        institucionId = institucionRepository.save(Institucion.builder()
            .nombre("Instituto de sesiones").activo(true).build()).getId();

        // Administrador y no institución: la cuenta institucional no se puede dar de baja, y
        // uno de los casos de acá es justamente la baja.
        Rol rol = rolRepository.findByCodigo("ADMIN")
            .orElseGet(() -> rolRepository.save(
                Rol.builder().codigo("ADMIN").descripcion("Administrador").build()));

        Usuario u = Usuario.builder()
            .persona(DatosDePrueba.persona("Ana", "Sesiones"))
            .username(USUARIO).email(USUARIO + "@ejemplo.edu.ar")
            .passwordHash(passwordEncoder.encode(CLAVE))
            .rol(rol).activo(true).emailVerificadoEn(LocalDateTime.now())
            .build();
        u.setInstitucionId(institucionId);
        usuarioId = usuarioRepository.save(u).getId();
    }

    @AfterEach
    void limpiar() {
        vaciarElRegistroDeSesiones();
        usuarioRepository.deleteById(usuarioId);
        institucionRepository.deleteById(institucionId);
        TenantContext.clear();
    }

    /**
     * El registro de sesiones vive en el contexto de Spring, que los tests comparten.
     *
     * <p>En el servidor una sesión sale del registro cuando el contenedor la destruye --cerrar
     * sesión, vencimiento--, y de eso avisa {@code HttpSessionEventPublisher}. Las sesiones de
     * MockMvc no las destruye nadie, así que sin esto las de un test quedarían contadas como
     * "otra sesión abierta" en el siguiente, y el segundo test vería un aviso que el primero
     * dejó colgado.
     */
    private void vaciarElRegistroDeSesiones() {
        sessionRegistry.getAllPrincipals().stream()
            .flatMap(p -> sessionRegistry.getAllSessions(p, true).stream())
            .map(SessionInformation::getSessionId)
            .toList()
            .forEach(sessionRegistry::removeSessionInformation);
    }

    @Test
    @DisplayName("Con una sola sesión se entra derecho, sin preguntar nada")
    void sinOtraSesionNoPregunta() throws Exception {
        MockHttpSession equipo = new MockHttpSession();

        entrar(equipo).andExpect(redirectedUrl("/"));
        mockMvc.perform(get("/").session(equipo)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("El segundo equipo queda en el paso que dice qué se va a cerrar")
    void elSegundoEquipoTieneQueDecidir() throws Exception {
        MockHttpSession primero = new MockHttpSession();
        entrar(primero).andExpect(redirectedUrl("/"));

        MockHttpSession segundo = new MockHttpSession();
        entrar(segundo).andExpect(redirectedUrl("/sesion/otra-abierta"));

        mockMvc.perform(get("/sesion/otra-abierta").session(segundo))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Ya hay una sesión abierta")))
            .andExpect(content().string(containsString(USUARIO)))
            // La otra sesion puede ser el equipo que toma asistencia: decirlo es la diferencia
            // entre una eleccion informada y apagar el pase sin enterarse.
            .andExpect(content().string(containsString("el pase deja de registrar")));

        // Hasta que no decida, esta sesion no sirve para otra cosa.
        mockMvc.perform(get("/docentes").session(segundo))
            .andExpect(redirectedUrl("/sesion/otra-abierta"));

        // Y la primera sigue trabajando: todavia no la desplazo nadie.
        mockMvc.perform(get("/").session(primero)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Al continuar acá, el primer equipo queda afuera y el login le dice por qué")
    void continuarAcaDesplazaAlPrimero() throws Exception {
        MockHttpSession primero = new MockHttpSession();
        entrar(primero);
        MockHttpSession segundo = new MockHttpSession();
        entrar(segundo);

        mockMvc.perform(post("/sesion/otra-abierta/continuar").session(segundo).with(csrf()))
            .andExpect(redirectedUrl("/"));

        // El que decidio continuar entra sin volver a pasar por el aviso.
        mockMvc.perform(get("/").session(segundo)).andExpect(status().isOk());

        mockMvc.perform(get("/").session(primero))
            .andExpect(redirectedUrl("/login?desplazada"));

        mockMvc.perform(get("/login").param("desplazada", ""))
            .andExpect(content().string(containsString("se entró con esta cuenta desde otro equipo")));
    }

    @Test
    @DisplayName("Salir del paso deja viva la otra sesión")
    void salirNoDesplazaANadie() throws Exception {
        MockHttpSession primero = new MockHttpSession();
        entrar(primero);
        MockHttpSession segundo = new MockHttpSession();
        entrar(segundo);

        mockMvc.perform(post("/logout").session(segundo).with(csrf()))
            .andExpect(redirectedUrl("/login?logout"));

        // Quien prefirio no interrumpir a nadie, no interrumpio a nadie.
        mockMvc.perform(get("/").session(primero)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Una sesión ya desplazada no se cuenta de nuevo")
    void laDesplazadaNoSeVuelveAAnunciar() throws Exception {
        MockHttpSession primero = new MockHttpSession();
        entrar(primero);
        MockHttpSession segundo = new MockHttpSession();
        entrar(segundo);
        mockMvc.perform(post("/sesion/otra-abierta/continuar").session(segundo).with(csrf()));

        // Un tercer equipo: la unica sesion abierta es la del segundo. La del primero quedo
        // desplazada y no tiene que sumar, o el aviso mandaria a cerrar algo ya cerrado.
        MockHttpSession tercero = new MockHttpSession();
        entrar(tercero).andExpect(redirectedUrl("/sesion/otra-abierta"));

        mockMvc.perform(get("/sesion/otra-abierta").session(tercero))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("otro equipo")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("2 equipos"))));
    }

    @Test
    @DisplayName("Dar de baja una cuenta corta la sesión que tenía abierta")
    void laBajaCortaLaSesion() throws Exception {
        MockHttpSession equipo = new MockHttpSession();
        entrar(equipo).andExpect(redirectedUrl("/"));

        TenantContext.set(institucionId);
        usuarioService.darDeBaja(usuarioId, 99L);
        TenantContext.clear();

        // Sin esto seguiria operando media hora con una cuenta dada de baja.
        mockMvc.perform(get("/").session(equipo))
            .andExpect(redirectedUrl("/login?desplazada"));
    }

    private org.springframework.test.web.servlet.ResultActions entrar(MockHttpSession sesion)
            throws Exception {
        return mockMvc.perform(post("/login").session(sesion).with(csrf())
            .param("username", USUARIO).param("password", CLAVE));
    }
}
