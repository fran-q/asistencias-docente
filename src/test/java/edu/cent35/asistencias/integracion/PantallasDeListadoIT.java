package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.CicloLectivoRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.PeriodoLectivoRepository;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El marco de las pantallas de listado: ocupan la ventana entera y el recuadro de la tabla
 * está siempre, con o sin registros adentro.
 *
 * <p><b>Qué cuida.</b> Que las dos clases que sostienen el layout —{@code pantalla-listado} en
 * la sección y {@code listado-caja} en la tarjeta de la tabla— no se caigan de una pantalla al
 * editarla. Sin cualquiera de las dos la pantalla vuelve sola al comportamiento viejo: la
 * tarjeta flotando arriba con media ventana vacía abajo, y la página entera desplazándose.
 * No se rompe nada visible al abrirla, así que es justo lo que se pasa por alto.
 *
 * <p>Y que un listado <b>sin ningún registro</b> siga mostrando el recuadro con el aviso
 * adentro. Dos de estas pantallas escondían la tabla entera y ponían una tarjeta suelta
 * debajo: la pantalla quedaba sin el marco de lo que uno venía a ver.
 *
 * <p>Corren sin sembrar nada a propósito: el caso vacío es el que importa acá, y además es el
 * que menos se mira.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PantallasDeListadoIT {

    private static final AtomicInteger SEC = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private CicloLectivoRepository cicloRepository;
    @Autowired private PeriodoLectivoRepository periodoRepository;

    private Long tenantId;

    @BeforeEach
    void sembrar() {
        TenantContext.clear();
        tenantId = institucionRepository.save(Institucion.builder()
            .nombre("Instituto de listados " + SEC.incrementAndGet()).activo(true).build()).getId();
        TenantContext.set(tenantId);
        // Los días sin clase se piden por año, y el año sale del ciclo activo.
        cicloRepository.save(DatosDePrueba.cicloAnualDelTenant(tenantId, LocalDate.now().getYear()));
        TenantContext.clear();
    }

    @AfterEach
    void limpiar() {
        TenantContext.set(tenantId);
        periodoRepository.deleteAll();
        cicloRepository.deleteAll();
        TenantContext.clear();
        institucionRepository.deleteById(tenantId);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/docentes", "/usuarios", "/carreras", "/materias", "/comisiones", "/horarios",
        "/ciclos", "/dias-sin-clase", "/asistencias", "/reportes"
    })
    @DisplayName("Cada listado trae el marco de alto completo y su recuadro de tabla")
    void elListadoTraeSuMarco(String ruta) throws Exception {
        String html = pantalla(ruta);

        assertThat(html)
            .as("sin pantalla-listado la página vuelve a desplazarse entera: " + ruta)
            .contains("pantalla-listado");
        assertThat(html)
            .as("sin listado-caja la tabla no se queda con el alto que sobra: " + ruta)
            .contains("listado-caja");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/docentes", "/usuarios", "/carreras", "/materias", "/comisiones", "/horarios",
        "/dias-sin-clase"
    })
    @DisplayName("Sin registros, el recuadro sigue estando y dice que no hay nada cargado")
    void sinRegistrosElRecuadroSigueEstando(String ruta) throws Exception {
        elRecuadroSigueEstando(ruta);
    }

    /**
     * Los ciclos van aparte porque el ciclo sembrado es justamente lo que hace falta para que
     * los días sin clase tengan un año que mostrar: no pueden estar los dos vacíos a la vez.
     */
    @Test
    @DisplayName("Sin ciclos, el recuadro sigue estando y dice que no hay ninguno")
    void sinCiclosElRecuadroSigueEstando() throws Exception {
        TenantContext.set(tenantId);
        periodoRepository.deleteAll();
        cicloRepository.deleteAll();
        TenantContext.clear();

        elRecuadroSigueEstando("/ciclos");
    }

    private void elRecuadroSigueEstando(String ruta) throws Exception {
        String html = pantalla(ruta);

        // El recuadro y su tabla, aunque no haya ni una fila de datos.
        assertThat(html).as("el recuadro desapareció en " + ruta).contains("listado-caja");
        assertThat(html).as("la tabla desapareció en " + ruta).contains("table-wrap");
        // Y el aviso adentro, no en una tarjeta suelta debajo.
        assertThat(html).as("no avisa que no hay nada cargado en " + ruta).contains("table__empty");
    }

    @Test
    @DisplayName("En el reporte, el recuento y las descargas van adentro de la tarjeta de filtros")
    void elRecuentoYLasDescargasVanEnLosFiltros() throws Exception {
        // Estaban en una franja propia entre los filtros y la tabla, y esa franja le comía
        // alto al listado para decir dos cosas que entran en el hueco que la última fila de
        // filtros deja libre. Sacarlas de ahí es justo lo que un vistazo no detecta: la
        // pantalla se ve bien igual, sólo que con menos registros a la vista.
        String html = pantalla("/reportes");

        int abre = html.indexOf("<form method=\"get\"");
        int cierra = html.indexOf("</form>", abre);
        assertThat(abre).as("no se encontró la barra de filtros del reporte").isNotNegative();

        assertThat(html.substring(abre, cierra))
            .as("el recuento y las descargas volvieron a quedar fuera de la tarjeta de filtros")
            .contains("reporte__acciones")
            .contains("Descargar PDF")
            .contains("Descargar CSV");
    }

    @Test
    @DisplayName("Ya no queda rastro del selector de densidad en ningún listado")
    void sinSelectorDeDensidad() throws Exception {
        // Elegir entre tres altos de fila no es una decisión que el listado le tenga que
        // pedir a nadie. Se fue el control, su script y el atributo que lo repuso.
        for (String ruta : new String[]{"/docentes", "/usuarios", "/carreras", "/materias",
                                        "/comisiones", "/horarios"}) {
            String html = pantalla(ruta);
            assertThat(html).as("quedó el conmutador en " + ruta).doesNotContain("segmentado");
            assertThat(html).as("quedó el atributo en " + ruta).doesNotContain("data-densidad");
            assertThat(html).as("quedó el script en " + ruta).doesNotContain("densidad.js");
        }
    }

    private String pantalla(String ruta) throws Exception {
        return mockMvc.perform(get(ruta).with(user(principal())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    // La cuenta institucional llega a las diez pantallas; con ADMIN quedan afuera las de
    // administración de usuarios, y el caso a cubrir es el marco, no el permiso.
    private UsuarioAutenticado principal() {
        Rol r = new Rol();
        r.setId((short) 1);
        r.setCodigo("INSTITUCION");
        r.setDescripcion("Institución");
        Usuario u = Usuario.builder()
            .persona(DatosDePrueba.persona("Test", "Listados"))
            .id(9999L).username("test.listados").passwordHash("no-se-usa")
            .activo(true).rol(r).emailVerificadoEn(LocalDateTime.now()).build();
        u.setInstitucionId(tenantId);
        return new UsuarioAutenticado(u);
    }
}
