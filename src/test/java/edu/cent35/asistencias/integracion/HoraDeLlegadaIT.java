package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.BloquePresencia;
import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoAsistencia;
import edu.cent35.asistencias.model.EstadoCierre;
import edu.cent35.asistencias.model.EstadoSalida;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.MetodoAsistencia;
import edu.cent35.asistencias.model.OrigenMarca;
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
import edu.cent35.asistencias.repository.PeriodoLectivoRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bajo el título "Entrada" va una llegada, o no va nada.
 *
 * <p><b>Qué problema cubre.</b> {@code hora_registrada} guarda tres cosas distintas según cómo
 * se originó la fila: la hora en que la cámara reconoció al docente, el momento en que un
 * administrador cargó el registro a mano, y —en las ausencias que genera el job— la hora de
 * fin de la clase. Las tres se mostraban con el mismo formato en la misma columna, así que un
 * registro cargado a las 14:32 se leía como un docente que llegó a las 14:32 a una clase de
 * las 18:00, y una ausencia se leía como una llegada justo al terminar la clase.
 *
 * <p>Las tres filas que siembra este test son los tres orígenes, el mismo día y para el mismo
 * docente, y se miran en las tres superficies que muestran esa hora: el listado del día, el
 * reporte en pantalla y el PDF. El CSV no entra: su columna se llama {@code hora_registrada},
 * que es exactamente lo que trae, y es el archivo que se abre para trabajar el dato crudo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HoraDeLlegadaIT {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    // Un lunes de junio de 2026, para que el horario de lunes le corresponda.
    private static final LocalDate LUNES = LocalDate.of(2026, 6, 15);

    private static final LocalTime LLEGO         = LocalTime.of(18, 5, 23);
    private static final LocalTime LA_CARGARON   = LocalTime.of(14, 32);
    private static final LocalTime TERMINO_CLASE = LocalTime.of(20, 0);

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
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Long tenantId;
    private Usuario cuenta;

    @BeforeEach
    void sembrar() {
        Institucion inst = institucionRepository.save(Institucion.builder()
            .nombre("Instituto de la llegada " + SECUENCIA.incrementAndGet())
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
            .username("llegada." + tenantId).email("llegada." + tenantId + "@test.local")
            .passwordHash("no-se-usa").activo(true).rol(rol)
            .emailVerificadoEn(LocalDateTime.now())
            .build();
        u.setInstitucionId(tenantId);
        cuenta = usuarioRepository.save(u);

        Carrera carrera = Carrera.builder()
            .codigo("LLE-" + tenantId).nombre("Tecnicatura").duracionAnios((short) 3)
            .activo(true).build();
        carrera.setInstitucionId(tenantId);
        carrera = carreraRepository.save(carrera);

        Materia materia = Materia.builder()
            .codigo("LLE1-" + tenantId).nombre("Programación I").carrera(carrera)
            .anio((short) 1).activo(true).build();
        materia.setInstitucionId(tenantId);
        materia = materiaRepository.save(materia);

        Docente docente = Docente.builder()
            .persona(DatosDePrueba.personaDelTenant(tenantId, "45" + tenantId, "Ana", "Pérez"))
            .fechaAlta(LocalDate.of(2020, 1, 1)).activo(true).build();
        docente.setInstitucionId(tenantId);
        docente = docenteRepository.save(docente);

        CicloLectivo ciclo = cicloRepository.save(
            DatosDePrueba.cicloAnualDelTenant(tenantId, 2026));

        // Tres comisiones para poder tener tres clases el mismo dia sin pisar el UNIQUE de
        // (docente, horario, fecha), que es lo que impide dos marcas de la misma clase.
        Horario deLaCamara = clase(materia, docente, ciclo, "A", 18, 20);
        Horario cargadaAMano = clase(materia, docente, ciclo, "B", 8, 10);
        Horario ausente = clase(materia, docente, ciclo, "C", 10, 12);

        // 1. La cámara lo reconoció: hay jornada y la hora es una llegada de verdad.
        BloquePresencia bloque = BloquePresencia.builder()
            .docente(docente).fecha(LUNES)
            .horaEntrada(LLEGO).horaSalida(TERMINO_CLASE)
            .origenEntrada(OrigenMarca.AUTOMATICO).origenSalida(OrigenMarca.AUTOMATICO)
            .estadoCierre(EstadoCierre.CERRADO_POR_ROSTRO)
            .estadoSalida(EstadoSalida.EN_HORA)
            .build();
        bloque.setInstitucionId(tenantId);
        bloque = bloqueRepository.save(bloque);
        asistencia(docente, deLaCamara, LLEGO, EstadoAsistencia.PRESENTE,
                   MetodoAsistencia.AUTOMATICO, bloque);

        // 2. La cargó un admin a las 14:32, para una clase de la mañana. Sin jornada: nadie
        //    observó una llegada, lo que se asentó es la declaración del administrador.
        asistencia(docente, cargadaAMano, LA_CARGARON, EstadoAsistencia.PRESENTE,
                   MetodoAsistencia.MANUAL, null);

        // 3. La generó el job al terminar la clase, con la hora de fin como marca de tiempo.
        asistencia(docente, ausente, LocalTime.of(12, 0), EstadoAsistencia.AUSENTE,
                   MetodoAsistencia.AUTOMATICO, null);
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
            borrar(usuarioRepository, u -> tenantId.equals(u.getInstitucionId()));
        }
        TenantContext.clear();
    }

    @Test
    @DisplayName("El reporte muestra la llegada observada y deja el resto en guion")
    void elReporteNoInventaLlegadas() throws Exception {
        String html = pedir("/reportes?desde=2026-06-01&hasta=2026-06-30");

        assertThat(html)
            .as("la que sí observó la cámara sale con su hora")
            .contains("18:05:23")
            .as("la carga manual dice cuándo se asentó, no que alguien llegó a esa hora")
            .contains("cargada 14:32")
            .doesNotContain("14:32:00")
            .as("la ausencia que generó el job no tiene llegada: la hora de fin de la clase "
                + "no es alguien entrando")
            .doesNotContain("12:00:00");
    }

    @Test
    @DisplayName("El listado del dia aplica el mismo criterio que el reporte")
    void elListadoTampoco() throws Exception {
        String html = pedir("/asistencias?fecha=2026-06-15");

        assertThat(html)
            .contains("18:05:23")
            .contains("cargada 14:32")
            .doesNotContain("14:32:00")
            .doesNotContain("12:00:00");
    }

    @Test
    @DisplayName("En el PDF la columna Entra queda vacia si no hubo llegada")
    void elPdfTampoco() throws Exception {
        byte[] pdf = mockMvc.perform(get("/reportes/pdf")
                .param("desde", "2026-06-01").param("hasta", "2026-06-30")
                .with(user(new UsuarioAutenticado(cuenta))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();

        String hoja = textoDe(pdf);
        assertThat(hoja)
            .as("la llegada observada, en HH:mm como el resto de las horas del PDF")
            .contains("18:05")
            .as("ni la hora del asiento ni la de fin de la clase se imprimen como entrada")
            .doesNotContain("14:32");
    }

    // ------------------------------------------------------------------------

    private String pedir(String ruta) throws Exception {
        return mockMvc.perform(get(ruta).with(user(new UsuarioAutenticado(cuenta))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private String textoDe(byte[] pdf) throws Exception {
        com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdf);
        try {
            return new com.lowagie.text.pdf.parser.PdfTextExtractor(reader)
                .getTextFromPage(1).replaceAll("\\s+", " ");
        } finally {
            reader.close();
        }
    }

    // Una clase del docente ese lunes, en su propia comision.
    private Horario clase(Materia materia, Docente docente, CicloLectivo ciclo,
                          String codigoComision, int desde, int hasta) {
        Comision comision = comisionRepository.save(Comision.builder()
            .materia(materia).codigo(codigoComision).docenteAsignado(docente).activo(true)
            .periodo(ciclo.getPeriodos().get(0))
            .build());
        return horarioRepository.save(Horario.builder()
            .comision(comision)
            .diaSemana((byte) LUNES.getDayOfWeek().getValue())
            .horaInicio(LocalTime.of(desde, 0)).horaFin(LocalTime.of(hasta, 0))
            .toleranciaMin((short) 15).activo(true)
            .build());
    }

    private void asistencia(Docente docente, Horario horario, LocalTime horaRegistrada,
                            EstadoAsistencia estado, MetodoAsistencia metodo,
                            BloquePresencia bloque) {
        Asistencia a = Asistencia.builder()
            .docente(docente).comision(horario.getComision()).horario(horario).bloque(bloque)
            .fecha(LUNES).horaRegistrada(horaRegistrada)
            .estado(estado).metodo(metodo)
            .build();
        a.setInstitucionId(tenantId);
        asistenciaRepository.save(a);
    }

    private <T> void borrar(org.springframework.data.jpa.repository.JpaRepository<T, ?> repo,
                            java.util.function.Predicate<T> condicion) {
        repo.deleteAll(repo.findAll().stream().filter(condicion).toList());
    }
}
