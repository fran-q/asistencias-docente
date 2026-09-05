package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.EstadoCiclo;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.PeriodoLectivo;
import edu.cent35.asistencias.repository.CicloLectivoRepository;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.PeriodoLectivoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Los años calendario de cursada y sus períodos (V023): alta, edición, cierre, y el copiado de
 * la oferta de un año al siguiente. Es lo que permite que la misma materia se dicte todos los
 * años sin que cada oferta pise a la anterior.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CicloLectivoService {

    private final CicloLectivoRepository cicloRepository;
    private final PeriodoLectivoRepository periodoRepository;
    private final ComisionRepository comisionRepository;
    private final HorarioRepository horarioRepository;

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
     * Cambia las fechas del ciclo. Los períodos se editan por su cuenta.
     *
     * <p>Achicar el ciclo por debajo de alguno de sus períodos se rechaza: un período que
     * empieza antes de su ciclo o termina después es una contradicción que después habría que
     * resolver en cada consulta.
     */
    @Transactional
    public CicloLectivo actualizarFechas(Long id, LocalDate inicio, LocalDate fin) {
        CicloLectivo ciclo = buscarPorId(id);
        exigirEstructuraEditable(ciclo);
        validarRango(inicio, fin);

        for (PeriodoLectivo p : ciclo.getPeriodos()) {
            if (p.getFechaInicio().isBefore(inicio) || p.getFechaFin().isAfter(fin)) {
                throw new IllegalArgumentException(
                    "El período \"" + p.getNombre() + "\" (" + p.getFechaInicio() + " a "
                    + p.getFechaFin() + ") quedaría fuera del ciclo. Ajustalo primero.");
            }
        }

        ciclo.setFechaInicio(inicio);
        ciclo.setFechaFin(fin);
        cicloRepository.save(ciclo);
        log.info("Ciclo lectivo actualizado: id={}, {} a {}", id, inicio, fin);
        return ciclo;
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
                "Un ciclo cerrado no se reabre. Si hace falta, creá uno nuevo.");
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

    private void validarRango(LocalDate inicio, LocalDate fin) {
        if (inicio == null || fin == null) {
            throw new IllegalArgumentException("El ciclo necesita fecha de inicio y de fin.");
        }
        if (fin.isBefore(inicio)) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la de inicio.");
        }
    }

    private void validarPeriodoDentroDelCiclo(PeriodoLectivo p, CicloLectivo ciclo) {
        if (p.getNombre() == null || p.getNombre().isBlank()) {
            throw new IllegalArgumentException("Cada período necesita un nombre.");
        }
        validarRango(p.getFechaInicio(), p.getFechaFin());
        if (p.getFechaInicio().isBefore(ciclo.getFechaInicio())
            || p.getFechaFin().isAfter(ciclo.getFechaFin())) {
            throw new IllegalArgumentException(
                "El período \"" + p.getNombre() + "\" tiene que caer dentro del ciclo ("
                + ciclo.getFechaInicio() + " a " + ciclo.getFechaFin() + ").");
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

    private String normalizar(String nombre) {
        return nombre == null ? "" : nombre.trim().toLowerCase();
    }

    // Solo para tests: fija el reloj y permite probar el ciclo vigente sin esperar al almanaque.
    void setClock(Clock clock) {
        this.clock = clock;
    }
}
