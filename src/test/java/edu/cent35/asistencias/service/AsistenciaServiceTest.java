package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoAsistencia;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.MotivoCargaManual;
import edu.cent35.asistencias.model.Usuario;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre el marcado de asistencia: la ventana de tolerancia que decide PRESENTE o TARDE, la
 * idempotencia por (docente, horario, fecha), el desempate del RF-18 cuando hay varias clases
 * en ventana y la validación de que la fecha manual caiga en el día del horario.
 */
@ExtendWith(MockitoExtension.class)
class AsistenciaServiceTest {

    private static final Long TENANT_A = 1L;
    private static final Long DOCENTE_ID = 50L;
    private static final Long HORARIO_ID = 60L;
    private static final Long COMISION_ID = 70L;
    private static final Long MATERIA_ID = 80L;
    private static final Long USUARIO_ID = 90L;

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
        when(asistenciaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

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
        when(asistenciaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

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
        when(asistenciaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        // Distancia 25 con umbral 100 → score 0.75
        AsistenciaService.ResultadoMarca r = service.marcarAutomatica(
            DOCENTE_ID, null, 25.0, instante);

        assertThat(r.asistencia().getConfianza())
            .isEqualByComparingTo(new java.math.BigDecimal("0.7500"));
    }

    // ========================================================================
    //  RF-18: desempate de horario ante ambigüedad
    // ========================================================================

    @Test
    @DisplayName("RF-18 consecutivos: si ya marcó la clase anterior, la marca va a la siguiente")
    void desempate_consecutivos_prefiereSinMarca() {
        Docente docente = docenteActivoA();
        Horario anterior = horarioLunes18a20Tolerancia15(docente);              // 18:00-20:00, id 60
        Horario siguiente = horarioLunes(docente, 61L, 20, 0, 22, 0);           // 20:00-22:00
        // 19:55: la ventana de la anterior sigue abierta (hasta 20:00) y la de
        // la siguiente ya abrió (20:00 - 15 min de tolerancia = 19:45).
        LocalDateTime instante = unLunesA(19, 55);
        LocalDate fecha = instante.toLocalDate();

        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docente));
        when(horarioRepository.findHoyParaDocente(DOCENTE_ID, (byte) 1, TENANT_A))
            .thenReturn(List.of(anterior, siguiente));
        // La clase anterior YA está marcada; la siguiente no.
        when(asistenciaRepository.findByDocenteIdAndHorarioIdAndFecha(DOCENTE_ID, HORARIO_ID, fecha))
            .thenReturn(Optional.of(Asistencia.builder().id(500L)
                .estado(EstadoAsistencia.PRESENTE).build()));
        when(asistenciaRepository.findByDocenteIdAndHorarioIdAndFecha(DOCENTE_ID, 61L, fecha))
            .thenReturn(Optional.empty());
        when(asistenciaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        AsistenciaService.ResultadoMarca r = service.marcarAutomatica(
            DOCENTE_ID, null, 50.0, instante);

        assertThat(r.marcada()).isTrue();
        assertThat(r.yaEstaba()).isFalse();
        // Se asignó a la clase que está por empezar, no a la ya marcada.
        assertThat(r.asistencia().getHorario().getId()).isEqualTo(61L);
        // Y como aún no dieron las 20:00, entra como PRESENTE (dentro de tolerancia).
        assertThat(r.asistencia().getEstado()).isEqualTo(EstadoAsistencia.PRESENTE);
    }

    @Test
    @DisplayName("RF-18 consecutivos sin marcas: gana el horario con inicio más cercano")
    void desempate_consecutivos_inicioMasCercano() {
        Docente docente = docenteActivoA();
        Horario anterior = horarioLunes18a20Tolerancia15(docente);              // 18:00-20:00
        Horario siguiente = horarioLunes(docente, 61L, 20, 0, 22, 0);           // 20:00-22:00
        LocalDateTime instante = unLunesA(19, 55);   // 5 min de las 20:00, 115 de las 18:00
        LocalDate fecha = instante.toLocalDate();

        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docente));
        when(horarioRepository.findHoyParaDocente(DOCENTE_ID, (byte) 1, TENANT_A))
            .thenReturn(List.of(anterior, siguiente));
        when(asistenciaRepository.findByDocenteIdAndHorarioIdAndFecha(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(asistenciaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        AsistenciaService.ResultadoMarca r = service.marcarAutomatica(
            DOCENTE_ID, null, 50.0, instante);

        assertThat(r.asistencia().getHorario().getId()).isEqualTo(61L);
    }

    @Test
    @DisplayName("RF-18 solapados con mismo inicio: desempata por menor id (determinista)")
    void desempate_solapados_menorId() {
        Docente docente = docenteActivoA();
        Horario comisionA = horarioLunes(docente, 71L, 18, 0, 20, 0);
        Horario comisionB = horarioLunes(docente, 70L, 18, 0, 20, 0);   // mismo horario, id menor
        LocalDateTime instante = unLunesA(18, 10);

        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docente));
        when(horarioRepository.findHoyParaDocente(DOCENTE_ID, (byte) 1, TENANT_A))
            .thenReturn(List.of(comisionA, comisionB));   // llegan en orden "arbitrario"
        when(asistenciaRepository.findByDocenteIdAndHorarioIdAndFecha(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(asistenciaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        AsistenciaService.ResultadoMarca r = service.marcarAutomatica(
            DOCENTE_ID, null, 50.0, instante);

        // Determinista: siempre el mismo, sin importar el orden de la query.
        assertThat(r.asistencia().getHorario().getId()).isEqualTo(70L);
    }

    @Test
    @DisplayName("RF-18 idempotencia preservada: con un solo horario ya marcado devuelve yaEstaba")
    void desempate_noRompeIdempotencia() {
        Docente docente = docenteActivoA();
        Horario h = horarioLunes18a20Tolerancia15(docente);
        LocalDateTime instante = unLunesA(18, 30);
        LocalDate fecha = instante.toLocalDate();
        Asistencia existente = Asistencia.builder().id(500L)
            .estado(EstadoAsistencia.PRESENTE).build();

        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docente));
        when(horarioRepository.findHoyParaDocente(DOCENTE_ID, (byte) 1, TENANT_A))
            .thenReturn(List.of(h));
        when(asistenciaRepository.findByDocenteIdAndHorarioIdAndFecha(DOCENTE_ID, HORARIO_ID, fecha))
            .thenReturn(Optional.of(existente));

        AsistenciaService.ResultadoMarca r = service.marcarAutomatica(
            DOCENTE_ID, null, 50.0, instante);

        assertThat(r.marcada()).isTrue();
        assertThat(r.yaEstaba()).isTrue();
        verify(asistenciaRepository, never()).saveAndFlush(any());
    }

    // ========================================================================
    //  marcarManual: la fecha tiene que caer en el dia del horario
    // ========================================================================

    @Test
    @DisplayName("marcarManual: rechaza una fecha que no cae en el dia del horario")
    void marcarManual_rechazaFechaDeOtroDia() {
        Docente docente = docenteActivoA();
        Horario horarioDeLunes = horarioLunes18a20Tolerancia15(docente);
        // 2026-05-30 fue sabado: el horario es de lunes, asi que no corresponde.
        LocalDate unSabado = LocalDate.of(2026, 5, 30);

        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docente));
        when(horarioRepository.findById(HORARIO_ID)).thenReturn(Optional.of(horarioDeLunes));

