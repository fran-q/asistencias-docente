package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.PuestoCaptura;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.PuestoCapturaRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.seguridad.CookiePuesto;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.service.PuestoCapturaService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica quién puede encender y apagar el funcionamiento sin sesión, y que encenderlo y
 * apagarlo tengan efecto real sobre la pantalla desatendida (RF-85, ADR-0019).
 *
 * <p>Lo que se está probando es una <b>asimetría deliberada</b>: habilitar el kiosco exige
 * estar sentado en esa misma máquina, apagarlo funciona desde cualquiera. Aflojar un control
 * pide estar ahí; volver a apretarlo, no — el momento en que hace falta apagarlo es
 * justamente aquel en el que no se puede llegar hasta el equipo.
 *
 * <p>Es de integración y no unitario porque la mitad de lo que hay que probar vive fuera del
 * servicio: la cookie, el interceptor que resuelve el tenant sin sesión, y que
 * {@code /kiosco} efectivamente abra o deje de abrir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KioscoHabilitacionIT {

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
    //  Habilitar: solo desde esa misma maquina
    // ========================================================================

    @Test
    @DisplayName("Desde el equipo autorizado, la cuenta institucional habilita el kiosco")
    void habilitaDesdeEseEquipo() throws Exception {
        String token = designarEn(institucionA, cuentaA);
        Long puestoId = puestoDe(institucionA).getId();

        mockMvc.perform(post("/puestos/{id}/kiosco/habilitar", puestoId)
                .with(user(new UsuarioAutenticado(cuentaA))).with(csrf())
                .cookie(new Cookie(CookiePuesto.NOMBRE, token)))
            .andExpect(status().is3xxRedirection());

        PuestoCaptura despues = puestoRepository.findById(puestoId).orElseThrow();
        assertThat(despues.operaDesatendido()).isTrue();
        // RF-85 pide registrar quien tomo la decision, no solo que este tomada: es una
        // atenuacion deliberada de un control y tiene que tener a quien atribuirsele.
        assertThat(despues.getKioscoHabilitadoEn()).isNotNull();
        assertThat(despues.getKioscoHabilitadoPor()).isNotNull();
        assertThat(despues.getKioscoHabilitadoPor().getId()).isEqualTo(cuentaA.getId());
    }

    @Test
    @DisplayName("Sin la cookie del equipo, la contrasena institucional sola no habilita el kiosco")
    void noHabilitaADistancia() throws Exception {
        // Es lo que sostiene toda la funcion. El kiosco convierte la cookie del puesto en la
        // credencial completa; si esto se pudiera a distancia, una contrasena institucional
        // filtrada alcanzaria para convertir esa cookie en una credencial valida.
        designarEn(institucionA, cuentaA);
        Long puestoId = puestoDe(institucionA).getId();

        mockMvc.perform(post("/puestos/{id}/kiosco/habilitar", puestoId)
                .with(user(new UsuarioAutenticado(cuentaA))).with(csrf()))
            .andExpect(status().is3xxRedirection());

        assertThat(puestoRepository.findById(puestoId).orElseThrow().operaDesatendido())
            .as("sin estar en esa maquina no se habilita")
            .isFalse();
    }

    @Test
    @DisplayName("La cookie de otra institucion no habilita el kiosco ajeno")
    void noHabilitaConCookieAjena() throws Exception {
        // La cookie sola tampoco alcanza: tiene que ser la del puesto de ESA institucion.
        designarEn(institucionA, cuentaA);
        String tokenDeB = designarEn(institucionB, cuentaB);
        Long puestoDeA = puestoDe(institucionA).getId();

        mockMvc.perform(post("/puestos/{id}/kiosco/habilitar", puestoDeA)
                .with(user(new UsuarioAutenticado(cuentaA))).with(csrf())
                .cookie(new Cookie(CookiePuesto.NOMBRE, tokenDeB)))
            .andExpect(status().is3xxRedirection());

        assertThat(puestoRepository.findById(puestoDeA).orElseThrow().operaDesatendido()).isFalse();
    }

    @Test
    @DisplayName("Una cuenta de otra institucion no toca el puesto ajeno")
    void otraInstitucionNoToca() throws Exception {
        String tokenDeA = designarEn(institucionA, cuentaA);
        Long puestoDeA = puestoDe(institucionA).getId();
        habilitar(puestoDeA, tokenDeA, cuentaA);

        // Con el id y hasta con la cookie de A, pero logueada en B: el tenant lo pone la
        // sesion, asi que el puesto de A no existe para esta peticion.
        mockMvc.perform(post("/puestos/{id}/kiosco/deshabilitar", puestoDeA)
                .with(user(new UsuarioAutenticado(cuentaB))).with(csrf())
                .cookie(new Cookie(CookiePuesto.NOMBRE, tokenDeA)))
            .andExpect(status().is3xxRedirection());

        assertThat(puestoRepository.findById(puestoDeA).orElseThrow().operaDesatendido())
            .as("una institucion no apaga ni enciende el kiosco de otra")
            .isTrue();
    }

    @Test
    @DisplayName("Un ADMIN no habilita ni deshabilita el kiosco")
    void adminNoDecide() throws Exception {
        String token = designarEn(institucionA, cuentaA);
        Long puestoId = puestoDe(institucionA).getId();
        Usuario admin = cuentaConRol("admin.simple", institucionA, "ADMIN");

        // Habilitar el kiosco es autorizar el tratamiento biometrico sin nadie mirando: es la
        // misma decision que designar el equipo, y la toma la cuenta institucional.
        mockMvc.perform(post("/puestos/{id}/kiosco/habilitar", puestoId)
                .with(user(new UsuarioAutenticado(admin))).with(csrf())
                .cookie(new Cookie(CookiePuesto.NOMBRE, token)))
            .andExpect(status().isForbidden());

        assertThat(puestoRepository.findById(puestoId).orElseThrow().operaDesatendido()).isFalse();

        habilitar(puestoId, token, cuentaA);
        mockMvc.perform(post("/puestos/{id}/kiosco/deshabilitar", puestoId)
                .with(user(new UsuarioAutenticado(admin))).with(csrf()))
            .andExpect(status().isForbidden());

        assertThat(puestoRepository.findById(puestoId).orElseThrow().operaDesatendido()).isTrue();
    }

    // ========================================================================
    //  Apagar: desde cualquier maquina, y sin revocar el equipo
    // ========================================================================

    @Test
    @DisplayName("El kiosco se apaga sin la cookie, desde cualquier maquina")
    void apagaADistancia() throws Exception {
        // La asimetria con habilitar. Cuando hace falta apagarlo --la maquina se la llevaron,
        // aparecio una marca que nadie explica-- lo mas probable es no poder llegar hasta ella.
        String token = designarEn(institucionA, cuentaA);
        Long puestoId = puestoDe(institucionA).getId();
        habilitar(puestoId, token, cuentaA);

        mockMvc.perform(post("/puestos/{id}/kiosco/deshabilitar", puestoId)
                .with(user(new UsuarioAutenticado(cuentaA))).with(csrf()))
            .andExpect(status().is3xxRedirection());

        assertThat(puestoRepository.findById(puestoId).orElseThrow().operaDesatendido()).isFalse();
    }

    @Test
    @DisplayName("Apagar el kiosco no revoca el equipo ni borra quien lo habia habilitado")
    void apagarNoRevocaNiBorraElRastro() throws Exception {
        String token = designarEn(institucionA, cuentaA);
        Long puestoId = puestoDe(institucionA).getId();
        habilitar(puestoId, token, cuentaA);
        LocalDateTime cuando = puestoRepository.findById(puestoId).orElseThrow()
            .getKioscoHabilitadoEn();

        mockMvc.perform(post("/puestos/{id}/kiosco/deshabilitar", puestoId)
                .with(user(new UsuarioAutenticado(cuentaA))).with(csrf()));

        PuestoCaptura despues = puestoRepository.findById(puestoId).orElseThrow();
        // RF-85: apagarlo tiene que poder hacerse SIN revocar el equipo. Si revocara, la
        // institucion se quedaria sin poder tomar asistencia ni siquiera con sesion abierta.
        assertThat(despues.habilitado())
            .as("el equipo sigue autorizado: solo dejo de funcionar solo")
            .isTrue();
        // El rastro sobrevive: la pregunta "desde cuando estuvo operando desatendido" se hace
        // despues de apagarlo, no antes.
        assertThat(despues.getKioscoHabilitadoEn()).isEqualTo(cuando);
        assertThat(despues.getKioscoHabilitadoPor()).isNotNull();
    }

    // ========================================================================
    //  Que el interruptor mueva algo de verdad
    // ========================================================================

    @Test
    @DisplayName("Encender y apagar abren y cierran la pantalla desatendida de verdad")
    void elInterruptorMueveLaPantalla() throws Exception {
        String token = designarEn(institucionA, cuentaA);
        Long puestoId = puestoDe(institucionA).getId();

        // Designado pero sin kiosco: la pantalla sin sesion no abre.
        assertThat(estadoDelKiosco(token))
            .as("designar no alcanza para operar desatendido")
            .isNotEqualTo(200);

        habilitar(puestoId, token, cuentaA);
        assertThat(estadoDelKiosco(token))
            .as("habilitado, la misma cookie abre la pantalla sin ninguna sesion")
            .isEqualTo(200);

        mockMvc.perform(post("/puestos/{id}/kiosco/deshabilitar", puestoId)
                .with(user(new UsuarioAutenticado(cuentaA))).with(csrf()));

        assertThat(estadoDelKiosco(token))
            .as("apagado, la cookie deja de servir sin sesion")
            .isNotEqualTo(200);
    }

    @Test
    @DisplayName("Revocar el equipo tambien apaga el kiosco")
    void revocarApagaElKiosco() throws Exception {
        // Revocar no limpia la bandera del kiosco --es historia: dice que ese equipo estuvo
        // operando desatendido--, asi que la fila queda con kiosco_habilitado = 1 y activo = 0.
        // Lo que cierra la puerta es que se miren las DOS, y eso pasa en dos lugares
        // independientes: la consulta que resuelve el tenant y el interceptor del puesto.
        String token = designarEn(institucionA, cuentaA);
        Long puestoId = puestoDe(institucionA).getId();
        habilitar(puestoId, token, cuentaA);

        mockMvc.perform(post("/puestos/{id}/revocar", puestoId)
                .with(user(new UsuarioAutenticado(cuentaA))).with(csrf())
                .cookie(new Cookie(CookiePuesto.NOMBRE, token)))
            .andExpect(status().is3xxRedirection());

        PuestoCaptura revocado = puestoRepository.findById(puestoId).orElseThrow();
        assertThat(revocado.habilitado()).isFalse();
        assertThat(revocado.getKioscoHabilitado())
            .as("la bandera queda: es el rastro de que ese equipo opero solo")
            .isTrue();

        assertThat(estadoDelKiosco(token))
            .as("un equipo revocado no opera de ninguna forma")
            .isNotEqualTo(200);
    }

    @Test
    @DisplayName("La resolucion del tenant sola ya rechaza al puesto revocado")
    void laResolucionDelTenantExigeElPuestoActivo() {
        // Va contra el servicio y no contra la pantalla a proposito. Por HTTP el pedido muere
        // igual gracias al interceptor del puesto, asi que sacarle el `activo` a la consulta
        // que resuelve el tenant no se notaria: el test end-to-end seguiria verde y quedaria
        // una sola capa sosteniendo la puerta, sin que nadie se entere.
        //
        // Y esta capa es la que importa primero: es la que publica la institucion en contexto.
        // Resolver el tenant desde un equipo revocado seria dejar entrar la peticion al
        // sistema para frenarla despues.
        String token = designarEn(institucionA, cuentaA);
        Long puestoId = puestoDe(institucionA).getId();

        try {
            habilitar(puestoId, token, cuentaA);
        } catch (Exception e) {
            throw new AssertionError("no se pudo habilitar el kiosco: " + e.getMessage(), e);
        }
        assertThat(puestoService.resolverKiosco(token))
            .as("habilitado, el token resuelve la institucion")
            .isPresent();

        puestoService.revocar(puestoId, institucionA, true);

        assertThat(puestoService.resolverKiosco(token))
            .as("revocado, el token no resuelve ninguna institucion")
            .isEmpty();
    }

    // ========================================================================
    //  La pantalla de puestos
    // ========================================================================

    @Test
    @DisplayName("El boton de habilitar solo aparece en el equipo autorizado")
    void elBotonSoloApareceEnEseEquipo() throws Exception {
        String token = designarEn(institucionA, cuentaA);
        Long puestoId = puestoDe(institucionA).getId();
        String accion = "/puestos/" + puestoId + "/kiosco/habilitar";

        String desdeOtraMaquina = mockMvc.perform(get("/puestos")
                .with(user(new UsuarioAutenticado(cuentaA))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(desdeOtraMaquina)
            .as("ofrecer el boton desde otra maquina manda a la persona a un rechazo previsible")
            .doesNotContain(accion);

        String desdeEseEquipo = mockMvc.perform(get("/puestos")
                .with(user(new UsuarioAutenticado(cuentaA)))
                .cookie(new Cookie(CookiePuesto.NOMBRE, token)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(desdeEseEquipo).contains(accion);
    }

    @Test
    @DisplayName("La pantalla dice si el equipo esta operando sin sesion")
    void laPantallaMuestraElEstado() throws Exception {
        String token = designarEn(institucionA, cuentaA);
        Long puestoId = puestoDe(institucionA).getId();

        assertThat(pantallaDePuestos()).contains("Requiere sesión");

        habilitar(puestoId, token, cuentaA);

        // Sin esto la pantalla no distingue un equipo que opera solo de uno que no, que es el
        // unico dato por el que alguien entraria a mirarla despues de habilitarlo.
        String encendido = pantallaDePuestos();
        assertThat(encendido).contains("Sin sesión");
        assertThat(encendido).contains("/kiosco/deshabilitar");
    }

    // ========================================================================
    //  El paso del pase al kiosco (cerrando la sesion)
    // ========================================================================

    @Test
    @DisplayName("El pase ofrece pasar al kiosco solo si este equipo lo tiene habilitado")
    void elPaseOfreceElKioscoSoloSiEstaHabilitado() throws Exception {
        String token = designarEn(institucionA, cuentaA);
        Long puestoId = puestoDe(institucionA).getId();

        // El boton cierra la sesion. Ofrecerlo con el kiosco apagado dejaria a la persona
        // afuera del sistema y contra una pantalla que no abre.
        //
        // Se busca el campo oculto y no el texto del boton. La etiqueta ya cambio una vez
        // --el acceso paso del encabezado al pie y se renombro-- y el test fallo por eso, sin
        // que nada del comportamiento se hubiera roto. Lo que hace al paso es ese value: es
        // lo que el logout lee para mandar al kiosco en vez de al login.
        assertThat(pantallaDelPase(token))
            .as("con el kiosco apagado no se ofrece")
            .doesNotContain("value=\"kiosco\"");

        habilitar(puestoId, token, cuentaA);

        assertThat(pantallaDelPase(token)).contains("value=\"kiosco\"");
    }

    @Test
    @DisplayName("Salir al kiosco cierra la sesion pero no borra la credencial del equipo")
    void salirAlKioscoCierraLaSesionYConservaElEquipo() throws Exception {
        // Es el punto de todo el paso. Si la sesion siguiera viva detras de la pantalla
        // desatendida, alcanzaria con apretar Atras para entrar al sistema entero con la
        // cuenta de quien lo dejo andando: exactamente la practica que el kiosco reemplaza.
        String token = designarEn(institucionA, cuentaA);
        Long puestoId = puestoDe(institucionA).getId();
        habilitar(puestoId, token, cuentaA);

        var respuesta = mockMvc.perform(post("/logout")
                .param("destino", "kiosco")
                .with(user(new UsuarioAutenticado(cuentaA))).with(csrf())
                .cookie(new Cookie(CookiePuesto.NOMBRE, token)))
            .andExpect(status().is3xxRedirection())
            .andReturn().getResponse();

        assertThat(respuesta.getRedirectedUrl()).endsWith("/kiosco");

        // La cookie del puesto identifica la MAQUINA, no la sesion: si el logout se la
        // llevara, la pantalla desatendida no abriria y el paso no serviria de nada.
        assertThat(respuesta.getHeaders("Set-Cookie"))
            .as("el logout no puede tocar la credencial del equipo")
            .noneMatch(h -> h.contains(CookiePuesto.NOMBRE));

        // Y con esa misma cookie, ya sin sesion, la pantalla abre.
        assertThat(estadoDelKiosco(token)).isEqualTo(200);
    }

    @Test
    @DisplayName("El logout comun sigue yendo al login")
    void elLogoutComunNoCambio() throws Exception {
        var respuesta = mockMvc.perform(post("/logout")
                .with(user(new UsuarioAutenticado(cuentaA))).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andReturn().getResponse();

        assertThat(respuesta.getRedirectedUrl()).endsWith("/login?logout");
    }

    @Test
    @DisplayName("El destino del logout no lo elige la peticion")
    void elDestinoNoSaleDeLaPeticion() throws Exception {
        // El logout es de las paginas mas faciles de hacerle abrir a alguien desde afuera. Si
        // el destino saliera del formulario seria un redirect abierto: "cerra sesion y anda a
        // este sitio que se parece al login".
        var respuesta = mockMvc.perform(post("/logout")
                .param("destino", "https://sitio-ajeno.example/login")
                .with(user(new UsuarioAutenticado(cuentaA))).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andReturn().getResponse();

        assertThat(respuesta.getRedirectedUrl())
            .as("cualquier destino que no sea el valor fijo cae en el login")
            .endsWith("/login?logout");
    }

    // ------------------------------------------------------------------------

    // La pantalla del pase, con sesion y con la cookie del equipo.
    private String pantallaDelPase(String token) throws Exception {
        return mockMvc.perform(get("/asistencia/pase")
                .with(user(new UsuarioAutenticado(cuentaA)))
                .cookie(new Cookie(CookiePuesto.NOMBRE, token)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    // El estado de /kiosco con esa cookie y SIN ninguna sesion abierta.
    private int estadoDelKiosco(String token) throws Exception {
        return mockMvc.perform(get("/kiosco").cookie(new Cookie(CookiePuesto.NOMBRE, token)))
            .andReturn().getResponse().getStatus();
    }

    private String pantallaDePuestos() throws Exception {
        return mockMvc.perform(get("/puestos").with(user(new UsuarioAutenticado(cuentaA))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    // Habilita el kiosco por la via real: el POST, desde ese mismo equipo.
    private void habilitar(Long puestoId, String token, Usuario quien) throws Exception {
        mockMvc.perform(post("/puestos/{id}/kiosco/habilitar", puestoId)
                .with(user(new UsuarioAutenticado(quien))).with(csrf())
                .cookie(new Cookie(CookiePuesto.NOMBRE, token)))
            .andExpect(status().is3xxRedirection());
    }

    private PuestoCaptura puestoDe(Long institucionId) {
        return puestoRepository.deInstitucion(institucionId).get(0);
    }

    private String designarEn(Long institucionId, Usuario quien) {
        return puestoService.designar(institucionId, "Secretaria PC-1", quien).getTokenEnClaro();
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
        Usuario u = Usuario.builder()
            .username(username)
            .email(username + "@ejemplo.edu.ar")
            .passwordHash(passwordEncoder.encode("Clave12345"))
            .persona(DatosDePrueba.persona("Cuenta", "Prueba"))
            .rol(rol)
            .activo(true)
            // Sin esto el interceptor de verificacion bloquea antes y el test estaria
            // midiendo el control equivocado.
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
