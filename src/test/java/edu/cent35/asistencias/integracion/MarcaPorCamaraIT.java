package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.dto.IdentificacionResultadoDto;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.BloquePresencia;
import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.ConsentimientoBiometrico;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoCierre;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.MetodoAsistencia;
import edu.cent35.asistencias.model.MetodoConsentimiento;
import edu.cent35.asistencias.model.OrigenMarca;
import edu.cent35.asistencias.model.PuestoCaptura;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.BloquePresenciaRepository;
import edu.cent35.asistencias.repository.CarreraRepository;
import edu.cent35.asistencias.repository.CicloLectivoRepository;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.ConsentimientoBiometricoRepository;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.MateriaRepository;
import edu.cent35.asistencias.repository.PeriodoLectivoRepository;
import edu.cent35.asistencias.repository.PuestoCapturaRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.seguridad.CookiePuesto;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.service.IdentificacionFacialService;
import edu.cent35.asistencias.service.PuestoCapturaService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La toma de asistencia entera, desde el endpoint que llama la cámara hasta la fila en la base,
 * en los dos modos: el pase con un administrador presente y el kiosco sin ninguna sesión.
 *
 * <p><b>Qué cubre que no cubría nada.</b> Los tests del servicio entran por
 * {@code BloquePresenciaService} y los del pase mockean los repositorios: entre el
 * {@code POST} y el registro quedaban sin probar los interceptores —el equipo autorizado, y la
 * institución que en el kiosco sale del propio equipo porque no hay sesión—, el contrato JSON
 * que lee la pantalla, y que la marca termine escrita con su método y su equipo.
 *
 * <p><b>El reconocimiento va mockeado.</b> Lo que se prueba acá es el camino, no el motor: sin
 * una cámara de verdad no hay imagen que reconocer, y la calibración del umbral tiene sus
 * propias mediciones. {@code IdentificacionFacialService} devuelve un docente reconocido y el
 * resto del recorrido es el real.
 *
 * <p><b>La ventana de confirmación se acorta a una lectura.</b> En producción la identidad
 * tiene que sostenerse tres segundos (RF-75); exigir eso acá obligaría a dormir el test entre
 * cuadro y cuadro para probar algo que ya está cubierto por
 * {@code PaseAsistenciaServiceTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.biometria.confirmacion.ventana-ms=0",
    "app.biometria.confirmacion.lecturas-minimas=1"
})
class MarcaPorCamaraIT {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    // Un JPEG cualquiera: la imagen no se mira, pero el data URL tiene que decodificar.
    private static final String IMAGEN = "{\"imagen\":\"data:image/jpeg;base64,/9j/4AAQSkZJRgABAQ==\"}";

    @Autowired private MockMvc mockMvc;
    @MockBean private IdentificacionFacialService identificacionService;

    @Autowired private PuestoCapturaService puestoService;
    @Autowired private PuestoCapturaRepository puestoRepository;
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
    @Autowired private ConsentimientoBiometricoRepository consentimientoRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Long tenantId;
    private Docente docente;
    private Usuario cuenta;
    private String tokenDelPuesto;

