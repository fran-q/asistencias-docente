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
                         String email, String telefono) {

        Long tenantId = TenantContext.getRequired();

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
            // La fecha de alta es el momento en que se carga, no un dato a tipear: quien
            // esta cargando al docente esta aca ahora, y pedirsela solo habilita el error.
            .fechaAlta(LocalDate.now())
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
                              String email, String telefono) {

        Docente d = buscarPorId(id);

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
        // La fecha de alta no se toca en la edicion: es el registro de cuando ingreso, no
        // un campo mas del legajo. Se corrige en la base si hiciera falta, y queda asentado.

        Docente saved = docenteRepository.save(d);
        log.info("Docente actualizado: id={}", id);
        return saved;
    }

    @Transactional
    // Baja lógica con la fecha en que dejó de prestar servicios; se bloquea si sigue
    // siendo titular o está asignado a comisiones activas.
    public void darDeBaja(Long id, LocalDate fechaBaja) {
        Docente d = buscarPorId(id);
        if (Boolean.FALSE.equals(d.getActivo())) {
            throw new IllegalArgumentException("El docente ya está inactivo.");
        }
        validarFechaBaja(fechaBaja, d.getFechaAlta());

        long materiasComoTitular = materiaRepository.countByDocenteTitularIdAndActivoTrue(id);
        long comisionesAsignadas = comisionRepository.countByDocenteAsignadoIdAndActivoTrue(id);

        if (materiasComoTitular > 0 || comisionesAsignadas > 0) {
            throw new IllegalArgumentException(
                "No se puede dar de baja: el docente es titular de " + materiasComoTitular +
                " materia(s) y está asignado a " + comisionesAsignadas + " comisión(es) activas. " +
                "Reasignalas primero.");
        }

        d.setActivo(false);
        d.setFechaBaja(fechaBaja);
        docenteRepository.save(d);
        log.info("Docente dado de baja: id={}, fecha_baja={}", id, fechaBaja);
    }

    @Transactional
    // Reactiva un docente dado de baja.
    public void darDeAlta(Long id) {
        Docente d = buscarPorId(id);
        if (Boolean.TRUE.equals(d.getActivo())) {
            throw new IllegalArgumentException("El docente ya está activo.");
        }
        d.setActivo(true);
        // Se borra la fecha de baja: dejarla puesta describiria a un docente activo que
        // ademas figura como desvinculado, que es una contradiccion.
        d.setFechaBaja(null);
        docenteRepository.save(d);
        log.info("Docente reactivado: id={}", id);
    }

    // La baja se carga despues del hecho, asi que puede ser anterior a hoy pero nunca
    // futura ni anterior al ingreso del docente.
    private void validarFechaBaja(LocalDate fechaBaja, LocalDate fechaAlta) {
        if (fechaBaja == null) {
            throw new IllegalArgumentException("La fecha de baja es obligatoria.");
        }
        if (fechaBaja.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de baja no puede ser futura.");
        }
        if (fechaAlta != null && fechaBaja.isBefore(fechaAlta)) {
            throw new IllegalArgumentException(
                "La fecha de baja no puede ser anterior a la de alta (" +
                fechaAlta.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ").");
        }
    }

    // Normaliza campos opcionales: deja null si viene vacío o solo con espacios.
    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
