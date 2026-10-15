package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.EscenarioDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.academico.Carrera;
import edu.cent35.asistencias.model.academico.CicloLectivo;
import edu.cent35.asistencias.model.academico.Comision;
import edu.cent35.asistencias.model.academico.Horario;
import edu.cent35.asistencias.model.academico.Materia;
import edu.cent35.asistencias.model.asistencia.Asistencia;
import edu.cent35.asistencias.model.asistencia.AsistenciaManual;
import edu.cent35.asistencias.model.asistencia.EstadoAsistencia;
import edu.cent35.asistencias.model.asistencia.JustificacionAusencia;
import edu.cent35.asistencias.model.asistencia.MetodoAsistencia;
import edu.cent35.asistencias.model.asistencia.MotivoCargaManual;
import edu.cent35.asistencias.model.institucion.Usuario;
import edu.cent35.asistencias.model.persona.Docente;
import edu.cent35.asistencias.repository.AsistenciaManualRepository;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.JustificacionAusenciaRepository;
import edu.cent35.asistencias.repository.MotivoCargaManualRepository;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El "Ver más" del reporte: la letra chica de cada fila, que hasta acá sólo estaba en el CSV.
 *
 * <p><b>Qué cuida.</b> La columna "Justif." decía "Sí" y ahí terminaba. El motivo por el que
 * esa ausencia estaba justificada, el motivo de una carga a mano, su detalle y quién la cargó
 * viajaban en {@code motivo_justificacion}, {@code motivo_carga_manual},
 * {@code detalle_carga_manual} y {@code usuario_registro} del CSV, así que la pantalla valía
 * menos que su propia descarga y la única forma de leer eso era bajarse el archivo.
 *
 * <p>Va como IT porque lo que se comprueba es lo que sale renderizado: la fila del detalle la
 * arma la plantilla con el DTO, y una condición mal escrita compila igual.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DetalleDelReporteIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private EscenarioDePrueba escenario;
    @Autowired private AsistenciaRepository asistenciaRepository;
    @Autowired private AsistenciaManualRepository manualRepository;
    @Autowired private JustificacionAusenciaRepository justificacionRepository;
    @Autowired private MotivoCargaManualRepository motivoRepository;

    private Long tenantId;
    private Short motivoId;
    private Usuario secretaria;
    private Usuario institucion;

    @BeforeEach
    void sembrar() {
        TenantContext.clear();

        // El catálogo de motivos se siembra en V001, y el perfil test corre sobre H2 con
        // Flyway apagado: acá no existe si no se crea a mano.
        MotivoCargaManual motivo = motivoRepository.save(MotivoCargaManual.builder()
            .codigo("FALLA_CAMARA").descripcion("Falla técnica de la cámara web")
            .activo(true).build());
        motivoId = motivo.getId();

        tenantId = escenario.institucion("Instituto del detalle");
        TenantContext.set(tenantId);

        // Quien justifica y carga a mano es una cuenta y quien mira el reporte, otra. La barra
        // de arriba muestra el usuario de la sesión: si fueran la misma, "quién la cargó"
        // aparecería en la pantalla aunque el detalle no lo mostrara.
        secretaria = escenario.cuenta(tenantId, "ADMIN", "secretaria." + tenantId,
                                      DatosDePrueba.persona("Marta", "Gómez"));
        institucion = escenario.cuenta(tenantId, "INSTITUCION", "institucion." + tenantId);

        Carrera carrera = escenario.carrera(tenantId, "DET");
        Materia materia = escenario.materia(carrera, "EN13", "Práctica Profesional I");
        Docente docente = escenario.docente(tenantId, "30" + tenantId);
        CicloLectivo ciclo = escenario.ciclo(tenantId, 2026);
        Comision comision = escenario.comision(materia, ciclo, docente, "EN13-26");
        Horario horario = escenario.horario(comision, DayOfWeek.SATURDAY,
                                            LocalTime.of(9, 0), LocalTime.of(10, 20));

        // 1) Automática y sin nada atrás: no tiene que traer botón.
        guardar(docente, comision, horario, LocalDate.of(2026, 9, 5),
                EstadoAsistencia.PRESENTE, MetodoAsistencia.AUTOMATICO);

        // 2) Ausente y justificada: el motivo es lo que no se veía.
        Asistencia ausente = guardar(docente, comision, horario, LocalDate.of(2026, 9, 12),
                EstadoAsistencia.AUSENTE, MetodoAsistencia.AUTOMATICO);
        justificacionRepository.save(JustificacionAusencia.builder()
            .asistencia(ausente).usuario(secretaria)
            .motivo("Certificado médico presentado en secretaría el 14/09. "
                  + "Reposo indicado por 48 horas; el docente avisó la misma mañana.")
            .build());

        // 3) Cargada a mano: motivo del catálogo, detalle y quién la cargó.
        Asistencia aMano = guardar(docente, comision, horario, LocalDate.of(2026, 9, 19),
                EstadoAsistencia.PRESENTE, MetodoAsistencia.MANUAL);
        manualRepository.save(AsistenciaManual.builder()
            .asistencia(aMano).usuario(secretaria).motivo(motivo)
            .detalleAdicional("Se cortó la luz y el equipo quedó sin cámara toda la mañana.")
            .build());

        TenantContext.clear();
    }

    private Asistencia guardar(Docente d, Comision c, Horario h, LocalDate fecha,
                               EstadoAsistencia estado, MetodoAsistencia metodo) {
        Asistencia a = Asistencia.builder()
            .docente(d).comision(c).horario(h).fecha(fecha)
            .horaRegistrada(LocalTime.of(9, 5)).estado(estado).metodo(metodo).build();
        a.setInstitucionId(tenantId);
        return asistenciaRepository.save(a);
    }

    @AfterEach
    void limpiar() {
        escenario.borrarTodoDe(tenantId);
        // El motivo es del catálogo y no del tenant: lo armó este test y lo borra él.
        if (motivoId != null) {
            motivoRepository.deleteById(motivoId);
        }
        TenantContext.clear();
    }

    @Test
    @DisplayName("El motivo de la justificación llega a la pantalla, detrás de Ver más")
    void elMotivoDeLaJustificacionSePuedeLeer() throws Exception {
        String html = reporte();

        assertThat(html)
            .as("el motivo estaba sólo en el CSV: la pantalla valía menos que su descarga")
            .contains("Certificado médico presentado en secretaría");
        assertThat(html).contains("fila-detalle");
        assertThat(html).contains("data-fila-detalle");
    }

    @Test
    @DisplayName("De una carga a mano se lee el motivo, el detalle y quién la cargó")
    void laCargaAManoCuentaSuPorque() throws Exception {
        String html = reporte();

        assertThat(html).contains("Falla técnica de la cámara web");
        assertThat(html).contains("Se cortó la luz y el equipo quedó sin cámara");
        assertThat(html).contains(secretaria.getUsername());
    }

    @Test
    @DisplayName("El detalle arranca cerrado")
    void elDetalleArrancaCerrado() throws Exception {
        // Si naciera abierto, la tabla mostraría de entrada el doble de filas y el reporte
        // dejaría de leerse de un vistazo, que es para lo que está.
        String html = reporte();

        // Por expresión regular y no por el texto exacto de la etiqueta: el orden de los
        // atributos es cosa de la plantilla, y un test que lo fija falla cuando se agrega
        // uno al lado sin que nada del comportamiento cambie. Pasó con role="row".
        assertThat(java.util.regex.Pattern
                .compile("<tr[^>]*fila-detalle[^>]*\\bhidden\\b")
                .matcher(html).find())
            .as("el detalle tiene que nacer cerrado")
            .isTrue();
        assertThat(html).contains("aria-expanded=\"false\"");
    }

    @Test
    @DisplayName("Una fila sin nada atrás no ofrece Ver más")
    void sinLetraChicaNoHayBoton() throws Exception {
        // Tres filas sembradas y solo dos tienen algo que contar: la automática y sin
        // justificar no tiene. Un "Ver más" que abre una caja vacía promete algo y después
        // no lo tiene.
        String html = reporte();

        assertThat(contar(html, "data-fila-detalle"))
            .as("una de las tres filas no tiene letra chica")
            .isEqualTo(2);
        assertThat(contar(html, "class=\"fila-detalle\"")).isEqualTo(2);
    }

    private String reporte() throws Exception {
        return mockMvc.perform(get("/reportes")
                .param("desde", "2026-09-01").param("hasta", "2026-09-30")
                .with(user(new UsuarioAutenticado(institucion))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private static int contar(String texto, String aguja) {
        return texto.split(java.util.regex.Pattern.quote(aguja), -1).length - 1;
    }
}
