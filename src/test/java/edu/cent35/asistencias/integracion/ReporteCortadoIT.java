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
import edu.cent35.asistencias.model.asistencia.EstadoAsistencia;
import edu.cent35.asistencias.model.asistencia.MetodoAsistencia;
import edu.cent35.asistencias.model.institucion.Usuario;
import edu.cent35.asistencias.model.persona.Docente;
import edu.cent35.asistencias.repository.AsistenciaRepository;
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
import java.time.LocalTime;
import java.util.List;

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

    // Tres lunes de junio de 2026: el horario de prueba es de lunes.
    private static final List<LocalDate> LUNES = List.of(
        LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 22));

    @Autowired private MockMvc mockMvc;
    @Autowired private EscenarioDePrueba escenario;
    @Autowired private AsistenciaRepository asistenciaRepository;

    private Long tenantId;
    private Usuario cuenta;

    @BeforeEach
    void sembrar() {
        tenantId = escenario.institucion("Instituto del tope");
        TenantContext.set(tenantId);

        cuenta = escenario.cuenta(tenantId, "INSTITUCION", "tope." + tenantId);
        Carrera carrera = escenario.carrera(tenantId, "TOP-" + tenantId);
        Materia materia = escenario.materia(carrera, "TOP1-" + tenantId, "Programación I");
        Docente docente = escenario.docente(tenantId, "46" + tenantId);
        CicloLectivo ciclo = escenario.ciclo(tenantId, 2026);
        Comision comision = escenario.comision(materia, ciclo, docente, "A");

        Horario horario = escenario.horario(comision, LUNES.get(0).getDayOfWeek(),
            LocalTime.of(18, 0), LocalTime.of(20, 0));

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
        escenario.borrarTodoDe(tenantId);
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
}
