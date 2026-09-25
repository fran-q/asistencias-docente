package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoCierre;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.MotivoCargaManual;
import edu.cent35.asistencias.model.OrigenMarca;
import edu.cent35.asistencias.model.BloquePresencia;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.BloquePresenciaRepository;
import edu.cent35.asistencias.repository.CarreraRepository;
import edu.cent35.asistencias.repository.CicloLectivoRepository;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.MateriaRepository;
import edu.cent35.asistencias.repository.MotivoCargaManualRepository;
import edu.cent35.asistencias.repository.PeriodoLectivoRepository;
import edu.cent35.asistencias.repository.PuestoCapturaRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
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
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Predicate;
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
    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private CarreraRepository carreraRepository;
    @Autowired private MateriaRepository materiaRepository;
    @Autowired private ComisionRepository comisionRepository;
    @Autowired private HorarioRepository horarioRepository;
    @Autowired private DocenteRepository docenteRepository;
    @Autowired private CicloLectivoRepository cicloRepository;
    @Autowired private PeriodoLectivoRepository periodoRepository;
    @Autowired private AsistenciaRepository asistenciaRepository;
    @Autowired private BloquePresenciaRepository bloqueRepository;
    @Autowired private MotivoCargaManualRepository motivoRepository;
    @Autowired private PuestoCapturaRepository puestoRepository;
    @Autowired private PuestoCapturaService puestoService;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;

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

        // El catalogo de motivos se siembra en V001, y el perfil test corre sobre H2 con
        // Flyway apagado: aca no existe si no se crea a mano.
        motivoFallaId = motivoRepository.save(MotivoCargaManual.builder()
            .codigo("NO_REGISTRADO_" + n).descripcion("Docente sin rostro registrado")
            .activo(true).build()).getId();
        motivoOtroId = motivoRepository.save(MotivoCargaManual.builder()
            .codigo("OTRO").descripcion("Otro motivo (detallar)").activo(true).build()).getId();

        Institucion inst = institucionRepository.save(Institucion.builder()
            .nombre("Instituto sin camara " + n).activo(true).build());
        tenantId = inst.getId();
        TenantContext.set(tenantId);

        Carrera carrera = Carrera.builder()
            .codigo("SC-" + tenantId).nombre("Tecnicatura").duracionAnios((short) 3)
            .activo(true).build();
        carrera.setInstitucionId(tenantId);
        carrera = carreraRepository.save(carrera);

        Materia materia = Materia.builder()
            .codigo("SC1-" + tenantId).nombre("Programación I").carrera(carrera)
            .anio((short) 1).activo(true).build();
        materia.setInstitucionId(tenantId);
        materia = materiaRepository.save(materia);

        docente = Docente.builder()
            .persona(DatosDePrueba.personaDelTenant(tenantId, "42" + tenantId, "Ana", "Pérez"))
            .fechaAlta(LocalDate.of(2020, 1, 1)).activo(true).build();
        docente.setInstitucionId(tenantId);
        docente = docenteRepository.save(docente);

        CicloLectivo ciclo = cicloRepository.save(
            DatosDePrueba.cicloAnualDelTenant(tenantId, LocalDate.now().getYear()));

        Comision comision = comisionRepository.save(Comision.builder()
            .materia(materia).codigo("A").docenteAsignado(docente).activo(true)
            .periodo(ciclo.getPeriodos().get(0))
            .build());

        // El endpoint marca con la hora del reloj, no con una que le pase el test: la clase
        // tiene que estar en curso AHORA, corra el test la hora que corra. Los topes son por
        // las dos puntas del dia --a las 23:30 restar una hora da una clase que termina antes
        // de empezar-- y no por elegancia.
        LocalTime ahora = LocalTime.now();
        empiezaLaClase = ahora.isBefore(LocalTime.of(1, 0)) ? LocalTime.MIDNIGHT : ahora.minusHours(1);
        LocalTime termina = ahora.isAfter(LocalTime.of(22, 59)) ? LocalTime.of(23, 59) : ahora.plusHours(1);

        horarioRepository.save(Horario.builder()
            .comision(comision)
            .diaSemana((byte) LocalDate.now().getDayOfWeek().getValue())
            .horaInicio(empiezaLaClase).horaFin(termina)
            .toleranciaMin((short) 15).activo(true)
            .build());

        Rol rol = rolRepository.findByCodigo("ADMIN").orElseGet(() -> {
            Rol nuevo = new Rol();
            nuevo.setCodigo("ADMIN");
            nuevo.setDescripcion("Administrador");
            return rolRepository.save(nuevo);
        });
        // El admin tiene que existir de verdad: el service lo busca por id para dejar asentado
        // quien cargo la marca. Un principal solo en memoria hace fallar el registro.
        Usuario u = Usuario.builder()
            .persona(DatosDePrueba.persona("Secre", "Taria"))
            .username("sin.camara." + n).passwordHash("no-se-usa")
            .email("sincamara" + n + "@test.local")
            .activo(true).rol(rol).emailVerificadoEn(LocalDateTime.now()).build();
        u.setInstitucionId(tenantId);
        admin = usuarioRepository.save(u);

        delPuesto = new Cookie(CookiePuesto.NOMBRE,
            puestoService.designar(tenantId, "Secretaria PC-" + n, admin).getTokenEnClaro());
    }

    @AfterEach
    void limpiar() {
        if (tenantId != null) {
            borrar(asistenciaRepository, a -> tenantId.equals(a.getInstitucionId()));
            borrar(bloqueRepository, b -> tenantId.equals(b.getInstitucionId()));
            List<Long> comisionIds = comisionRepository.findAllDelTenant(tenantId).stream()
                .map(Comision::getId).toList();
            borrar(horarioRepository, h -> comisionIds.contains(h.getComision().getId()));
            borrar(comisionRepository, c -> comisionIds.contains(c.getId()));
            borrar(periodoRepository, p -> tenantId.equals(p.getInstitucionId()));
            borrar(cicloRepository, c -> tenantId.equals(c.getInstitucionId()));
            borrar(materiaRepository, m -> tenantId.equals(m.getInstitucionId()));
            borrar(carreraRepository, c -> tenantId.equals(c.getInstitucionId()));
            borrar(docenteRepository, d -> tenantId.equals(d.getInstitucionId()));
            borrar(puestoRepository, p -> tenantId.equals(p.getInstitucionId()));
            borrar(usuarioRepository, u -> tenantId.equals(u.getInstitucionId()));
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

        // El boton va sobre la fila del docente, con su id: elegir de esa lista y no de un
        // buscador es lo que hace imposible marcarle a alguien que no tiene clase ahora.
        assertThat(html).contains("pase__sin-camara");
        assertThat(html).contains("data-docente-id=\"" + docente.getId() + "\"");
        assertThat(html).contains("Ana");

        // Y el cuadro, con el catalogo de motivos adentro.
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
            // Quien la carga tiene que poder confirmar que no se equivoco de persona.
            .andExpect(jsonPath("$.docenteNombre").value(org.hamcrest.Matchers.containsString("Ana")));

        BloquePresencia b = unicoBloque();
        assertThat(b.getOrigenEntrada()).isEqualTo(OrigenMarca.MANUAL);
        assertThat(b.getEstadoCierre()).isEqualTo(EstadoCierre.ABIERTO);
        assertThat(b.getAbiertoPor().getId()).isEqualTo(admin.getId());
        assertThat(b.getMotivoEntrada().getId()).isEqualTo(motivoFallaId);
        assertThat(b.getDetalleEntrada()).isEqualTo("todavía sin foto");
        // Lo exige ck_bloques_entrada_modelo: no hubo medicion, no hay evidencia que guardar.
        assertThat(b.getModeloFacialEntrada()).isNull();
        assertThat(b.getConfianzaEntrada()).isNull();

        // Y la clase en curso quedo imputada, que es para lo que se marca.
        assertThat(asistenciaRepository.findAll().stream()
            .filter(a -> tenantId.equals(a.getInstitucionId())).toList()).hasSize(1);
    }

    @Test
    @DisplayName("Con la jornada abierta registra la salida, no una segunda entrada")
    void conJornadaAbiertaRegistraLaSalida() throws Exception {
        // La jornada se siembra abierta en vez de marcar dos veces seguidas: las dos marcas
        // caerian en el mismo segundo y la salida no seria posterior a la entrada.
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

    private <T> void borrar(CrudRepository<T, ?> repo, Predicate<T> cual) {
        for (T fila : repo.findAll()) {
            if (cual.test(fila)) repo.delete(fila);
        }
    }
}
