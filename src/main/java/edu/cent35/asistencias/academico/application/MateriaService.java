package edu.cent35.asistencias.academico.application;

import edu.cent35.asistencias.academico.domain.Carrera;
import edu.cent35.asistencias.academico.domain.Materia;
import edu.cent35.asistencias.academico.infrastructure.CarreraRepository;
import edu.cent35.asistencias.academico.infrastructure.ComisionRepository;
import edu.cent35.asistencias.academico.infrastructure.MateriaRepository;
import edu.cent35.asistencias.docente.domain.Docente;
import edu.cent35.asistencias.docente.infrastructure.DocenteRepository;
import edu.cent35.asistencias.shared.multitenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Operaciones sobre las materias del tenant actual. Cubre RF-12.
 * <p>
 * Validaciones especiales:
 * <ul>
 *   <li>La carrera asignada debe pertenecer al mismo tenant.</li>
 *   <li>Al crear, la carrera debe estar activa.</li>
 *   <li>Al editar, si no se cambia de carrera, se permite aunque
 *       esa carrera este inactiva (legacy / no obligar a reasignar).</li>
 *   <li>No se puede dar de baja una materia con comisiones activas.</li>
 *   <li>Para reactivar, la carrera debe estar activa.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MateriaService {

    private final MateriaRepository materiaRepository;
    private final CarreraRepository carreraRepository;
    private final ComisionRepository comisionRepository;
    private final DocenteRepository docenteRepository;

    @Transactional(readOnly = true)
    public List<Materia> listar() {
        List<Materia> materias = materiaRepository.findAllByOrderByActivoDescNombreAsc();
        // Forzar inicializacion de carrera + docente titular (lazy)
        materias.forEach(m -> {
            if (m.getCarrera() != null) m.getCarrera().getCodigo();
            if (m.getDocenteTitular() != null) m.getDocenteTitular().getDni();
        });
        return materias;
    }

    @Transactional(readOnly = true)
    public Materia buscarPorId(Long id) {
        Long tenantId = TenantContext.getRequired();
        Materia m = materiaRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Materia no encontrada: " + id));
        if (!tenantId.equals(m.getInstitucionId())) {
            log.warn("Cross-tenant blocked: tenant {} intento acceder materia id={} (tenant {})",
                     tenantId, id, m.getInstitucionId());
            throw new EntityNotFoundException("Materia no encontrada");
        }
        // touch para inicializar carrera + docente titular lazy
        if (m.getCarrera() != null) m.getCarrera().getCodigo();
        if (m.getDocenteTitular() != null) m.getDocenteTitular().getDni();
        return m;
    }

    /** Docentes activos del tenant para el selector de "Titular". */
    @Transactional(readOnly = true)
    public List<Docente> docentesActivosParaSelector() {
        return docenteRepository.findByActivoTrueOrderByApellidoAscNombreAsc();
    }

    /** Lista las carreras activas del tenant para selectores de UI. */
    @Transactional(readOnly = true)
    public List<Carrera> carrerasActivasParaSelector() {
        return carreraRepository.findByActivoTrueOrderByNombreAsc();
    }

    @Transactional
    public Materia crear(String codigo, String nombre, Long carreraId, Long docenteTitularId) {
        Long tenantId = TenantContext.getRequired();
        Carrera carrera = obtenerCarreraValidada(carreraId, tenantId);

        if (Boolean.FALSE.equals(carrera.getActivo())) {
            throw new IllegalArgumentException(
                "La carrera '" + carrera.getNombre() + "' está inactiva. Reactivala antes de crear materias.");
        }

        String codigoNorm = codigo.trim();
        if (materiaRepository.existsByCodigo(codigoNorm)) {
            throw new IllegalArgumentException(
                "Ya existe una materia con código '" + codigoNorm + "' en esta institución.");
        }

        Docente titular = obtenerDocenteValidadoOrNull(docenteTitularId, tenantId, /*requireActivo*/ true);

        Materia m = Materia.builder()
            .codigo(codigoNorm)
            .nombre(nombre.trim())
            .carrera(carrera)
            .docenteTitular(titular)
            .activo(true)
            .build();
        m.setInstitucionId(tenantId);

        Materia saved = materiaRepository.save(m);
        log.info("Materia creada: id={}, codigo={}, carrera_id={}, titular_id={}",
                 saved.getId(), saved.getCodigo(), carreraId, docenteTitularId);
        return saved;
    }

    @Transactional
    public Materia actualizar(Long id, String codigo, String nombre, Long carreraId,
                              Long docenteTitularId) {
        Materia m = buscarPorId(id);
        Long tenantId = TenantContext.getRequired();
        Carrera carrera = obtenerCarreraValidada(carreraId, tenantId);

        boolean cambiaCarrera = !carrera.getId().equals(m.getCarrera().getId());
        if (cambiaCarrera && Boolean.FALSE.equals(carrera.getActivo())) {
            throw new IllegalArgumentException(
                "La nueva carrera '" + carrera.getNombre() + "' está inactiva. Elegí una activa.");
        }

        String codigoNuevo = codigo.trim();
        if (!codigoNuevo.equalsIgnoreCase(m.getCodigo())
                && materiaRepository.existsByCodigo(codigoNuevo)) {
            throw new IllegalArgumentException(
                "Ya existe otra materia con código '" + codigoNuevo + "' en esta institución.");
        }

        // Si NO cambia de titular, permitir mantenerlo aunque ahora este inactivo (legacy).
        // Si cambia, el nuevo titular debe estar activo.
        Long titularActualId = m.getDocenteTitular() != null ? m.getDocenteTitular().getId() : null;
        boolean cambiaTitular = !java.util.Objects.equals(titularActualId, docenteTitularId);
        Docente titular = obtenerDocenteValidadoOrNull(docenteTitularId, tenantId, cambiaTitular);

        m.setCodigo(codigoNuevo);
        m.setNombre(nombre.trim());
        m.setCarrera(carrera);
        m.setDocenteTitular(titular);
        Materia saved = materiaRepository.save(m);
        log.info("Materia actualizada: id={}, codigo={}, titular_id={}",
                 saved.getId(), saved.getCodigo(), docenteTitularId);
        return saved;
    }

    @Transactional
    public void darDeBaja(Long id) {
        Materia m = buscarPorId(id);
        if (Boolean.FALSE.equals(m.getActivo())) {
            throw new IllegalArgumentException("La materia ya está inactiva.");
        }
        long comisionesActivas = comisionRepository.countByMateriaIdAndActivoTrue(id);
        if (comisionesActivas > 0) {
            throw new IllegalArgumentException(
                "No se puede dar de baja: la materia tiene " + comisionesActivas +
                " comisión(es) activa(s). Dales de baja primero.");
        }
        m.setActivo(false);
        materiaRepository.save(m);
        log.info("Materia dada de baja: id={}", id);
    }

    @Transactional
    public void darDeAlta(Long id) {
        Materia m = buscarPorId(id);
        if (Boolean.TRUE.equals(m.getActivo())) {
            throw new IllegalArgumentException("La materia ya está activa.");
        }
        if (Boolean.FALSE.equals(m.getCarrera().getActivo())) {
            throw new IllegalArgumentException(
                "La carrera de esta materia está inactiva. Reactivala primero.");
        }
        m.setActivo(true);
        materiaRepository.save(m);
        log.info("Materia reactivada: id={}", id);
    }

    /**
     * Obtiene el docente validando que pertenezca al tenant.
     * Devuelve null si {@code docenteId} es null (titular es opcional).
     * Si {@code requireActivo}, ademas se valida que este activo.
     */
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

    /** Obtiene la carrera validando que pertenezca al tenant actual. */
    private Carrera obtenerCarreraValidada(Long carreraId, Long tenantId) {
        Carrera c = carreraRepository.findById(carreraId)
            .orElseThrow(() -> new IllegalArgumentException("La carrera seleccionada no existe."));
        if (!tenantId.equals(c.getInstitucionId())) {
            log.warn("Cross-tenant blocked: tenant {} intento usar carrera id={} (tenant {})",
                     tenantId, carreraId, c.getInstitucionId());
            throw new IllegalArgumentException("La carrera seleccionada no existe.");
        }
        return c;
    }
}
