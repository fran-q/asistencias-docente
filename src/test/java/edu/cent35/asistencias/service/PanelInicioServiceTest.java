package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.dto.PanelInicioDto;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoAsistencia;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.ModeloFacial;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.ConsentimientoBiometricoRepository;
import edu.cent35.asistencias.repository.ConsentimientoBiometricoRepository.UltimoEstadoConsentimientoView;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.ModeloFacialRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Tests del panel de inicio (RF-60).
 *
 * <p>Todo el panel se define contra "ahora", así que el reloj se fija en un lunes a las 10:30
 * y los horarios se arman alrededor de esa hora. Sin eso los tests pasarían o fallarían según
 * la hora a la que se corra la suite.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PanelInicioServiceTest {

    private static final Long TENANT_A = 1L;
    // 2026-05-25 fue lunes; el dia importa porque se consultan los horarios de ese dia.
    private static final LocalDate LUNES = LocalDate.of(2026, 5, 25);
    private static final LocalTime AHORA = LocalTime.of(10, 30);

    @Mock private HorarioRepository horarioRepository;
    @Mock private AsistenciaRepository asistenciaRepository;
    @Mock private DocenteRepository docenteRepository;
    @Mock private ConsentimientoBiometricoRepository consentimientoRepository;
    @Mock private ModeloFacialRepository modeloFacialRepository;
    @Mock private ComisionRepository comisionRepository;

    @InjectMocks private PanelInicioService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_A);
        service.setClock(Clock.fixed(
            LUNES.atTime(AHORA).atZone(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault()));

        // Por defecto no hay nada cargado a medias: cada test agrega lo suyo.
        when(docenteRepository.findByActivoTrueOrderByApellidoAscNombreAsc()).thenReturn(List.of());
        when(consentimientoRepository.findUltimoEstadoPorDocenteEnTenant(anyLong()))
            .thenReturn(List.of());
        when(modeloFacialRepository.findActivosDelTenant(anyLong())).thenReturn(List.of());
        when(comisionRepository.findActivasDelTenant(anyLong())).thenReturn(List.of());
        when(horarioRepository.findActivosDelDiaConDocente(any(), anyLong())).thenReturn(List.of());
        when(asistenciaRepository.findDelDia(any())).thenReturn(List.of());
    }

    @AfterEach
    void clear() { TenantContext.clear(); }

    // ========================================================================
    //  Ahora mismo
    // ========================================================================

    @Test
    @DisplayName("enCurso: solo las clases con la ventana abierta, no todas las del día")
    void enCurso_filtraPorVentana() {
        Docente d = docente(1L, "Pérez");
        when(horarioRepository.findActivosDelDiaConDocente(any(), anyLong())).thenReturn(List.of(
            horario(10L, d, 10, 0, 12, 0),   // corriendo
            horario(20L, d, 14, 0, 16, 0),   // todavia no empezo
            horario(30L, d,  8, 0, 10, 0)    // ya termino
        ));

        PanelInicioDto panel = service.armar();

        assertThat(panel.enCurso()).hasSize(1);
        assertThat(panel.enCurso().get(0).horaInicio()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    @DisplayName("enCurso: la tolerancia abre la ventana antes del horario de inicio")
    void enCurso_respetaLaTolerancia() {
        Docente d = docente(1L, "Pérez");
        // Empieza 10:40 con 15 de tolerancia: la ventana abrio 10:25 y son las 10:30.
        when(horarioRepository.findActivosDelDiaConDocente(any(), anyLong()))
            .thenReturn(List.of(horario(10L, d, 10, 40, 12, 0)));

        PanelInicioDto panel = service.armar();

        assertThat(panel.enCurso())
            .as("si la home usara la hora de inicio pelada, mostraria como 'todavia no' "
                + "una clase que el pase ya esta aceptando marcar")
            .hasSize(1);
    }

    @Test
    @DisplayName("enCurso: distingue al docente que ya marcó del que falta")
    void enCurso_marcaElEstado() {
        Docente conMarca = docente(1L, "Pérez");
        Docente sinMarca = docente(2L, "García");
        Horario h1 = horario(10L, conMarca, 10, 0, 12, 0);
        Horario h2 = horario(20L, sinMarca, 10, 0, 12, 0);
        when(horarioRepository.findActivosDelDiaConDocente(any(), anyLong()))
            .thenReturn(List.of(h1, h2));
        when(asistenciaRepository.findDelDia(any()))
            .thenReturn(List.of(marca(conMarca, h1, EstadoAsistencia.PRESENTE, 10, 5)));

        PanelInicioDto panel = service.armar();

        assertThat(panel.enCurso()).hasSize(2);
        assertThat(panel.enCurso().get(0).marcada()).isTrue();
        assertThat(panel.enCurso().get(0).estado()).isEqualTo("PRESENTE");
        assertThat(panel.enCurso().get(1).marcada()).isFalse();
        assertThat(panel.hayAlguienSinMarcar()).isTrue();
    }

    // ========================================================================
    //  El dia en numeros
    // ========================================================================

    @Test
    @DisplayName("resumen: una clase sin marca que todavía no terminó no es una ausencia")
    void resumen_noCuentaComoAusenteLoQueNoTermino() {
        Docente d = docente(1L, "Pérez");
        when(horarioRepository.findActivosDelDiaConDocente(any(), anyLong())).thenReturn(List.of(
            horario(10L, d, 14, 0, 16, 0),   // arranca a la tarde, sin marca
            horario(20L, d,  8, 0, 10, 0)    // ya termino, sin marca -> esa si es ausencia
        ));

        PanelInicioDto.ResumenDelDia r = service.armar().resumen();

        assertThat(r.ausentes())
            .as("contar como ausente lo que todavia no empezo dejaria el tablero en rojo "
                + "todas las mananas")
            .isEqualTo(1);
        assertThat(r.pendientesDeMarcar()).isEqualTo(1);
    }

    @Test
    @DisplayName("resumen: separa presentes de tarde")
    void resumen_cuentaPorEstado() {
        Docente uno = docente(1L, "Pérez");
        Docente dos = docente(2L, "García");
        Horario h1 = horario(10L, uno, 8, 0, 10, 0);
        Horario h2 = horario(20L, dos, 8, 0, 10, 0);
        when(horarioRepository.findActivosDelDiaConDocente(any(), anyLong()))
            .thenReturn(List.of(h1, h2));
        when(asistenciaRepository.findDelDia(any())).thenReturn(List.of(
            marca(uno, h1, EstadoAsistencia.PRESENTE, 8, 0),
            marca(dos, h2, EstadoAsistencia.TARDE, 8, 30)));

        PanelInicioDto.ResumenDelDia r = service.armar().resumen();

        assertThat(r.presentes()).isEqualTo(1);
        assertThat(r.tarde()).isEqualTo(1);
        assertThat(r.ausentes()).isZero();
    }

    @Test
    @DisplayName("resumen: una ausencia YA persistida se cuenta como ausente")
    void resumen_cuentaLasAusenciasPersistidas() {
        Docente d = docente(1L, "Pérez");
        Horario h = horario(10L, d, 8, 0, 10, 0);
        when(horarioRepository.findActivosDelDiaConDocente(any(), anyLong()))
            .thenReturn(List.of(h));
        // La escribe el generador de ausencias al cierre del dia.
        when(asistenciaRepository.findDelDia(any()))
            .thenReturn(List.of(marca(d, h, EstadoAsistencia.AUSENTE, 10, 0)));

        PanelInicioDto.ResumenDelDia r = service.armar().resumen();

        assertThat(r.ausentes())
            .as("el tablero decia 0 ausentes con una fila AUSENTE en la base: la fila caia "
                + "en 'ya tiene marca' y no se contaba en ningun lado, mientras el listado "
                + "de asistencias si la mostraba")
            .isEqualTo(1);
    }

    @Test
    @DisplayName("cobertura: un docente ausente NO cuenta como que ya marcó")
    void cobertura_elAusenteNoCuentaComoMarcado() {
        Docente vino = docente(1L, "Pérez");
        Docente falto = docente(2L, "García");
        Horario h1 = horario(10L, vino, 8, 0, 10, 0);
        Horario h2 = horario(20L, falto, 8, 0, 10, 0);
        when(horarioRepository.findActivosDelDiaConDocente(any(), anyLong()))
            .thenReturn(List.of(h1, h2));
        when(asistenciaRepository.findDelDia(any())).thenReturn(List.of(
            marca(vino, h1, EstadoAsistencia.PRESENTE, 8, 0),
            marca(falto, h2, EstadoAsistencia.AUSENTE, 10, 0)));

        PanelInicioDto.ResumenDelDia r = service.armar().resumen();

        assertThat(r.docentesQueMarcaron())
            .as("una ausencia es una fila de asistencia, no una marca; contarla daba "
                + "'2 de 2 ya marcaron (100%)' con la mitad del personal sin venir")
            .isEqualTo(1);
        assertThat(r.docentesConClase()).isEqualTo(2);
        assertThat(r.porcentajeCobertura()).isEqualTo(50);
    }

    @Test
    @DisplayName("cobertura: cuenta docentes distintos, no clases")
    void cobertura_cuentaPersonasNoClases() {
        Docente conDosClases = docente(1L, "Pérez");
        Docente sinMarcar = docente(2L, "García");
        Horario h1 = horario(10L, conDosClases, 8, 0, 10, 0);
        Horario h2 = horario(20L, conDosClases, 10, 0, 12, 0);
        Horario h3 = horario(30L, sinMarcar, 10, 0, 12, 0);
        when(horarioRepository.findActivosDelDiaConDocente(any(), anyLong()))
            .thenReturn(List.of(h1, h2, h3));
        when(asistenciaRepository.findDelDia(any())).thenReturn(List.of(
            marca(conDosClases, h1, EstadoAsistencia.PRESENTE, 8, 0),
            marca(conDosClases, h2, EstadoAsistencia.PRESENTE, 10, 0)));

        PanelInicioDto.ResumenDelDia r = service.armar().resumen();

        assertThat(r.docentesQueMarcaron())
            .as("dos marcas del mismo docente son una sola persona: contando clases daria "
                + "2 de 3 y el numero hablaria de otra cosa")
            .isEqualTo(1);
        assertThat(r.docentesConClase()).isEqualTo(2);
        assertThat(r.porcentajeCobertura()).isEqualTo(50);
    }

    @Test
    @DisplayName("cobertura: sin clases hoy no divide por cero")
    void cobertura_sinClases() {
        PanelInicioDto panel = service.armar();

        assertThat(panel.resumen().sinClasesHoy()).isTrue();
        assertThat(panel.resumen().porcentajeCobertura()).isZero();
        assertThat(panel.sinClasesAhora()).isTrue();
    }

    // ========================================================================
    //  Requiere atencion
    // ========================================================================

    @Test
    @DisplayName("pendientes: el que no tiene consentimiento no se cuenta además como sin rostro")
    void pendientes_noDuplicaAlDocenteSinConsentimiento() {
        Docente sinNada = docente(1L, "Pérez");
        Docente soloConsentimiento = docente(2L, "García");
        when(docenteRepository.findByActivoTrueOrderByApellidoAscNombreAsc())
            .thenReturn(List.of(sinNada, soloConsentimiento));
        when(consentimientoRepository.findUltimoEstadoPorDocenteEnTenant(anyLong()))
            .thenReturn(List.of(vista(2L, true)));

        List<PanelInicioDto.Pendiente> p = service.armar().pendientes();

        assertThat(p).hasSize(2);
        assertThat(p.get(0).titulo()).contains("sin consentimiento");
        assertThat(p.get(0).cantidad()).isEqualTo(1);
        assertThat(p.get(1).titulo()).contains("sin rostro");
        assertThat(p.get(1).cantidad())
            .as("al que le falta el consentimiento le falta el paso previo: contarlo tambien "
                + "como 'sin rostro' seria decir dos veces lo mismo")
            .isEqualTo(1);
    }

    @Test
    @DisplayName("pendientes: el docente con consentimiento y rostro no aparece")
    void pendientes_docenteCompletoNoAparece() {
        Docente completo = docente(1L, "Pérez");
        when(docenteRepository.findByActivoTrueOrderByApellidoAscNombreAsc())
            .thenReturn(List.of(completo));
        when(consentimientoRepository.findUltimoEstadoPorDocenteEnTenant(anyLong()))
            .thenReturn(List.of(vista(1L, true)));
        when(modeloFacialRepository.findActivosDelTenant(anyLong()))
            .thenReturn(List.of(ModeloFacial.builder().id(1L).docente(completo).activo(true).build()));

        assertThat(service.armar().todoEnOrden()).isTrue();
    }

    @Test
    @DisplayName("pendientes: el consentimiento revocado cuenta como faltante")
    void pendientes_consentimientoRevocado() {
        Docente revocado = docente(1L, "Pérez");
        when(docenteRepository.findByActivoTrueOrderByApellidoAscNombreAsc())
            .thenReturn(List.of(revocado));
        when(consentimientoRepository.findUltimoEstadoPorDocenteEnTenant(anyLong()))
            .thenReturn(List.of(vista(1L, false)));

        List<PanelInicioDto.Pendiente> p = service.armar().pendientes();

        assertThat(p).hasSize(1);
        assertThat(p.get(0).titulo()).contains("sin consentimiento");
    }

    @Test
    @DisplayName("pendientes: comisión sin docente y comisión sin horarios")
    void pendientes_comisionesIncompletas() {
        Comision sinDocente = Comision.builder()
            .id(1L).codigo("A").materia(materia()).docenteAsignado(null).activo(true).build();
        Comision sinHorarios = Comision.builder()
            .id(2L).codigo("B").materia(materia()).docenteAsignado(docente(1L, "Pérez"))
            .activo(true).build();
        when(comisionRepository.findActivasDelTenant(anyLong()))
            .thenReturn(List.of(sinDocente, sinHorarios));
        when(horarioRepository.countByComisionIdAndActivoTrue(1L)).thenReturn(3L);
        when(horarioRepository.countByComisionIdAndActivoTrue(2L)).thenReturn(0L);

        List<PanelInicioDto.Pendiente> p = service.armar().pendientes();

        assertThat(p).extracting(PanelInicioDto.Pendiente::titulo)
            .containsExactly("comisiones sin docente asignado", "comisiones sin horarios cargados");
        assertThat(p).extracting(PanelInicioDto.Pendiente::cantidad).containsExactly(1L, 1L);
    }

    // ========================================================================
    //  Helpers
    // ========================================================================

    private Docente docente(Long id, String apellido) {
        Docente d = Docente.builder()
            .id(id).nombre("Nombre").apellido(apellido).dni("3000000" + id).activo(true).build();
        d.setInstitucionId(TENANT_A);
        return d;
    }

    private Materia materia() {
        Materia m = Materia.builder().id(1L).codigo("MAT").nombre("Matemática").activo(true).build();
        m.setInstitucionId(TENANT_A);
        return m;
    }

    // Horario del lunes con su propia comision, para que cada uno sea una clase distinta.
    private Horario horario(Long id, Docente docente, int hi, int mi, int hf, int mf) {
        Comision c = Comision.builder()
            .id(id).codigo("C" + id).materia(materia()).docenteAsignado(docente).activo(true)
            .build();
        return Horario.builder()
            .id(id).comision(c)
            .diaSemana((byte) 1)
            .horaInicio(LocalTime.of(hi, mi))
            .horaFin(LocalTime.of(hf, mf))
            .toleranciaMin((short) 15)
                        .activo(true)
            .build();
    }

    private Asistencia marca(Docente d, Horario h, EstadoAsistencia estado, int hora, int min) {
        return Asistencia.builder()
            .id(h.getId()).docente(d).comision(h.getComision()).horario(h)
            .fecha(LUNES).horaRegistrada(LocalTime.of(hora, min))
            .estado(estado)
            .creadoEn(LocalDateTime.of(LUNES, LocalTime.of(hora, min)))
            .build();
    }

    private UltimoEstadoConsentimientoView vista(Long docenteId, boolean vigente) {
        return new UltimoEstadoConsentimientoView() {
            @Override public Long getDocenteId() { return docenteId; }
            @Override public Boolean getVigente() { return vigente; }
        };
    }
}
