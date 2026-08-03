package edu.cent35.asistencias.service;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.repository.CarreraRepository;
import edu.cent35.asistencias.repository.MateriaRepository;
import edu.cent35.asistencias.config.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ABM de las carreras de la institución actual (RF-11), con baja lógica en vez de borrado.
 * El aislamiento se apoya en el filtro de Hibernate y además valida el tenant a mano en
 * buscarPorId, porque findById no pasa por el filtro.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CarreraService {

    private final CarreraRepository carreraRepository;
    private final MateriaRepository materiaRepository;

    // Lista todas las carreras del tenant (activas e inactivas).
    @Transactional(readOnly = true)
    public List<Carrera> listar() {
        return carreraRepository.findAllByOrderByActivoDescNombreAsc();
    }

    // Busca por id validando que pertenezca al tenant actual.
    @Transactional(readOnly = true)
    public Carrera buscarPorId(Long id) {
        Long tenantId = TenantContext.getRequired();
        Carrera c = carreraRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Carrera no encontrada: " + id));
        if (!tenantId.equals(c.getInstitucionId())) {
            log.warn("Cross-tenant blocked: tenant {} intento acceder carrera id={} (tenant {})",
                     tenantId, id, c.getInstitucionId());
            // Se responde "no encontrada" para no revelar que el id existe en otra institución.
            throw new EntityNotFoundException("Carrera no encontrada");
        }
        return c;
    }

    // Crea una carrera con código único dentro de la institución.
    @Transactional
    public Carrera crear(String codigo, String nombre, Short duracionAnios) {
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
            .duracionAnios(duracionAnios)
            .activo(true)
            .build();
        c.setInstitucionId(tenantId);

        Carrera saved = carreraRepository.save(c);
        log.info("Carrera creada: id={}, codigo={}, institucion_id={}",
                 saved.getId(), saved.getCodigo(), tenantId);
        return saved;
    }

    // Renombra la carrera, cuidando que el código nuevo no choque con otra.
    @Transactional
    public Carrera actualizar(Long id, String codigo, String nombre, Short duracionAnios) {
        Carrera c = buscarPorId(id);
        String codigoNuevo = codigo.trim();

        if (!codigoNuevo.equalsIgnoreCase(c.getCodigo())
                && carreraRepository.existsByCodigo(codigoNuevo)) {
            throw new IllegalArgumentException(
                "Ya existe otra carrera con código '" + codigoNuevo + "' en esta institución.");
        }

        // Acortar la duracion dejaria materias fuera del plan, en un anio que ya no existe.
        // Se frena antes de guardar y se dice cual es el anio que estorba: mandar a "revisar
        // las materias" sin decir cual no le ahorra la busqueda a nadie.
        Short maxAnio = materiaRepository.maxAnioDeLaCarrera(id);
        if (duracionAnios != null && maxAnio != null && maxAnio > duracionAnios) {
            throw new IllegalArgumentException(
                "No se puede acortar la carrera a " + duracionAnios + " año"
                + (duracionAnios == 1 ? "" : "s") + ": tiene materias de " + maxAnio
                + "° año. Cambiales el año o dalas de baja primero.");
        }

        c.setCodigo(codigoNuevo);
        c.setNombre(nombre.trim());
        c.setDuracionAnios(duracionAnios);

        Carrera saved = carreraRepository.save(c);
        log.info("Carrera actualizada: id={}, codigo={}", saved.getId(), saved.getCodigo());
        return saved;
    }

    // Baja lógica; se bloquea si todavía cuelgan materias activas, para no dejarlas huérfanas.
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

    // Reactiva una carrera previamente dada de baja.
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
