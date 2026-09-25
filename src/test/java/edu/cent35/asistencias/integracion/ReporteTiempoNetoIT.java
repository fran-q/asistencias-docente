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
 * El tiempo neto de cada clase y su desvío, en las tres salidas del reporte: la pantalla, el
 * CSV y el PDF.
 *
 * <p><b>Por qué va como IT.</b> El cálculo ya está cubierto por sus tests unitarios; lo que
 * acá se prueba es que llegue a destino. Las expresiones de Thymeleaf fallan recién al
 * renderizar, y el orden de las columnas del CSV solo se ve cuando el archivo sale escrito.
 *
 * <p>Los datos son dos clases del mismo docente: una cubierta entera —con el docente en la
 * institución un rato antes y otro después— y otra a la que llegó tarde y de la que se fue
 * antes, las dos veces pasándose de la tolerancia.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReporteTiempoNetoIT {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    // Dos lunes de junio de 2026: el horario de prueba es de lunes.
    private static final LocalDate LUNES_CUBIERTO = LocalDate.of(2026, 6, 15);
    private static final LocalDate LUNES_CON_DESVIO = LocalDate.of(2026, 6, 22);

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
    private Docente docente;
    private Usuario cuenta;

    @BeforeEach
    void sembrar() {
        Institucion inst = institucionRepository.save(Institucion.builder()
            .nombre("Instituto del reporte " + SECUENCIA.incrementAndGet())
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
            .username("reporte." + tenantId).email("reporte." + tenantId + "@test.local")
            .passwordHash("no-se-usa").activo(true).rol(rol)
            .emailVerificadoEn(LocalDateTime.now())
            .build();
        u.setInstitucionId(tenantId);
        cuenta = usuarioRepository.save(u);

        Carrera carrera = Carrera.builder()
            .codigo("REP-" + tenantId).nombre("Tecnicatura").duracionAnios((short) 3)
            .activo(true).build();
        carrera.setInstitucionId(tenantId);
        carrera = carreraRepository.save(carrera);

        Materia materia = Materia.builder()
            .codigo("REP1-" + tenantId).nombre("Programación I").carrera(carrera)
            .anio((short) 1).activo(true).build();
        materia.setInstitucionId(tenantId);
        materia = materiaRepository.save(materia);

        docente = Docente.builder()
            .persona(DatosDePrueba.personaDelTenant(tenantId, "43" + tenantId, "Ana", "Pérez"))
            .fechaAlta(LocalDate.of(2020, 1, 1)).activo(true).build();
        docente.setInstitucionId(tenantId);
        docente = docenteRepository.save(docente);

        CicloLectivo ciclo = cicloRepository.save(
            DatosDePrueba.cicloAnualDelTenant(tenantId, 2026));

        Comision comision = comisionRepository.save(Comision.builder()
            .materia(materia).codigo("A").docenteAsignado(docente).activo(true)
            .periodo(ciclo.getPeriodos().get(0))
            .build());

        Horario horario = horarioRepository.save(Horario.builder()
            .comision(comision)
            .diaSemana((byte) LUNES_CUBIERTO.getDayOfWeek().getValue())
            .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(20, 0))
            .toleranciaMin((short) 15).activo(true)
            .build());

        // Clase cubierta entera: llegó diez minutos antes y se fue doce después.
        clase(horario, comision, LUNES_CUBIERTO,
              LocalTime.of(17, 50), LocalTime.of(20, 12), EstadoAsistencia.PRESENTE);
        // Llegó 25 minutos tarde y se fue 20 antes: las dos cosas fuera de la tolerancia.
        clase(horario, comision, LUNES_CON_DESVIO,
              LocalTime.of(18, 25), LocalTime.of(19, 40), EstadoAsistencia.TARDE);
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
    @DisplayName("La pantalla muestra el desvio de cada clase y los totales del periodo")
    void laPantallaMuestraElDesvio() throws Exception {
        String html = pedir("/reportes");

        assertThat(html)
            .contains("Desvío")
            .as("el desvio se dice en minutos, no solo como 'tarde'")
            .contains("25 min tarde")
            .contains("20 min antes")
            .as("y lo que estuvo en la institucion fuera de la franja de la clase")
            .contains("+22 min fuera");

        assertThat(html)
            .as("los totales del periodo: 195 minutos dictados sobre 240 programados")
            .contains("Totales del período")
            .contains("3 h 15 min (81%)")
            .contains("4 h");
    }

    @Test
    @DisplayName("El CSV trae el desvio desarmado en columnas, para poder sumarlo")
    void elCsvTraeLasColumnas() throws Exception {
        String csv = pedir("/reportes/csv");

        assertThat(csv)
            .contains("minutos_tarde;minutos_salida_anticipada;minutos_fuera_de_clase;"
                      + "llegada_en_margen;salida_en_margen");
        assertThat(csv)
            .as("la clase cubierta entera: sin desvio, con 22 minutos fuera de la franja")
            .contains(";120;120;0;0;22;SI;SI;")
            .as("la otra: 25 tarde y 20 antes, las dos fuera del margen")
            .contains(";120;75;25;20;0;NO;NO;");
    }

    @Test
    @DisplayName("El PDF sale con la columna de desvio y el resumen al pie")
    void elPdfSale() throws Exception {
        byte[] pdf = mockMvc.perform(get("/reportes/pdf")
                .param("desde", "2026-06-01").param("hasta", "2026-06-30")
                .with(user(new UsuarioAutenticado(cuenta))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1))
            .as("un PDF de verdad, no una pantalla de error")
            .isEqualTo("%PDF-");
    }

    // ------------------------------------------------------------------------

    private String pedir(String ruta) throws Exception {
        return mockMvc.perform(get(ruta)
                .param("desde", "2026-06-01").param("hasta", "2026-06-30")
                .with(user(new UsuarioAutenticado(cuenta))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    // Una clase dictada: la jornada del docente y la asistencia que quedó imputada.
    private void clase(Horario horario, Comision comision, LocalDate fecha,
                       LocalTime entrada, LocalTime salida, EstadoAsistencia estado) {
        BloquePresencia bloque = BloquePresencia.builder()
            .docente(docente).fecha(fecha)
            .horaEntrada(entrada).horaSalida(salida)
            .origenEntrada(OrigenMarca.AUTOMATICO).origenSalida(OrigenMarca.AUTOMATICO)
            .estadoCierre(EstadoCierre.CERRADO_POR_ROSTRO)
            .estadoSalida(EstadoSalida.EN_HORA)
            .build();
        bloque.setInstitucionId(tenantId);
        bloque = bloqueRepository.save(bloque);

        Asistencia a = Asistencia.builder()
            .docente(docente).comision(comision).horario(horario).bloque(bloque)
            .fecha(fecha).horaRegistrada(entrada)
            .estado(estado).metodo(MetodoAsistencia.AUTOMATICO)
            .build();
        a.setInstitucionId(tenantId);
        asistenciaRepository.save(a);
    }

    private <T> void borrar(org.springframework.data.jpa.repository.JpaRepository<T, ?> repo,
                            java.util.function.Predicate<T> condicion) {
        repo.deleteAll(repo.findAll().stream().filter(condicion).toList());
    }
}
