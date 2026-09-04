package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.dto.AsistenciaReporteRowDto;
import edu.cent35.asistencias.dto.ReporteFiltroDto;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.AsistenciaManual;
import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoAsistencia;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.JustificacionAusencia;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.MetodoAsistencia;
import edu.cent35.asistencias.model.MotivoCargaManual;
import edu.cent35.asistencias.repository.AsistenciaManualRepository;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.JustificacionAusenciaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Cubre el armado del reporte: que los filtros opcionales se apliquen bien y que cada fila
 * traiga adosado su detalle de carga manual y de justificación.
 */
@ExtendWith(MockitoExtension.class)
class ReporteAsistenciaServiceTest {

    @Mock private AsistenciaRepository asistenciaRepository;
    @Mock private AsistenciaManualRepository asistenciaManualRepository;
    @Mock private JustificacionAusenciaRepository justificacionAusenciaRepository;

    @InjectMocks private ReporteAsistenciaService service;

    @AfterEach
    void limpiarTenant() { TenantContext.clear(); }

    @BeforeEach
    void setUp() {
        // El reporte pasa el tenant explicito a la consulta desde ADR-0016.
        TenantContext.set(1L);
        // El tope viene de application.properties; sin fijarlo, en el test unitario vale 0
        // y todo reporte saldria vacio por truncamiento.
        ReflectionTestUtils.setField(service, "maxFilas", 2000);
    }

    @Test
    @DisplayName("reporte: rango por defecto = mes actual hasta hoy")
    void reporte_rangoDefault() {
        when(asistenciaRepository.findParaReporte(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of());

        List<AsistenciaReporteRowDto> filas = service.reporte(new ReporteFiltroDto());

        assertThat(filas).isEmpty();
    }

    @Test
    @DisplayName("reporte: desde > hasta -> IllegalArgumentException")
    void reporte_rangoInvertido() {
        ReporteFiltroDto filtro = ReporteFiltroDto.builder()
            .desde(LocalDate.of(2026, 6, 30))
            .hasta(LocalDate.of(2026, 6, 1))
            .build();

        assertThatThrownBy(() -> service.reporte(filtro))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("desde");
    }

    @Test
    @DisplayName("reporte: enriquece con motivo manual y justificación")
    void reporte_enriquece() {
        Asistencia a = construirAsistenciaAutomatica();
        when(asistenciaRepository.findParaReporte(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of(a));

        AsistenciaManual manual = AsistenciaManual.builder()
            .asistencia(a)
            .detalleAdicional("cámara apagada")
            .build();
        manual.setMotivo(MotivoCargaManual.builder()
            .id((short) 1).codigo("FALLA_CAMARA")
            .descripcion("Falla técnica de la cámara").build());
        when(asistenciaManualRepository.findByAsistenciaIdIn(any())).thenReturn(List.of(manual));

        JustificacionAusencia just = JustificacionAusencia.builder()
            .asistencia(a)
            .motivo("Certificado médico").build();
        when(justificacionAusenciaRepository.findByAsistenciaIdIn(any())).thenReturn(List.of(just));

        List<AsistenciaReporteRowDto> filas = service.reporte(ReporteFiltroDto.builder()
            .desde(LocalDate.of(2026, 6, 1))
            .hasta(LocalDate.of(2026, 6, 30))
            .build());

        assertThat(filas).hasSize(1);
        AsistenciaReporteRowDto fila = filas.get(0);
        assertThat(fila.getMotivoManual()).isEqualTo("Falla técnica de la cámara");
        assertThat(fila.getDetalleManual()).isEqualTo("cámara apagada");
        assertThat(fila.isJustificada()).isTrue();
        assertThat(fila.getMotivoJustificacion()).isEqualTo("Certificado médico");
    }

    // ------------------------------------------------------------------------

    private Asistencia construirAsistenciaAutomatica() {
        Carrera carrera = Carrera.builder().codigo("ECO").nombre("Eco").build();
        Materia materia = Materia.builder().codigo("MAT").nombre("Matemática")
            .carrera(carrera).build();
        Comision comision = Comision.builder().codigo("A").materia(materia).build();
        Horario horario = Horario.builder()
            .diaSemana((byte) 1)
            .horaInicio(LocalTime.of(18, 0))
            .horaFin(LocalTime.of(20, 0))
            .comision(comision)
            .build();
        Docente docente = Docente.builder().persona(DatosDePrueba.personaConDni("12345678", "Juana", "Pérez")).build();

        return Asistencia.builder()
            .id(1L)
            .docente(docente).comision(comision).horario(horario)
            .fecha(LocalDate.of(2026, 6, 15))
            .horaRegistrada(LocalTime.of(18, 5, 23))
            .estado(EstadoAsistencia.PRESENTE)
            .metodo(MetodoAsistencia.AUTOMATICO)
            .build();
    }
}
