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
import org.springframework.test.context.TestPropertySource;
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
 * La pantalla de gráficos del reporte (RF-33), sobre el HTML que de verdad sale.
 *
 * <p><b>Por qué va como IT.</b> El cálculo ya lo cubre {@code GraficosDeAsistenciaServiceTest};
 * lo que acá se prueba es que llegue dibujado. Las expresiones de Thymeleaf fallan recién al
 * renderizar, y un SVG con una coordenada mal puesta no falla nunca: sale torcido y nadie se
 * entera. Por eso el test lee los atributos del SVG, no solo que la página responda 200.
 *
 * <p><b>El tope de filas se baja a dos a propósito.</b> Los gráficos tienen que ver el período
 * entero: armados sobre un reporte cortado mostrarían una curva falsa, que es peor que no
 * mostrarla. Con tres clases sembradas y el tope en dos, si los gráficos usaran el mismo
 * camino que la tabla se notaría.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.reportes.max-filas=2")
class GraficosDelReporteIT {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    // Dos lunes de semanas distintas, para que el grafico semanal tenga dos columnas.
    private static final LocalDate SEMANA_1 = LocalDate.of(2026, 6, 8);
    private static final LocalDate SEMANA_2 = LocalDate.of(2026, 6, 15);

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
    private Docente docente;
    private Materia materia;
    private CicloLectivo ciclo;

