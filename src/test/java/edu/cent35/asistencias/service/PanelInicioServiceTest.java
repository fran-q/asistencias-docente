package edu.cent35.asistencias.service;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.dto.ClasesDeAhoraDto;
import edu.cent35.asistencias.dto.PanelInicioDto;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoAsistencia;
import edu.cent35.asistencias.model.EstadoCiclo;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.ModeloFacial;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.CicloLectivoRepository;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.ConsentimientoBiometricoRepository;
import edu.cent35.asistencias.repository.ConsentimientoBiometricoRepository.UltimoEstadoConsentimientoView;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.ModeloFacialRepository;
import edu.cent35.asistencias.repository.PuestoCapturaRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @Mock private edu.cent35.asistencias.repository.BloquePresenciaRepository bloquePresenciaRepository;
    @Mock private CicloLectivoRepository cicloRepository;
    @Mock private PuestoCapturaRepository puestoRepository;
    @Mock private DiaNoLaborableService diaNoLaborableService;

    @InjectMocks private PanelInicioService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_A);
        service.setClock(Clock.fixed(
            LUNES.atTime(AHORA).atZone(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault()));

        // Por defecto no hay nada cargado a medias: cada test agrega lo suyo.
        when(docenteRepository.listarVigentesDelTenant(any())).thenReturn(List.of());
        when(consentimientoRepository.findUltimoEstadoPorDocenteEnTenant(anyLong()))
            .thenReturn(List.of());
        when(modeloFacialRepository.findActivosDelTenant(anyLong())).thenReturn(List.of());
        when(comisionRepository.findActivasEnFecha(any(), anyLong())).thenReturn(List.of());
        when(horarioRepository.findActivosDelDiaConDocente(any(), any(), anyLong())).thenReturn(List.of());
        when(asistenciaRepository.findDelDia(any(), any())).thenReturn(List.of());
        // Un calendario en orden y un equipo autorizado: los tests que no miran eso no tienen
        // por que ver sus avisos.
        when(cicloRepository.listarDelTenant(anyLong()))
            .thenReturn(List.of(ciclo(2026, EstadoCiclo.ACTIVO)));
        when(puestoRepository.contarHabilitados(anyLong())).thenReturn(1L);
        when(diaNoLaborableService.motivoSinClases(anyLong(), any())).thenReturn(Optional.empty());
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
        when(horarioRepository.findActivosDelDiaConDocente(any(), any(), anyLong())).thenReturn(List.of(
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
        when(horarioRepository.findActivosDelDiaConDocente(any(), any(), anyLong()))
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
        when(horarioRepository.findActivosDelDiaConDocente(any(), any(), anyLong()))
            .thenReturn(List.of(h1, h2));
        when(asistenciaRepository.findDelDia(any(), any()))
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
        when(horarioRepository.findActivosDelDiaConDocente(any(), any(), anyLong())).thenReturn(List.of(
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
        when(horarioRepository.findActivosDelDiaConDocente(any(), any(), anyLong()))
            .thenReturn(List.of(h1, h2));
        when(asistenciaRepository.findDelDia(any(), any())).thenReturn(List.of(
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
        when(horarioRepository.findActivosDelDiaConDocente(any(), any(), anyLong()))
            .thenReturn(List.of(h));
        // La escribe el generador de ausencias al cierre del dia.
        when(asistenciaRepository.findDelDia(any(), any()))
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
        when(horarioRepository.findActivosDelDiaConDocente(any(), any(), anyLong()))
            .thenReturn(List.of(h1, h2));
        when(asistenciaRepository.findDelDia(any(), any())).thenReturn(List.of(
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
        when(horarioRepository.findActivosDelDiaConDocente(any(), any(), anyLong()))
            .thenReturn(List.of(h1, h2, h3));
        when(asistenciaRepository.findDelDia(any(), any())).thenReturn(List.of(
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
        when(docenteRepository.listarVigentesDelTenant(any()))
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
        when(docenteRepository.listarVigentesDelTenant(any()))
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
        when(docenteRepository.listarVigentesDelTenant(any()))
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
        when(comisionRepository.findActivasEnFecha(any(), anyLong()))
            .thenReturn(List.of(sinDocente, sinHorarios));
        when(horarioRepository.countByComisionIdAndActivoTrue(1L)).thenReturn(3L);
        when(horarioRepository.countByComisionIdAndActivoTrue(2L)).thenReturn(0L);

        List<PanelInicioDto.Pendiente> p = service.armar().pendientes();

        assertThat(p).extracting(PanelInicioDto.Pendiente::titulo)
            .containsExactly("comisiones sin docente asignado", "comisiones sin horarios cargados");
        assertThat(p).extracting(PanelInicioDto.Pendiente::cantidad).containsExactly(1L, 1L);
    }

    // ========================================================================
    //  Por que no se toma asistencia
    // ========================================================================

    @Test
    @DisplayName("calendario: el ciclo del año en preparación va primero y explica el día vacío")
    void calendario_cicloEnPreparacion() {
        when(cicloRepository.listarDelTenant(anyLong()))
            .thenReturn(List.of(ciclo(2026, EstadoCiclo.PREPARACION)));
        when(docenteRepository.listarVigentesDelTenant(any()))
            .thenReturn(List.of(docente(1L, "Pérez")));

        PanelInicioDto panel = service.armar();

        PanelInicioDto.Pendiente primero = panel.pendientes().get(0);
        assertThat(primero.titulo())
            .as("deja sin asistencia a la institucion entera: va antes que lo de cada docente")
            .isEqualTo("El ciclo lectivo 2026 está en preparación");
        assertThat(primero.cantidad()).as("un ciclo es uno solo: no se cuenta").isNull();
        assertThat(primero.url()).isEqualTo("/ciclos");
        assertThat(primero.soloInstitucion()).isTrue();

        PanelInicioDto.MotivoSinClases motivo = panel.motivoSinClases();
        assertThat(motivo.texto())
            .as("es la causa de que el pase no vea ninguna clase, aunque la grilla las muestre")
            .contains("está en preparación")
            .contains("no ve ninguna clase");
        assertThat(motivo.problema()).isTrue();
        assertThat(motivo.url()).isEqualTo("/ciclos");
        assertThat(motivo.soloInstitucion()).isTrue();
    }

    @Test
    @DisplayName("calendario: sin ningún ciclo activo lo dice")
    void calendario_sinCiclo() {
        when(cicloRepository.listarDelTenant(anyLong())).thenReturn(List.of());

        PanelInicioDto panel = service.armar();

        assertThat(panel.pendientes()).extracting(PanelInicioDto.Pendiente::titulo)
            .containsExactly("No hay ningún ciclo lectivo activo");
        assertThat(panel.motivoSinClases().texto()).startsWith("No hay ningún ciclo lectivo activo.");
    }

    @Test
    @DisplayName("calendario: un ciclo activo que ya terminó se avisa con su fecha")
    void calendario_cicloTerminadoYActivo() {
        when(cicloRepository.listarDelTenant(anyLong()))
            .thenReturn(List.of(ciclo(2025, EstadoCiclo.ACTIVO)));

        assertThat(service.armar().pendientes().get(0).titulo())
            .isEqualTo("El ciclo lectivo 2025 terminó el 31/12/2025 y sigue activo");
    }

    @Test
    @DisplayName("calendario: un ciclo activo que todavía no empezó se explica, pero no es un pendiente")
    void calendario_cicloQueTodaviaNoEmpezo() {
        when(cicloRepository.listarDelTenant(anyLong()))
            .thenReturn(List.of(ciclo(2027, EstadoCiclo.ACTIVO)));

        PanelInicioDto panel = service.armar();

        assertThat(panel.todoEnOrden()).as("no hay nada que hacer: hay que esperar").isTrue();
        assertThat(panel.motivoSinClases().texto()).startsWith("El ciclo lectivo 2027 empieza el 01/01/2027");
        assertThat(panel.motivoSinClases().problema()).isFalse();
    }

    @Test
    @DisplayName("equipo: sin ninguno autorizado se avisa, sin cantidad y solo para la institución")
    void sinEquipoAutorizado() {
        when(puestoRepository.contarHabilitados(TENANT_A)).thenReturn(0L);

        PanelInicioDto.Pendiente p = service.armar().pendientes().get(0);

        assertThat(p.titulo()).isEqualTo("Ningún equipo está autorizado para tomar asistencia");
        assertThat(p.cantidad()).isNull();
        assertThat(p.soloInstitucion()).isTrue();
    }

    @Test
    @DisplayName("día vacío: un feriado se explica con su motivo, sin nada que resolver")
    void motivo_feriado() {
        when(diaNoLaborableService.motivoSinClases(TENANT_A, LUNES))
            .thenReturn(Optional.of("Día del estudiante"));

        PanelInicioDto.MotivoSinClases m = service.armar().motivoSinClases();

        assertThat(m.texto()).isEqualTo("Hoy no hay clases: Día del estudiante.");
        assertThat(m.problema()).isFalse();
        assertThat(m.url()).isNull();
    }

    @Test
    @DisplayName("día vacío: si hubo clases y terminaron, lo dice")
    void motivo_clasesTerminadas() {
        when(horarioRepository.findActivosDelDiaConDocente(any(), any(), anyLong()))
            .thenReturn(List.of(horario(10L, docente(1L, "Pérez"), 8, 0, 10, 0)));

        assertThat(service.armar().motivoSinClases().texto())
            .isEqualTo("Las clases de hoy ya terminaron.");
    }

    @Test
    @DisplayName("día vacío: las clases en comisiones sin docente se cuentan y llevan a Comisiones")
    void motivo_clasesSinDocente() {
        when(horarioRepository.contarDelDiaSinDocente((byte) 1, LUNES, TENANT_A)).thenReturn(2L);

        PanelInicioDto.MotivoSinClases m = service.armar().motivoSinClases();

        assertThat(m.texto()).startsWith("Hoy hay 2 clases en comisiones sin docente asignado");
        assertThat(m.problema()).isTrue();
        assertThat(m.url()).isEqualTo("/comisiones");
        assertThat(m.soloInstitucion())
            .as("Comisiones la abre tambien el administrativo")
            .isFalse();
    }

    @Test
    @DisplayName("día vacío: entre dos períodos del ciclo lo dice, sin tratarlo como error")
    void motivo_entrePeriodos() {
        CicloLectivo c = ciclo(2026, EstadoCiclo.ACTIVO);
        c.getPeriodos().get(0).setFechaFin(LocalDate.of(2026, 5, 1));
        when(cicloRepository.listarDelTenant(anyLong())).thenReturn(List.of(c));

        PanelInicioDto.MotivoSinClases m = service.armar().motivoSinClases();

        assertThat(m.texto()).isEqualTo("Hoy no cae dentro de ningún período del ciclo 2026.");
        assertThat(m.problema()).isFalse();
    }

    @Test
    @DisplayName("día vacío: sin nada que lo explique, dice que ese día no hay clases cargadas")
    void motivo_sinClasesCargadas() {
        assertThat(service.armar().motivoSinClases().texto())
            .isEqualTo("Los lunes no hay clases cargadas.");
    }

    @Test
    @DisplayName("día vacío: con una clase por venir no hay nada que explicar")
    void motivo_soloConElDiaVacio() {
        when(horarioRepository.findActivosDelDiaConDocente(any(), any(), anyLong()))
            .thenReturn(List.of(horario(10L, docente(1L, "Pérez"), 14, 0, 16, 0)));

        assertThat(service.armar().motivoSinClases()).isNull();
    }

    @Test
    @DisplayName("pendientes: nombran los casos, hasta tres y el resto resumido")
    void pendientes_nombranLosCasos() {
        when(docenteRepository.listarVigentesDelTenant(any())).thenReturn(List.of(
            docente(1L, "Acosta"), docente(2L, "Benítez"), docente(3L, "Castro"), docente(4L, "Díaz")));

        PanelInicioDto.Pendiente p = service.armar().pendientes().get(0);

        assertThat(p.cualesResumidos())
            .as("el listado de docentes no dice a quien le falta: sin los nombres habia que "
                + "abrir las fichas una por una")
            .isEqualTo("Acosta, Nombre · Benítez, Nombre · Castro, Nombre y 1 más");
        assertThat(p.url()).isEqualTo("/docentes");
    }

    @Test
    @DisplayName("pendientes: con un solo docente se va derecho a su pantalla")
    void pendientes_unSoloDocenteVaASuFicha() {
        when(docenteRepository.listarVigentesDelTenant(any())).thenReturn(List.of(docente(7L, "Pérez")));

        PanelInicioDto.Pendiente p = service.armar().pendientes().get(0);

        assertThat(p.url()).isEqualTo("/docentes/7/editar");
        assertThat(p.cualesResumidos()).isEqualTo("Pérez, Nombre");
    }

    @Test
    @DisplayName("pase: trae lo que falta cargar, sin el calendario, el equipo ni las salidas")
    void clasesDeAhora_avisos() {
        when(cicloRepository.listarDelTenant(anyLong())).thenReturn(List.of());
        when(puestoRepository.contarHabilitados(anyLong())).thenReturn(0L);
        when(bloquePresenciaRepository.countPendientesDeCierre(TENANT_A)).thenReturn(2L);
        when(docenteRepository.listarVigentesDelTenant(any())).thenReturn(List.of(docente(1L, "Pérez")));

        ClasesDeAhoraDto clases = service.clasesDeAhora();

        assertThat(clases.avisos()).extracting(PanelInicioDto.Pendiente::titulo)
            .as("el calendario ya lo explica el motivo, si se ve el pase el equipo esta "
                + "autorizado, y una salida pendiente no traba el pase")
            .containsExactly("docentes sin consentimiento vigente");
        assertThat(clases.motivoSinClases().texto()).startsWith("No hay ningún ciclo lectivo activo");
    }

    @Test
    @DisplayName("pase: con una clase por venir no consulta los ciclos para explicar nada")
    void clasesDeAhora_sinMotivoNoConsultaCiclos() {
        when(horarioRepository.findActivosDelDiaConDocente(any(), any(), anyLong()))
            .thenReturn(List.of(horario(10L, docente(1L, "Pérez"), 14, 0, 16, 0)));

        assertThat(service.clasesDeAhora().motivoSinClases()).isNull();
        verify(cicloRepository, never()).listarDelTenant(anyLong());
    }

    // ========================================================================
    //  Helpers
    // ========================================================================

    // Un ciclo del ano entero, con un periodo Anual que lo cubre.
    private CicloLectivo ciclo(int anio, EstadoCiclo estado) {
        CicloLectivo c = DatosDePrueba.cicloAnualDelTenant(TENANT_A, anio);
        c.setEstado(estado);
        return c;
    }

    private Docente docente(Long id, String apellido) {
        Docente d = Docente.builder().persona(DatosDePrueba.personaConDni("3000000" + id, "Nombre", apellido)).id(id).activo(true).build();
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

    @Test
    @DisplayName("pendientes: las salidas sin registrar se anuncian primero")
    void pendientesSalidasSinRegistrar() {
        // Va primero porque es lo unico de la lista que hay que resolver hoy: los demas son
        // cargas incompletas de configuracion (RF-79).
        when(bloquePresenciaRepository.countPendientesDeCierre(TENANT_A)).thenReturn(3L);
        // Tiene que haber OTRO pendiente, si no el de salidas es el unico y quedaria primero
        // aunque el codigo lo agregara al final: el test no probaria el orden.
        Comision sinDocente = Comision.builder()
            .id(1L).codigo("A").materia(materia()).docenteAsignado(null).activo(true).build();
        when(comisionRepository.findActivasEnFecha(any(), anyLong())).thenReturn(List.of(sinDocente));
        when(horarioRepository.countByComisionIdAndActivoTrue(1L)).thenReturn(3L);

        PanelInicioDto panel = service.armar();

        assertThat(panel.pendientes()).hasSize(2);
        assertThat(panel.pendientes()).extracting(PanelInicioDto.Pendiente::titulo)
            .containsExactly("salidas sin registrar", "comisiones sin docente asignado");

        PanelInicioDto.Pendiente primero = panel.pendientes().get(0);
        assertThat(primero.cantidad()).isEqualTo(3L);
        assertThat(primero.url()).isEqualTo("/asistencias/bloques/pendientes");
        // El texto tiene que decir que esa hora no la observo nadie: es la diferencia entre
        // un dato medido y uno completado por el sistema.
        assertThat(primero.detalle()).contains("nadie la observó");
    }

    @Test
    @DisplayName("pendientes: sin salidas pendientes no se anuncia la fila")
    void sinSalidasPendientesNoHayFila() {
        when(bloquePresenciaRepository.countPendientesDeCierre(TENANT_A)).thenReturn(0L);

        PanelInicioDto panel = service.armar();

        assertThat(panel.pendientes())
            .noneMatch(pd -> pd.titulo().contains("salida"));
    }
}
