package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.interceptor.TenantInterceptor;
import edu.cent35.asistencias.config.TenantFilterAspect;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.seguridad.CookiePuesto;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.PuestoCaptura;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.PuestoCapturaRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.service.PuestoCapturaService;
import jakarta.servlet.http.Cookie;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que la captura de datos biométricos solo ocurra desde un equipo autorizado
 * (ADR-0015), y —lo que sostiene todo lo demás— que la cookie de una institución no sirva
 * en otra.
 *
 * <p>Es un test de integración y no unitario a propósito. Lo que se está probando es la
 * cadena completa: el interceptor, el filtro de tenant y la consulta real contra la base.
 * Con Mockito nada de eso se ejerce, que es exactamente cómo la fuga multi-tenant de TD-007
 * pasó desapercibida con toda la suite en verde.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PuestoCapturaIT {

    private static final String PANTALLA_BLOQUEO = "/puesto-requerido";

    @Autowired private MockMvc mockMvc;
    @Autowired private PuestoCapturaService puestoService;
    @Autowired private PuestoCapturaRepository puestoRepository;
    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long institucionA;
    private Long institucionB;
    private Usuario cuentaA;
    private Usuario cuentaB;
    private Rol rolInstitucion;

    @BeforeEach
    void preparar() {
        TenantContext.clear();
        limpiar();

        institucionA = institucionRepository.save(
            Institucion.builder().nombre("Instituto A").activo(true).build()).getId();
        institucionB = institucionRepository.save(
            Institucion.builder().nombre("Instituto B").activo(true).build()).getId();

        Rol r = new Rol();
        r.setCodigo("INSTITUCION");
        r.setDescripcion("Cuenta institucional");
        rolInstitucion = rolRepository.save(r);

        cuentaA = cuenta("admin.a", institucionA);
        cuentaB = cuenta("admin.b", institucionB);
    }

    @AfterEach
    void limpiarDespues() {
        TenantContext.clear();
        limpiar();
    }

    // ========================================================================
    //  El bloqueo
    // ========================================================================

    @ParameterizedTest(name = "sin puesto no entra a {0}")
    @ValueSource(strings = {"/asistencia/pase", "/docentes/1/rostro/registrar"})
    @DisplayName("Sin cookie de puesto, las pantallas de captura mandan a la explicacion")
    void sinPuestoNoEntraALasPantallas(String ruta) throws Exception {
        var respuesta = mockMvc.perform(get(ruta).with(user(new UsuarioAutenticado(cuentaA))))
            .andExpect(status().is3xxRedirection())
            .andReturn().getResponse();

        assertThat(respuesta.getRedirectedUrl())
            .as("tiene que explicar por que, no mandar a cualquier lado")
            .endsWith(PANTALLA_BLOQUEO);
    }

    @ParameterizedTest(name = "sin puesto {0} responde 403 con JSON")
    @ValueSource(strings = {"/reconocimiento/detectar", "/asistencia/pase/marcar"})
    @DisplayName("Los endpoints que llama fetch reciben 403 con JSON, no un redirect")
    void sinPuestoLosEndpointsDevuelvenJson(String ruta) throws Exception {
        // Un redirect acá llegaría al navegador como el HTML de otra página metido en un
        // response.json(), y el script no tendría forma de explicar lo que pasó.
        mockMvc.perform(post(ruta)
                .with(user(new UsuarioAutenticado(cuentaA))).with(csrf())
                .contentType("application/json")
                .content("{\"imagen\":\"x\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("PUESTO_NO_AUTORIZADO"))
            .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("La pantalla de gestion renderiza y no exige estar en un puesto")
    void laPantallaDeGestionRenderiza() throws Exception {
        // Es el destino del menú: tiene que abrirse desde cualquier equipo. Si el guard la
        // alcanzara, revocar el último puesto dejaría la pantalla de puestos inaccesible
        // desde todos lados y no habría forma de volver a autorizar nada.
        mockMvc.perform(get("/puestos").with(user(new UsuarioAutenticado(cuentaA))))
            .andExpect(status().isOk());

        designarEn(institucionA);
        mockMvc.perform(get("/puestos").with(user(new UsuarioAutenticado(cuentaA))))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("El enlace a puestos esta en el menu, y solo para la cuenta institucional")
    void elMenuMuestraPuestosSoloAInstitucion() throws Exception {
        // Se mira el HTML de una pantalla cualquiera: el menú viene del layout, así que si el
        // enlace está, está en todas. Sin este caso, el enlace podría desaparecer del layout
        // y la única forma de notarlo sería que alguien lo buscara a mano.
        String comoInstitucion = mockMvc.perform(get("/docentes")
                .with(user(new UsuarioAutenticado(cuentaA))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(comoInstitucion)
            .as("la cuenta institucional tiene que poder llegar por el menu")
            .contains("/puestos");

        String comoAdmin = mockMvc.perform(get("/docentes")
                .with(user(new UsuarioAutenticado(cuentaConRol("admin.menu", institucionA, "ADMIN")))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(comoAdmin)
            .as("un ADMIN no puede designar puestos: ofrecerle el enlace lo llevaria a un 403")
            .doesNotContain("/puestos");
    }

    @Test
    @DisplayName("Un ADMIN no entra a la gestion de puestos, pero si ve por que fue bloqueado")
    void adminNoGestionaPeroSiEntiende() throws Exception {
        Usuario admin = cuentaConRol("admin.simple", institucionA, "ADMIN");

        mockMvc.perform(get("/puestos").with(user(new UsuarioAutenticado(admin))))
            .andExpect(status().isForbidden());

        // La explicación no lleva rol: quien choca con el bloqueo tiene derecho a saber por
        // qué, aunque no pueda autorizar el equipo.
        mockMvc.perform(get(PANTALLA_BLOQUEO).with(user(new UsuarioAutenticado(admin))))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("La pantalla de bloqueo renderiza, con puestos cargados y sin ninguno")
    void laPantallaDeBloqueoRenderiza() throws Exception {
        // El guard manda acá, pero MockMvc no sigue los redirects: sin este caso, un error
        // de Thymeleaf en la plantilla dejaría a todo el mundo contra una pantalla rota
        // justo cuando ya no puede entrar a la que quería, y la suite seguiría en verde.
        mockMvc.perform(get(PANTALLA_BLOQUEO).with(user(new UsuarioAutenticado(cuentaA))))
            .andExpect(status().isOk());

        // Con un puesto cargado se dibuja además la tabla, que es otra rama de la plantilla.
        designarEn(institucionA);
        mockMvc.perform(get(PANTALLA_BLOQUEO).with(user(new UsuarioAutenticado(cuentaA))))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Una cookie con un token inventado no abre nada")
    void tokenInventadoNoAbre() throws Exception {
        mockMvc.perform(get("/asistencia/pase")
                .with(user(new UsuarioAutenticado(cuentaA)))
                .cookie(new Cookie(CookiePuesto.NOMBRE, "esto-no-es-un-token-valido")))
            .andExpect(status().is3xxRedirection());
    }

    // ========================================================================
    //  El paso
    // ========================================================================

    @Test
    @DisplayName("Con el puesto designado, la pantalla del pase abre")
    void conPuestoAbre() throws Exception {
        String token = designarEn(institucionA);

        mockMvc.perform(get("/asistencia/pase")
                .with(user(new UsuarioAutenticado(cuentaA)))
                .cookie(new Cookie(CookiePuesto.NOMBRE, token)))
            .andExpect(status().isOk());
    }

    // ========================================================================
    //  Aislamiento entre instituciones: lo que sostiene todo el control
    // ========================================================================

    @Test
    @DisplayName("La cookie de una institucion NO sirve en otra")
    void cookieDeUnaInstitucionNoSirveEnOtra() throws Exception {
        String tokenDeA = designarEn(institucionA);

        // Misma cookie, pero la sesión es de la institución B. Si esto pasara, cualquier
        // equipo autorizado en un instituto podría capturar rostros en otro.
        var respuesta = mockMvc.perform(get("/asistencia/pase")
                .with(user(new UsuarioAutenticado(cuentaB)))
                .cookie(new Cookie(CookiePuesto.NOMBRE, tokenDeA)))
            .andExpect(status().is3xxRedirection())
            .andReturn().getResponse();

        assertThat(respuesta.getRedirectedUrl()).endsWith(PANTALLA_BLOQUEO);
    }

    /**
     * El mismo aislamiento, pero un escalón más abajo, y no es una repetición del test de
     * arriba: los dos comprueban cosas distintas y hace falta tener los dos.
     *
     * <p>El aislamiento está sostenido por DOS mecanismos independientes. Durante una
     * petición HTTP el filtro de Hibernate está activo —TenantInterceptor publicó la
     * institución y TenantFilterAspect lo encendió— y ya recorta la fila ajena. Encima de
     * eso, la consulta nombra {@code institucionId} de forma explícita.
     *
     * <p>Se comprobó quitando el filtro explícito de la consulta y volviendo a correr la
     * clase: <b>el test por HTTP siguió pasando</b>, porque el filtro de Hibernate tapaba la
     * fuga; el único que la detectó fue este. Acá no hay tenant en contexto, así que el
     * aspecto no se activa y lo único que queda protegiendo es el parámetro explícito.
     *
     * <p>Sin este test, alguien podría sacar el {@code AND p.institucionId} creyendo que
     * sobra —la suite quedaría en verde— y el sistema pasaría a depender de que el filtro
     * esté activo en todos los caminos. Que es exactamente la forma que tuvo TD-007.
     */
    @Test
    @DisplayName("Sin el filtro de Hibernate activo, la consulta sigue sin cruzar instituciones")
    void elServicioNoResuelveCrossTenant() {
        String tokenDeA = designarEn(institucionA);

        assertThat(puestoService.verificar(tokenDeA, institucionA))
            .as("en su propia institucion tiene que resolver")
            .isPresent();
        assertThat(puestoService.verificar(tokenDeA, institucionB))
            .as("el token es unico en toda la tabla: sin el filtro por institucion "
                + "explicito en la consulta, esta busqueda lo encontraria igual")
            .isEmpty();
    }

    // ========================================================================
    //  Revocacion
    // ========================================================================

    @Test
    @DisplayName("Revocar corta el acceso en la peticion siguiente, sin tocar el equipo")
    void revocarCortaElAcceso() throws Exception {
        String token = designarEn(institucionA);
        Cookie cookie = new Cookie(CookiePuesto.NOMBRE, token);

        mockMvc.perform(get("/asistencia/pase")
                .with(user(new UsuarioAutenticado(cuentaA))).cookie(cookie))
            .andExpect(status().isOk());

        Long puestoId = puestoRepository.deInstitucion(institucionA).get(0).getId();
        puestoService.revocar(puestoId, institucionA);

        mockMvc.perform(get("/asistencia/pase")
                .with(user(new UsuarioAutenticado(cuentaA))).cookie(cookie))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Una institucion no puede revocar el puesto de otra")
    void noSePuedeRevocarElPuestoDeOtraInstitucion() {
        designarEn(institucionA);
        Long puestoDeA = puestoRepository.deInstitucion(institucionA).get(0).getId();

        assertThat(puestoRepository.porIdEnInstitucion(puestoDeA, institucionB)).isEmpty();
        assertThat(puestoRepository.deInstitucion(institucionB)).isEmpty();
    }

    // ========================================================================
    //  Lo que el control NO alcanza
    // ========================================================================

    @ParameterizedTest(name = "{0} sigue funcionando sin puesto")
    @ValueSource(strings = {"/", "/asistencias", "/reportes", "/docentes"})
    @DisplayName("La gestion no necesita puesto: es justamente lo que se habilita en movil")
    void laGestionNoNecesitaPuesto(String ruta) throws Exception {
        mockMvc.perform(get(ruta).with(user(new UsuarioAutenticado(cuentaA))))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("La supresion ARCO NO se bloquea aunque viva bajo el mismo prefijo")
    void laSupresionArcoNoSeBloquea() throws Exception {
        // Suprimir el dato biométrico es un derecho, y condicionar su ejercicio a estar
        // frente a una máquina determinada sería ponerle una traba. Comparte prefijo con
        // /rostro/registrar, así que un patrón /rostro/** lo habría alcanzado sin querer.
        //
        // No se afirma nada sobre QUE responde --eso es del controlador y de si el docente
        // existe--, solo que no lo desvió el guard.
        var respuesta = mockMvc.perform(post("/docentes/1/rostro/suprimir")
                .with(user(new UsuarioAutenticado(cuentaA))).with(csrf()))
            .andReturn().getResponse();

        assertThat(respuesta.getRedirectedUrl())
            .as("el guard no tiene que meterse con el ejercicio de un derecho ARCO")
            .doesNotEndWith(PANTALLA_BLOQUEO);
    }

    // ========================================================================
    //  El token en reposo
    // ========================================================================

    @Test
    @DisplayName("El token no queda guardado en claro en ningun lado")
    void elTokenNoSeGuardaEnClaro() {
        String token = designarEn(institucionA);
        PuestoCaptura guardado = puestoRepository.deInstitucion(institucionA).get(0);

        assertThat(guardado.getTokenHash())
            .as("una copia de la base no puede alcanzar para fabricar un puesto valido")
            .isNotEqualTo(token)
            .doesNotContain(token)
            .hasSize(64);                     // SHA-256 en hexadecimal
    }

    @Test
    @DisplayName("Dos puestos nunca comparten token")
    void dosPuestosNuncaCompartenToken() {
        String primero = designarEn(institucionA);
        String segundo = puestoService.designar(institucionA, "Secretaria PC-2", cuentaA, true)
            .getTokenEnClaro();

        assertThat(primero).isNotEqualTo(segundo);
        assertThat(puestoRepository.deInstitucion(institucionA))
            .extracting(PuestoCaptura::getTokenHash)
            .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("No se admiten dos puestos con el mismo nombre en la misma institucion")
    void nombreRepetidoEnLaMismaInstitucion() {
        designarEn(institucionA);

        assertThatNombreRepetidoFalla();
        // Pero en otra institucion el mismo nombre si va: son espacios separados.
        assertThat(puestoService.designar(institucionB, "Secretaria PC-1", cuentaB, false).getPuesto())
            .isNotNull();
    }

    // ========================================================================
    //  Quien puede autorizar un equipo nuevo
    // ========================================================================

    @Test
    @DisplayName("El primer puesto se autoriza desde cualquier equipo: es el arranque")
    void elPrimeroSeAutorizaDesdeDondeSea() {
        // Sin ningun puesto nadie puede tomar asistencia, y hay que poder salir de esa
        // situacion. Para eso alcanza con la cuenta institucional.
        assertThat(puestoService.designar(institucionA, "Secretaria PC-1", cuentaA, false))
            .isNotNull();
    }

    @Test
    @DisplayName("Con un puesto ya habilitado, otro equipo NO se puede autorizar a si mismo")
    void elSegundoNoSeAutorizaDesdeAfuera() {
        // Es lo que sostiene todo el control. Sin esta regla, cualquiera con la cuenta
        // institucional convierte su propia maquina en puesto desde donde este, que es
        // justo lo que ADR-0015 quiere impedir.
        designarEn(institucionA);

        try {
            puestoService.designar(institucionA, "Mi notebook", cuentaA, false);
            throw new AssertionError("tendria que haber rechazado la designacion");
        } catch (IllegalArgumentException esperado) {
            assertThat(esperado.getMessage()).contains("ya tiene un equipo autorizado");
        }
    }

    @Test
    @DisplayName("Desde un puesto ya autorizado si se puede sumar otro")
    void desdeUnPuestoSiSeSumaOtro() {
        designarEn(institucionA);

        assertThat(puestoService.designar(institucionA, "Secretaria PC-2", cuentaA, true)
            .getPuesto()).isNotNull();
    }

    @Test
    @DisplayName("El POST directo tampoco saltea la regla: no alcanza con esconder el form")
    void elPostDirectoNoSalteaLaRegla() throws Exception {
        // Una vista que no muestra el boton no frena a quien arma la peticion a mano. Por eso
        // la condicion vive en el service y este caso la ejerce por HTTP, sin cookie.
        designarEn(institucionA);

        mockMvc.perform(post("/puestos/designar")
                .with(user(new UsuarioAutenticado(cuentaA))).with(csrf())
                .param("nombre", "Notebook de alguien"))
            .andExpect(status().is3xxRedirection());

        assertThat(puestoRepository.deInstitucion(institucionA))
            .as("no tendria que haberse creado el segundo puesto")
            .hasSize(1);
    }

    @Test
    @DisplayName("Con un puesto ya habilitado, la pantalla de bloqueo no ofrece autorizar")
    void laPantallaDeBloqueoNoOfreceAutorizar() throws Exception {
        designarEn(institucionA);

        String html = mockMvc.perform(get(PANTALLA_BLOQUEO)
                .with(user(new UsuarioAutenticado(cuentaA))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html)
            .as("ofrecer un boton que el service va a rechazar es peor que no ofrecerlo")
            .doesNotContain("Autorizar este equipo");
        assertThat(html).contains("ya tiene un equipo autorizado");
    }

    @Test
    @DisplayName("Sin ningun puesto, la pantalla de bloqueo si ofrece autorizar")
    void sinPuestosLaPantallaOfreceAutorizar() throws Exception {
        String html = mockMvc.perform(get(PANTALLA_BLOQUEO)
                .with(user(new UsuarioAutenticado(cuentaA))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Autorizar este equipo");
        // Y ya no lleva el cuadro de aclaracion que estaba arriba.
        assertThat(html).doesNotContain("Ley 25.326");
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    private void assertThatNombreRepetidoFalla() {
        try {
            puestoService.designar(institucionA, "Secretaria PC-1", cuentaA, true);
            throw new AssertionError("tendria que haber rechazado el nombre repetido");
        } catch (IllegalArgumentException esperado) {
            assertThat(esperado.getMessage()).contains("nombre");
        }
    }

    private String designarEn(Long institucionId) {
        Usuario designante = institucionId.equals(institucionA) ? cuentaA : cuentaB;
        return puestoService.designar(institucionId, "Secretaria PC-1", designante, false).getTokenEnClaro();
    }

    private Usuario cuenta(String username, Long institucionId) {
        return cuentaConRol(username, institucionId, "INSTITUCION");
    }

    private Usuario cuentaConRol(String username, Long institucionId, String codigoRol) {
        Rol rol = rolInstitucion;
        if (!"INSTITUCION".equals(codigoRol)) {
            Rol otro = new Rol();
            otro.setCodigo(codigoRol);
            otro.setDescripcion(codigoRol);
            rol = rolRepository.save(otro);
        }
        return guardar(username, institucionId, rol);
    }

    private Usuario guardar(String username, Long institucionId, Rol rol) {
        Usuario u = Usuario.builder()
            .username(username)
            .email(username + "@ejemplo.edu.ar")
            .passwordHash(passwordEncoder.encode("Clave12345"))
            .persona(DatosDePrueba.persona("Cuenta", "Prueba"))
            .rol(rol)
            .activo(true)
            // Verificado: si no, el interceptor de verificacion bloquea antes y este test
            // estaria midiendo el control equivocado.
            .emailVerificadoEn(LocalDateTime.now())
            .build();
        u.setInstitucionId(institucionId);
        return usuarioRepository.save(u);
    }

    private void limpiar() {
        puestoRepository.deleteAll();
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        institucionRepository.deleteAll();
    }
}