        assertThatThrownBy(() -> service.marcarManual(
                DOCENTE_ID, HORARIO_ID, unSabado, LocalTime.of(18, 5),
                EstadoAsistencia.TARDE, (short) 1, null, USUARIO_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Sábado")
            .hasMessageContaining("Lunes");

        // No se persiste nada si la fecha no corresponde.
        verify(asistenciaRepository, never()).save(any());
        verify(asistenciaManualRepository, never()).save(any());
    }

    @Test
    @DisplayName("marcarManual: acepta la fecha cuando cae en el dia del horario")
    void marcarManual_aceptaFechaDelMismoDia() {
        Docente docente = docenteActivoA();
        Horario horarioDeLunes = horarioLunes18a20Tolerancia15(docente);
        LocalDate unLunes = LocalDate.of(2026, 5, 25);   // lunes, igual que el horario

        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docente));
        when(horarioRepository.findById(HORARIO_ID)).thenReturn(Optional.of(horarioDeLunes));
        when(asistenciaRepository.findByDocenteIdAndHorarioIdAndFecha(DOCENTE_ID, HORARIO_ID, unLunes))
            .thenReturn(Optional.empty());
        when(motivoCargaManualRepository.findById((short) 1))
            .thenReturn(Optional.of(motivoActivo()));
        when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(new Usuario()));
        when(asistenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Asistencia guardada = service.marcarManual(
            DOCENTE_ID, HORARIO_ID, unLunes, LocalTime.of(18, 5),
            EstadoAsistencia.TARDE, (short) 1, null, USUARIO_ID);

        assertThat(guardada.getFecha()).isEqualTo(unLunes);
        assertThat(guardada.getEstado()).isEqualTo(EstadoAsistencia.TARDE);
        verify(asistenciaManualRepository).save(any());
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    private MotivoCargaManual motivoActivo() {
        return MotivoCargaManual.builder()
            .id((short) 1).codigo("FALLA_CAMARA").descripcion("Falla tecnica de la camara web")
            .activo(true).build();
    }

    // Docente activo del tenant A.
    private Docente docenteActivoA() {
        Docente d = Docente.builder()
            .id(DOCENTE_ID).dni("12345678").nombre("Juana").apellido("Pérez").activo(true)
            .build();
        d.setInstitucionId(TENANT_A);
        return d;
    }

    // Horario lunes 18:00-20:00 con 15 min de tolerancia previa.
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

    // Construye un LocalDateTime de un lunes (2026-05-25 fue lunes).
    private LocalDateTime unLunesA(int hora, int minuto) {
        return LocalDate.of(2026, 5, 25).atTime(hora, minuto);
    }

    // Horario de lunes con franja a medida, para los casos de ambigüedad del RF-18. Cada uno
    // lleva su propia comisión, así representan comisiones distintas del mismo docente.
    private Horario horarioLunes(Docente docente, Long id,
                                 int inicioHora, int inicioMin,
                                 int finHora, int finMin) {
        Materia materia = Materia.builder().id(MATERIA_ID).codigo("MAT").nombre("Matemática")
            .build();
        materia.setInstitucionId(TENANT_A);
        Comision comision = Comision.builder()
            .id(COMISION_ID + id).codigo("C" + id).materia(materia)
            .docenteAsignado(docente).activo(true).build();
        return Horario.builder()
            .id(id).comision(comision)
            .diaSemana((byte) 1)
            .horaInicio(LocalTime.of(inicioHora, inicioMin))
            .horaFin(LocalTime.of(finHora, finMin))
            .toleranciaMin((short) 15)
            .vigenteDesde(LocalDate.of(2026, 1, 1))
            .activo(true)
            .build();
    }
}
