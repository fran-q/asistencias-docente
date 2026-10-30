package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.EscenarioDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.academico.Carrera;
import edu.cent35.asistencias.model.academico.CicloLectivo;
import edu.cent35.asistencias.model.academico.Horario;
import edu.cent35.asistencias.model.academico.Materia;
import edu.cent35.asistencias.model.asistencia.Asistencia;
import edu.cent35.asistencias.model.asistencia.AsistenciaManual;
import edu.cent35.asistencias.model.asistencia.EstadoAsistencia;
import edu.cent35.asistencias.model.asistencia.MetodoAsistencia;
import edu.cent35.asistencias.model.asistencia.MotivoCargaManual;
import edu.cent35.asistencias.model.institucion.Usuario;
import edu.cent35.asistencias.model.persona.Docente;
import edu.cent35.asistencias.repository.AsistenciaManualRepository;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.JustificacionAusenciaRepository;
import edu.cent35.asistencias.repository.MotivoCargaManualRepository;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.service.AsistenciaService;
import edu.cent35.asistencias.service.GeneradorAusenciasService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Una ausencia que generó el job se puede corregir, y ninguna otra marca (RF-19, RF-22).
 *
 * <p><b>Qué problema cubre.</b> Una asistencia grabada no cambiaba nunca. Eso está bien para una
 * marca de la cámara o una carga de un admin, que son hechos registrados, pero la ausencia del
 * job no la midió ni la declaró nadie: dice que no hubo marca. Si el docente estuvo y nadie lo
 * registró a tiempo, la clase quedaba ausente para siempre y lo único posible era justificarla.
 *
 * <p>Va como IT porque lo que importa es el recorrido entero: la fila que deja el job, el botón
 * del listado, el formulario que llega precargado, el POST que la pisa y la corrida siguiente
 * del job, que la tiene que respetar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorregirAusenciaIT {

    // Un lunes del ciclo 2026, ya pasado: la carga manual no acepta fechas futuras.
    private static final LocalDate LUNES = LocalDate.of(2026, 9, 7);

    private static final String AVISO = "Estás corrigiendo una ausencia";
    private static final String BOTON = ">Corregir</a>";

    @Autowired private MockMvc mockMvc;
    @Autowired private EscenarioDePrueba escenario;
    @Autowired private AsistenciaRepository asistenciaRepository;
    @Autowired private AsistenciaManualRepository manualRepository;
    @Autowired private JustificacionAusenciaRepository justificacionRepository;
    @Autowired private MotivoCargaManualRepository motivoRepository;
    @Autowired private AsistenciaService asistenciaService;
    @Autowired private GeneradorAusenciasService generador;

    private Long tenantId;
    private Long otroTenantId;
    private Usuario cuenta;
    private Docente docente;
    private Horario horario;
    private Short motivoId;

    @BeforeEach
    void sembrar() {
        tenantId = escenario.institucion("Instituto de la correccion");
        TenantContext.set(tenantId);
        cuenta = escenario.cuenta(tenantId, "INSTITUCION", "correccion." + tenantId);
        Carrera carrera = escenario.carrera(tenantId, "COR-" + tenantId);
        Materia materia = escenario.materia(carrera, "COR1-" + tenantId, "Programación I");
        CicloLectivo ciclo = escenario.ciclo(tenantId, 2026);
        docente = escenario.docente(tenantId, "49" + tenantId);
        horario = escenario.horario(escenario.comision(materia, ciclo, docente, "A"),
            DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(20, 0));
        motivoId = motivoRepository.save(MotivoCargaManual.builder()
            .codigo("SIN_PASAR_" + tenantId).descripcion("No pasó por la cámara")
            .activo(true).build()).getId();
    }

    @AfterEach
    void limpiar() {
        escenario.borrarTodoDe(tenantId);
        escenario.borrarTodoDe(otroTenantId);
        // El motivo es del catálogo y no del tenant: lo armó este test y lo borra él.
        motivoRepository.deleteById(motivoId);
        TenantContext.clear();
    }

    @Test
    @DisplayName("La carga a mano pisa la ausencia del job en la misma fila, con motivo y autor")
    void reemplazaLaAusenciaDelJob() throws Exception {
        correrElJob();
        Asistencia delJob = marca();
        assertThat(delJob.getMetodo()).isEqualTo(MetodoAsistencia.SISTEMA);
        assertThat(delJob.getEstado()).isEqualTo(EstadoAsistencia.AUSENTE);

        cargarAMano(EstadoAsistencia.PRESENTE).andExpect(status().is3xxRedirection());

        // marca() revienta si hay dos filas: la clase sigue teniendo una sola.
        Asistencia corregida = marca();
        assertThat(corregida.getId()).isEqualTo(delJob.getId());
        assertThat(corregida.getMetodo()).isEqualTo(MetodoAsistencia.MANUAL);
        assertThat(corregida.getEstado()).isEqualTo(EstadoAsistencia.PRESENTE);

        AsistenciaManual detalle = manualRepository.findByAsistenciaId(corregida.getId()).orElseThrow();
        assertThat(detalle.getMotivo().getId()).isEqualTo(motivoId);
        assertThat(detalle.getUsuario().getId()).isEqualTo(cuenta.getId());
        assertThat(detalle.getDetalleAdicional()).isEqualTo("Estuvo y no pasó por la cámara.");
    }

    @Test
    @DisplayName("La corrida siguiente del job respeta la corrección")
    void elJobNoVuelveAGenerarla() throws Exception {
        correrElJob();
        cargarAMano(EstadoAsistencia.PRESENTE).andExpect(status().is3xxRedirection());

        correrElJob();

        assertThat(marca().getMetodo()).isEqualTo(MetodoAsistencia.MANUAL);
        assertThat(marca().getEstado()).isEqualTo(EstadoAsistencia.PRESENTE);
    }

    @Test
    @DisplayName("Una marca de la cámara no se reemplaza")
    void unaMarcaDeLaCamaraNoSeReemplaza() throws Exception {
        marcaDeLaCamara();

        assertThat(rechazoDe(EstadoAsistencia.PRESENTE)).contains("Ya existe una marca");

        Asistencia sigue = marca();
        assertThat(sigue.getMetodo()).isEqualTo(MetodoAsistencia.AUTOMATICO);
        assertThat(sigue.getEstado()).isEqualTo(EstadoAsistencia.TARDE);
        assertThat(manualRepository.findByAsistenciaId(sigue.getId())).isEmpty();
    }

    @Test
    @DisplayName("Una carga a mano no se pisa con otra")
    void unaCargaAManoNoSeReemplaza() throws Exception {
        // Sin corrida del job: no hay ausencia que reemplazar, es una carga común.
        cargarAMano(EstadoAsistencia.TARDE).andExpect(status().is3xxRedirection());

        assertThat(rechazoDe(EstadoAsistencia.PRESENTE)).contains("Ya existe una marca");

        assertThat(marca().getEstado()).isEqualTo(EstadoAsistencia.TARDE);
    }

    @Test
    @DisplayName("Cargarle otra ausencia no reemplaza nada")
    void otraAusenciaNoLaReemplaza() throws Exception {
        correrElJob();

        assertThat(rechazoDe(EstadoAsistencia.AUSENTE)).contains("ya figura como ausente");

        assertThat(marca().getMetodo()).isEqualTo(MetodoAsistencia.SISTEMA);
        assertThat(manualRepository.findByAsistenciaId(marca().getId())).isEmpty();
    }

    @Test
    @DisplayName("Una ausencia ya justificada no se reemplaza")
    void unaAusenciaJustificadaNoSeReemplaza() throws Exception {
        correrElJob();
        Long id = marca().getId();
        asistenciaService.justificarAusencia(id, "Certificado médico", null, cuenta.getId());

        assertThat(rechazoDe(EstadoAsistencia.PRESENTE)).contains("ya está justificada");

        assertThat(marca().getEstado()).isEqualTo(EstadoAsistencia.AUSENTE);
        assertThat(justificacionRepository.findByAsistenciaId(id)).isPresent();
    }

    @Test
    @DisplayName("El listado ofrece Corregir solo en la ausencia del job")
    void elListadoOfreceCorregirSoloEnLaDelJob() throws Exception {
        String listado = "/asistencias?fecha=" + LUNES;

        assertThat(pedir(listado))
            .as("sin fila todavía: es una ausencia calculada, no hay nada que reemplazar")
            .contains("cargá manual para justificar")
            .doesNotContain(BOTON);

        correrElJob();
        assertThat(pedir(listado))
            .contains(BOTON)
            .contains(">Justificar</a>")
            .contains("/asistencias/manual/nueva?docenteId=" + docente.getId()
                + "&amp;horarioId=" + horario.getId() + "&amp;fecha=" + LUNES);

        cargarAMano(EstadoAsistencia.PRESENTE).andExpect(status().is3xxRedirection());
        assertThat(pedir(listado))
            .as("ya la declaró un admin: se muestra como carga a mano y no se corrige de nuevo")
            .contains("cargada ")
            .doesNotContain(BOTON);
    }

    @Test
    @DisplayName("El listado no ofrece Corregir en una marca de la cámara")
    void elListadoNoOfreceCorregirEnLaCamara() throws Exception {
        marcaDeLaCamara();

        assertThat(pedir("/asistencias?fecha=" + LUNES)).doesNotContain(BOTON);
    }

    @Test
    @DisplayName("Una ausencia ya justificada no ofrece lo que se rechazaría: dice que lo está")
    void elListadoNoOfreceNadaEnUnaJustificada() throws Exception {
        correrElJob();
        asistenciaService.justificarAusencia(marca().getId(), "Certificado médico", null, cuenta.getId());

        assertThat(pedir("/asistencias?fecha=" + LUNES))
            .doesNotContain(BOTON)
            .doesNotContain(">Justificar</a>")
            .contains("<small>justificada</small>");
    }

    @Test
    @DisplayName("Una ausencia cargada a mano se justifica, pero no se corrige")
    void unaAusenciaCargadaAManoNoSeCorrige() throws Exception {
        cargarAMano(EstadoAsistencia.AUSENTE).andExpect(status().is3xxRedirection());

        assertThat(pedir("/asistencias?fecha=" + LUNES))
            .contains(">Justificar</a>")
            .doesNotContain(BOTON);
        assertThat(rechazoDe(EstadoAsistencia.PRESENTE)).contains("Ya existe una marca");
    }

    @Test
    @DisplayName("El botón lleva al formulario con la clase elegida y avisa que reemplaza")
    void elFormularioLlegaPrecargado() throws Exception {
        correrElJob();

        assertThat(pedir(formularioDeLaClase()))
            .contains(AVISO)
            .containsPattern("<option[^>]*value=\"" + docente.getId() + "\"[^>]*selected")
            .containsPattern("<option[^>]*value=\"" + horario.getId() + "\"[^>]*selected")
            .containsPattern("<input[^>]*id=\"fecha\"[^>]*value=\"" + LUNES + "\"");
    }

    @Test
    @DisplayName("El aviso sale de lo que hay en la base, no de lo que diga la URL")
    void elAvisoNoSeCreeLaUrl() throws Exception {
        assertThat(pedir("/asistencias/manual/nueva")).doesNotContain(AVISO);
        assertThat(pedir(formularioDeLaClase()))
            .as("esa clase no tiene ninguna ausencia que reemplazar")
            .doesNotContain(AVISO);

        marcaDeLaCamara();
        assertThat(pedir(formularioDeLaClase()))
            .as("tiene una marca, pero no es de las que se reemplazan")
            .doesNotContain(AVISO);
    }

    @Test
    @DisplayName("Si falta el motivo, el formulario vuelve con el error y sigue avisando")
    void elAvisoSigueSiElFormularioVuelve() throws Exception {
        correrElJob();

        String html = mockMvc.perform(post("/asistencias/manual/nueva")
                .param("docenteId", docente.getId().toString())
                .param("horarioId", horario.getId().toString())
                .param("fecha", LUNES.toString())
                .param("estado", EstadoAsistencia.PRESENTE.name())
                .with(user(new UsuarioAutenticado(cuenta))).with(csrf()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Elegí un motivo").contains(AVISO);
        assertThat(marca().getMetodo()).isEqualTo(MetodoAsistencia.SISTEMA);
    }

    @Test
    @DisplayName("Otra institución no ve esa ausencia ni la puede reemplazar")
    void otraInstitucionNoLaToca() throws Exception {
        correrElJob();
        otroTenantId = escenario.institucion("Otra institucion");
        Usuario ajena = escenario.cuenta(otroTenantId, "INSTITUCION", "ajena." + otroTenantId);

        String formulario = mockMvc.perform(get(formularioDeLaClase())
                .with(user(new UsuarioAutenticado(ajena))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(formulario).doesNotContain(AVISO);

        mockMvc.perform(cargaAMano(EstadoAsistencia.PRESENTE)
            .with(user(new UsuarioAutenticado(ajena))).with(csrf()));

        Asistencia sigue = marca();
        assertThat(sigue.getMetodo()).isEqualTo(MetodoAsistencia.SISTEMA);
        assertThat(sigue.getEstado()).isEqualTo(EstadoAsistencia.AUSENTE);
        assertThat(manualRepository.findByAsistenciaId(sigue.getId())).isEmpty();
    }

    // ------------------------------------------------------------------------

    // Una corrida del job después de la clase. Se vuelve a poner el tenant porque cada request
    // lo limpia del hilo al terminar, y el test corre en ese mismo hilo.
    private void correrElJob() {
        TenantContext.set(tenantId);
        generador.generarParaInstitucion(tenantId, LUNES, LocalTime.of(21, 0));
    }

    // La cámara lo reconoció tarde. El estado distinto de PRESENTE deja ver si algo lo pisó.
    private void marcaDeLaCamara() {
        Asistencia a = Asistencia.builder()
            .docente(docente).comision(horario.getComision()).horario(horario)
            .fecha(LUNES).horaRegistrada(LocalTime.of(18, 20))
            .estado(EstadoAsistencia.TARDE).metodo(MetodoAsistencia.AUTOMATICO)
            .build();
        a.setInstitucionId(tenantId);
        asistenciaRepository.save(a);
    }

    private Asistencia marca() {
        return asistenciaRepository.findByDocenteIdAndHorarioIdAndFecha(
            docente.getId(), horario.getId(), LUNES).orElseThrow();
    }

    private String formularioDeLaClase() {
        return "/asistencias/manual/nueva?docenteId=" + docente.getId()
            + "&horarioId=" + horario.getId() + "&fecha=" + LUNES;
    }

    private MockHttpServletRequestBuilder cargaAMano(EstadoAsistencia estado) {
        return post("/asistencias/manual/nueva")
            .param("docenteId", docente.getId().toString())
            .param("horarioId", horario.getId().toString())
            .param("fecha", LUNES.toString())
            .param("estado", estado.name())
            .param("motivoId", motivoId.toString())
            .param("detalleAdicional", "Estuvo y no pasó por la cámara.");
    }

    private ResultActions cargarAMano(EstadoAsistencia estado) throws Exception {
        return mockMvc.perform(cargaAMano(estado)
            .with(user(new UsuarioAutenticado(cuenta))).with(csrf()));
    }

    // El formulario de vuelta, con el motivo del rechazo: no redirige porque no se guardó nada.
    private String rechazoDe(EstadoAsistencia estado) throws Exception {
        return cargarAMano(estado)
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String pedir(String ruta) throws Exception {
        return mockMvc.perform(get(ruta).with(user(new UsuarioAutenticado(cuenta))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
