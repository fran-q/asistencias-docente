package edu.cent35.asistencias.integracion;

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
import edu.cent35.asistencias.service.PuestoCapturaService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que la pantalla desatendida sea una pantalla <b>sin salida</b> (RF-84, ADR-0019).
 * <p>
 * Es lo que separa al kiosco de "dejar el pase abierto sin sesión". Si la pantalla trajera la
 * barra lateral, cualquiera que se siente en esa máquina llegaría a docentes, reportes y
 * configuración sin autenticarse — y quedaría peor que la práctica que el kiosco viene a
 * reemplazar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KioscoPantallaIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private PuestoCapturaService puestoService;
    @Autowired private PuestoCapturaRepository puestoRepository;
    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;

    private Long institucionId;
    private Usuario cuenta;

    @BeforeEach
    void preparar() {
        TenantContext.clear();
        limpiar();

        institucionId = institucionRepository.save(
            Institucion.builder().nombre("Instituto Kiosco").activo(true).build()).getId();

        Rol r = new Rol();
        r.setCodigo("INSTITUCION");
        r.setDescripcion("Cuenta institucional");
        Rol rol = rolRepository.save(r);

        Usuario u = Usuario.builder()
            .username("admin.k").passwordHash("x").email("k@test.local")
            .rol(rol).activo(true).build();
        u.setInstitucionId(institucionId);
        cuenta = usuarioRepository.save(u);
    }

    @AfterEach
    void limpiarDespues() {
        TenantContext.clear();
        limpiar();
    }

    @Test
    @DisplayName("Con el equipo habilitado, la pantalla abre SIN sesion")
    void abreSinSesion() {
        String token = kioscoHabilitado();

        try {
            mockMvc.perform(get("/kiosco").cookie(new Cookie(CookiePuesto.NOMBRE, token)))
                .andExpect(status().isOk());
        } catch (Exception e) {
            throw new AssertionError("la pantalla tendria que abrir: " + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("La pantalla NO trae navegacion a ninguna otra parte del sistema")
    void noTraeNavegacion() throws Exception {
        String token = kioscoHabilitado();

        String html = mockMvc.perform(get("/kiosco").cookie(new Cookie(CookiePuesto.NOMBRE, token)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Si alguna de estas aparece, la pantalla dejo de ser un kiosco: es el sistema entero
        // abierto en una maquina que nadie vigila.
        assertThat(html)
            .as("una pantalla desatendida no puede llevar a ningun lado")
            .doesNotContain("/docentes")
            .doesNotContain("/reportes")
            .doesNotContain("/comisiones")
            .doesNotContain("/materias")
            .doesNotContain("/usuarios")
            .doesNotContain("/mi-institucion")
            .doesNotContain("/puestos")
            .doesNotContain("lateral__link");

        // Y tampoco puede ofrecer cerrar sesion, porque no hay ninguna abierta.
        assertThat(html).doesNotContain("/logout");
    }

    @Test
    @DisplayName("Un equipo designado pero SIN kiosco habilitado no abre la pantalla")
    void designadoNoAlcanza() throws Exception {
        // Designar dice "la captura ocurre aca"; habilitar el kiosco dice "ademas puede
        // ocurrir sin nadie mirando". Son dos permisos distintos (RF-85).
        String token = puestoService.designar(institucionId, "Secretaria", cuenta)
            .getTokenEnClaro();

        var respuesta = mockMvc.perform(get("/kiosco").cookie(new Cookie(CookiePuesto.NOMBRE, token)))
            .andReturn().getResponse();

        assertThat(respuesta.getStatus())
            .as("sin el permiso de kiosco no hay institucion que resolver, asi que se rechaza")
            .isNotEqualTo(200);
    }

    @Test
    @DisplayName("El registro del rostro sigue exigiendo sesion, aun con el kiosco habilitado")
    void elRegistroDelRostroSigueCerrado() throws Exception {
        // RF-86: marcar sin supervision registra un hecho; enrolar sin supervision crea una
        // identidad, y permitiria que cualquiera registre su cara como la de un docente.
        String token = kioscoHabilitado();

        var respuesta = mockMvc.perform(
                get("/docentes/1/rostro/registrar").cookie(new Cookie(CookiePuesto.NOMBRE, token)))
            .andReturn().getResponse();

        assertThat(respuesta.getStatus()).isEqualTo(302);
        assertThat(respuesta.getRedirectedUrl()).containsIgnoringCase("login");
    }

    // ------------------------------------------------------------------------

    // Designa el equipo y ademas lo habilita para operar sin sesion.
    private String kioscoHabilitado() {
        String token = puestoService.designar(institucionId, "Secretaria", cuenta)
            .getTokenEnClaro();
        PuestoCaptura p = puestoRepository.deInstitucion(institucionId).get(0);
        p.setKioscoHabilitado(true);
        p.setKioscoHabilitadoEn(LocalDateTime.now());
        p.setKioscoHabilitadoPor(cuenta);
        puestoRepository.save(p);
        return token;
    }

    private void limpiar() {
        puestoRepository.deleteAll();
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        institucionRepository.deleteAll();
    }
}
