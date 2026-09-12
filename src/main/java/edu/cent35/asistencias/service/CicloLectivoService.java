package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.DiaNoLaborable;
import edu.cent35.asistencias.model.EstadoCiclo;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.PeriodoLectivo;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.CicloLectivoRepository;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.DiaNoLaborableRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.PeriodoLectivoRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Los años calendario de cursada y sus períodos (V023, V027): alta, edición del calendario,
 * cierre y reapertura, el borrado de lo que se cargó por error, y el copiado de la oferta de un
 * año al siguiente. Es lo que permite que la misma materia se dicte todos los años sin que cada
 * oferta pise a la anterior.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CicloLectivoService {

    // Las fechas de los mensajes van como se leen aca, no en ISO: "07/04/2026" y no "2026-04-07".
    private static final DateTimeFormatter DD_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CicloLectivoRepository cicloRepository;
    private final PeriodoLectivoRepository periodoRepository;
    private final ComisionRepository comisionRepository;
    private final HorarioRepository horarioRepository;
    // Para no dejar asistencias fuera de su periodo al mover fechas (V027).
    private final AsistenciaRepository asistenciaRepository;
    // Los dias sin clase que caen dentro del ciclo, para el detalle.
    private final DiaNoLaborableRepository diaNoLaborableRepository;
    // Para mostrar quien cerro o reabrio el ciclo: la tabla guarda el id pelado.
    private final UsuarioRepository usuarioRepository;

    // Reloj inyectable, mismo patron que CodigoVerificacionService: sin esto, probar que un
    // ciclo esta vigente o vencido exigiria esperar a que cambie el almanaque.
    private Clock clock = Clock.systemDefaultZone();

    // ========================================================================
    //  Lectura
    // ========================================================================

    // Todos los ciclos de la institucion, del mas nuevo al mas viejo, con sus periodos.
    @Transactional(readOnly = true)
    public List<CicloLectivo> listar() {
        return cicloRepository.listarDelTenant(TenantContext.getRequired());
    }

    /**
     * Un ciclo por id, validando el tenant.
     *
     * <p>Responde "no encontrado" y no "no autorizado" cuando el ciclo es de otra institución:
     * lo segundo confirmaría que ese id existe en algún lado (convención del proyecto).
     */
    @Transactional(readOnly = true)
    public CicloLectivo buscarPorId(Long id) {
        return cicloRepository.porIdEnTenant(TenantContext.getRequired(), id)
            .orElseThrow(() -> new EntityNotFoundException("Ciclo lectivo no encontrado: " + id));
    }

    /** El ciclo activo que contiene esa fecha, si lo hay. Vacío fuera del ciclo: en enero no hay clases. */
    @Transactional(readOnly = true)
    public Optional<CicloLectivo> activoEn(LocalDate fecha) {
        return cicloRepository.activoEnFecha(TenantContext.getRequired(), fecha);
    }

    /**
     * Qué ciclo mostrar cuando la pantalla no trae uno elegido.
     *
     * <p>Prefiere el que está corriendo hoy, y si no hay ninguno cae al más reciente. Sin este
     * segundo paso, una institución que todavía no abrió su ciclo vería la pantalla vacía sin
     * ninguna explicación, incluso teniendo el año siguiente ya armado.
     */
    @Transactional(readOnly = true)
    public Optional<CicloLectivo> cicloParaMostrar(Long cicloElegidoId) {
        if (cicloElegidoId != null) {
            return Optional.of(buscarPorId(cicloElegidoId));
        }
        Optional<CicloLectivo> enCurso = activoEn(LocalDate.now(clock));
        if (enCurso.isPresent()) {
            return enCurso;
        }
        return listar().stream().findFirst();     // listarDelTenant ya viene por anio DESC
    }

    /**
     * Todo lo que muestra la pantalla de un ciclo (V027): sus períodos con cuántas comisiones
     * cuelgan de cada uno, los días sin clase que caen adentro, quién lo cerró o lo reabrió, y
     * si se puede reabrir.
     *
     * <p>Se arma acá y no en el controlador porque lo que decide qué botones mostrar tiene que
     * ser lo mismo que después valida la acción: si la pantalla lo calculara por su lado, tarde
     * o temprano ofrecería un botón que el servicio rechaza.
     */
    @Transactional(readOnly = true)
    public DetalleCiclo detalle(Long id) {
        Long tenantId = TenantContext.getRequired();
        CicloLectivo ciclo = buscarPorId(id);

        // Una consulta por periodo: son dos o tres por ciclo, y contarComisiones ya lleva los
        // WHERE del tenant. Una agrupada ahorraria dos viajes a costa de otra query que cuidar.
        Map<Long, Long> comisionesPorPeriodo = new HashMap<>();
        for (PeriodoLectivo p : ciclo.getPeriodos()) {
            comisionesPorPeriodo.put(p.getId(), periodoRepository.contarComisiones(tenantId, p.getId()));
        }
        List<DiaNoLaborable> dias = diaNoLaborableRepository.entreFechas(
            tenantId, ciclo.getFechaInicio(), ciclo.getFechaFin());

        return new DetalleCiclo(ciclo, comisionesPorPeriodo, dias,
                                nombreDeUsuario(ciclo.getCerradoPor(), tenantId),
                                nombreDeUsuario(ciclo.getReabiertoPor(), tenantId),
                                motivoParaNoReabrir(ciclo, tenantId));
    }

    /**
     * Lo que muestra la pantalla de un ciclo.
     *
     * <p>Los métodos de abajo son los que consulta la plantilla para decidir qué botones
     * mostrar. Repiten las condiciones de {@link #borrar} y {@link #quitarPeriodo} a propósito,
     * y al lado de ellas: si una cambia, la otra tiene que cambiar igual.
     */
    public record DetalleCiclo(CicloLectivo ciclo,
                               Map<Long, Long> comisionesPorPeriodo,
                               List<DiaNoLaborable> diasSinClase,
                               String cerradoPor,
                               String reabiertoPor,
                               String motivoParaNoReabrir) {

        public long comisionesDe(Long periodoId) {
            return comisionesPorPeriodo.getOrDefault(periodoId, 0L);
        }

        public long totalDeComisiones() {
            return comisionesPorPeriodo.values().stream().mapToLong(Long::longValue).sum();
        }

        // Mismas condiciones que borrar(): en preparacion y sin ninguna comision colgando.
        public boolean sePuedeBorrar() {
            return ciclo.getEstado() == EstadoCiclo.PREPARACION && totalDeComisiones() == 0;
        }

        // Mismas condiciones que quitarPeriodo(): ciclo abierto, sin comisiones, y no el unico.
        public boolean sePuedeQuitar(Long periodoId) {
            return ciclo.getEstado().admiteCambiosDeEstructura()
                && ciclo.getPeriodos().size() > 1
                && comisionesDe(periodoId) == 0;
        }

        public boolean sePuedeReabrir() {
            return ciclo.getEstado() == EstadoCiclo.CERRADO && motivoParaNoReabrir == null;
        }
    }

    // ========================================================================
    //  Alta y edicion
    // ========================================================================

    /**
     * Crea el ciclo de un año con sus períodos.
     *
     * <p>Los períodos vienen en la misma operación y no aparte porque un ciclo sin ninguno no
     * sirve para nada: no se le puede colgar una comisión, así que quedaría como una fila que
     * hay que acordarse de completar después.
     */
    @Transactional
    public CicloLectivo crear(Short anio, LocalDate inicio, LocalDate fin,
                              List<PeriodoLectivo> periodos) {
        Long tenantId = TenantContext.getRequired();
        validarAnio(anio);
        validarRango(inicio, fin);

        if (cicloRepository.findByInstitucionIdAndAnio(tenantId, anio).isPresent()) {
            throw new IllegalArgumentException(
                "Ya existe un ciclo lectivo " + anio + " en esta institución.");
        }
        if (periodos == null || periodos.isEmpty()) {
            throw new IllegalArgumentException(
                "El ciclo necesita al menos un período. Si no dividís el año, cargá uno solo "
                + "que se llame \"Anual\" y cubra el ciclo entero.");
        }

        CicloLectivo ciclo = CicloLectivo.builder()
            .anio(anio)
            .fechaInicio(inicio)
            .fechaFin(fin)
            .estado(EstadoCiclo.PREPARACION)
            .build();
        ciclo.setInstitucionId(tenantId);

        for (PeriodoLectivo p : periodos) {
            validarPeriodoDentroDelCiclo(p, ciclo);
            ciclo.agregarPeriodo(p);
        }
        validarNombresDePeriodoUnicos(ciclo.getPeriodos());

        CicloLectivo guardado = cicloRepository.save(ciclo);
        log.info("Ciclo lectivo creado: id={}, anio={}, periodos={}, institucion={}",
                 guardado.getId(), anio, guardado.getPeriodos().size(), tenantId);
        return guardado;
    }

    /**
     * Corrige el año y las fechas del ciclo. Los períodos se editan por su cuenta.
     *
     * <p><b>El año, solo en preparación.</b> Un ciclo que ya corrió tiene asistencias de ese
     * año: cambiarle el número diría que ocurrieron en otro. Mientras se arma, en cambio, un año
     * mal cargado es un error de tipeo, y sin esto la única salida sería borrarlo. {@code null}
     * deja el que estaba: la pantalla no lo manda cuando el campo va deshabilitado.
     *
     * <p><b>Las fechas, mientras no esté cerrado</b>, con dos límites: sus períodos tienen que
     * seguir cayendo adentro, y el rango nuevo no puede dejar afuera días que ya tienen
     * asistencias. Mover fechas no crea ni borra nada hacia atrás —el job de ausencias trabaja
     * solo sobre el día en curso—, así que lo único que puede romper es esa coherencia.
     */
    @Transactional
    public CicloLectivo actualizar(Long id, Short anio, LocalDate inicio, LocalDate fin) {
        Long tenantId = TenantContext.getRequired();
        CicloLectivo ciclo = buscarPorId(id);
        exigirEstructuraEditable(ciclo);
        validarRango(inicio, fin);

        boolean cambiaElAnio = anio != null && !anio.equals(ciclo.getAnio());
        if (cambiaElAnio) {
            if (ciclo.getEstado() != EstadoCiclo.PREPARACION) {
                throw new IllegalArgumentException(
                    "El año solo se corrige mientras el ciclo está en preparación. Este ya "
                    + "corrió: cambiarle el año diría que sus asistencias ocurrieron en otro.");
            }
            validarAnio(anio);
            if (cicloRepository.findByInstitucionIdAndAnio(tenantId, anio).isPresent()) {
                throw new IllegalArgumentException(
                    "Ya existe un ciclo lectivo " + anio + " en esta institución.");
            }
        }

        for (PeriodoLectivo p : ciclo.getPeriodos()) {
            if (p.getFechaInicio().isBefore(inicio) || p.getFechaFin().isAfter(fin)) {
                throw new IllegalArgumentException(
                    "El período \"" + p.getNombre() + "\" (" + fecha(p.getFechaInicio()) + " al "
                    + fecha(p.getFechaFin()) + ") quedaría fuera del ciclo. Ajustalo primero.");
            }
        }
        // Con los periodos adentro, esto solo puede saltar por una asistencia que ya estaba
        // fuera de su periodo pero dentro del ciclo. Es raro, pero sacarla del ciclo tambien
        // la dejaria fuera de su ano.
        exigirQueNoQuedenAsistenciasAfuera("El ciclo",
            ciclo.getFechaInicio(), ciclo.getFechaFin(), inicio, fin,
            (desde, hasta) -> asistenciaRepository.primeraDelCicloEntre(tenantId, id, desde, hasta));

        Short anioAnterior = ciclo.getAnio();
        if (cambiaElAnio) {
            ciclo.setAnio(anio);
        }
        ciclo.setFechaInicio(inicio);
        ciclo.setFechaFin(fin);
        cicloRepository.save(ciclo);
        log.info("Ciclo lectivo actualizado: id={}, anio {} -> {}, {} a {}",
                 id, anioAnterior, ciclo.getAnio(), inicio, fin);
        return ciclo;
    }

    // ========================================================================
    //  Periodos
    // ========================================================================

    /**
     * Suma un período a un ciclo que ya existe (V027).
     *
     * <p>Con las mismas reglas que el alta —nombre único, dentro del ciclo— y al final de la
     * lista: el orden es el de carga, que casi siempre es el cronológico.
     */
    @Transactional
    public PeriodoLectivo agregarPeriodo(Long cicloId, String nombre, LocalDate inicio, LocalDate fin) {
        CicloLectivo ciclo = buscarPorId(cicloId);
        exigirEstructuraEditable(ciclo);

        PeriodoLectivo nuevo = PeriodoLectivo.builder()
            .nombre(nombre == null ? null : nombre.trim())
            .fechaInicio(inicio)
            .fechaFin(fin)
            .orden((short) (ciclo.getPeriodos().stream()
                .mapToInt(PeriodoLectivo::getOrden).max().orElse(0) + 1))
            .build();
        validarPeriodoDentroDelCiclo(nuevo, ciclo);
        exigirNombreLibre(ciclo, nuevo.getNombre(), null);

        // Se guarda por su repositorio y no por el del ciclo: save() sobre un ciclo que ya
        // existe es un merge, y el merge copia el periodo nuevo en vez de persistir este.
        ciclo.agregarPeriodo(nuevo);
        PeriodoLectivo guardado = periodoRepository.save(nuevo);
        log.info("Periodo agregado: ciclo={}, nombre='{}', {} a {}",
                 cicloId, guardado.getNombre(), inicio, fin);
        return guardado;
    }

    /**
     * Renombra un período o le cambia las fechas (V027).
     *
     * <p>Las fechas tienen el mismo límite que las del ciclo: no pueden dejar afuera días que
     * ya tienen asistencias de sus comisiones. El nombre, además de único, es lo que empareja un
     * año con el siguiente al copiar la oferta. La pantalla lo advierte, pero renombrar no se
     * prohíbe: a veces el nombre viejo era el error.
     */
    @Transactional
    public void editarPeriodo(Long cicloId, Long periodoId, String nombre,
                              LocalDate inicio, LocalDate fin) {
        Long tenantId = TenantContext.getRequired();
        CicloLectivo ciclo = buscarPorId(cicloId);
        exigirEstructuraEditable(ciclo);
        PeriodoLectivo periodo = periodoDelCiclo(ciclo, periodoId);

        PeriodoLectivo propuesto = PeriodoLectivo.builder()
            .nombre(nombre == null ? null : nombre.trim())
            .fechaInicio(inicio)
            .fechaFin(fin)
            .build();
        validarPeriodoDentroDelCiclo(propuesto, ciclo);
        exigirNombreLibre(ciclo, propuesto.getNombre(), periodo.getId());
        exigirQueNoQuedenAsistenciasAfuera("El período \"" + periodo.getNombre() + "\"",
            periodo.getFechaInicio(), periodo.getFechaFin(), inicio, fin,
            (desde, hasta) -> asistenciaRepository.primeraDelPeriodoEntre(tenantId, periodoId, desde, hasta));

        String nombreAnterior = periodo.getNombre();
        periodo.setNombre(propuesto.getNombre());
        periodo.setFechaInicio(inicio);
        periodo.setFechaFin(fin);
        periodoRepository.save(periodo);
        log.info("Periodo actualizado: ciclo={}, '{}' -> '{}', {} a {}",
                 cicloId, nombreAnterior, periodo.getNombre(), inicio, fin);
    }

    /**
     * Quita un período vacío (V027). Es un DELETE de verdad, no una baja lógica.
     *
     * <p>Misma excepción que los días sin clase: nada apunta a un período sin comisiones, así
     * que marcarlo inactivo solo dejaría una opción muerta en el combo de Comisiones. Con una
     * sola comisión colgando —aunque esté dada de baja— ya no: esa comisión es historia y
     * apunta a él.
     *
     * <p>El último no se quita: un ciclo sin períodos no admite comisiones, que es la misma
     * razón por la que el alta exige al menos uno.
     */
    @Transactional
    public void quitarPeriodo(Long cicloId, Long periodoId) {
        Long tenantId = TenantContext.getRequired();
        CicloLectivo ciclo = buscarPorId(cicloId);
        exigirEstructuraEditable(ciclo);
        PeriodoLectivo periodo = periodoDelCiclo(ciclo, periodoId);

        long comisiones = periodoRepository.contarComisiones(tenantId, periodoId);
        if (comisiones > 0) {
            throw new IllegalArgumentException(
                "El período \"" + periodo.getNombre() + "\" tiene " + comisiones
                + " comisión(es), contando las dadas de baja: no se puede quitar. Si sobra, "
                + "pasá esas comisiones a otro período desde Comisiones.");
        }
        if (ciclo.getPeriodos().size() == 1) {
            throw new IllegalArgumentException(
                "Es el único período del ciclo, y un ciclo sin períodos no admite comisiones. "
                + "Si el nombre o las fechas están mal, corregilos.");
        }

        ciclo.getPeriodos().remove(periodo);     // orphanRemoval: el DELETE sale en el flush
        log.info("Periodo quitado: ciclo={}, nombre='{}'", cicloId, periodo.getNombre());
    }

    // ========================================================================
    //  Cambios de estado
    // ========================================================================

    /**
     * Pone el ciclo en curso.
     *
     * <p><b>Solo uno activo a la vez.</b> Con dos, el pase no tendría cómo decidir contra cuál
     * registrar una marca, y el job de ausencias generaría dos por la misma clase. El ciclo que
     * se está armando para el año que viene vive en PREPARACION justamente para poder convivir
     * con el que está corriendo.
     */
    @Transactional
    public void activar(Long id) {
        Long tenantId = TenantContext.getRequired();
        CicloLectivo ciclo = buscarPorId(id);

        if (ciclo.getEstado() == EstadoCiclo.ACTIVO) {
            throw new IllegalArgumentException("Ese ciclo ya está activo.");
        }
        if (ciclo.getEstado() == EstadoCiclo.CERRADO) {
            throw new IllegalArgumentException(
                "Un ciclo cerrado no se activa directamente: primero hay que reabrirlo.");
        }
        if (cicloRepository.countByInstitucionIdAndEstado(tenantId, EstadoCiclo.ACTIVO) > 0) {
            throw new IllegalArgumentException(
                "Ya hay un ciclo activo. Cerralo antes de activar este: con dos en curso, una "
                + "misma clase generaría dos registros.");
        }

        ciclo.setEstado(EstadoCiclo.ACTIVO);
        cicloRepository.save(ciclo);
        log.info("Ciclo lectivo activado: id={}, anio={}", id, ciclo.getAnio());
    }

    /**
     * Cierra el ciclo: la estructura queda congelada.
     *
     * <p><b>Las asistencias no.</b> Un reclamo o una inspección llegan casi siempre después de
     * terminado el año, y no poder justificar una ausencia de marzo en febrero siguiente
     * convertiría el cierre en una trampa. Lo que se congela son comisiones, horarios y
     * períodos, que es lo que define la oferta.
     */
    @Transactional
    public void cerrar(Long id, Long usuarioActualId) {
        CicloLectivo ciclo = buscarPorId(id);
        if (ciclo.getEstado() == EstadoCiclo.CERRADO) {
            throw new IllegalArgumentException("Ese ciclo ya está cerrado.");
        }

        ciclo.setEstado(EstadoCiclo.CERRADO);
        ciclo.setCerradoEn(LocalDateTime.now(clock));
        ciclo.setCerradoPor(usuarioActualId);
        cicloRepository.save(ciclo);
        log.info("Ciclo lectivo cerrado: id={}, anio={}, por usuario={}",
                 id, ciclo.getAnio(), usuarioActualId);
    }

    /**
     * Reabre el último ciclo cerrado (V027). Vuelve a PREPARACIÓN, no a ACTIVO.
     *
     * <p><b>Para qué.</b> Cerrar por error el ciclo en curso dejaba a la institución sin tomar
     * asistencia el resto del año: el pase solo busca clases en el ciclo activo, y no se podía
     * ni reactivarlo ni crear otro del mismo año.
     *
     * <p><b>Por qué a preparación.</b> El ciclo pudo haberse cerrado sin llegar a correr: a
     * ACTIVO quedaría tomando asistencia sin que nadie lo pidiera, y de ACTIVO no se vuelve.
     * Desde preparación se activa como siempre, con su propia confirmación.
     *
     * <p>El cierre anterior no se borra: queda como registro, y al lado quién lo reabrió.
     */
    @Transactional
    public void reabrir(Long id, Long usuarioActualId) {
        Long tenantId = TenantContext.getRequired();
        CicloLectivo ciclo = buscarPorId(id);
        String motivo = motivoParaNoReabrir(ciclo, tenantId);
        if (motivo != null) {
            throw new IllegalArgumentException(motivo);
        }

        ciclo.setEstado(EstadoCiclo.PREPARACION);
        ciclo.setReabiertoEn(LocalDateTime.now(clock));
        ciclo.setReabiertoPor(usuarioActualId);
        cicloRepository.save(ciclo);
        log.info("Ciclo lectivo reabierto: id={}, anio={}, cerrado el {}, por usuario={}",
                 id, ciclo.getAnio(), ciclo.getCerradoEn(), usuarioActualId);
    }

    // ========================================================================
    //  Borrar lo cargado por error
    // ========================================================================

    /**
     * Borra un ciclo cargado por error (V027). DELETE de verdad, con sus períodos.
     *
     * <p>Solo en preparación y sin ninguna comisión: es la misma excepción a la baja lógica que
     * tienen los días sin clase —nada apunta a esas filas—. Un ciclo que llegó a correr o que ya
     * tiene oferta cargada es historia de la institución y no se borra: si algo está mal, se
     * corrige.
     */
    @Transactional
    public void borrar(Long id) {
        Long tenantId = TenantContext.getRequired();
        CicloLectivo ciclo = buscarPorId(id);

        if (ciclo.getEstado() != EstadoCiclo.PREPARACION) {
            throw new IllegalArgumentException(
                "Solo se borra un ciclo en preparación. Uno que ya corrió o que está cerrado es "
                + "historia de la institución: si algo está mal, corregilo.");
        }
        long comisiones = 0;
        for (PeriodoLectivo p : ciclo.getPeriodos()) {
            comisiones += periodoRepository.contarComisiones(tenantId, p.getId());
        }
        if (comisiones > 0) {
            throw new IllegalArgumentException(
                "El ciclo " + ciclo.getAnio() + " tiene " + comisiones + " comisión(es), "
                + "contando las dadas de baja: no se puede borrar. Si el año o las fechas están "
                + "mal, corregilos.");
        }

        cicloRepository.delete(ciclo);
        log.info("Ciclo lectivo borrado: id={}, anio={}, institucion={}",
                 id, ciclo.getAnio(), tenantId);
    }

    // ========================================================================
    //  Copiar la oferta al ano siguiente
    // ========================================================================

    /**
     * Copia comisiones y horarios de un ciclo a otro.
     *
     * <p><b>Para qué.</b> Sin esto, abrir marzo significa volver a cargar a mano cada comisión
     * y cada franja horaria de toda la institución. La oferta cambia poco de un año al otro:
     * lo razonable es copiarla y corregir las diferencias, no empezar de cero.
     *
     * <p><b>Qué copia y qué no.</b> Copia la comisión con su materia, su código, su cupo y su
     * docente asignado, y los horarios activos de cada una. <b>No</b> copia asistencias,
     * bloques ni nada del historial: eso pertenece al año en que ocurrió.
     *
     * <p><b>Los períodos se emparejan por nombre.</b> Una comisión del "1er cuatrimestre" de
     * 2026 va al "1er cuatrimestre" de 2027. Si el ciclo destino no tiene un período con ese
     * nombre, esa comisión se saltea y se cuenta aparte, en vez de meterla en cualquier otro:
     * poner una materia cuatrimestral en un período anual cambia lo que el sistema espera de
     * ella todo el año.
     *
     * <p>Se puede correr más de una vez sin duplicar: las comisiones que ya existen en el
     * destino —misma materia, mismo código, mismo período— se saltean.
     *
     * @return qué se copió y qué se dejó afuera
     */
    @Transactional
    public ResultadoCopia copiarOferta(Long cicloOrigenId, Long cicloDestinoId) {
        Long tenantId = TenantContext.getRequired();

        CicloLectivo origen  = buscarPorId(cicloOrigenId);
        CicloLectivo destino = buscarPorId(cicloDestinoId);

        if (origen.getId().equals(destino.getId())) {
            throw new IllegalArgumentException("El ciclo de origen y el de destino son el mismo.");
        }
        exigirEstructuraEditable(destino);

        // Los periodos del destino, por nombre normalizado, para emparejarlos con los del origen.
        Map<String, PeriodoLectivo> destinoPorNombre = new HashMap<>();
        for (PeriodoLectivo p : destino.getPeriodos()) {
            destinoPorNombre.put(normalizar(p.getNombre()), p);
        }

        List<Comision> aCopiar = comisionRepository.findDelCiclo(cicloOrigenId, tenantId);
        int copiadas = 0;
        int horariosCopiados = 0;
        List<String> sinPeriodoEquivalente = new ArrayList<>();

        for (Comision original : aCopiar) {
            if (Boolean.FALSE.equals(original.getActivo())) {
                continue;                          // una comision dada de baja no se reofrece
            }
            PeriodoLectivo destinoDelPeriodo =
                destinoPorNombre.get(normalizar(original.getPeriodo().getNombre()));

            if (destinoDelPeriodo == null) {
                sinPeriodoEquivalente.add(
                    original.getMateria().getNombre() + " " + original.getCodigo()
                    + " (" + original.getPeriodo().getNombre() + ")");
                continue;
            }
            if (comisionRepository.existsByMateriaIdAndCodigoAndPeriodoId(
                    original.getMateria().getId(), original.getCodigo(), destinoDelPeriodo.getId())) {
                continue;                          // ya se copio en una corrida anterior
            }

            Comision copia = Comision.builder()
                .materia(original.getMateria())
                .codigo(original.getCodigo())
                .docenteAsignado(original.getDocenteAsignado())
                .periodo(destinoDelPeriodo)
                .activo(true)
                .build();
            Comision guardada = comisionRepository.save(copia);
            copiadas++;

            for (Horario h : horarioRepository.findByComisionIdOrderByDiaSemanaAscHoraInicioAsc(
                    original.getId())) {
                if (Boolean.FALSE.equals(h.getActivo())) {
                    continue;
                }
                horarioRepository.save(Horario.builder()
                    .comision(guardada)
                    .diaSemana(h.getDiaSemana())
                    .horaInicio(h.getHoraInicio())
                    .horaFin(h.getHoraFin())
                    .toleranciaMin(h.getToleranciaMin())
                    .activo(true)
                    .build());
                horariosCopiados++;
            }
        }

        log.info("Oferta copiada: ciclo {} -> {}, comisiones={}, horarios={}, sin periodo={}",
                 origen.getAnio(), destino.getAnio(), copiadas, horariosCopiados,
                 sinPeriodoEquivalente.size());

        return new ResultadoCopia(copiadas, horariosCopiados, sinPeriodoEquivalente);
    }

    /** Qué dejó el copiado, para poder contarlo en pantalla en vez de decir solo "listo". */
    public record ResultadoCopia(int comisiones, int horarios, List<String> sinPeriodoEquivalente) {

        public boolean hayPendientes() {
            return !sinPeriodoEquivalente.isEmpty();
        }
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    /**
     * Corta si el ciclo ya no admite cambios de estructura.
     *
     * <p>Lo usan también otros servicios —comisiones, horarios— antes de guardar: la regla del
     * cierre no sirve de nada si solo la aplica la pantalla de ciclos.
     */
    public void exigirEstructuraEditable(CicloLectivo ciclo) {
        if (!ciclo.getEstado().admiteCambiosDeEstructura()) {
            throw new IllegalArgumentException(
                "El ciclo " + ciclo.getAnio() + " está cerrado: su oferta no se puede cambiar. "
                + "Las asistencias de ese año sí se pueden seguir corrigiendo.");
        }
    }

    /**
     * Por qué no se puede reabrir este ciclo, o null si se puede.
     *
     * <p>Una sola fuente para las dos preguntas: {@link #reabrir} la usa para validar y
     * {@link #detalle} para explicar en pantalla por qué no está el botón.
     */
    private String motivoParaNoReabrir(CicloLectivo ciclo, Long tenantId) {
        if (ciclo.getEstado() != EstadoCiclo.CERRADO) {
            return "Ese ciclo no está cerrado.";
        }
        if (cicloRepository.countByInstitucionIdAndEstado(tenantId, EstadoCiclo.ACTIVO) > 0) {
            return "Hay otro ciclo activo. Reabrir este dejaría editable la oferta de un año "
                + "terminado mientras corre el siguiente.";
        }
        Optional<CicloLectivo> ultimo = cicloRepository
            .findFirstByInstitucionIdAndEstadoOrderByCerradoEnDescIdDesc(tenantId, EstadoCiclo.CERRADO);
        if (ultimo.isPresent() && !ultimo.get().getId().equals(ciclo.getId())) {
            return "Solo se puede reabrir el último ciclo que se cerró, que es el "
                + ultimo.get().getAnio() + ": reabrir uno anterior ya no es corregir un error.";
        }
        return null;
    }

    /**
     * Corta si mover un rango de [antes] a [después] deja afuera algún día que ya tiene
     * asistencias.
     *
     * <p>Se miran solo los tramos que se recortan —del inicio viejo al nuevo, y del fin nuevo
     * al viejo—, no todo lo que queda fuera del rango nuevo. Así una asistencia que ya estaba
     * afuera antes de editar no traba la edición para siempre: lo que se protege es no empeorar
     * la coherencia, no exigir una que nunca existió.
     */
    private void exigirQueNoQuedenAsistenciasAfuera(
            String que, LocalDate antesDesde, LocalDate antesHasta,
            LocalDate despuesDesde, LocalDate despuesHasta,
            BiFunction<LocalDate, LocalDate, LocalDate> primeraEntre) {

        LocalDate afuera = null;
        if (despuesDesde.isAfter(antesDesde)) {
            LocalDate hasta = despuesDesde.minusDays(1);
            afuera = primeraEntre.apply(antesDesde, hasta.isAfter(antesHasta) ? antesHasta : hasta);
        }
        if (afuera == null && despuesHasta.isBefore(antesHasta)) {
            LocalDate desde = despuesHasta.plusDays(1);
            afuera = primeraEntre.apply(desde.isBefore(antesDesde) ? antesDesde : desde, antesHasta);
        }
        if (afuera != null) {
            throw new IllegalArgumentException(
                que + " dejaría afuera el " + fecha(afuera) + ", que ya tiene asistencias "
                + "registradas. Las asistencias no cambian de fecha: el rango nuevo tiene que "
                + "seguir cubriéndolas.");
        }
    }

    /**
     * El período de ese ciclo, o "no encontrado".
     *
     * <p>Se busca en la lista del ciclo, que ya viene validado por tenant, y no por
     * {@code findById}: así un período de otro ciclo —o de otra institución— no se puede
     * editar poniendo su id en la dirección de este.
     */
    private PeriodoLectivo periodoDelCiclo(CicloLectivo ciclo, Long periodoId) {
        return ciclo.getPeriodos().stream()
            .filter(p -> p.getId().equals(periodoId))
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Período no encontrado: " + periodoId));
    }

    // El usuario de quien cerro o reabrio, para mostrarlo. Null si no hay nadie registrado o si
    // la cuenta es de otra institucion: findById no pasa por el filtro de tenant.
    private String nombreDeUsuario(Long usuarioId, Long tenantId) {
        if (usuarioId == null) {
            return null;
        }
        return usuarioRepository.findById(usuarioId)
            .filter(u -> tenantId.equals(u.getInstitucionId()))
            .map(Usuario::getUsername)
            .orElse(null);
    }

    // El mismo rango que el CHECK de la base: sin esto, un ano mal tipeado llegaria hasta el
    // INSERT y volveria como un error de base que nadie entiende.
    private void validarAnio(Short anio) {
        if (anio == null || anio < 2000 || anio > 2200) {
            throw new IllegalArgumentException(
                "El año tiene que ser un año calendario, entre 2000 y 2200.");
        }
    }

    private void validarRango(LocalDate inicio, LocalDate fin) {
        if (inicio == null || fin == null) {
            throw new IllegalArgumentException("Faltan la fecha de inicio o la de fin.");
        }
        if (fin.isBefore(inicio)) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la de inicio.");
        }
    }

    private void validarPeriodoDentroDelCiclo(PeriodoLectivo p, CicloLectivo ciclo) {
        if (p.getNombre() == null || p.getNombre().isBlank()) {
            throw new IllegalArgumentException("Cada período necesita un nombre.");
        }
        if (p.getNombre().length() > 60) {
            throw new IllegalArgumentException("El nombre del período no puede pasar de 60 caracteres.");
        }
        validarRango(p.getFechaInicio(), p.getFechaFin());
        if (p.getFechaInicio().isBefore(ciclo.getFechaInicio())
            || p.getFechaFin().isAfter(ciclo.getFechaFin())) {
            throw new IllegalArgumentException(
                "El período \"" + p.getNombre() + "\" tiene que caer dentro del ciclo ("
                + fecha(ciclo.getFechaInicio()) + " al " + fecha(ciclo.getFechaFin()) + ").");
        }
    }

    // Dos periodos con el mismo nombre en un ciclo hacen que el copiado no sepa a cual apuntar,
    // ademas de que la base lo rechaza por el UNIQUE. Se avisa acá para que el mensaje se entienda.
    private void validarNombresDePeriodoUnicos(List<PeriodoLectivo> periodos) {
        List<String> vistos = new ArrayList<>();
        for (PeriodoLectivo p : periodos) {
            String n = normalizar(p.getNombre());
            if (vistos.contains(n)) {
                throw new IllegalArgumentException(
                    "Hay dos períodos que se llaman \"" + p.getNombre() + "\". Los nombres "
                    + "tienen que distinguirse: son lo que empareja un año con el siguiente.");
            }
            vistos.add(n);
        }
    }

    // Lo mismo contra los periodos que el ciclo ya tiene, salvo el que se esta editando: ese
    // puede quedarse con su nombre, o cambiarle solo las mayusculas.
    private void exigirNombreLibre(CicloLectivo ciclo, String nombre, Long exceptoPeriodoId) {
        for (PeriodoLectivo otro : ciclo.getPeriodos()) {
            if (!otro.getId().equals(exceptoPeriodoId)
                && normalizar(otro.getNombre()).equals(normalizar(nombre))) {
                throw new IllegalArgumentException(
                    "Ya hay un período que se llama \"" + otro.getNombre() + "\" en este ciclo. "
                    + "Los nombres tienen que distinguirse: son lo que empareja un año con el "
                    + "siguiente.");
            }
        }
    }

    private String normalizar(String nombre) {
        return nombre == null ? "" : nombre.trim().toLowerCase();
    }

    private static String fecha(LocalDate f) {
        return f.format(DD_MM_AAAA);
    }

    // Solo para tests: fija el reloj y permite probar el ciclo vigente sin esperar al almanaque.
    void setClock(Clock clock) {
        this.clock = clock;
    }
}