    @BeforeEach
    void sembrar() {
        // La clase tiene que estar en curso a la hora en que corre el test, y las horas son
        // LocalTime: cerca de medianoche, restarle media hora al reloj cae en el dia anterior.
        // Se saltea antes que dar un resultado que depende de la hora de la maquina.
        LocalTime ahora = LocalTime.now();
        Assumptions.assumeTrue(
            ahora.isAfter(LocalTime.of(1, 0)) && ahora.isBefore(LocalTime.of(22, 0)),
            "La clase de prueba se arma alrededor de la hora actual: entre las 22 y la 1 no da.");

        Institucion inst = institucionRepository.save(Institucion.builder()
            .nombre("Instituto de la camara " + SECUENCIA.incrementAndGet())
            .activo(true).build());
        tenantId = inst.getId();
        TenantContext.set(tenantId);

        Rol rol = rolRepository.findByCodigo("INSTITUCION").orElseGet(() -> {
            Rol nuevo = new Rol();
            nuevo.setCodigo("INSTITUCION");
            nuevo.setDescripcion("Institucion");
            return rolRepository.save(nuevo);
        });
        Usuario u = Usuario.builder()
            .username("camara." + tenantId).email("camara." + tenantId + "@test.local")
            .passwordHash("no-se-usa").activo(true).rol(rol)
            .emailVerificadoEn(LocalDateTime.now())
            .build();
        u.setInstitucionId(tenantId);
        cuenta = usuarioRepository.save(u);

        Carrera carrera = Carrera.builder()
            .codigo("CAM-" + tenantId).nombre("Tecnicatura").duracionAnios((short) 3)
            .activo(true).build();
        carrera.setInstitucionId(tenantId);
        carrera = carreraRepository.save(carrera);

        Materia materia = Materia.builder()
            .codigo("CAM1-" + tenantId).nombre("Programación I").carrera(carrera)
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

        horarioRepository.save(Horario.builder()
            .comision(comision)
            .diaSemana((byte) LocalDate.now().getDayOfWeek().getValue())
            .horaInicio(ahora.minusMinutes(30).withSecond(0).withNano(0))
            .horaFin(ahora.plusMinutes(60).withSecond(0).withNano(0))
            .toleranciaMin((short) 15).activo(true)
            .build());

        conConsentimientoVigente(rol);

        // El equipo autorizado, con el kiosco encendido: el pase exige lo primero y la pantalla
        // desatendida, las dos cosas.
        tokenDelPuesto = puestoService.designar(tenantId, "Secretaría", cuenta).getTokenEnClaro();
        PuestoCaptura p = puestoRepository.deInstitucion(tenantId).get(0);
        p.setKioscoHabilitado(true);
        p.setKioscoHabilitadoEn(LocalDateTime.now());
        p.setKioscoHabilitadoPor(cuenta);
        puestoRepository.save(p);

        when(identificacionService.identificar(any())).thenReturn(
            IdentificacionResultadoDto.match(docente.getId(), "Pérez, Ana", "Pérez",
                                             null, 40.0, 10, 20, 100, 100));
    }

    @AfterEach
    void limpiar() {
        if (tenantId != null) {
            borrar(asistenciaRepository, a -> tenantId.equals(a.getInstitucionId()));
            borrar(bloqueRepository, b -> tenantId.equals(b.getInstitucionId()));
            borrar(consentimientoRepository,
                   c -> docente != null && docente.getId().equals(c.getDocente().getId()));
            borrar(puestoRepository, p -> tenantId.equals(p.getInstitucionId()));
            List<Long> comisionIds = comisionRepository.findAllDelTenant(tenantId).stream()
                .map(Comision::getId).toList();
            borrar(horarioRepository, h -> comisionIds.contains(h.getComision().getId()));
            borrar(comisionRepository, c -> comisionIds.contains(c.getId()));
            borrar(periodoRepository, p -> tenantId.equals(p.getInstitucionId()));
            borrar(cicloRepository, c -> tenantId.equals(c.getInstitucionId()));
            borrar(materiaRepository, m -> tenantId.equals(m.getInstitucionId()));
            borrar(carreraRepository, c -> tenantId.equals(c.getInstitucionId()));
            borrar(docenteRepository, d -> tenantId.equals(d.getInstitucionId()));
            borrar(usuarioRepository, u -> tenantId.equals(u.getInstitucionId()));
        }
        TenantContext.clear();
    }

    // ========================================================================
    //  Modo normal: el pase, con un administrador presente
    // ========================================================================

