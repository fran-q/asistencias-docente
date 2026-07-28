package edu.cent35.asistencias.config;

import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.CodigoVerificacionRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.service.NotificadorEmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre la única zona que queda abierta sin sesión además del login. Lo que se prueba no es que
 * el flujo funcione, sino que no filtre información: la respuesta tiene que ser idéntica exista
 * o no la cuenta, porque si difiriera alcanzaría con probar direcciones para saber quién tiene
 * cuenta en el sistema.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecuperacionPublicaIT {

    private static final String USUARIO_REAL = "persona.real";
    private static final String EMAIL_REAL = "persona.real@cent35.edu.ar";

    @Autowired private MockMvc mockMvc;
    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private CodigoVerificacionRepository codigoRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // Se reemplaza el notificador real para poder afirmar que NO se lo llama. Con el real solo
    // se sabria que el envio fallo por no haber SMTP, que no es lo mismo que no haberlo pedido.
    @MockBean private NotificadorEmailService notificador;

    // Una cuenta real contra la cual comparar el comportamiento con una inexistente.
    @BeforeEach
    void sembrar() {
        TenantContext.clear();
        limpiar();

        Institucion inst = institucionRepository.save(
            Institucion.builder().nombre("Instituto de prueba").activo(true).build());

        Rol rol = new Rol();
        rol.setCodigo("ADMIN");
        rol.setDescripcion("Administrativo");
        rol = rolRepository.save(rol);

        Usuario u = Usuario.builder()
            .username(USUARIO_REAL)
            .email(EMAIL_REAL)
            .passwordHash(passwordEncoder.encode("original123"))
            .nombre("Persona").apellido("Real")
            .activo(true)
            .rol(rol)
            .build();
        u.setInstitucionId(inst.getId());
        usuarioRepository.save(u);
    }

    @AfterEach
    void limpiarDespues() {
        TenantContext.clear();
        limpiar();
    }

    // ========================================================================
    //  Acceso sin sesion
    // ========================================================================

    @Test
    @DisplayName("Las pantallas de recuperacion se abren sin estar logueado")
    void sonPublicas() throws Exception {
        mockMvc.perform(get("/recuperar")).andExpect(status().isOk());
        mockMvc.perform(get("/recuperar/codigo")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Sin token CSRF el pedido se rechaza igual que en el resto del sistema")
    void exigeCsrf() throws Exception {
        mockMvc.perform(post("/recuperar").param("usuarioOEmail", USUARIO_REAL))
            .andExpect(status().isForbidden());
    }

    // ========================================================================
    //  No revelar quien tiene cuenta
    // ========================================================================

    @Test
    @DisplayName("Pedir el codigo responde lo mismo exista o no la cuenta")
    void noRevelaSiLaCuentaExiste() throws Exception {
        MvcResult existente = pedirCodigo(USUARIO_REAL);
        MvcResult inventado = pedirCodigo("no.existe.esta.cuenta");

        assertThat(inventado.getResponse().getStatus())
            .isEqualTo(existente.getResponse().getStatus());
        assertThat(inventado.getResponse().getRedirectedUrl())
            .isEqualTo(existente.getResponse().getRedirectedUrl());
    }

    @Test
    @DisplayName("La pantalla del codigo se ve identica exista o no la cuenta")
    void laPantallaDelCodigoNoDelataLaCuenta() throws Exception {
        // Comparar el estado y la redireccion no alcanza: los dos casos redirigen igual y la
        // diferencia aparece recien en la pantalla siguiente. Aca se sigue el flujo hasta el
        // HTML renderizado, que es donde se filtraba el correo enmascarado del titular.
        String conCuentaReal = pantallaDelCodigoTras(USUARIO_REAL);
        String conCuentaInventada = pantallaDelCodigoTras("no.existe.esta.cuenta");

        assertThat(conCuentaReal)
            .as("el HTML debe ser identico; cualquier diferencia permite enumerar cuentas")
            .isEqualTo(conCuentaInventada);
    }

    @Test
    @DisplayName("La pantalla del codigo nunca muestra el correo del titular")
    void laPantallaDelCodigoNoMuestraElCorreo() throws Exception {
        String pantalla = pantallaDelCodigoTras(USUARIO_REAL);

        assertThat(pantalla).doesNotContain(EMAIL_REAL);
        assertThat(pantalla)
            .as("ni siquiera enmascarado: la inicial y el dominio ya confirman la cuenta")
            .doesNotContain("****");
    }

    @Test
    @DisplayName("Tambien responde igual buscando por correo")
    void noRevelaPorCorreo() throws Exception {
        MvcResult existente = pedirCodigo(EMAIL_REAL);
        MvcResult inventado = pedirCodigo("nadie@ejemplo.com");

        assertThat(inventado.getResponse().getRedirectedUrl())
            .isEqualTo(existente.getResponse().getRedirectedUrl());
    }

    @Test
    @DisplayName("Solo se emite codigo cuando la cuenta existe de verdad")
    void soloSeEmiteParaCuentasReales() throws Exception {
        pedirCodigo("no.existe.esta.cuenta");
        assertThat(codigoRepository.count())
            .as("una cuenta inventada no debe generar ningun codigo")
            .isZero();

        pedirCodigo(USUARIO_REAL);
        assertThat(codigoRepository.count())
            .as("la cuenta real si genera su codigo, aunque el envio falle")
            .isEqualTo(1);
    }

    @Test
    @DisplayName("Un identificador que no es de nadie no dispara ningun correo")
    void noSeMandaCorreoACuentasInexistentes() throws Exception {
        pedirCodigo("no.existe.esta.cuenta");
        pedirCodigo("desconocido@otrodominio.com");

        verify(notificador, never()).enviarCodigo(any(), any(), any(), any());
    }

    @Test
    @DisplayName("El correo va a la direccion registrada, no a la que se escribio")
    void elCorreoVaALaDireccionRegistrada() throws Exception {
        // Se busca por usuario, asi que la direccion de destino la pone el sistema. Si saliera
        // hacia lo tipeado, cualquiera podria hacer que el sistema le escriba a un tercero.
        pedirCodigo(USUARIO_REAL);

        verify(notificador).enviarCodigo(any(), any(), org.mockito.ArgumentMatchers.eq(EMAIL_REAL), any());
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    // Dispara el paso 1 del flujo. En el perfil de test no hay SMTP escuchando, asi que el
    // envio falla: se aprovecha para verificar que ni aun asi cambia la respuesta.
    private MvcResult pedirCodigo(String identificador) throws Exception {
        return mockMvc.perform(post("/recuperar")
                .with(csrf())
                .param("usuarioOEmail", identificador))
            .andReturn();
    }

    // Recorre el paso 1 y devuelve el HTML del paso 2, arrastrando la sesion: es la sesion la
    // que sabe si la cuenta existia, asi que sin ella la comparacion no probaria nada.
    private String pantallaDelCodigoTras(String identificador) throws Exception {
        MvcResult paso1 = mockMvc.perform(post("/recuperar")
                .with(csrf())
                .param("usuarioOEmail", identificador))
            .andReturn();

        String html = mockMvc.perform(get("/recuperar/codigo")
                .session((MockHttpSession) paso1.getRequest().getSession()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // El token CSRF es distinto en cada sesion por definicion, asi que se normaliza: de
        // lo contrario las dos paginas nunca serian iguales y el test no probaria nada.
        return html.replaceAll("name=\"_csrf\" value=\"[^\"]*\"", "name=\"_csrf\" value=\"TOKEN\"");
    }

    private void limpiar() {
        codigoRepository.deleteAll();
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        institucionRepository.deleteAll();
    }
}
