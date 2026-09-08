package edu.cent35.asistencias.interceptor;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.PuestoCaptura;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.seguridad.CookiePuesto;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.service.PuestoCapturaService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre de dónde sale la institución cuando no hay sesión (RF-84, ADR-0019).
 * <p>
 * El caso que justifica esta clase es el de la sesión que gana: un administrador de una
 * institución trabajando en la máquina de otra tiene las dos credenciales encima, y si el
 * equipo pisara a la sesión terminaría operando sobre los datos de la institución
 * equivocada.
 */
@ExtendWith(MockitoExtension.class)
class KioscoTenantInterceptorTest {

    private static final Long INSTITUCION_DEL_EQUIPO = 7L;
    private static final Long INSTITUCION_DE_LA_SESION = 99L;
    private static final String TOKEN = "un-token-cualquiera";

    @Mock private PuestoCapturaService puestoService;
    @InjectMocks private KioscoTenantInterceptor interceptor;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("sin sesion y con credencial valida, la institucion sale del equipo")
    void sinSesionResuelveDesdeElEquipo() {
        conCookie(TOKEN);
        when(puestoService.resolverKiosco(TOKEN)).thenReturn(Optional.of(puesto()));

        interceptor.preHandle(request, response, new Object());

        assertThat(TenantContext.get()).contains(INSTITUCION_DEL_EQUIPO);
    }

    @Test
    @DisplayName("CON sesion abierta el equipo no se consulta siquiera")
    void conSesionGanaLaSesion() {
        // Es la fuga que este interceptor tiene que evitar: un admin de una institucion
        // sentado en la maquina de otra lleva las dos credenciales encima. Si el equipo
        // pisara a la sesion, operaria sobre los datos de la institucion equivocada.
        conSesionDe(INSTITUCION_DE_LA_SESION);
        conCookie(TOKEN);

        interceptor.preHandle(request, response, new Object());

        assertThat(TenantContext.get())
            .as("el tenant lo publica TenantInterceptor desde la sesion, no este")
            .isEmpty();
        verify(puestoService, never()).resolverKiosco(any());
    }

    @Test
    @DisplayName("el usuario anonimo de Spring cuenta como no tener sesion")
    void elAnonimoNoEsSesion() {
        // Spring pone un token anonimo cuando nadie inicio sesion. Tomarlo por una sesion
        // real dejaria el kiosco sin resolver nunca y sin decir por que.
        SecurityContextHolder.getContext().setAuthentication(
            new AnonymousAuthenticationToken("clave", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        conCookie(TOKEN);
        when(puestoService.resolverKiosco(TOKEN)).thenReturn(Optional.of(puesto()));

        interceptor.preHandle(request, response, new Object());

        assertThat(TenantContext.get()).contains(INSTITUCION_DEL_EQUIPO);
    }

    @Test
    @DisplayName("sin cookie no se publica ninguna institucion")
    void sinCookieNoPublicaNada() {
        interceptor.preHandle(request, response, new Object());

        assertThat(TenantContext.get()).isEmpty();
    }

    @Test
    @DisplayName("con credencial rechazada no se publica ninguna institucion")
    void credencialRechazadaNoPublicaNada() {
        // Token vencido, puesto revocado, kiosco no habilitado: el servicio devuelve vacio y
        // acá no se inventa una institucion por defecto.
        conCookie(TOKEN);
        when(puestoService.resolverKiosco(TOKEN)).thenReturn(Optional.empty());

        interceptor.preHandle(request, response, new Object());

        assertThat(TenantContext.get()).isEmpty();
    }

    @Test
    @DisplayName("deja pasar la peticion siempre: rechazar es tarea del control de acceso")
    void siempreDejaPasar() {
        conCookie(TOKEN);
        when(puestoService.resolverKiosco(TOKEN)).thenReturn(Optional.empty());

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    // ------------------------------------------------------------------------

    private void conCookie(String token) {
        request.setCookies(new Cookie(CookiePuesto.NOMBRE, token));
    }

    private void conSesionDe(Long institucionId) {
        Usuario u = Usuario.builder()
            .id(1L).username("admin").passwordHash("x").email("a@b.c")
            .rol(rolInstitucion()).activo(true).build();
        u.setInstitucionId(institucionId);
        UsuarioAutenticado principal = new UsuarioAutenticado(u);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities()));
    }

    private Rol rolInstitucion() {
        Rol r = new Rol();
        r.setCodigo("INSTITUCION");
        r.setDescripcion("Cuenta institucional");
        return r;
    }

    private PuestoCaptura puesto() {
        PuestoCaptura p = PuestoCaptura.builder()
            .id(3L).nombre("Secretaria").tokenHash("hash").activo(true)
            .kioscoHabilitado(true).build();
        p.setInstitucionId(INSTITUCION_DEL_EQUIPO);
        return p;
    }
}
