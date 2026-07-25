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

import java.util.List;

/**
 * Operaciones sobre las comisiones del tenant actual. Cubre RF-13.
 * <p>
 * <b>Comision no es tenant-scoped directamente</b> (no tiene
 * institucion_id propia). El tenant lo determina la materia padre,
 * y la query {@code findAllDelTenant()} usa JOIN con materia, lo que
 * activa el filtro Hibernate {@code "tenant"} sobre Materia.
 * Para {@code findById} validamos manualmente via la materia.
 * <p>
 * El campo {@code docenteAsignadoId} queda NULL hasta Sprint 3
 * (V004 lo hizo nullable).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComisionService {

    private final ComisionRepository comisionRepository;
    private final MateriaRepository materiaRepository;
    private final HorarioRepository horarioRepository;
    private final DocenteRepository docenteRepository;

    @Transactional(readOnly = true)
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
            if (c.getDocenteAsignado() != null) c.getDocenteAsignado().getDni();
        });
        return comisiones;
    }

    @Transactional(readOnly = true)
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
        if (c.getDocenteAsignado() != null) c.getDocenteAsignado().getDni();
        return c;
    }

    // Docentes activos del tenant - para el selector del form.
    @Transactional(readOnly = true)
    public List<Docente> docentesActivosParaSelector() {
        return docenteRepository.findByActivoTrueOrderByApellidoAscNombreAsc();
    }

    /**
     * Comisiones activas del tenant para popular selectores de UI
     * (ej: el form de creacion de horarios).
     */
    @Transactional(readOnly = true)
    public List<Comision> comisionesActivasParaSelector() {
        Long tenantId = TenantContext.getRequired();
        List<Comision> cs = comisionRepository.findActivasDelTenant(tenantId);
        cs.forEach(c -> {
            if (c.getMateria() != null) {
                c.getMateria().getCodigo();
                if (c.getMateria().getCarrera() != null) c.getMateria().getCarrera().getCodigo();
            }
        });
        return cs;
    }

    @Transactional(readOnly = true)
    public List<Materia> materiasActivasParaSelector() {
        List<Materia> ms = materiaRepository.findByActivoTrueOrderByNombreAsc();
        ms.forEach(m -> { if (m.getCarrera() != null) m.getCarrera().getCodigo(); });
        return ms;
    }

    @Transactional
    public Comision crear(String codigo, Long materiaId, Integer cupo, Long docenteAsignadoId) {
        Long tenantId = TenantContext.getRequired();
        Materia materia = obtenerMateriaValidada(materiaId, tenantId);

        if (Boolean.FALSE.equals(materia.getActivo())) {
            throw new IllegalArgumentException(
                "La materia '" + materia.getNombre() + "' está inactiva. Reactivala antes de crear comisiones.");
        }

        String codigoNorm = codigo.trim();
        if (comisionRepository.existsByMateriaIdAndCodigo(materiaId, codigoNorm)) {
            throw new IllegalArgumentException(
                "Ya existe una comisión '" + codigoNorm + "' en la materia '" + materia.getCodigo() + "'.");
        }

        if (cupo != null && cupo <= 0) {
            throw new IllegalArgumentException("El cupo debe ser un número positivo.");
        }

        Docente asignado = obtenerDocenteValidadoOrNull(docenteAsignadoId, tenantId, /*requireActivo*/ true);

        Comision c = Comision.builder()
            .codigo(codigoNorm)
            .materia(materia)
            .cupo(cupo)
            .docenteAsignado(asignado)
            .activo(true)
            .build();

        Comision saved = comisionRepository.save(c);
        log.info("Comision creada: id={}, codigo={}, materia_id={}, docente_asignado_id={}",
                 saved.getId(), saved.getCodigo(), materiaId, docenteAsignadoId);
        return saved;
    }

    @Transactional
    public Comision actualizar(Long id, String codigo, Long materiaId, Integer cupo, Long docenteAsignadoId) {
        Comision c = buscarPorId(id);
        Long tenantId = TenantContext.getRequired();
        Materia materia = obtenerMateriaValidada(materiaId, tenantId);

        boolean cambiaMateria = !materia.getId().equals(c.getMateria().getId());
        if (cambiaMateria && Boolean.FALSE.equals(materia.getActivo())) {
            throw new IllegalArgumentException(
                "La nueva materia '" + materia.getNombre() + "' está inactiva. Elegí una activa.");
        }

        String codigoNuevo = codigo.trim();
        boolean cambiaCodigo  = !codigoNuevo.equalsIgnoreCase(c.getCodigo());
        if ((cambiaCodigo || cambiaMateria)
                && comisionRepository.existsByMateriaIdAndCodigo(materiaId, codigoNuevo)
                && !codigoNuevo.equalsIgnoreCase(c.getCodigo())) {
            // Si cambia codigo y/o materia, validar unicidad en la (nueva) materia
            // (solo si el codigo en la nueva materia no es el de esta misma comision)
            throw new IllegalArgumentException(
                "Ya existe una comisión '" + codigoNuevo + "' en la materia '" + materia.getCodigo() + "'.");
        }

        if (cupo != null && cupo <= 0) {
            throw new IllegalArgumentException("El cupo debe ser un número positivo.");
        }

        // Si NO cambia el docente asignado, permitir mantenerlo aunque ahora este inactivo (legacy).
        // Si cambia, el nuevo asignado debe estar activo.
        Long asignadoActualId = c.getDocenteAsignado() != null ? c.getDocenteAsignado().getId() : null;
        boolean cambiaAsignado = !java.util.Objects.equals(asignadoActualId, docenteAsignadoId);
        Docente asignado = obtenerDocenteValidadoOrNull(docenteAsignadoId, tenantId, cambiaAsignado);

        c.setCodigo(codigoNuevo);
        c.setMateria(materia);
        c.setCupo(cupo);
        c.setDocenteAsignado(asignado);
        Comision saved = comisionRepository.save(c);
        log.info("Comision actualizada: id={}, codigo={}, docente_asignado_id={}",
                 saved.getId(), saved.getCodigo(), docenteAsignadoId);
        return saved;
    }

    @Transactional
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
        comisionRepository.save(c);
        log.info("Comision dada de baja: id={}", id);
    }

    @Transactional
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
        comisionRepository.save(c);
        log.info("Comision reactivada: id={}", id);
    }

    private Materia obtenerMateriaValidada(Long materiaId, Long tenantId) {
        Materia m = materiaRepository.findById(materiaId)
            .orElseThrow(() -> new IllegalArgumentException("La materia seleccionada no existe."));
        if (!tenantId.equals(m.getInstitucionId())) {
            log.warn("Cross-tenant blocked: tenant {} intento usar materia id={} (tenant {})",
                     tenantId, materiaId, m.getInstitucionId());
            throw new IllegalArgumentException("La materia seleccionada no existe.");
        }
        // touch carrera para que se inicialice (lazy)
        if (m.getCarrera() != null) m.getCarrera().getCodigo();
        return m;
    }

    /**
     * Obtiene el docente validando que pertenezca al tenant.
     * Devuelve null si {@code docenteId} es null (el asignado es opcional).
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
}
