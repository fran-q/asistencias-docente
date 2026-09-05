package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.PropositoCodigo;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.service.CanalDeCodigos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre el alta de una institución, que es la única operación que corre sin sesión y sin
 * institución en contexto. Lo que se prueba no es que el formulario funcione, sino que nada
 * llegue a crearse hasta que el código enviado al correo se valide.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AltaInstitucionIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;

    // Se intercepta el envío para poder leer el código sin levantar un servidor de correo.
    // Se mockea la interfaz, no el canal concreto: el test verifica que el codigo se
    // pida, no por donde sale.
    @MockBean private CanalDeCodigos notificador;

    // El freno de envios es un unico bean que vive todo el contexto de Spring y cuenta por
    // direccion de destino. Si todos los tests usaran el mismo correo, el cuarto quedaria
    // frenado por los anteriores y el resultado dependeria del orden de ejecucion.
    private static final java.util.concurrent.atomic.AtomicInteger SECUENCIA =
        new java.util.concurrent.atomic.AtomicInteger();
    private String correo;

    @BeforeEach
    void preparar() {
        TenantContext.clear();
        limpiar();
        // El alta busca el rol INSTITUCION: sin el, la base no esta inicializada.
        Rol rol = new Rol();
        rol.setCodigo("INSTITUCION");
        rol.setDescripcion("Cuenta institucional");
        rolRepository.save(rol);

        correo = "jefe" + SECUENCIA.incrementAndGet() + "@ejemplo.edu.ar";
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

    // ========================================================================
    //  Paso 1: enviar el codigo
    // ========================================================================

    @Test
    @DisplayName("Enviar el formulario manda el código pero todavía no crea nada")
    void elFormularioNoCreaNada() throws Exception {
        mockMvc.perform(post("/alta-institucion").with(csrf()).params(datos()))
            .andExpect(status().is3xxRedirection());

        verify(notificador).enviarCodigo(any(), eq(PropositoCodigo.VERIFICACION_EMAIL),
                                         eq(correo), anyString());

        assertThat(institucionRepository.count())
            .as("la institucion no puede existir antes de que el correo este comprobado")
            .isZero();
        assertThat(usuarioRepository.count()).isZero();
    }

    @Test
    @DisplayName("Si el correo no se puede enviar, no queda nada a medio crear")
    void siFallaElCorreoNoQuedaNada() throws Exception {
        doThrow(new MailSendException("SMTP caido"))
            .when(notificador).enviarCodigo(any(), any(), anyString(), anyString());

        mockMvc.perform(post("/alta-institucion").with(csrf()).params(datos()))
            .andExpect(status().isOk());          // vuelve al formulario con el error

        assertThat(institucionRepository.count()).isZero();
        assertThat(usuarioRepository.count()).isZero();
    }

    @Test
    @DisplayName("No se puede repetir el nombre de una institución ya registrada")
    void elNombreEsUnicoEnTodoElSistema() throws Exception {
        crearInstitucionCompleta(new MockHttpSession());

        mockMvc.perform(post("/alta-institucion").with(csrf()).params(datos()))
            .andExpect(status().isOk());          // vuelve al formulario con el error

        assertThat(institucionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("El mismo CUIT escrito de otra forma se detecta como repetido")
    void elCuitSeComparaNormalizado() throws Exception {
        MultiValueMap<String, String> conGuiones = datos();
        conGuiones.set("cuit", "30-44445555-8");
        crearConDatos(conGuiones, new MockHttpSession());

        // Mismo numero, escrito de corrido. Sin normalizar entraria como si fuera otro.
        MultiValueMap<String, String> deCorrido = datos();
        deCorrido.set("cuit", "30444455558");
        deCorrido.set("nombreInstitucion", "Otro Instituto");
        deCorrido.set("username", "otro.jefe");

        mockMvc.perform(post("/alta-institucion").with(csrf()).params(deCorrido))
            .andExpect(status().isOk());          // vuelve al formulario con el error

        assertThat(institucionRepository.count())
            .as("el CUIT es unico en todo el sistema: dos formas del mismo numero son uno solo")
            .isEqualTo(1);
    }

    @Test
    @DisplayName("El CUIT se guarda siempre con guiones, se haya escrito como se haya escrito")
    void elCuitSeGuardaCanonico() throws Exception {
        MultiValueMap<String, String> p = datos();
        p.set("cuit", "30777788880");          // de corrido
        crearConDatos(p, new MockHttpSession());

        assertThat(institucionRepository.findAll().get(0).getCuit())
            .isEqualTo("30-77778888-0");
    }

    @Test
    @DisplayName("Un CUIT con el dígito verificador mal no llega a mandar el código")
    void elCuitInvalidoSeRechazaEnElFormulario() throws Exception {
        MultiValueMap<String, String> p = datos();
        p.set("cuit", "30-12345678-9");        // forma correcta, verificador equivocado

        mockMvc.perform(post("/alta-institucion").with(csrf()).params(p))
            .andExpect(status().isOk());        // vuelve al formulario con el error

        // Ni siquiera se gasta un envio de correo en un dato que no puede existir.
        verify(notificador, org.mockito.Mockito.never())
            .enviarCodigo(any(), any(), anyString(), anyString());
        assertThat(institucionRepository.count()).isZero();
    }

    @Test
    @DisplayName("Sin token CSRF el alta se rechaza igual que en el resto del sistema")
    void exigeCsrf() throws Exception {
        mockMvc.perform(post("/alta-institucion").params(datos()))
            .andExpect(status().isForbidden());
        assertThat(institucionRepository.count()).isZero();
    }

    // ========================================================================
    //  Paso 2: validar el codigo
    // ========================================================================

    @Test
    @DisplayName("Con el código correcto se crea la institución y su cuenta, ya verificada")
    void elCodigoCorrectoCreaTodo() throws Exception {
        crearInstitucionCompleta(new MockHttpSession());

        assertThat(institucionRepository.count()).isEqualTo(1);

        Optional<Usuario> creado = usuarioRepository.findByUsername("jefe.nuevo").stream().findFirst();
        assertThat(creado).isPresent();
        assertThat(creado.get().getRol().getCodigo()).isEqualTo("INSTITUCION");
        assertThat(creado.get().getEmailVerificadoEn())
            .as("acaba de demostrar que controla esa casilla, que es lo que la verificacion pide")
            .isNotNull();
    }

    @Test
    @DisplayName("Un código equivocado no crea nada y deja seguir intentando")
    void codigoEquivocadoNoCreaNada() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        mockMvc.perform(post("/alta-institucion").with(csrf()).params(datos()).session(sesion))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/alta-institucion/codigo").with(csrf())
                        .param("codigo", "000000").session(sesion))
            .andExpect(status().isOk());          // vuelve a la pantalla del codigo

        assertThat(institucionRepository.count()).isZero();
        assertThat(usuarioRepository.count()).isZero();
    }

    @Test
    @DisplayName("Al agotar los intentos el alta se descarta entera")
    void alAgotarIntentosSeDescarta() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        mockMvc.perform(post("/alta-institucion").with(csrf()).params(datos()).session(sesion))
            .andExpect(status().is3xxRedirection());
        String correcto = codigoEnviado();

        // El tope configurado son 5 intentos fallidos.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/alta-institucion/codigo").with(csrf())
                            .param("codigo", "000000").session(sesion));
        }

        // Ni siquiera el codigo correcto sirve ya: los datos se descartaron.
        mockMvc.perform(post("/alta-institucion/codigo").with(csrf())
                        .param("codigo", correcto).session(sesion))
            .andExpect(status().is3xxRedirection());

        assertThat(institucionRepository.count()).isZero();
    }

    @Test
    @DisplayName("Sin un alta en curso, la pantalla del código no se puede usar")
    void sinAltaEnCursoNoHayPantalla() throws Exception {
        mockMvc.perform(get("/alta-institucion/codigo"))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/alta-institucion/codigo").with(csrf()).param("codigo", "123456"))
            .andExpect(status().is3xxRedirection());

        assertThat(institucionRepository.count()).isZero();
    }

    /**
     * La pantalla del código manda el valor por un campo oculto.
     *
     * <p>Se tipea en seis casillas y ninguna de las seis tiene {@code name}: son de la
     * interfaz, no del formulario. Lo que viaja es un {@code <input type="hidden"
     * name="codigo">} que un script mantiene sincronizado con lo que se escribe.
     *
     * <p>Es un contrato frágil y silencioso: si al campo oculto le falta el name, o si
     * cambia el nombre del parámetro, el formulario se envía igual y el servidor recibe un
     * código vacío. No hay error en ningún lado, sólo un alta que nunca se confirma.
     */
    @Test
    @DisplayName("La pantalla del código manda el valor por el campo oculto")
    void laPantallaDelCodigoMandaElCampoOculto() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        mockMvc.perform(post("/alta-institucion").with(csrf()).params(datos()).session(sesion))
            .andExpect(status().is3xxRedirection());

        String html = mockMvc.perform(get("/alta-institucion/codigo").session(sesion))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html)
            .as("el campo oculto es el unico que el controlador lee")
            .contains("type=\"hidden\"")
            .contains("name=\"codigo\"")
            .contains("data-codigo-valor");
        assertThat(html)
            .as("y el script que lo sincroniza tiene que estar cargado")
            .contains("codigo-input.js");
    }

    @Test
    @DisplayName("El código de una sesión no sirve en otra")
    void elCodigoNoCruzaDeSesion() throws Exception {
        MockHttpSession deAlguien = new MockHttpSession();
        mockMvc.perform(post("/alta-institucion").with(csrf()).params(datos()).session(deAlguien))
            .andExpect(status().is3xxRedirection());

        // Otro navegador, con el codigo correcto pero sin el alta en su sesion.
        mockMvc.perform(post("/alta-institucion/codigo").with(csrf())
                        .param("codigo", codigoEnviado()).session(new MockHttpSession()))
            .andExpect(status().is3xxRedirection());

        assertThat(institucionRepository.count()).isZero();
    }

    // ========================================================================
    //  Freno de envios
    // ========================================================================

    @Test
    @DisplayName("Una misma dirección no puede recibir códigos sin límite")
    void frenaLosEnviosRepetidos() throws Exception {
        // El tope configurado son 3 por hora y por direccion.
        for (int i = 0; i < 3; i++) {
            MultiValueMap<String, String> p = datos();
            p.set("nombreInstitucion", "Instituto " + i);   // nombre distinto, mismo correo
            mockMvc.perform(post("/alta-institucion").with(csrf()).params(p))
                .andExpect(status().is3xxRedirection());
        }

        MultiValueMap<String, String> unoMas = datos();
        unoMas.set("nombreInstitucion", "Instituto de mas");
        mockMvc.perform(post("/alta-institucion").with(csrf()).params(unoMas))
            .andExpect(status().isOk());          // vuelve al formulario con el aviso

        // Salieron los tres primeros y ninguno mas: el cuarto quedo frenado.
        verify(notificador, times(3))
            .enviarCodigo(any(), any(), anyString(), anyString());
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    // Recorre el alta completa: formulario, lectura del codigo enviado y confirmacion.
    private void crearInstitucionCompleta(MockHttpSession sesion) throws Exception {
        crearConDatos(datos(), sesion);
    }

    // Igual que la anterior pero con datos propios, para los casos del CUIT.
    private void crearConDatos(MultiValueMap<String, String> p, MockHttpSession sesion)
            throws Exception {
        mockMvc.perform(post("/alta-institucion").with(csrf()).params(p).session(sesion))
            .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/alta-institucion/codigo").with(csrf())
                        .param("codigo", codigoEnviado()).session(sesion))
            .andExpect(status().is3xxRedirection());
    }

    // Lee el codigo que la aplicacion le paso al notificador.
    private String codigoEnviado() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(notificador, atLeastOnce())
            .enviarCodigo(any(), any(), anyString(), captor.capture());
        return captor.getValue();
    }

    private MultiValueMap<String, String> datos() {
        MultiValueMap<String, String> p = new LinkedMultiValueMap<>();
        p.add("nombreInstitucion", "Instituto Nuevo");
        p.add("cuit", "");
        p.add("username", "jefe.nuevo");
        p.add("email", correo);
        p.add("password", "Clave12345");
        p.add("confirmacion", "Clave12345");
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
