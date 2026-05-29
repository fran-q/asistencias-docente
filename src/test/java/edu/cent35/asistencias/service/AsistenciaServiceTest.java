package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoAsistencia;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.repository.AsistenciaManualRepository;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.JustificacionAusenciaRepository;
import edu.cent35.asistencias.repository.ModeloFacialRepository;
import edu.cent35.asistencias.repository.MotivoCargaManualRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsistenciaServiceTest {

    private static final Long TENANT_A = 1L;
    private static final Long DOCENTE_ID = 50L;
    private static final Long HORARIO_ID = 60L;
    private static final Long COMISION_ID = 70L;
    private static final Long MATERIA_ID = 80L;

    @Mock private AsistenciaRepository asistenciaRepository;
    @Mock private HorarioRepository horarioRepository;
    @Mock private DocenteRepository docenteRepository;
    @Mock private ModeloFacialRepository modeloFacialRepository;
    @Mock private AsistenciaManualRepository asistenciaManualRepository;
    @Mock private JustificacionAusenciaRepository justificacionAusenciaRepository;
    @Mock private MotivoCargaManualRepository motivoCargaManualRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks private AsistenciaService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_A);
        ReflectionTestUtils.setField(service, "umbralDistancia", 100.0);
    }

    @AfterEach
    void clear() { TenantContext.clear(); }

    // ========================================================================
    //  marcarAutomatica
    // ========================================================================

    @Test
    @DisplayName("marcarAutomatica: PRESENTE si llega dentro de la tolerancia previa")
    void marcarAutomatica_presente() {
        Docente docente = docenteActivoA();
        Horario horario = horarioLunes18a20Tolerancia15(docente);
        // Hoy lunes 17:55 → dentro de tolerancia [17:45, 20:00]
        LocalDateTime instante = unLunesA(17, 55);

        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docente));
        when(horarioRepository.findHoyParaDocente(DOCENTE_ID, (byte) 1, TENANT_A))
            .thenReturn(List.of(horario));
        when(asistenciaRepository.findByDocenteIdAndHorarioIdAndFecha(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(asistenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AsistenciaService.ResultadoMarca r = service.marcarAutomatica(
            DOCENTE_ID, null, 50.0, instante);

        assertThat(r.marcada()).isTrue();
        assertThat(r.yaEstaba()).isFalse();
        assertThat(r.asistencia().getEstado()).isEqualTo(EstadoAsistencia.PRESENTE);
    }

    @Test
    @DisplayName("marcarAutomatica: TARDE si llega después del hora_inicio")
    void marcarAutomatica_tarde() {
        Docente docente = docenteActivoA();
        Horario horario = horarioLunes18a20Tolerancia15(docente);
        // 18:30 → TARDE
        LocalDateTime instante = unLunesA(18, 30);

        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docente));
        when(horarioRepository.findHoyParaDocente(DOCENTE_ID, (byte) 1, TENANT_A))
            .thenReturn(List.of(horario));
        when(asistenciaRepository.findByDocenteIdAndHorarioIdAndFecha(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(asistenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AsistenciaService.ResultadoMarca r = service.marcarAutomatica(
            DOCENTE_ID, null, 80.0, instante);

        assertThat(r.marcada()).isTrue();
        assertThat(r.asistencia().getEstado()).isEqualTo(EstadoAsistencia.TARDE);
    }

    @Test
    @DisplayName("marcarAutomatica: sin clase ahora -> sinClase")
    void marcarAutomatica_sinClase() {
        Docente docente = docenteActivoA();
        Horario horario = horarioLunes18a20Tolerancia15(docente);
        // 17:00 → antes de [17:45, 20:00]
        LocalDateTime instante = unLunesA(17, 0);

        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docente));
        when(horarioRepository.findHoyParaDocente(DOCENTE_ID, (byte) 1, TENANT_A))
            .thenReturn(List.of(horario));

        AsistenciaService.ResultadoMarca r = service.marcarAutomatica(
            DOCENTE_ID, null, 50.0, instante);

        assertThat(r.marcada()).isFalse();
        assertThat(r.motivoNoMarca()).contains("No hay clase");
        verify(asistenciaRepository, never()).save(any());
    }

    @Test
    @DisplayName("marcarAutomatica: idempotente - si ya hay marca, devuelve yaEstaba")
    void marcarAutomatica_idempotente() {
        Docente docente = docenteActivoA();
        Horario horario = horarioLunes18a20Tolerancia15(docente);
        LocalDateTime instante = unLunesA(18, 5);
        LocalDate fecha = instante.toLocalDate();

        Asistencia existente = Asistencia.builder()
            .id(999L).docente(docente).horario(horario)
            .estado(EstadoAsistencia.PRESENTE).build();

        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docente));
        when(horarioRepository.findHoyParaDocente(DOCENTE_ID, (byte) 1, TENANT_A))
            .thenReturn(List.of(horario));
        when(asistenciaRepository.findByDocenteIdAndHorarioIdAndFecha(DOCENTE_ID, HORARIO_ID, fecha))
            .thenReturn(Optional.of(existente));

        AsistenciaService.ResultadoMarca r = service.marcarAutomatica(
            DOCENTE_ID, null, 50.0, instante);

        assertThat(r.marcada()).isTrue();
        assertThat(r.yaEstaba()).isTrue();
        assertThat(r.asistencia()).isSameAs(existente);
        verify(asistenciaRepository, never()).save(any());
    }

    @Test
    @DisplayName("marcarAutomatica: convierte distancia LBPH a confianza 0-1")
    void marcarAutomatica_confianza() {
        Docente docente = docenteActivoA();
        Horario horario = horarioLunes18a20Tolerancia15(docente);
        LocalDateTime instante = unLunesA(18, 0);

        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docente));
        when(horarioRepository.findHoyParaDocente(DOCENTE_ID, (byte) 1, TENANT_A))
            .thenReturn(List.of(horario));
        when(asistenciaRepository.findByDocenteIdAndHorarioIdAndFecha(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(asistenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Distancia 25 con umbral 100 → score 0.75
        AsistenciaService.ResultadoMarca r = service.marcarAutomatica(
            DOCENTE_ID, null, 25.0, instante);

        assertThat(r.asistencia().getConfianza())
            .isEqualByComparingTo(new java.math.BigDecimal("0.7500"));
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    private Docente docenteActivoA() {
        Docente d = Docente.builder()
            .id(DOCENTE_ID).dni("12345678").nombre("Juana").apellido("Pérez").activo(true)
            .build();
        d.setInstitucionId(TENANT_A);
        return d;
    }

    /** Horario lunes 18:00-20:00 con 15 min de tolerancia previa. */
    private Horario horarioLunes18a20Tolerancia15(Docente docenteAsignado) {
        Materia materia = Materia.builder().id(MATERIA_ID).codigo("MAT").nombre("Matemática")
            .build();
        materia.setInstitucionId(TENANT_A);
        Comision comision = Comision.builder()
            .id(COMISION_ID).codigo("A").materia(materia).docenteAsignado(docenteAsignado)
            .activo(true).build();
        return Horario.builder()
            .id(HORARIO_ID).comision(comision)
            .diaSemana((byte) 1)             // lunes
            .horaInicio(LocalTime.of(18, 0))
            .horaFin(LocalTime.of(20, 0))
            .toleranciaMin((short) 15)
            .vigenteDesde(LocalDate.of(2026, 1, 1))
            .activo(true)
            .build();
    }

    /** Construye un LocalDateTime de un lunes (2026-05-25 fue lunes). */
    private LocalDateTime unLunesA(int hora, int minuto) {
        return LocalDate.of(2026, 5, 25).atTime(hora, minuto);
    }
}
