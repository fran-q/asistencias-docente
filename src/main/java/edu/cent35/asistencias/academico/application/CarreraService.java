package edu.cent35.asistencias.academico.application;

import edu.cent35.asistencias.academico.domain.Carrera;
import edu.cent35.asistencias.academico.infrastructure.CarreraRepository;
import edu.cent35.asistencias.academico.infrastructure.MateriaRepository;
import edu.cent35.asistencias.shared.multitenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Operaciones sobre las carreras de la institucion del tenant actual.
 * Cubre RF-11.
 * <p>
 * Aislamiento multi-tenant: combina el filtro Hibernate {@code "tenant"}
 * (activado por {@code TenantFilterAspect} en metodos transaccionales)
 * con validacion explicita en {@link #buscarPorId(Long)} para casos
 * donde el filtro no aplica (ej: {@code findById}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CarreraService {

    private final CarreraRepository carreraRepository;
    private final MateriaRepository materiaRepository;

    /** Lista todas las carreras del tenant (activas e inactivas). */
    @Transactional(readOnly = true)
    public List<Carrera> listar() {
        return carreraRepository.findAllByOrderByActivoDescNombreAsc();
    }

    /** Busca por id validando que pertenezca al tenant actual. */
    @Transactional(readOnly = true)
    public Carrera buscarPorId(Long id) {
        Long tenantId = TenantContext.getRequired();
        Carrera c = carreraRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Carrera no encontrada: " + id));
        if (!tenantId.equals(c.getInstitucionId())) {
            log.warn("Cross-tenant blocked: tenant {} intento acceder carrera id={} (tenant {})",
                     tenantId, id, c.getInstitucionId());
            // Camuflar como not-found
            throw new EntityNotFoundException("Carrera no encontrada");
        }
        return c;
    }

    @Transactional
    public Carrera crear(String codigo, String nombre) {
        Long tenantId = TenantContext.getRequired();
        String codigoNorm = codigo.trim();
        String nombreNorm = nombre.trim();

        if (carreraRepository.existsByCodigo(codigoNorm)) {
            throw new IllegalArgumentException(
                "Ya existe una carrera con código '" + codigoNorm + "' en esta institución.");
        }

        Carrera c = Carrera.builder()
            .codigo(codigoNorm)
            .nombre(nombreNorm)
            .activo(true)
            .build();
        c.setInstitucionId(tenantId);

        Carrera saved = carreraRepository.save(c);
        log.info("Carrera creada: id={}, codigo={}, institucion_id={}",
                 saved.getId(), saved.getCodigo(), tenantId);
        return saved;
    }

    @Transactional
    public Carrera actualizar(Long id, String codigo, String nombre) {
        Carrera c = buscarPorId(id);
        String codigoNuevo = codigo.trim();

        if (!codigoNuevo.equalsIgnoreCase(c.getCodigo())
                && carreraRepository.existsByCodigo(codigoNuevo)) {
            throw new IllegalArgumentException(
                "Ya existe otra carrera con código '" + codigoNuevo + "' en esta institución.");
        }

        c.setCodigo(codigoNuevo);
        c.setNombre(nombre.trim());

        Carrera saved = carreraRepository.save(c);
        log.info("Carrera actualizada: id={}, codigo={}", saved.getId(), saved.getCodigo());
        return saved;
    }

    /**
     * Da de baja lógica una carrera. Bloquea si tiene materias activas
     * (forzar al usuario a darlas de baja primero - evita orfandad lógica).
     */
    @Transactional
    public void darDeBaja(Long id) {
        Carrera c = buscarPorId(id);
        if (Boolean.FALSE.equals(c.getActivo())) {
            throw new IllegalArgumentException("La carrera ya está inactiva.");
        }
        long materiasActivas = materiaRepository.countByCarreraIdAndActivoTrue(id);
        if (materiasActivas > 0) {
            throw new IllegalArgumentException(
                "No se puede dar de baja: la carrera tiene " + materiasActivas +
                " materia(s) activa(s). Dales de baja primero.");
        }
        c.setActivo(false);
        carreraRepository.save(c);
        log.info("Carrera dada de baja: id={}", id);
    }

    /** Reactiva una carrera previamente dada de baja. */
    @Transactional
    public void darDeAlta(Long id) {
        Carrera c = buscarPorId(id);
        if (Boolean.TRUE.equals(c.getActivo())) {
            throw new IllegalArgumentException("La carrera ya está activa.");
        }
        c.setActivo(true);
        carreraRepository.save(c);
        log.info("Carrera reactivada: id={}", id);
    }
}
