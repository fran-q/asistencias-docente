package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.dto.CambioDeCorreo;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Persona;
import edu.cent35.asistencias.model.PropositoCodigo;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.CodigoVerificacionRepository;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.PersonaRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.service.CanalDeCodigos;
import edu.cent35.asistencias.validacion.UsuarioValido;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Edición de los datos de la propia cuenta: usuario, nombre y correo (docx 3b).
 *
 * <p>Lo que más importa probar es el correo, porque es por donde se recupera la contraseña: que
 * no cambie hasta el segundo código y que el segundo no se pueda pedir sin el primero. Si
 * alcanzara con confirmar la dirección nueva, quien encontrara una sesión abierta pondría la
 * suya y después se quedaría con la cuenta usando "olvidé mi contraseña".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MiCuentaEdicionIT {

    private static final AtomicInteger SEC = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PersonaRepository personaRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private DocenteRepository docenteRepository;
    @Autowired private CodigoVerificacionRepository codigoRepository;
    @Autowired private PlatformTransactionManager txManager;

    // Se mockea el canal para leer los codigos que "llegan" a cada buzon.
    @MockBean private CanalDeCodigos notificador;

    private int n;
    private Long tenantId;
    private Long otraInstitucionId;
    private Long adminId;
    private Long institucionalId;
    private Long ajenoId;
    private String correoAdmin;

    @BeforeEach
    void sembrar() {
        TenantContext.clear();
        n = SEC.incrementAndGet();
        correoAdmin = "adm" + n + "@x.test";

        tenantId = institucionRepository.save(Institucion.builder()
            .nombre("Instituto mi cuenta " + n).activo(true).build()).getId();
        otraInstitucionId = institucionRepository.save(Institucion.builder()
            .nombre("Otro instituto mi cuenta " + n).activo(true).build()).getId();

        Rol admin = rolRepository.findByCodigo("ADMIN")
            .orElseGet(() -> rolRepository.save(Rol.builder().codigo("ADMIN").descripcion("Administrador").build()));
        Rol inst = rolRepository.findByCodigo("INSTITUCION")
            .orElseGet(() -> rolRepository.save(Rol.builder().codigo("INSTITUCION").descripcion("Institución").build()));

        TenantContext.set(tenantId);
        adminId = guardar(Usuario.builder()
            .persona(DatosDePrueba.persona("Marcelo", "Quinteros"))
            .username("adm.cuenta" + n).email(correoAdmin), admin, tenantId);
        // La cuenta institucional va sin persona (V018).
        institucionalId = guardar(Usuario.builder()
            .username("inst.cuenta" + n).email("inst" + n + "@x.test"), inst, tenantId);

        TenantContext.set(otraInstitucionId);
        ajenoId = guardar(Usuario.builder()
            .persona(DatosDePrueba.persona("Otra", "Persona"))
            .username("ocupado" + n).email("ajeno" + n + "@x.test"), admin, otraInstitucionId);
        TenantContext.clear();
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
        codigoRepository.deleteAll();
        docenteRepository.findAll().stream()
            .filter(d -> tenantId.equals(d.getInstitucionId()))
            .forEach(docenteRepository::delete);
        usuarioRepository.deleteAllById(List.of(adminId, institucionalId, ajenoId));
        institucionRepository.deleteAllById(List.of(tenantId, otraInstitucionId));
    }

    // ========================================================================
    //  La ficha y el formulario
    // ========================================================================

    @Test
    @DisplayName("Mi cuenta ofrece editar, y el formulario viene con los datos de la cuenta")
    void fichaYFormulario() throws Exception {
        UsuarioAutenticado yo = principalDe(adminId);

        mockMvc.perform(get("/mi-cuenta").with(user(yo)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("href=\"/mi-cuenta/editar\"")));

        String form = mockMvc.perform(get("/mi-cuenta/editar").with(user(yo)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(form)
            .contains("value=\"adm.cuenta" + n + "\"")
            .contains("value=\"" + correoAdmin + "\"")
            .containsPattern("id=\"nombre\"[^>]*data-nombre-propio");
        assertThat(form)
            .as("el navegador avisa con la misma regla que el servidor")
            .contains("pattern=\"" + UsuarioValido.PATRON_HTML + "\"");
    }

    @Test
    @DisplayName("La cuenta de la institución edita usuario y correo; su nombre es el del establecimiento")
    void institucionSinNombrePropio() throws Exception {
        mockMvc.perform(get("/mi-cuenta/editar").with(user(principalDe(institucionalId))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"username\"")))
            .andExpect(content().string(containsString("Es el nombre de la institución")))
            .andExpect(content().string(not(containsString("id=\"apellido\""))));
    }

    // ========================================================================
    //  Usuario y nombre
    // ========================================================================

    @Test
    @DisplayName("Cambia el usuario y el nombre, y la sesión los muestra sin volver a entrar")
    void cambiaUsuarioYNombre() throws Exception {
        UsuarioAutenticado yo = principalDe(adminId);

        mockMvc.perform(post("/mi-cuenta/editar").with(user(yo)).with(csrf())
                .param("username", "marcelo.q" + n)
                .param("email", correoAdmin)
                .param("nombre", "marcelo andrés")
                .param("apellido", "QUINTEROS LÓPEZ"))
            .andExpect(redirectedUrl("/mi-cuenta"));

        Usuario guardado = usuarioRepository.findById(adminId).orElseThrow();
        assertThat(guardado.getUsername()).isEqualTo("marcelo.q" + n);
        assertThat(guardado.getPersona().getNombreCompleto())
            .as("el nombre se guarda con mayúscula inicial, como en el resto del sistema")
            .isEqualTo("Quinteros López, Marcelo Andrés");
        assertThat(yo.getUsername())
            .as("la barra lee el usuario del principal, que vive en la sesión")
            .isEqualTo("marcelo.q" + n);
        verify(notificador, never()).enviarCodigo(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Un usuario que ya usa otra institución no se puede tomar")
    void usuarioDeOtraInstitucion() throws Exception {
        mockMvc.perform(post("/mi-cuenta/editar").with(user(principalDe(adminId))).with(csrf())
                .param("username", "ocupado" + n)
                .param("email", correoAdmin)
                .param("nombre", "Marcelo")
                .param("apellido", "Quinteros"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Ese usuario ya lo usa otra cuenta")));

        assertThat(usuarioRepository.findById(adminId).orElseThrow().getUsername())
            .as("repetirlo dejaría ambiguo el ingreso con usuario, también para la otra persona")
            .isEqualTo("adm.cuenta" + n);
    }

    @Test
    @DisplayName("Un usuario anterior a la regla se puede dejar como está, pero no cambiar por otro que no la cumpla")
    void usuarioAnteriorALaRegla() throws Exception {
        Usuario u = usuarioRepository.findById(adminId).orElseThrow();
        u.setUsername("..." + n);
        usuarioRepository.save(u);
        UsuarioAutenticado yo = principalDe(adminId);

        String form = mockMvc.perform(get("/mi-cuenta/editar").with(user(yo)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(form)
            .as("con el patrón puesto, el campo no dejaría guardar ni siquiera el nombre")
            .doesNotContainPattern("id=\"username\"[^>]*pattern=");

        mockMvc.perform(post("/mi-cuenta/editar").with(user(yo)).with(csrf())
                .param("username", "..." + n)
                .param("email", correoAdmin)
                .param("nombre", "Marcelo")
                .param("apellido", "Quinteros Paz"))
            .andExpect(redirectedUrl("/mi-cuenta"));
        assertThat(usuarioRepository.findById(adminId).orElseThrow().getPersona().getApellido())
            .isEqualTo("Quinteros Paz");

        mockMvc.perform(post("/mi-cuenta/editar").with(user(yo)).with(csrf())
                .param("username", "ab" + n)
                .param("email", correoAdmin)
                .param("nombre", "Marcelo")
                .param("apellido", "Quinteros Paz"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Tiene que tener al menos 3 letras")));
    }

    // ========================================================================
    //  El correo: un codigo al actual y otro al nuevo
    // ========================================================================

    @Test
    @DisplayName("El correo cambia recién con el código del actual y el del nuevo, y queda verificado")
    void correoConDosCodigos() throws Exception {
        UsuarioAutenticado yo = principalDe(adminId);
        MockHttpSession sesion = new MockHttpSession();
        String nuevo = "nuevo" + n + "@x.test";

        mockMvc.perform(post("/mi-cuenta/editar").session(sesion).with(user(yo)).with(csrf())
                .param("username", "adm.cuenta" + n)
                .param("email", nuevo)
                .param("nombre", "Marcelo")
                .param("apellido", "Quinteros"))
            .andExpect(redirectedUrl("/mi-cuenta/correo"));

        ArgumentCaptor<String> primero = ArgumentCaptor.forClass(String.class);
        verify(notificador).enviarCodigo(
            any(), eq(PropositoCodigo.CAMBIO_EMAIL), eq(correoAdmin), primero.capture());
        assertThat(correoDeLaCuenta()).as("todavía no cambió nada").isEqualTo(correoAdmin);

        mockMvc.perform(get("/mi-cuenta/correo").session(sesion).with(user(yo)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("1 de 2")));

        mockMvc.perform(post("/mi-cuenta/correo/actual").session(sesion).with(user(yo)).with(csrf())
                .param("codigo", primero.getValue()))
            .andExpect(redirectedUrl("/mi-cuenta/correo"));

        ArgumentCaptor<String> segundo = ArgumentCaptor.forClass(String.class);
        verify(notificador).enviarCodigo(
            any(), eq(PropositoCodigo.EMAIL_NUEVO), eq(nuevo), segundo.capture());
        assertThat(correoDeLaCuenta())
            .as("con el código del correo actual solamente, tampoco")
            .isEqualTo(correoAdmin);

        mockMvc.perform(post("/mi-cuenta/correo/nuevo").session(sesion).with(user(yo)).with(csrf())
                .param("codigo", segundo.getValue()))
            .andExpect(redirectedUrl("/mi-cuenta"));

        Usuario guardado = usuarioRepository.findById(adminId).orElseThrow();
        assertThat(guardado.getEmail()).isEqualTo(nuevo);
        assertThat(guardado.getEmailVerificadoEn())
            .as("el código que acaba de entrar llegó a esa dirección")
            .isNotNull();
        assertThat(sesion.getAttribute(CambioDeCorreo.SESION)).isNull();
    }

    @Test
    @DisplayName("El código del correo nuevo no sirve sin haber confirmado antes el actual")
    void noSeSalteaElCorreoActual() throws Exception {
        UsuarioAutenticado yo = principalDe(adminId);
        MockHttpSession sesion = new MockHttpSession();

        mockMvc.perform(post("/mi-cuenta/editar").session(sesion).with(user(yo)).with(csrf())
                .param("username", "adm.cuenta" + n)
                .param("email", "atacante" + n + "@x.test")
                .param("nombre", "Marcelo")
                .param("apellido", "Quinteros"))
            .andExpect(redirectedUrl("/mi-cuenta/correo"));

        ArgumentCaptor<String> primero = ArgumentCaptor.forClass(String.class);
        verify(notificador).enviarCodigo(any(), eq(PropositoCodigo.CAMBIO_EMAIL), any(), primero.capture());
        String equivocado = primero.getValue().equals("000000") ? "111111" : "000000";

        mockMvc.perform(post("/mi-cuenta/correo/actual").session(sesion).with(user(yo)).with(csrf())
                .param("codigo", equivocado))
            .andExpect(redirectedUrl("/mi-cuenta/correo"))
            .andExpect(flash().attributeExists("error"));

        mockMvc.perform(post("/mi-cuenta/correo/nuevo").session(sesion).with(user(yo)).with(csrf())
                .param("codigo", "123456"))
            .andExpect(redirectedUrl("/mi-cuenta/correo"))
            .andExpect(flash().attributeExists("flashError"));

        verify(notificador, never()).enviarCodigo(any(), eq(PropositoCodigo.EMAIL_NUEVO), any(), any());
        assertThat(correoDeLaCuenta()).isEqualTo(correoAdmin);
    }

    @Test
    @DisplayName("Un correo que ya usa otra cuenta de la institución se rechaza antes de mandar nada")
    void correoDeOtraCuenta() throws Exception {
        mockMvc.perform(post("/mi-cuenta/editar").with(user(principalDe(adminId))).with(csrf())
                .param("username", "adm.cuenta" + n)
                .param("email", "inst" + n + "@x.test")
                .param("nombre", "Marcelo")
                .param("apellido", "Quinteros"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("ya lo usa otra cuenta de esta institución")));

        verify(notificador, never()).enviarCodigo(any(), any(), any(), any());
        assertThat(correoDeLaCuenta()).isEqualTo(correoAdmin);
    }

    // ========================================================================
    //  El nombre que se comparte con el legajo docente
    // ========================================================================

    @Test
    @DisplayName("Si la persona además da clases, cambiar el nombre pide confirmar; cambiar el usuario no")
    void nombreCompartidoConDocente() throws Exception {
        darleClases(adminId);
        UsuarioAutenticado yo = principalDe(adminId);

        // Solo el usuario: no hay nada que advertir, y preguntar entrena a aceptar sin leer.
        mockMvc.perform(post("/mi-cuenta/editar").with(user(yo)).with(csrf())
                .param("username", "marcelo.docente" + n)
                .param("email", correoAdmin)
                .param("nombre", "Marcelo")
                .param("apellido", "Quinteros"))
            .andExpect(redirectedUrl("/mi-cuenta"));

        // El nombre se ve también en la ficha del docente: avisa antes de escribir.
        mockMvc.perform(post("/mi-cuenta/editar").with(user(yo)).with(csrf())
                .param("username", "marcelo.docente" + n)
                .param("email", correoAdmin)
                .param("nombre", "Marcelo")
                .param("apellido", "Quinteros Díaz"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("El cambio alcanza a más de un rol")));
        assertThat(apellidoDeLaCuenta()).isEqualTo("Quinteros");

        mockMvc.perform(post("/mi-cuenta/editar").with(user(yo)).with(csrf())
                .param("username", "marcelo.docente" + n)
                .param("email", correoAdmin)
                .param("nombre", "Marcelo")
                .param("apellido", "Quinteros Díaz")
                .param("confirmado", "true"))
            .andExpect(redirectedUrl("/mi-cuenta"));
        assertThat(apellidoDeLaCuenta()).isEqualTo("Quinteros Díaz");
    }

    @Test
    @DisplayName("Una cuenta con el correo sin confirmar no edita sus datos")
    void sinVerificarNoEdita() throws Exception {
        Usuario u = usuarioRepository.findById(adminId).orElseThrow();
        u.setEmailVerificadoEn(null);
        usuarioRepository.save(u);
        UsuarioAutenticado yo = principalDe(adminId);

        mockMvc.perform(get("/mi-cuenta").with(user(yo)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("href=\"/mi-cuenta/editar\""))));
        mockMvc.perform(get("/mi-cuenta/editar").with(user(yo)))
            .andExpect(status().is3xxRedirection());
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    private Long guardar(Usuario.UsuarioBuilder builder, Rol rol, Long institucionId) {
        Usuario u = builder.passwordHash("no-se-usa").rol(rol).activo(true)
            .emailVerificadoEn(LocalDateTime.now()).build();
        u.setInstitucionId(institucionId);
        return usuarioRepository.save(u).getId();
    }

    // El principal tal como sale de la base: la pantalla relee la cuenta por su id.
    private UsuarioAutenticado principalDe(Long usuarioId) {
        return new UsuarioAutenticado(usuarioRepository.findById(usuarioId).orElseThrow());
    }

    private String correoDeLaCuenta() {
        return usuarioRepository.findById(adminId).orElseThrow().getEmail();
    }

    private String apellidoDeLaCuenta() {
        return usuarioRepository.findById(adminId).orElseThrow().getPersona().getApellido();
    }

    // Le abre un periodo docente a la persona de esa cuenta, para que su identidad quede
    // compartida entre los dos roles.
    //
    // Va dentro de una transaccion a proposito: Docente.persona cascadea PERSIST, y con la
    // persona detachada --como sale de un findById suelto-- Hibernate rechaza el guardado.
    private void darleClases(Long usuarioId) {
        Long personaId = usuarioRepository.findById(usuarioId).orElseThrow().getPersona().getId();
        new TransactionTemplate(txManager).executeWithoutResult(estado -> {
            Persona persona = personaRepository.findById(personaId).orElseThrow();
            Docente d = Docente.builder()
                .persona(persona).fechaAlta(LocalDate.now()).activo(true).build();
            d.setInstitucionId(tenantId);
            docenteRepository.save(d);
        });
    }
}
