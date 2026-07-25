package edu.cent35.asistencias.service;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.MateriaRepository;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.config.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * ABM de los docentes de la institución actual (RF-07), con DNI obligatorio y legajo opcional,
 * ambos únicos dentro de la institución. La baja es lógica y se bloquea mientras el docente
 * siga siendo titular de materias activas o esté asignado a comisiones activas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocenteService {

    private final DocenteRepository docenteRepository;
    private final MateriaRepository materiaRepository;
    private final ComisionRepository comisionRepository;

    @Transactional(readOnly = true)
    // Lista los docentes del tenant, activos primero.
    public List<Docente> listar() {
        return docenteRepository.findAllByOrderByActivoDescApellidoAscNombreAsc();
    }

    // Docentes activos para selectores.
    @Transactional(readOnly = true)
    // Solo los activos, para poblar los combos de los formularios.
    public List<Docente> activosParaSelector() {
        return docenteRepository.findByActivoTrueOrderByApellidoAscNombreAsc();
    }

    @Transactional(readOnly = true)
    // Busca por id validando que sea del tenant actual; si no, responde "no encontrado".
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
    // Da de alta un docente, exigiendo DNI y legajo sin repetir dentro de la institución.
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
    // Edita los datos del docente, cuidando que DNI y legajo no choquen con otro.
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
    // Baja lógica; se bloquea si sigue siendo titular o está asignado a comisiones activas.
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
    // Reactiva un docente dado de baja.
    public void darDeAlta(Long id) {
        Docente d = buscarPorId(id);
        if (Boolean.TRUE.equals(d.getActivo())) {
            throw new IllegalArgumentException("El docente ya está activo.");
        }
        d.setActivo(true);
        docenteRepository.save(d);
        log.info("Docente reactivado: id={}", id);
    }

    // Exige fecha de alta y que no sea futura.
    private void validarFechaAlta(LocalDate fecha) {
        if (fecha == null) throw new IllegalArgumentException("La fecha de alta es obligatoria.");
        if (fecha.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de alta no puede ser futura.");
        }
    }

    // Normaliza campos opcionales: deja null si viene vacío o solo con espacios.
    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
