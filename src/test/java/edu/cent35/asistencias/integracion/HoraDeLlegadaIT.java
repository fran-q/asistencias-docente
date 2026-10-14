package edu.cent35.asistencias.integracion;

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
import edu.cent35.asistencias.model.institucion.Usuario;
import edu.cent35.asistencias.model.persona.Docente;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.BloquePresenciaRepository;
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
import java.time.LocalTime;

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

    // Un lunes de junio de 2026, para que el horario de lunes le corresponda.
    private static final LocalDate LUNES = LocalDate.of(2026, 6, 15);

    private static final LocalTime LLEGO         = LocalTime.of(18, 5, 23);
    private static final LocalTime LA_CARGARON   = LocalTime.of(14, 32);
    private static final LocalTime TERMINO_CLASE = LocalTime.of(20, 0);

    @Autowired private MockMvc mockMvc;
    @Autowired private EscenarioDePrueba escenario;
    @Autowired private AsistenciaRepository asistenciaRepository;
    @Autowired private BloquePresenciaRepository bloqueRepository;

    private Long tenantId;
    private Usuario cuenta;

    @BeforeEach
    void sembrar() {
        tenantId = escenario.institucion("Instituto de la llegada");
        TenantContext.set(tenantId);

        cuenta = escenario.cuenta(tenantId, "INSTITUCION", "llegada." + tenantId);
        Carrera carrera = escenario.carrera(tenantId, "LLE-" + tenantId);
        Materia materia = escenario.materia(carrera, "LLE1-" + tenantId, "Programación I");
        Docente docente = escenario.docente(tenantId, "45" + tenantId);
        CicloLectivo ciclo = escenario.ciclo(tenantId, 2026);

        // Tres comisiones para poder tener tres clases el mismo día sin pisar el UNIQUE de
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
        escenario.borrarTodoDe(tenantId);
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

    // Una clase del docente ese lunes, en su propia comisión.
    private Horario clase(Materia materia, Docente docente, CicloLectivo ciclo,
                          String codigoComision, int desde, int hasta) {
        Comision comision = escenario.comision(materia, ciclo, docente, codigoComision);
        return escenario.horario(comision, LUNES.getDayOfWeek(),
            LocalTime.of(desde, 0), LocalTime.of(hasta, 0));
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
}
