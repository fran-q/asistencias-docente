package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.EscenarioDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoCierre;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.MotivoCargaManual;
import edu.cent35.asistencias.model.OrigenMarca;
import edu.cent35.asistencias.model.BloquePresencia;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.BloquePresenciaRepository;
import edu.cent35.asistencias.repository.MotivoCargaManualRepository;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.service.PuestoCapturaService;
import edu.cent35.asistencias.seguridad.CookiePuesto;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La marca que carga el admin cuando la cámara no reconoce al docente (V029).
 *
 * <p><b>Por qué va como IT.</b> Lo que hay que probar no es el cálculo sino el camino: que el
 * botón llegue a la pantalla con el docente que corresponde, que el endpoint exija el mismo
 * equipo autorizado que el resto del pase, y que la fila que queda en la base tenga origen
 * manual con su autor y su motivo. Con los repositorios mockeados eso pasa en verde aunque el
 * CHECK de V029 rechace la fila.
 *
 * <p><b>El docente de acá no tiene consentimiento biométrico.</b> Es deliberado: es el caso que
 * este camino viene a resolver —quien revocó su consentimiento no puede usar la cámara (RF-82)
 * y hasta ahora se quedaba sin forma de registrar su asistencia en el momento—. Que estos casos
 * pasen en verde sin consentimiento es parte de lo que se está probando.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarcaSinCamaraIT {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private EscenarioDePrueba escenario;
    @Autowired private AsistenciaRepository asistenciaRepository;
    @Autowired private BloquePresenciaRepository bloqueRepository;
    @Autowired private MotivoCargaManualRepository motivoRepository;
    @Autowired private PuestoCapturaService puestoService;

    private Long tenantId;
    private Docente docente;
    private Usuario admin;
    private Short motivoFallaId;
    private Short motivoOtroId;
    private Cookie delPuesto;
    private LocalTime empiezaLaClase;

    @BeforeEach
    void sembrar() {
        int n = SECUENCIA.incrementAndGet();
        TenantContext.clear();

        // El catálogo de motivos se siembra en V001, y el perfil test corre sobre H2 con
        // Flyway apagado: acá no existe si no se crea a mano.
        motivoFallaId = motivoRepository.save(MotivoCargaManual.builder()
            .codigo("NO_REGISTRADO_" + n).descripcion("Docente sin rostro registrado")
            .activo(true).build()).getId();
        motivoOtroId = motivoRepository.save(MotivoCargaManual.builder()
            .codigo("OTRO").descripcion("Otro motivo (detallar)").activo(true).build()).getId();

        tenantId = escenario.institucion("Instituto sin camara");
        TenantContext.set(tenantId);

        Carrera carrera = escenario.carrera(tenantId, "SC-" + tenantId);
        Materia materia = escenario.materia(carrera, "SC1-" + tenantId, "Programación I");
        docente = escenario.docente(tenantId, "42" + tenantId);
        CicloLectivo ciclo = escenario.ciclo(tenantId, LocalDate.now().getYear());
        Comision comision = escenario.comision(materia, ciclo, docente, "A");

        // El endpoint marca con la hora del reloj, no con una que le pase el test: la clase
        // tiene que estar en curso AHORA, corra el test la hora que corra. Los topes son por
        // las dos puntas del día --a las 23:30 restar una hora da una clase que termina antes
        // de empezar-- y no por elegancia.
        LocalTime ahora = LocalTime.now();
        empiezaLaClase = ahora.isBefore(LocalTime.of(1, 0)) ? LocalTime.MIDNIGHT : ahora.minusHours(1);
        LocalTime termina = ahora.isAfter(LocalTime.of(22, 59)) ? LocalTime.of(23, 59) : ahora.plusHours(1);

        escenario.horario(comision, LocalDate.now().getDayOfWeek(), empiezaLaClase, termina);

        // El admin tiene que existir de verdad: el service lo busca por id para dejar asentado
        // quien cargó la marca. Un principal solo en memoria hace fallar el registro.
        admin = escenario.cuenta(tenantId, "ADMIN", "sin.camara." + n,
                                 DatosDePrueba.persona("Secre", "Taria"));

        delPuesto = new Cookie(CookiePuesto.NOMBRE,
            puestoService.designar(tenantId, "Secretaria PC-" + n, admin).getTokenEnClaro());
    }

    @AfterEach
    void limpiar() {
        escenario.borrarTodoDe(tenantId);
        if (tenantId != null) {
            motivoRepository.deleteById(motivoFallaId);
            motivoRepository.deleteById(motivoOtroId);
        }
        TenantContext.clear();
    }

    @Test
    @DisplayName("La pantalla del pase trae el botón sobre la clase en curso y el cuadro")
    void laPantallaOfreceLaMarcaSinCamara() throws Exception {
        String html = mockMvc.perform(get("/asistencia/pase")
                .with(user(new UsuarioAutenticado(admin))).cookie(delPuesto))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // El botón va sobre la fila del docente, con su id: elegir de esa lista y no de un
        // buscador es lo que hace imposible marcarle a alguien que no tiene clase ahora.
        assertThat(html).contains("pase__sin-camara");
        assertThat(html).contains("data-docente-id=\"" + docente.getId() + "\"");
        assertThat(html).contains("Ana");

        // Y el cuadro, con el catálogo de motivos adentro.
        assertThat(html).contains("pa-sc-overlay");
        assertThat(html).contains("Docente sin rostro registrado");
    }

    @Test
    @DisplayName("Registra la entrada con origen manual, su autor y su motivo")
    void registraLaEntrada() throws Exception {
        mockMvc.perform(post("/asistencia/pase/sin-camara")
                .with(user(new UsuarioAutenticado(admin))).with(csrf()).cookie(delPuesto)
                .contentType("application/json")
                .content(cuerpo(docente.getId(), motivoFallaId, "todavía sin foto")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registrada").value(true))
            .andExpect(jsonPath("$.tipoDeMarca").value("ENTRADA"))
            // Quien la carga tiene que poder confirmar que no se equivocó de persona.
            .andExpect(jsonPath("$.docenteNombre").value(org.hamcrest.Matchers.containsString("Ana")));

        BloquePresencia b = unicoBloque();
        assertThat(b.getOrigenEntrada()).isEqualTo(OrigenMarca.MANUAL);
        assertThat(b.getEstadoCierre()).isEqualTo(EstadoCierre.ABIERTO);
        assertThat(b.getAbiertoPor().getId()).isEqualTo(admin.getId());
        assertThat(b.getMotivoEntrada().getId()).isEqualTo(motivoFallaId);
        assertThat(b.getDetalleEntrada()).isEqualTo("todavía sin foto");
        // Lo exige ck_bloques_entrada_modelo: no hubo medición, no hay evidencia que guardar.
        assertThat(b.getModeloFacialEntrada()).isNull();
        assertThat(b.getConfianzaEntrada()).isNull();

        // Y la clase en curso quedó imputada, que es para lo que se marca.
        assertThat(asistenciaRepository.findAll().stream()
            .filter(a -> tenantId.equals(a.getInstitucionId())).toList()).hasSize(1);
    }

    @Test
    @DisplayName("Con la jornada abierta registra la salida, no una segunda entrada")
    void conJornadaAbiertaRegistraLaSalida() throws Exception {
        // La jornada se siembra abierta en vez de marcar dos veces seguidas: las dos marcas
        // caerian en el mismo segundo y la salida no sería posterior a la entrada.
        BloquePresencia abierto = BloquePresencia.builder()
            .docente(docente).fecha(LocalDate.now()).horaEntrada(empiezaLaClase)
            .origenEntrada(OrigenMarca.AUTOMATICO).estadoCierre(EstadoCierre.ABIERTO)
            .build();
        abierto.setInstitucionId(tenantId);
        bloqueRepository.save(abierto);

        mockMvc.perform(post("/asistencia/pase/sin-camara")
                .with(user(new UsuarioAutenticado(admin))).with(csrf()).cookie(delPuesto)
                .contentType("application/json")
                .content(cuerpo(docente.getId(), motivoFallaId, null)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registrada").value(true))
            .andExpect(jsonPath("$.tipoDeMarca").value("SALIDA"));

        BloquePresencia b = unicoBloque();
        assertThat(b.getEstadoCierre()).isEqualTo(EstadoCierre.CERRADO_POR_ADMIN);
        assertThat(b.getOrigenSalida()).isEqualTo(OrigenMarca.MANUAL);
        assertThat(b.getHoraSalida()).isNotNull();
    }

    @Test
    @DisplayName("El motivo Otro sin detalle responde 400 y no registra nada")
    void otroSinDetalleNoRegistra() throws Exception {
        // 400 y no 200: es un dato a corregir en el cuadro, que por eso se queda abierto.
        mockMvc.perform(post("/asistencia/pase/sin-camara")
                .with(user(new UsuarioAutenticado(admin))).with(csrf()).cookie(delPuesto)
                .contentType("application/json")
                .content(cuerpo(docente.getId(), motivoOtroId, "   ")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.registrada").value(false))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Otro")));

        assertThat(bloquesDelTenant()).isEmpty();
    }

    @Test
    @DisplayName("Sin el equipo autorizado no se puede marcar, aunque haya sesión")
    void sinEquipoAutorizadoNoMarca() throws Exception {
        // Esto no registra un rostro, pero sí registra presencia: si se pudiera desde
        // cualquier máquina, alcanzaría con la sesión para marcarle la entrada a alguien que
        // no está (ADR-0015).
        //
        // Responde 403 con JSON y no la redirección de las pantallas: el interceptor mira si
        // el endpoint devuelve cuerpo, y a este el navegador lo llama por fetch. Una
        // redirección ahí termina en HTML de otra pantalla metido en un JSON.parse.
        mockMvc.perform(post("/asistencia/pase/sin-camara")
                .with(user(new UsuarioAutenticado(admin))).with(csrf())
                .contentType("application/json")
                .content(cuerpo(docente.getId(), motivoFallaId, null)))
            .andExpect(status().isForbidden())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("PUESTO_NO_AUTORIZADO")));

        assertThat(bloquesDelTenant()).isEmpty();
    }

    // ------------------------------------------------------------------------

    private static String cuerpo(Long docenteId, Short motivoId, String detalle) {
        return "{\"docenteId\":" + docenteId + ",\"motivoId\":" + motivoId + ",\"detalle\":"
            + (detalle == null ? "null" : "\"" + detalle + "\"") + "}";
    }

    private List<BloquePresencia> bloquesDelTenant() {
        return bloqueRepository.findAll().stream()
            .filter(b -> tenantId.equals(b.getInstitucionId())).toList();
    }

    private BloquePresencia unicoBloque() {
        List<BloquePresencia> bloques = bloquesDelTenant();
        assertThat(bloques).hasSize(1);
        return bloques.get(0);
    }
}