    @Test
    @DisplayName("El pase registra la entrada y la deja escrita con su metodo y su equipo")
    void elPaseRegistraLaEntrada() throws Exception {
        String json = mockMvc.perform(post("/asistencia/pase/marcar")
                .with(user(new UsuarioAutenticado(cuenta))).with(csrf())
                .cookie(new Cookie(CookiePuesto.NOMBRE, tokenDelPuesto))
                .contentType(MediaType.APPLICATION_JSON).content(IMAGEN))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(json)
            .as("la pantalla necesita saber que se marco, y de que clase")
            .contains("\"asistenciaMarcada\":true")
            .contains("\"tipoDeMarca\":\"ENTRADA\"")
            .contains("Programación I");

        List<BloquePresencia> bloques = bloquesDelTenant();
        assertThat(bloques).hasSize(1);
        assertThat(bloques.get(0).getEstadoCierre()).isEqualTo(EstadoCierre.ABIERTO);
        assertThat(bloques.get(0).getPuesto())
            .as("de que equipo salio la jornada (RF-89)")
            .isNotNull();

        List<Asistencia> asistencias = asistenciaRepository.findDelDia(tenantId, LocalDate.now());
        assertThat(asistencias).hasSize(1);
        assertThat(asistencias.get(0).getMetodo()).isEqualTo(MetodoAsistencia.AUTOMATICO);
        assertThat(asistencias.get(0).getPuesto()).isNotNull();
    }

