package edu.cent35.asistencias.service;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.MateriaRepository;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.config.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cent35.asistencias.model.PeriodoLectivo;
import edu.cent35.asistencias.repository.PeriodoLectivoRepository;
import java.time.LocalDate;
import java.util.List;

/**
 * ABM de las comisiones del tenant actual (RF-13), con docente asignado opcional.
 * Comisión no lleva institucion_id propio: su tenant sale de la materia padre, así que los
 * listados van por JOIN contra materia y los findById validan la cadena a mano.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComisionService {

    private final ComisionRepository comisionRepository;
    private final PeriodoLectivoRepository periodoRepository;
    // Para negarse a tocar la oferta de un ciclo cerrado. La regla vive en CicloLectivoService
    // y se consulta desde aca: si solo la aplicara la pantalla de ciclos, no serviria de nada.
    private final CicloLectivoService cicloLectivoService;
    private final MateriaRepository materiaRepository;
    private final HorarioRepository horarioRepository;
    private final DocenteRepository docenteRepository;

    @Transactional(readOnly = true)
    // Lista las comisiones del tenant, resolviendo el tenant por JOIN con materia.
    public List<Comision> listar() {
        Long tenantId = TenantContext.getRequired();
        List<Comision> comisiones = comisionRepository.findAllDelTenant(tenantId);
        // Touch lazy: materia + carrera + docente asignado (necesarios para el listado)
        comisiones.forEach(c -> {
            if (c.getMateria() != null) {
                c.getMateria().getCodigo();
                if (c.getMateria().getCarrera() != null) {
                    c.getMateria().getCarrera().getCodigo();
                }
            }
            if (c.getDocenteAsignado() != null) c.getDocenteAsignado().getPersona().getDni();
        });
        return comisiones;
    }

    @Transactional(readOnly = true)
    // Busca por id validando el tenant a través de la materia padre.
    public Comision buscarPorId(Long id) {
        Long tenantId = TenantContext.getRequired();
        Comision c = comisionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Comision no encontrada: " + id));
        // Validar tenant via materia padre
        if (c.getMateria() == null || !tenantId.equals(c.getMateria().getInstitucionId())) {
            log.warn("Cross-tenant blocked: tenant {} intento acceder comision id={}", tenantId, id);
            throw new EntityNotFoundException("Comision no encontrada");
        }
        // Touch para inicializar lazy
        c.getMateria().getCodigo();
        if (c.getMateria().getCarrera() != null) c.getMateria().getCarrera().getCodigo();
        if (c.getDocenteAsignado() != null) c.getDocenteAsignado().getPersona().getDni();
        return c;
    }

    // Docentes activos del tenant - para el selector del form.
    @Transactional(readOnly = true)
    public List<Docente> docentesActivosParaSelector() {
        return docenteRepository.listarVigentesDelTenant(TenantContext.getRequired());
    }

    // Solo las activas, para poblar el combo del formulario de horarios.
    @Transactional(readOnly = true)
    public List<Comision> comisionesActivasParaSelector() {
        Long tenantId = TenantContext.getRequired();
        // Acotado al ciclo de hoy: ofrecer comisiones de un ano que ya termino deja elegir
        // una clase que no existe, y el rechazo llegaria recien al guardar.
        List<Comision> cs = comisionRepository.findActivasEnFecha(LocalDate.now(), tenantId);
        cs.forEach(c -> {
            if (c.getMateria() != null) {
                c.getMateria().getCodigo();
                if (c.getMateria().getCarrera() != null) c.getMateria().getCarrera().getCodigo();
            }
        });
        return cs;
    }

    /**
     * Trae el período validando el tenant, y rechaza los de ciclos cerrados.
     *
     * <p>Responde "no encontrado" si es de otra institución, igual que el resto: decir "no
     * autorizado" confirmaría que ese id existe en algún lado.
     */
    private PeriodoLectivo obtenerPeriodoValidado(Long periodoId, Long tenantId) {
        if (periodoId == null) {
            throw new IllegalArgumentException(
                "Elegí el período en el que se dicta esta comisión.");
        }
        PeriodoLectivo periodo = periodoRepository.porIdEnTenant(tenantId, periodoId)
            .orElseThrow(() -> new EntityNotFoundException("Período no encontrado: " + periodoId));

        cicloLectivoService.exigirEstructuraEditable(periodo.getCiclo());
        return periodo;
    }

    // Los periodos que se pueden elegir en el formulario: los de ciclos que aun admiten cambios.
    @Transactional(readOnly = true)
    public List<PeriodoLectivo> periodosParaSelector() {
        List<PeriodoLectivo> ps =
            periodoRepository.seleccionablesDelTenant(TenantContext.getRequired());
        // Se toca el ciclo, que la plantilla lee para armar la etiqueta "2026 - 1er cuatrimestre".
        ps.forEach(p -> p.getCiclo().getAnio());
        return ps;
    }

    @Transactional(readOnly = true)
    // Materias activas del tenant, para el combo del formulario de comisiones.
    public List<Materia> materiasActivasParaSelector() {
        List<Materia> ms = materiaRepository.findByActivoTrueOrderByNombreAsc();
        // Se tocan las relaciones LAZY que la plantilla va a leer: la carrera para el texto de
        // la opcion y el titular para proponerlo como docente de la comision. Sin esto la
        // sesion ya esta cerrada cuando Thymeleaf las pide.
        ms.forEach(m -> {
            if (m.getCarrera() != null) m.getCarrera().getCodigo();
            if (m.getDocenteTitular() != null) m.getDocenteTitular().getNombreCompleto();
        });
        return ms;
    }

    /**
     * Crea una comisión bajo una materia activa, con código único dentro de esa materia y ese
     * período.
     *
     * <p><b>El período entró desde V023</b> y es lo que ubica la comisión en un año concreto.
     * El código dejó de ser único por materia y pasó a serlo por materia y período: "Comisión A"
     * de 2026 y "Comisión A" de 2027 son dos ofertas distintas de la misma materia, y esa
     * repetición es exactamente lo que antes estaba prohibido.
     */
    @Transactional
    public Comision crear(String codigo, Long materiaId, Long docenteAsignadoId, Long periodoId) {
        Long tenantId = TenantContext.getRequired();
        Materia materia = obtenerMateriaValidada(materiaId, tenantId);

        if (Boolean.FALSE.equals(materia.getActivo())) {
            throw new IllegalArgumentException(
                "La materia '" + materia.getNombre() + "' está inactiva. Reactivala antes de crear comisiones.");
        }

        PeriodoLectivo periodo = obtenerPeriodoValidado(periodoId, tenantId);

        String codigoNorm = codigo.trim();
        if (comisionRepository.existsByMateriaIdAndCodigoAndPeriodoId(materiaId, codigoNorm, periodo.getId())) {
            throw new IllegalArgumentException(
                "Ya existe una comisión '" + codigoNorm + "' en la materia '" + materia.getCodigo()
                + "' para " + periodo.getNombre() + " " + periodo.getCiclo().getAnio() + ".");
        }

        Docente asignado = obtenerDocenteValidadoOrNull(docenteAsignadoId, tenantId, /*requireActivo*/ true);

        Comision c = Comision.builder()
            .codigo(codigoNorm)
            .materia(materia)
            .docenteAsignado(asignado)
            .periodo(periodo)
            .activo(true)
            .build();

        Comision saved = comisionRepository.save(c);
        log.info("Comision creada: id={}, codigo={}, materia_id={}, periodo_id={}, docente_asignado_id={}",
                 saved.getId(), saved.getCodigo(), materiaId, periodo.getId(), docenteAsignadoId);
        return saved;
    }

    @Transactional
    // Edita la comisión: código, materia, docente asignado y período.
    public Comision actualizar(Long id, String codigo, Long materiaId, Long docenteAsignadoId,
                               Long periodoId) {
        Comision c = buscarPorId(id);
        Long tenantId = TenantContext.getRequired();
        Materia materia = obtenerMateriaValidada(materiaId, tenantId);

        // La comision de un ciclo cerrado no se toca: es la oferta de un ano que ya termino.
        cicloLectivoService.exigirEstructuraEditable(c.getPeriodo().getCiclo());
        PeriodoLectivo periodo = obtenerPeriodoValidado(periodoId, tenantId);

        boolean cambiaMateria = !materia.getId().equals(c.getMateria().getId());
        if (cambiaMateria && Boolean.FALSE.equals(materia.getActivo())) {
            throw new IllegalArgumentException(
                "La nueva materia '" + materia.getNombre() + "' está inactiva. Elegí una activa.");
        }

        // La pregunta es una sola: ¿hay OTRA comision con ese codigo en esa materia? La
        // version anterior la partia en tres condiciones encadenadas y el ultimo termino
        // repetia el primero, con lo cual el chequeo se reducia a "cambia el codigo": mover
        // una comision "A" a una materia que ya tenia una "A" pasaba de largo por aca y
        // terminaba rebotando contra el UNIQUE de la base, con un mensaje generico.
        String codigoNuevo = codigo.trim();
        boolean colisiona = comisionRepository
            .findByMateriaIdAndCodigoAndPeriodoId(materiaId, codigoNuevo, periodo.getId())
            .filter(otra -> !otra.getId().equals(c.getId()))
            .isPresent();
        if (colisiona) {
            throw new IllegalArgumentException(
                "Ya existe una comisión '" + codigoNuevo + "' en la materia '" + materia.getCodigo()
                + "' para " + periodo.getNombre() + " " + periodo.getCiclo().getAnio() + ".");
        }

        // Si NO cambia el docente asignado, permitir mantenerlo aunque ahora este inactivo (legacy).
        // Si cambia, el nuevo asignado debe estar activo.
        Long asignadoActualId = c.getDocenteAsignado() != null ? c.getDocenteAsignado().getId() : null;
        boolean cambiaAsignado = !java.util.Objects.equals(asignadoActualId, docenteAsignadoId);
        Docente asignado = obtenerDocenteValidadoOrNull(docenteAsignadoId, tenantId, cambiaAsignado);

        c.setPeriodo(periodo);
        c.setCodigo(codigoNuevo);
        c.setMateria(materia);
        c.setDocenteAsignado(asignado);
        Comision saved = comisionRepository.save(c);
        log.info("Comision actualizada: id={}, codigo={}, docente_asignado_id={}",
                 saved.getId(), saved.getCodigo(), docenteAsignadoId);
        return saved;
    }

    @Transactional
    // Baja lógica; se bloquea si todavía cuelgan horarios activos.
    public void darDeBaja(Long id) {
        Comision c = buscarPorId(id);
        if (Boolean.FALSE.equals(c.getActivo())) {
            throw new IllegalArgumentException("La comisión ya está inactiva.");
        }
        long horariosActivos = horarioRepository.countByComisionIdAndActivoTrue(id);
        if (horariosActivos > 0) {
            throw new IllegalArgumentException(
                "No se puede dar de baja: la comisión tiene " + horariosActivos +
                " horario(s) activo(s). Dales de baja primero.");
        }
        c.setActivo(false);
        c.setFechaBaja(java.time.LocalDate.now());
        comisionRepository.save(c);
        log.info("Comision dada de baja: id={}", id);
    }

    @Transactional
    // Reactiva la comisión, siempre que su materia esté activa.
    public void darDeAlta(Long id) {
        Comision c = buscarPorId(id);
        if (Boolean.TRUE.equals(c.getActivo())) {
            throw new IllegalArgumentException("La comisión ya está activa.");
        }
        if (Boolean.FALSE.equals(c.getMateria().getActivo())) {
            throw new IllegalArgumentException(
                "La materia de esta comisión está inactiva. Reactivala primero.");
        }
        c.setActivo(true);
        c.setFechaBaja(null);
        comisionRepository.save(c);
        log.info("Comision reactivada: id={}", id);
    }

    // Trae la materia asegurando que sea del tenant actual.
    private Materia obtenerMateriaValidada(Long materiaId, Long tenantId) {
        Materia m = materiaRepository.findById(materiaId)
            .orElseThrow(() -> new IllegalArgumentException("La materia seleccionada no existe."));
        if (!tenantId.equals(m.getInstitucionId())) {
            log.warn("Cross-tenant blocked: tenant {} intento usar materia id={} (tenant {})",
                     tenantId, materiaId, m.getInstitucionId());
            throw new IllegalArgumentException("La materia seleccionada no existe.");
        }
        // Touch para inicializar la carrera antes de que la use el template.
        if (m.getCarrera() != null) m.getCarrera().getCodigo();
        return m;
    }

    // Trae el docente asignado validando tenant; devuelve null si no se eligió ninguno (es opcional).
    private Docente obtenerDocenteValidadoOrNull(Long docenteId, Long tenantId, boolean requireActivo) {
        if (docenteId == null) return null;
        Docente d = docenteRepository.findById(docenteId)
            .orElseThrow(() -> new IllegalArgumentException("El docente seleccionado no existe."));
        if (!tenantId.equals(d.getInstitucionId())) {
            log.warn("Cross-tenant blocked: tenant {} intento asignar docente id={} (tenant {})",
                     tenantId, docenteId, d.getInstitucionId());
            throw new IllegalArgumentException("El docente seleccionado no existe.");
        }
        if (requireActivo && Boolean.FALSE.equals(d.getActivo())) {
            throw new IllegalArgumentException(
                "El docente '" + d.getNombreCompleto() + "' está inactivo. Elegí uno activo.");
        }
        return d;
    }
}
