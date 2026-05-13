package edu.cent35.asistencias.docente.application;

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

import java.time.LocalDate;
import java.util.List;

/**
 * Operaciones sobre los docentes de la institucion del tenant actual.
 * Cubre RF-07.
 * <p>
 * Reglas:
 * <ul>
 *   <li>DNI unico por institucion (obligatorio).</li>
 *   <li>Legajo unico por institucion (opcional).</li>
 *   <li>Fecha de alta no puede ser futura.</li>
 *   <li>No se puede dar de baja un docente que sea titular de
 *       materias activas o este asignado a comisiones activas.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocenteService {

    private final DocenteRepository docenteRepository;
    private final MateriaRepository materiaRepository;
    private final ComisionRepository comisionRepository;

    @Transactional(readOnly = true)
    public List<Docente> listar() {
        return docenteRepository.findAllByOrderByActivoDescApellidoAscNombreAsc();
    }

    /** Docentes activos para selectores. */
    @Transactional(readOnly = true)
    public List<Docente> activosParaSelector() {
        return docenteRepository.findByActivoTrueOrderByApellidoAscNombreAsc();
    }

    @Transactional(readOnly = true)
    public Docente buscarPorId(Long id) {
        Long tenantId = TenantContext.getRequired();
        Docente d = docenteRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Docente no encontrado: " + id));
        if (!tenantId.equals(d.getInstitucionId())) {
            log.warn("Cross-tenant blocked: tenant {} intento acceder docente id={}", tenantId, id);
            throw new EntityNotFoundException("Docente no encontrado");
        }
        return d;
    }

    @Transactional
    public Docente crear(String dni, String legajo, String nombre, String apellido,
                         String email, String telefono, LocalDate fechaAlta) {

        Long tenantId = TenantContext.getRequired();
        validarFechaAlta(fechaAlta);

        String dniNorm    = blankToNull(dni);
        String legajoNorm = blankToNull(legajo);

        if (dniNorm == null) throw new IllegalArgumentException("El DNI es obligatorio.");
        if (docenteRepository.existsByDni(dniNorm)) {
            throw new IllegalArgumentException("Ya existe un docente con DNI '" + dniNorm + "' en esta institución.");
        }
        if (legajoNorm != null && docenteRepository.existsByLegajo(legajoNorm)) {
            throw new IllegalArgumentException("Ya existe un docente con legajo '" + legajoNorm + "' en esta institución.");
        }

        Docente d = Docente.builder()
            .dni(dniNorm)
            .legajo(legajoNorm)
            .nombre(nombre.trim())
            .apellido(apellido.trim())
            .email(blankToNull(email))
            .telefono(blankToNull(telefono))
            .fechaAlta(fechaAlta)
            .activo(true)
            .build();
        d.setInstitucionId(tenantId);

        Docente saved = docenteRepository.save(d);
        log.info("Docente creado: id={}, dni={}, institucion_id={}", saved.getId(), dniNorm, tenantId);
        return saved;
    }

    @Transactional
    public Docente actualizar(Long id, String dni, String legajo, String nombre, String apellido,
                              String email, String telefono, LocalDate fechaAlta) {

        Docente d = buscarPorId(id);
        validarFechaAlta(fechaAlta);

        String dniNuevo    = blankToNull(dni);
        String legajoNuevo = blankToNull(legajo);

        if (dniNuevo == null) throw new IllegalArgumentException("El DNI es obligatorio.");
        if (!dniNuevo.equals(d.getDni()) && docenteRepository.existsByDni(dniNuevo)) {
            throw new IllegalArgumentException("Ya existe otro docente con DNI '" + dniNuevo + "'.");
        }
        if (legajoNuevo != null && !legajoNuevo.equals(d.getLegajo())
                && docenteRepository.existsByLegajo(legajoNuevo)) {
            throw new IllegalArgumentException("Ya existe otro docente con legajo '" + legajoNuevo + "'.");
        }

        d.setDni(dniNuevo);
        d.setLegajo(legajoNuevo);
        d.setNombre(nombre.trim());
        d.setApellido(apellido.trim());
        d.setEmail(blankToNull(email));
        d.setTelefono(blankToNull(telefono));
        d.setFechaAlta(fechaAlta);

        Docente saved = docenteRepository.save(d);
        log.info("Docente actualizado: id={}", id);
        return saved;
    }

    @Transactional
    public void darDeBaja(Long id) {
        Docente d = buscarPorId(id);
        if (Boolean.FALSE.equals(d.getActivo())) {
            throw new IllegalArgumentException("El docente ya está inactivo.");
        }

        long materiasComoTitular = materiaRepository.countByDocenteTitularIdAndActivoTrue(id);
        long comisionesAsignadas = comisionRepository.countByDocenteAsignadoIdAndActivoTrue(id);

        if (materiasComoTitular > 0 || comisionesAsignadas > 0) {
            throw new IllegalArgumentException(
                "No se puede dar de baja: el docente es titular de " + materiasComoTitular +
                " materia(s) y está asignado a " + comisionesAsignadas + " comisión(es) activas. " +
                "Reasignalas primero.");
        }

        d.setActivo(false);
        docenteRepository.save(d);
        log.info("Docente dado de baja: id={}", id);
    }

    @Transactional
    public void darDeAlta(Long id) {
        Docente d = buscarPorId(id);
        if (Boolean.TRUE.equals(d.getActivo())) {
            throw new IllegalArgumentException("El docente ya está activo.");
        }
        d.setActivo(true);
        docenteRepository.save(d);
        log.info("Docente reactivado: id={}", id);
    }

    private void validarFechaAlta(LocalDate fecha) {
        if (fecha == null) throw new IllegalArgumentException("La fecha de alta es obligatoria.");
        if (fecha.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de alta no puede ser futura.");
        }
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
