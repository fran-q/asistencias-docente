package edu.cent35.asistencias.academico.application;

import edu.cent35.asistencias.academico.domain.Carrera;
import edu.cent35.asistencias.academico.domain.Materia;
import edu.cent35.asistencias.academico.infrastructure.CarreraRepository;
import edu.cent35.asistencias.academico.infrastructure.ComisionRepository;
import edu.cent35.asistencias.academico.infrastructure.MateriaRepository;
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

    @Transactional(readOnly = true)
    public List<Materia> listar() {
        List<Materia> materias = materiaRepository.findAllByOrderByActivoDescNombreAsc();
        // Forzar inicializacion de la carrera (lazy) dentro del scope transaccional
        materias.forEach(m -> {
            if (m.getCarrera() != null) m.getCarrera().getCodigo();
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
        // touch para inicializar la carrera lazy
        if (m.getCarrera() != null) m.getCarrera().getCodigo();
        return m;
    }

    /** Lista las carreras activas del tenant para selectores de UI. */
    @Transactional(readOnly = true)
    public List<Carrera> carrerasActivasParaSelector() {
        return carreraRepository.findByActivoTrueOrderByNombreAsc();
    }

    @Transactional
    public Materia crear(String codigo, String nombre, Long carreraId) {
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

        Materia m = Materia.builder()
            .codigo(codigoNorm)
            .nombre(nombre.trim())
            .carrera(carrera)
            .activo(true)
            .build();
        m.setInstitucionId(tenantId);

        Materia saved = materiaRepository.save(m);
        log.info("Materia creada: id={}, codigo={}, carrera_id={}, institucion_id={}",
                 saved.getId(), saved.getCodigo(), carreraId, tenantId);
        return saved;
    }

    @Transactional
    public Materia actualizar(Long id, String codigo, String nombre, Long carreraId) {
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

        m.setCodigo(codigoNuevo);
        m.setNombre(nombre.trim());
        m.setCarrera(carrera);
        Materia saved = materiaRepository.save(m);
        log.info("Materia actualizada: id={}, codigo={}", saved.getId(), saved.getCodigo());
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
