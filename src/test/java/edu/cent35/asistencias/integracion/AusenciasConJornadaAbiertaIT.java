package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.EscenarioDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.academico.Carrera;
import edu.cent35.asistencias.model.academico.CicloLectivo;
import edu.cent35.asistencias.model.academico.Horario;
import edu.cent35.asistencias.model.academico.Materia;
import edu.cent35.asistencias.model.asistencia.Asistencia;
import edu.cent35.asistencias.model.asistencia.BloquePresencia;
import edu.cent35.asistencias.model.asistencia.EstadoAsistencia;
import edu.cent35.asistencias.model.asistencia.EstadoCierre;
import edu.cent35.asistencias.model.asistencia.OrigenMarca;
import edu.cent35.asistencias.model.persona.Docente;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.BloquePresenciaRepository;
import edu.cent35.asistencias.service.AsistenciaService;
import edu.cent35.asistencias.service.BloquePresenciaService;
import edu.cent35.asistencias.service.GeneradorAusenciasService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El job de ausencias frente a un docente que todavía no salió (RF-19, RF-81).
 *
 * <p><b>Qué problema cubre.</b> El job corre cada media hora y genera una ausencia por cada
 * clase terminada sin marca. Las clases de una jornada abierta, salvo la que estaba en curso al
 * entrar, se imputan recién al cerrarla, y esa imputación no pisa lo que ya está escrito. Así,
 * un docente con tres clases seguidas quedaba ausente en la del medio si el job corría mientras
 * daba la tercera, y quien llegaba antes de su primera clase quedaba ausente en esa.
 *
 * <p>Va como IT porque lo que fallaba es el encuentro entre el job, el cierre de la jornada y
 * el UNIQUE de la asistencia. Con los servicios mockeados cada uno hace bien lo suyo y el bug
 * no aparece: así pasó desapercibido.
 */
@SpringBootTest
@ActiveProfiles("test")
class AusenciasConJornadaAbiertaIT {

    // Un lunes del ciclo 2026, para que le correspondan los horarios de lunes.
    private static final LocalDate LUNES = LocalDate.of(2026, 9, 7);

    @Autowired private EscenarioDePrueba escenario;
    @Autowired private BloquePresenciaRepository bloqueRepository;
    @Autowired private AsistenciaRepository asistenciaRepository;
    @Autowired private AsistenciaService asistenciaService;
    @Autowired private BloquePresenciaService bloquePresenciaService;
    @Autowired private GeneradorAusenciasService generador;

    private Long tenantId;
    private Materia materia;
    private CicloLectivo ciclo;
    private Docente docente;

    @BeforeEach
    void sembrar() {
        tenantId = escenario.institucion("Instituto de la jornada");
        TenantContext.set(tenantId);
        Carrera carrera = escenario.carrera(tenantId, "JOR-" + tenantId);
        materia = escenario.materia(carrera, "JOR1-" + tenantId, "Programación I");
        ciclo = escenario.ciclo(tenantId, 2026);
        docente = escenario.docente(tenantId, "48" + tenantId);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
        escenario.borrarTodoDe(tenantId);
    }

    @Test
    @DisplayName("Con tres clases seguidas, el job no deja ausente la del medio")
    void laClaseDelMedioNoQuedaAusente() {
        Horario primera = clase("A", 18, 19);
        Horario segunda = clase("B", 19, 20);
        Horario tercera = clase("C", 20, 21);
        // Entra 18:02 por la cámara: se imputa la clase en curso, como hace el pase.
        BloquePresencia jornada = jornadaAbierta(LocalTime.of(18, 2));
        asistenciaService.imputarDelBloque(jornada, primera, LocalTime.of(18, 2));

        correrElJob(LocalTime.of(20, 30));     // terminó la segunda; está dando la tercera

        assertThat(asistenciaDe(segunda))
            .as("la está cubriendo: se imputa al cerrar la jornada, no se declara ausente")
            .isEmpty();

        correrElJob(LocalTime.of(21, 30));     // terminó la franja: cierra e imputa

        assertThat(asistenciaDe(segunda)).hasValueSatisfying(this::presenteConJornada);
        assertThat(asistenciaDe(tercera)).hasValueSatisfying(this::presenteConJornada);
    }

    @Test
    @DisplayName("Quien llega antes de su primera clase no queda ausente en ella")
    void llegarAntesNoDejaAusente() {
        Horario primera = clase("A", 18, 19);
        Horario segunda = clase("B", 19, 20);
        // Entra 17:55, antes de que empiece: al entrar no hay clase en curso que imputar.
        jornadaAbierta(LocalTime.of(17, 55));

        correrElJob(LocalTime.of(19, 30));     // terminó la primera; está dando la segunda

        assertThat(asistenciaDe(primera)).isEmpty();

        correrElJob(LocalTime.of(20, 30));

        assertThat(asistenciaDe(primera)).hasValueSatisfying(this::presenteConJornada);
        assertThat(asistenciaDe(segunda)).hasValueSatisfying(this::presenteConJornada);
    }

    @Test
    @DisplayName("La clase que terminó antes de que entrara sí queda ausente")
    void laClaseAnteriorALaJornadaSiQuedaAusente() {
        // El corte no puede tapar ausencias reales: a la clase de las 14 no vino, y que
        // después haya entrado a la de las 18 no cambia eso.
        Horario temprano = clase("A", 14, 15);
        clase("B", 18, 19);
        jornadaAbierta(LocalTime.of(18, 2));

        correrElJob(LocalTime.of(18, 30));

        assertThat(asistenciaDe(temprano))
            .hasValueSatisfying(a -> assertThat(a.getEstado()).isEqualTo(EstadoAsistencia.AUSENTE));
    }

    // ------------------------------------------------------------------------

    // Una clase de la docente los lunes, en su propia comisión: dos clases el mismo día en una
    // misma comisión chocarían con el UNIQUE de la asistencia.
    private Horario clase(String comision, int desde, int hasta) {
        return escenario.horario(escenario.comision(materia, ciclo, docente, comision),
            DayOfWeek.MONDAY, LocalTime.of(desde, 0), LocalTime.of(hasta, 0));
    }

    // La jornada que abre el pase al reconocerla, sin salida todavía.
    private BloquePresencia jornadaAbierta(LocalTime entrada) {
        BloquePresencia b = BloquePresencia.builder()
            .docente(docente).fecha(LUNES).horaEntrada(entrada)
            .origenEntrada(OrigenMarca.AUTOMATICO).estadoCierre(EstadoCierre.ABIERTO)
            .build();
        b.setInstitucionId(tenantId);
        return bloqueRepository.saveAndFlush(b);
    }

    // Una corrida del job, en su orden: cierra las jornadas vencidas y después genera las
    // ausencias. Se llama a los dos pasos con la hora en mano en vez de esperar al reloj.
    private void correrElJob(LocalTime ahora) {
        bloquePresenciaService.cerrarBloquesVencidos(LUNES, ahora);
        generador.generarParaInstitucion(tenantId, LUNES, ahora);
    }

    private Optional<Asistencia> asistenciaDe(Horario h) {
        return asistenciaRepository.findByDocenteIdAndHorarioIdAndFecha(docente.getId(), h.getId(), LUNES);
    }

    private void presenteConJornada(Asistencia a) {
        assertThat(a.getEstado()).isEqualTo(EstadoAsistencia.PRESENTE);
        assertThat(a.getBloque()).as("la imputó la jornada, no el job").isNotNull();
    }
}
