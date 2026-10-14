package edu.cent35.asistencias.integracion;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import edu.cent35.asistencias.EscenarioDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.academico.Carrera;
import edu.cent35.asistencias.model.academico.CicloLectivo;
import edu.cent35.asistencias.model.academico.Comision;
import edu.cent35.asistencias.model.academico.Horario;
import edu.cent35.asistencias.model.academico.Materia;
import edu.cent35.asistencias.model.asistencia.Asistencia;
import edu.cent35.asistencias.model.asistencia.BloquePresencia;
import edu.cent35.asistencias.model.asistencia.EstadoAsistencia;
import edu.cent35.asistencias.model.asistencia.EstadoCierre;
import edu.cent35.asistencias.model.asistencia.EstadoSalida;
import edu.cent35.asistencias.model.asistencia.MetodoAsistencia;
import edu.cent35.asistencias.model.asistencia.OrigenMarca;
import edu.cent35.asistencias.model.biometria.PuestoCaptura;
import edu.cent35.asistencias.model.institucion.Usuario;
import edu.cent35.asistencias.model.persona.Docente;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.BloquePresenciaRepository;
import edu.cent35.asistencias.repository.PuestoCapturaRepository;
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
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El tiempo neto de cada clase, en las tres salidas del reporte: la pantalla, el CSV y el PDF.
 *
 * <p><b>Por qué va como IT.</b> El cálculo ya está cubierto por sus tests unitarios; lo que
 * acá se prueba es que llegue a destino. Las expresiones de Thymeleaf fallan recién al
 * renderizar, y el orden de las columnas del CSV solo se ve cuando el archivo sale escrito.
 *
 * <p>Los datos son dos clases del mismo docente: una cubierta entera —con el docente en la
 * institución un rato antes y otro después— y otra a la que llegó tarde y de la que se fue
 * antes, las dos veces pasándose de la tolerancia.
 *
 * <p><b>El desvío desarmado quedó sólo en el CSV.</b> En pantalla y en el PDF ocupaba una
 * columna entera para decir en palabras lo que "Dictado" ya dice en números. En una planilla
 * sí sirve, porque ahí se filtra y se suma por esas cifras.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReporteTiempoNetoIT {

    // Dos lunes de junio de 2026: el horario de prueba es de lunes.
    private static final LocalDate LUNES_CUBIERTO = LocalDate.of(2026, 6, 15);
    private static final LocalDate LUNES_CON_DESVIO = LocalDate.of(2026, 6, 22);

    @Autowired private MockMvc mockMvc;
    @Autowired private EscenarioDePrueba escenario;
    @Autowired private AsistenciaRepository asistenciaRepository;
    @Autowired private BloquePresenciaRepository bloqueRepository;
    @Autowired private PuestoCapturaRepository puestoRepository;
    @Autowired private PuestoCapturaService puestoService;

    private Long tenantId;
    private Docente docente;
    private Usuario cuenta;

    @BeforeEach
    void sembrar() {
        tenantId = escenario.institucion("Instituto del reporte");
        TenantContext.set(tenantId);

        cuenta = escenario.cuenta(tenantId, "INSTITUCION", "reporte." + tenantId);
        Carrera carrera = escenario.carrera(tenantId, "REP-" + tenantId);
        Materia materia = escenario.materia(carrera, "REP1-" + tenantId, "Programación I");
        docente = escenario.docente(tenantId, "43" + tenantId);
        CicloLectivo ciclo = escenario.ciclo(tenantId, 2026);
        Comision comision = escenario.comision(materia, ciclo, docente, "A");

        Horario horario = escenario.horario(comision, LUNES_CUBIERTO.getDayOfWeek(),
            LocalTime.of(18, 0), LocalTime.of(20, 0));

        // Dos equipos: el docente entra por una puerta y sale por la otra (V031).
        puestoService.designar(tenantId, "Entrada norte", cuenta);
        puestoService.designar(tenantId, "Entrada sur", cuenta);
        PuestoCaptura norte = puestoPorNombre("Entrada norte");
        PuestoCaptura sur = puestoPorNombre("Entrada sur");

        // Clase cubierta entera: llegó diez minutos antes y se fue doce después.
        clase(horario, comision, LUNES_CUBIERTO,
              LocalTime.of(17, 50), LocalTime.of(20, 12), EstadoAsistencia.PRESENTE, norte, sur);
        // Llegó 25 minutos tarde y se fue 20 antes: las dos cosas fuera de la tolerancia.
        clase(horario, comision, LUNES_CON_DESVIO,
              LocalTime.of(18, 25), LocalTime.of(19, 40), EstadoAsistencia.TARDE, null, null);
    }

    @AfterEach
    void limpiar() {
        escenario.borrarTodoDe(tenantId);
        TenantContext.clear();
    }

    @Test
    @DisplayName("La pantalla muestra lo dictado de cada clase y por que puerta paso")
    void laPantallaMuestraLoDictado() throws Exception {
        String html = pedir("/reportes");

        assertThat(html)
            .as("lo dictado sobre lo programado, que es la cifra del reporte")
            .contains("120 / 120 min")
            .contains("75 / 120 min");

        assertThat(html)
            .as("el equipo tiene columna propia: con una camara por entrada, la hora sola "
                + "no dice donde estuvo la persona")
            .contains("Entrada norte › Entrada sur");

        assertThat(html)
            .as("el desvio desarmado y los totales se fueron de la pantalla: uno ocupaba "
                + "una columna para repetir en palabras lo que Dictado dice en numeros")
            .doesNotContain("Desvío")
            .doesNotContain("min tarde")
            .doesNotContain("Totales del período");
    }

    @Test
    @DisplayName("El CSV trae el desvio desarmado en columnas, para poder sumarlo")
    void elCsvTraeLasColumnas() throws Exception {
        String csv = pedir("/reportes/csv");

        assertThat(csv)
            .contains("minutos_tarde;minutos_salida_anticipada;minutos_fuera_de_clase;"
                      + "llegada_en_margen;salida_en_margen")
            .contains("salida_presumida;equipo_entrada;equipo_salida;")
            .as("en columnas separadas: en una planilla se filtra por puerta")
            .contains(";NO;Entrada norte;Entrada sur;");
        assertThat(csv)
            .as("la clase cubierta entera: sin desvio, con 22 minutos fuera de la franja")
            .contains(";120;120;0;0;22;SI;SI;")
            .as("la otra: 25 tarde y 20 antes, las dos fuera del margen")
            .contains(";120;75;25;20;0;NO;NO;");
    }

    @Test
    @DisplayName("El PDF sale con lo dictado, los equipos y la referencia al pie")
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

        String hoja = textoDe(pdf);
        assertThat(hoja)
            .contains("Dictado").contains("Equipo")
            .as("por que puerta entro y por cual salio, en la hoja que se archiva")
            .contains("Entrada norte › Entrada sur")
            .as("al pie queda una sola aclaracion, la de Dictado")
            .contains("Dictado: minutos de la clase")
            .doesNotContain("Cómo leer esta tabla")
            .as("el desvio y el resumen del periodo se fueron tambien del papel")
            .doesNotContain("Desvío")
            .doesNotContain("Programado");
    }

    // El texto de la primera hoja del PDF, con los saltos normalizados: una celda angosta
    // parte su contenido en dos renglones y eso es correcto en la hoja impresa.
    private String textoDe(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(reader).getTextFromPage(1).replaceAll("\\s+", " ");
        } finally {
            reader.close();
        }
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
                       LocalTime entrada, LocalTime salida, EstadoAsistencia estado,
                       PuestoCaptura puestoEntrada, PuestoCaptura puestoSalida) {
        BloquePresencia bloque = BloquePresencia.builder()
            .docente(docente).fecha(fecha)
            .horaEntrada(entrada).horaSalida(salida)
            .origenEntrada(OrigenMarca.AUTOMATICO).origenSalida(OrigenMarca.AUTOMATICO)
            .estadoCierre(EstadoCierre.CERRADO_POR_ROSTRO)
            .estadoSalida(EstadoSalida.EN_HORA)
            .puesto(puestoEntrada).puestoSalida(puestoSalida)
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

    private PuestoCaptura puestoPorNombre(String nombre) {
        return puestoRepository.deInstitucion(tenantId).stream()
            .filter(p -> nombre.equals(p.getNombre()))
            .findFirst().orElseThrow();
    }
}
