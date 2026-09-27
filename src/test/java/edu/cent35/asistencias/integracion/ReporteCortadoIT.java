package edu.cent35.asistencias.integracion;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoAsistencia;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.MetodoAsistencia;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.AsistenciaRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
 * Un reporte cortado por el tope no puede leerse como el período entero.
 *
 * <p><b>Qué problema cubre.</b> El reporte devuelve hasta {@code app.reportes.max-filas} y
 * descarta el resto. La pantalla avisaba; las descargas no. Alguien pedía un semestre sin
 * filtros, se llevaba el PDF con las primeras 2000 filas, lo imprimía y lo firmaba: la hoja
 * no decía en ninguna parte que faltaba la mitad. El CSV era peor todavía, porque se abre
 * justamente para sumar.
 *
 * <p>Los totales tenían el mismo problema y son más fáciles de leer mal: se calculan sobre
 * las filas devueltas, así que con el reporte cortado "Totales del período" no eran los del
 * período.
 *
 * <p>El tope se baja a dos filas para que tres asistencias alcancen. Con el tope real harían
 * falta 2001, que es un test que nadie va a querer esperar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.reportes.max-filas=2")
class ReporteCortadoIT {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    // Tres lunes de junio de 2026: el horario de prueba es de lunes.
    private static final List<LocalDate> LUNES = List.of(
        LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 22));

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
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Long tenantId;
    private Usuario cuenta;

    @BeforeEach
    void sembrar() {
        Institucion inst = institucionRepository.save(Institucion.builder()
            .nombre("Instituto del tope " + SECUENCIA.incrementAndGet())
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
            .username("tope." + tenantId).email("tope." + tenantId + "@test.local")
            .passwordHash("no-se-usa").activo(true).rol(rol)
            .emailVerificadoEn(LocalDateTime.now())
            .build();
        u.setInstitucionId(tenantId);
        cuenta = usuarioRepository.save(u);

        Carrera carrera = Carrera.builder()
            .codigo("TOP-" + tenantId).nombre("Tecnicatura").duracionAnios((short) 3)
            .activo(true).build();
        carrera.setInstitucionId(tenantId);
        carrera = carreraRepository.save(carrera);

        Materia materia = Materia.builder()
            .codigo("TOP1-" + tenantId).nombre("Programación I").carrera(carrera)
            .anio((short) 1).activo(true).build();
        materia.setInstitucionId(tenantId);
        materia = materiaRepository.save(materia);

        Docente docente = Docente.builder()
            .persona(DatosDePrueba.personaDelTenant(tenantId, "46" + tenantId, "Ana", "Pérez"))
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
            .diaSemana((byte) LUNES.get(0).getDayOfWeek().getValue())
            .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(20, 0))
            .toleranciaMin((short) 15).activo(true)
            .build());

        // Tres clases del mismo horario en tres lunes: el tope de dos deja una afuera.
        for (LocalDate fecha : LUNES) {
            Asistencia a = Asistencia.builder()
                .docente(docente).comision(comision).horario(horario)
                .fecha(fecha).horaRegistrada(LocalTime.of(18, 0))
                .estado(EstadoAsistencia.PRESENTE).metodo(MetodoAsistencia.AUTOMATICO)
                .build();
            a.setInstitucionId(tenantId);
            asistenciaRepository.save(a);
        }
    }

    @AfterEach
    void limpiar() {
        if (tenantId != null) {
            borrar(asistenciaRepository, a -> tenantId.equals(a.getInstitucionId()));
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
    @DisplayName("El CSV cortado lo dice en el nombre del archivo y en su ultima fila")
    void elCsvCortadoLoDice() throws Exception {
        MvcResult r = pedir("/reportes/csv", "2026-06-01", "2026-06-30");
        String csv = r.getResponse().getContentAsString();

        assertThat(r.getResponse().getHeader("Content-Disposition"))
            .as("el nombre es lo unico del aviso que sobrevive a guardar y reenviar el archivo")
            .contains("_parcial.csv");
        assertThat(csv)
            .as("al final y en una sola celda: arriba correria las columnas, y el que abre "
                + "el archivo para ver si esta completo va justo al final")
            .contains("Reporte incompleto: se exportaron las primeras 2 filas de 3.");
        assertThat(csv.strip().lines().count())
            .as("encabezado + las dos filas del tope + el aviso")
            .isEqualTo(4);
    }

    @Test
    @DisplayName("El PDF cortado lo dice arriba, al pie de cada hoja y en el nombre")
    void elPdfCortadoLoDice() throws Exception {
        MvcResult r = pedir("/reportes/pdf", "2026-06-01", "2026-06-30");

        assertThat(r.getResponse().getHeader("Content-Disposition"))
            .contains("_parcial.pdf");

        String hoja = textoDe(r.getResponse().getContentAsByteArray());
        assertThat(hoja)
            .contains("REPORTE INCOMPLETO")
            .contains("se listan 2 de 3 registros")
            .as("el recuento del encabezado deja de decir que son todos")
            .contains("2 de 3 registros");
    }

    @Test
    @DisplayName("La pantalla avisa en el recuento que el reporte vino cortado")
    void laPantallaAvisaDelCorte() throws Exception {
        String html = pedir("/reportes", "2026-06-01", "2026-06-30")
            .getResponse().getContentAsString();

        assertThat(html)
            .as("un reporte cortado en silencio se lee como uno completo")
            .contains("de 3 · acotá el rango o los filtros");
    }

    @Test
    @DisplayName("Un reporte que entra entero no lleva ningun aviso")
    void elReporteEnteroNoAvisa() throws Exception {
        // Un solo lunes: dos filas de tope y una sola que traer.
        MvcResult csv = pedir("/reportes/csv", "2026-06-15", "2026-06-15");
        assertThat(csv.getResponse().getHeader("Content-Disposition"))
            .doesNotContain("_parcial");
        assertThat(csv.getResponse().getContentAsString())
            .doesNotContain("Reporte incompleto");

        MvcResult pdf = pedir("/reportes/pdf", "2026-06-15", "2026-06-15");
        assertThat(pdf.getResponse().getHeader("Content-Disposition"))
            .doesNotContain("_parcial");
        assertThat(textoDe(pdf.getResponse().getContentAsByteArray()))
            .doesNotContain("INCOMPLETO");

        assertThat(pedir("/reportes", "2026-06-15", "2026-06-15")
                       .getResponse().getContentAsString())
            .as("sin corte, la pantalla no avisa nada")
            .doesNotContain("acotá el rango o los filtros");
    }

    // ------------------------------------------------------------------------

    private MvcResult pedir(String ruta, String desde, String hasta) throws Exception {
        return mockMvc.perform(get(ruta)
                .param("desde", desde).param("hasta", hasta)
                .with(user(new UsuarioAutenticado(cuenta))))
            .andExpect(status().isOk())
            .andReturn();
    }

    private String textoDe(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(reader).getTextFromPage(1).replaceAll("\\s+", " ");
        } finally {
            reader.close();
        }
    }

    private <T> void borrar(org.springframework.data.jpa.repository.JpaRepository<T, ?> repo,
                            java.util.function.Predicate<T> condicion) {
        repo.deleteAll(repo.findAll().stream().filter(condicion).toList());
    }
}