    @BeforeEach
    void sembrar() {
        Institucion inst = institucionRepository.save(Institucion.builder()
            .nombre("Instituto de los gráficos " + SECUENCIA.incrementAndGet())
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
            .username("graf." + tenantId).email("graf." + tenantId + "@test.local")
            .passwordHash("no-se-usa").activo(true).rol(rol)
            .emailVerificadoEn(LocalDateTime.now())
            .build();
        u.setInstitucionId(tenantId);
        cuenta = usuarioRepository.save(u);

        Carrera carrera = Carrera.builder()
            .codigo("GRA-" + tenantId).nombre("Tecnicatura").duracionAnios((short) 3)
            .activo(true).build();
        carrera.setInstitucionId(tenantId);
        carrera = carreraRepository.save(carrera);

        materia = Materia.builder()
            .codigo("GRA1-" + tenantId).nombre("Programación I").carrera(carrera)
            .anio((short) 1).activo(true).build();
        materia.setInstitucionId(tenantId);
        materia = materiaRepository.save(materia);

        docente = Docente.builder()
            .persona(DatosDePrueba.personaDelTenant(tenantId, "47" + tenantId, "Ana", "Pérez"))
            .fechaAlta(LocalDate.of(2020, 1, 1)).activo(true).build();
        docente.setInstitucionId(tenantId);
        docente = docenteRepository.save(docente);

        ciclo = cicloRepository.save(DatosDePrueba.cicloAnualDelTenant(tenantId, 2026));

        // Cada clase va en su propia comision: un docente no puede tener dos marcas del
        // mismo horario y la misma fecha --lo impide un indice unico-- y dos de las tres
        // caen el mismo dia.
        Horario turnoTarde = horarioDe("A", 18, 20);
        Horario turnoNoche = horarioDe("B", 20, 22);

        // Tres clases: dos en la primera semana --una entera y una ausente-- y una en la
        // segunda, cubierta entera. Con el tope en dos, la tercera solo entra si los
        // graficos NO pasan por el camino topeado de la tabla.
        clase(turnoTarde, SEMANA_1, LocalTime.of(18, 0), LocalTime.of(20, 0),
              EstadoAsistencia.PRESENTE);
        clase(turnoNoche, SEMANA_1, null, null, EstadoAsistencia.AUSENTE);
        clase(turnoTarde, SEMANA_2, LocalTime.of(18, 0), LocalTime.of(20, 0),
              EstadoAsistencia.PRESENTE);
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
    @DisplayName("El grafico semanal sale dibujado, con una columna por semana")
    void elGraficoSemanalSaleDibujado() throws Exception {
        String html = pedir("/reportes/graficos");

        assertThat(html)
            .contains("Dictado sobre programado, semana a semana")
            .as("dos semanas, dos columnas")
            .contains("08/06").contains("15/06");

        // Dos <rect> de barra: si una coordenada quedara mal, el SVG sale igual pero torcido,
        // asi que se leen los atributos y no solo que la pagina responda.
        assertThat(contarOcurrencias(html, "class=\"grafico__barra\""))
            .isEqualTo(2);
        // Se busca el valor DENTRO de su etiqueta y no suelto: el eje del gráfico también
        // dice "50%" y "100%", así que un contains pelado pasaría aunque las columnas
        // estuvieran mal calculadas.
        assertThat(html)
            .as("la primera semana tiene una clase entera y una ausente: 120 de 240")
            .contains("grafico__etiqueta-valor\">50%<")
            .as("y la segunda, cubierta entera")
            .contains("grafico__etiqueta-valor\">100%<");

        assertThat(html)
            .as("el eje dice qué marcan las guías, en el gráfico y no en una nota al pie")
            .contains("grafico__escala-marca")
            .doesNotContain("Las dos líneas marcan");
    }

    @Test
    @DisplayName("Los graficos ven el periodo entero aunque la tabla venga cortada")
    void losGraficosNoUsanElTope() throws Exception {
        // El tope esta en dos y hay tres clases: la tabla avisa que corto.
        assertThat(pedir("/reportes"))
            .as("la tabla sigue topeada y avisando")
            .contains("de 3 · acotá el rango o los filtros");

        assertThat(pedir("/reportes/graficos"))
            .as("los graficos, en cambio, cuentan las tres: una curva armada sobre un "
                + "periodo cortado es peor que no mostrarla")
            .contains("· 3 clase(s)")
            .contains("15/06");
    }

    @Test
    @DisplayName("La barra de estados y el ranking salen con sus numeros")
    void elRepartoYElRanking() throws Exception {
        String html = pedir("/reportes/graficos");

        assertThat(html)
            .contains("Presentes, tarde y ausentes")
            .as("dos presentes y una ausente de tres clases")
            .contains("grafico__tramo--ok")
            .contains("grafico__tramo--ausente")
            .as("sin ninguna clase tarde, ese tramo no se dibuja")
            .doesNotContain("grafico__tramo--tarde");

        assertThat(html)
            .contains("Dónde se concentran las ausencias")
            .contains("Pérez, Ana")
            .as("una ausencia sobre las tres clases del docente")
            .contains("1 de 3");
    }

    @Test
    @DisplayName("Sin datos, la pantalla lo dice en palabras y no dibuja un eje vacio")
    void sinDatosNoDibujaNada() throws Exception {
        String html = pedir("/reportes/graficos?desde=2026-01-01&hasta=2026-01-31");

        assertThat(html)
            .contains("No hay asistencias registradas con los filtros elegidos")
            .doesNotContain("grafico__barra");
    }

    @Test
    @DisplayName("Con el rango al reves, explica el problema en vez de reventar")
    void rangoInvertido() throws Exception {
        assertThat(pedir("/reportes/graficos?desde=2026-12-01&hasta=2026-01-31"))
            .contains("La fecha &#39;desde&#39; no puede ser posterior a &#39;hasta&#39;.")
            .doesNotContain("grafico__barra");
    }

    @Test
    @DisplayName("La pantalla dice de que periodo es y manda a la tabla a cambiarlo")
    void diceSuPeriodoYEnlazaConLaTabla() throws Exception {
        String html = pedir("/reportes/graficos?desde=2026-06-01&hasta=2026-06-30");

        assertThat(html)
            .as("un gráfico sin su período es un gráfico del que no se sabe nada")
            .contains("01/06/2026").contains("30/06/2026")
            .contains("Cambiar el período en la tabla")
            .contains("desde=2026-06-01")
            .contains("hasta=2026-06-30");

        assertThat(html)
            .as("los criterios se eligen una sola vez, en la tabla: dos formularios para el "
                + "mismo filtro obligan a elegir dos veces lo mismo")
            .doesNotContain("data-texto-enviando=\"Aplicando...\"")
            .doesNotContain("name=\"docenteId\"");
    }

    @Test
    @DisplayName("La barra lateral tiene su enlace a los graficos")
    void estaEnLaBarraLateral() throws Exception {
        assertThat(pedir("/reportes/graficos"))
            .as("la barra está escrita a mano en el layout, así que una pantalla nueva no "
                + "aparece sola por estar dada de alta en SeccionService")
            .contains("/reportes/graficos")
            .contains("Gráficos");
    }

    @Test
    @DisplayName("Los filtros que trae el enlace de la tabla se aplican de verdad")
    void losFiltrosDelEnlaceSeAplican() throws Exception {
        assertThat(pedir("/reportes/graficos?desde=2026-06-01&hasta=2026-06-30"))
            .as("sin filtrar, las tres clases")
            .contains("· 3 clase(s)");

        assertThat(pedir("/reportes/graficos?desde=2026-06-01&hasta=2026-06-30&estado=AUSENTE"))
            .as("filtrando por ausente, una sola")
            .contains("· 1 clase(s)");

        assertThat(pedir("/reportes/graficos?desde=2026-06-15&hasta=2026-06-30"))
            .as("acotando el rango, solo la segunda semana")
            .contains("· 1 clase(s)")
            .doesNotContain("08/06");
    }

    // ------------------------------------------------------------------------

    private String pedir(String ruta) throws Exception {
        String base = ruta.contains("?") ? ruta : ruta + "?desde=2026-06-01&hasta=2026-06-30";
        return mockMvc.perform(get(base).with(user(new UsuarioAutenticado(cuenta))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private static int contarOcurrencias(String texto, String aguja) {
        int total = 0;
        int desde = texto.indexOf(aguja);
        while (desde >= 0) {
            total++;
            desde = texto.indexOf(aguja, desde + aguja.length());
        }
        return total;
    }

    // Una comision con su horario semanal, para poder tener dos clases el mismo dia.
    private Horario horarioDe(String codigoComision, int desde, int hasta) {
        Comision c = comisionRepository.save(Comision.builder()
            .materia(materia).codigo(codigoComision).docenteAsignado(docente).activo(true)
            .periodo(ciclo.getPeriodos().get(0))
            .build());
        return horarioRepository.save(Horario.builder()
            .comision(c)
            .diaSemana((byte) SEMANA_1.getDayOfWeek().getValue())
            .horaInicio(LocalTime.of(desde, 0)).horaFin(LocalTime.of(hasta, 0))
            .toleranciaMin((short) 15).activo(true)
            .build());
    }

    // Una clase del docente. Sin horas, queda ausente y sin jornada.
    private void clase(Horario horario, LocalDate fecha, LocalTime entrada, LocalTime salida,
                       EstadoAsistencia estado) {
        BloquePresencia bloque = null;
        if (entrada != null) {
            bloque = BloquePresencia.builder()
                .docente(docente).fecha(fecha)
                .horaEntrada(entrada).horaSalida(salida)
                .origenEntrada(OrigenMarca.AUTOMATICO).origenSalida(OrigenMarca.AUTOMATICO)
                .estadoCierre(EstadoCierre.CERRADO_POR_ROSTRO)
                .estadoSalida(EstadoSalida.EN_HORA)
                .build();
            bloque.setInstitucionId(tenantId);
            bloque = bloqueRepository.save(bloque);
        }

        Asistencia a = Asistencia.builder()
            .docente(docente).comision(horario.getComision()).horario(horario).bloque(bloque)
            .fecha(fecha).horaRegistrada(entrada != null ? entrada : horario.getHoraFin())
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