    @Test
    @DisplayName("Sin el equipo autorizado el pase no marca nada, aunque haya sesion")
    void sinEquipoAutorizadoNoMarca() throws Exception {
        mockMvc.perform(post("/asistencia/pase/marcar")
                .with(user(new UsuarioAutenticado(cuenta))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(IMAGEN))
            .andExpect(status().isForbidden());

        assertThat(bloquesDelTenant())
            .as("con la sesion sola se le podria marcar la entrada a alguien que no esta")
            .isEmpty();
    }

    // ========================================================================
    //  Modo kiosco: sin ninguna sesion
    // ========================================================================

    @Test
    @DisplayName("El kiosco registra sin sesion, resolviendo la institucion desde el equipo")
    void elKioscoRegistraSinSesion() throws Exception {
        // Sin esto el test se engaña solo: MockMvc corre en el hilo del test, que sembró los
        // datos con la institución puesta en el TenantContext, y el pedido la heredaría en
        // vez de resolverla desde el equipo. Limpiarlo es lo que obliga al kiosco a hacer su
        // trabajo, que es de lo único que dispone cuando no hay ninguna sesión.
        TenantContext.clear();

        String json = mockMvc.perform(post("/kiosco/marcar").with(csrf())
                .cookie(new Cookie(CookiePuesto.NOMBRE, tokenDelPuesto))
                .contentType(MediaType.APPLICATION_JSON).content(IMAGEN))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(json)
            .contains("\"registrada\":true")
            .contains("\"tipoDeMarca\":\"ENTRADA\"")
            .as("la pantalla desatendida muestra el apellido y nada mas (RF-87)")
            .contains("Pérez")
            .doesNotContain("Ana");

        assertThat(bloquesDelTenant())
            .as("la institucion sale del equipo, que es lo unico que identifica al kiosco")
            .singleElement()
            .satisfies(b -> assertThat(b.getInstitucionId()).isEqualTo(tenantId));
        assertThat(asistenciaRepository.findDelDia(tenantId, LocalDate.now())).hasSize(1);
    }

    // ========================================================================
    //  El cierre al retirarse
    // ========================================================================

    @Test
    @DisplayName("Con la jornada abierta, la pasada siguiente la cierra e imputa la clase")
    void laPasadaSiguienteCierraLaJornada() throws Exception {
        // La entrada se siembra veinte minutos atras: la permanencia minima son diez, y la
        // hora de la marca la pone el servidor, asi que no hay forma de adelantarla desde el
        // pedido.
        LocalTime entrada = LocalTime.now().minusMinutes(20).withSecond(0).withNano(0);
        BloquePresencia abierto = BloquePresencia.builder()
            .docente(docente)
            .fecha(LocalDate.now())
            .horaEntrada(entrada)
            .origenEntrada(OrigenMarca.AUTOMATICO)
            .estadoCierre(EstadoCierre.ABIERTO)
            .build();
        abierto.setInstitucionId(tenantId);
        bloqueRepository.save(abierto);

        String json = mockMvc.perform(post("/asistencia/pase/marcar")
                .with(user(new UsuarioAutenticado(cuenta))).with(csrf())
                .cookie(new Cookie(CookiePuesto.NOMBRE, tokenDelPuesto))
                .contentType(MediaType.APPLICATION_JSON).content(IMAGEN))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(json)
            .as("la misma pasada significa salida porque hay una jornada abierta (ADR-0017)")
            .contains("\"tipoDeMarca\":\"SALIDA\"");

        BloquePresencia cerrado = bloqueRepository.findById(abierto.getId()).orElseThrow();
        assertThat(cerrado.getEstadoCierre()).isEqualTo(EstadoCierre.CERRADO_POR_ROSTRO);
        assertThat(cerrado.getHoraSalida()).isNotNull();
        assertThat(cerrado.getEstadoSalida()).isNotNull();

        assertThat(asistenciaRepository.findDelDia(tenantId, LocalDate.now()))
            .as("al cerrar se imputa la clase que el docente cubrio")
            .hasSize(1);
    }


    @Test
    @DisplayName("Entra por una puerta y sale por la otra: cada marca deja su equipo")
    void laSalidaPorOtraPuertaQuedaAsentada() throws Exception {
        // Es el caso que pidio abrir el tope de equipos: con una camara por entrada, el
        // docente sale por donde le queda mas cerca. Sin el equipo de salida, la jornada
        // afirmaria que salio por donde entro, que es un dato falso y no uno incompleto.
        PuestoCaptura norte = puestoRepository.deInstitucion(tenantId).get(0);
        String tokenSur = puestoService.designar(tenantId, "Entrada sur", cuenta).getTokenEnClaro();
        PuestoCaptura sur = puestoRepository.deInstitucion(tenantId).stream()
            .filter(p -> "Entrada sur".equals(p.getNombre()))
            .findFirst().orElseThrow();

        BloquePresencia abierto = BloquePresencia.builder()
            .docente(docente)
            .fecha(LocalDate.now())
            .horaEntrada(LocalTime.now().minusMinutes(20).withSecond(0).withNano(0))
            .origenEntrada(OrigenMarca.AUTOMATICO)
            .estadoCierre(EstadoCierre.ABIERTO)
            .puesto(norte)
            .build();
        abierto.setInstitucionId(tenantId);
        bloqueRepository.save(abierto);

        mockMvc.perform(post("/asistencia/pase/marcar")
                .with(user(new UsuarioAutenticado(cuenta))).with(csrf())
                .cookie(new Cookie(CookiePuesto.NOMBRE, tokenSur))
                .contentType(MediaType.APPLICATION_JSON).content(IMAGEN))
            .andExpect(status().isOk());

        BloquePresencia cerrado = bloqueRepository.findById(abierto.getId()).orElseThrow();
        assertThat(cerrado.getPuesto().getId())
            .as("la entrada sigue siendo la del equipo que abrio la jornada")
            .isEqualTo(norte.getId());
        assertThat(cerrado.getPuestoSalida().getId())
            .as("y la salida, la del equipo por el que se fue")
            .isEqualTo(sur.getId());
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    private List<BloquePresencia> bloquesDelTenant() {
        return bloqueRepository.findAll().stream()
            .filter(b -> tenantId.equals(b.getInstitucionId()))
            .toList();
    }

    private void conConsentimientoVigente(Rol rol) {
        ConsentimientoBiometrico c = ConsentimientoBiometrico.builder()
            .docente(docente)
            .versionTerminos("v1")
            .metodo(MetodoConsentimiento.DIGITAL)
            .fechaConsentimiento(LocalDate.now().minusDays(1).atStartOfDay())
            .vigente(true)
            .registradoPor(cuenta)
            .build();
        consentimientoRepository.save(c);
    }

    private <T> void borrar(org.springframework.data.jpa.repository.JpaRepository<T, ?> repo,
                            java.util.function.Predicate<T> condicion) {
        repo.deleteAll(repo.findAll().stream().filter(condicion).toList());
    }
}
