package edu.cent35.asistencias.service;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.repository.CarreraRepository;
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

import java.util.List;

/**
 * ABM de las materias del tenant actual (RF-12), con baja lógica y docente titular opcional.
 * La carrera y el titular tienen que ser de la misma institución; al editar se tolera una
 * carrera ya inactiva si no se la está cambiando, para no obligar a reasignar lo viejo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MateriaService {

    private final MateriaRepository materiaRepository;
    private final CarreraRepository carreraRepository;
    private final ComisionRepository comisionRepository;
    private final DocenteRepository docenteRepository;

    // Lista las materias del tenant, activas primero.
    @Transactional(readOnly = true)
    public List<Materia> listar() {
        List<Materia> materias = materiaRepository.findAllByOrderByActivoDescNombreAsc();
        // Touch para inicializar carrera y titular antes de que los use el template.
        materias.forEach(m -> {
            if (m.getCarrera() != null) m.getCarrera().getCodigo();
            if (m.getDocenteTitular() != null) m.getDocenteTitular().getDni();
        });
        return materias;
    }

    // Busca por id validando que sea del tenant actual; si no, responde "no encontrada".
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
        // Touch para inicializar carrera y titular antes de que los use el template.
        if (m.getCarrera() != null) m.getCarrera().getCodigo();
        if (m.getDocenteTitular() != null) m.getDocenteTitular().getDni();
        return m;
    }

    // Docentes activos del tenant para el selector de "Titular".
    @Transactional(readOnly = true)
    public List<Docente> docentesActivosParaSelector() {
        return docenteRepository.findByActivoTrueOrderByApellidoAscNombreAsc();
    }

    // Lista las carreras activas del tenant para selectores de UI.
    @Transactional(readOnly = true)
    public List<Carrera> carrerasActivasParaSelector() {
        return carreraRepository.findByActivoTrueOrderByNombreAsc();
    }

    @Transactional
    // Crea una materia bajo una carrera activa, con código único en la institución.
    public Materia crear(String codigo, String nombre, Long carreraId, Short anio,
                         Long docenteTitularId) {
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

        validarAnio(anio, carrera);

        Docente titular = obtenerDocenteValidadoOrNull(docenteTitularId, tenantId, /*requireActivo*/ true);

        Materia m = Materia.builder()
            .codigo(codigoNorm)
            .nombre(nombre.trim())
            .carrera(carrera)
            .anio(anio)
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
    // Edita la materia; solo exige carrera activa si se la está cambiando por otra.
    public Materia actualizar(Long id, String codigo, String nombre, Long carreraId, Short anio,
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

        validarAnio(anio, carrera);

        // Si NO cambia de titular, permitir mantenerlo aunque ahora este inactivo (legacy).
        // Si cambia, el nuevo titular debe estar activo.
        Long titularActualId = m.getDocenteTitular() != null ? m.getDocenteTitular().getId() : null;
        boolean cambiaTitular = !java.util.Objects.equals(titularActualId, docenteTitularId);
        Docente titular = obtenerDocenteValidadoOrNull(docenteTitularId, tenantId, cambiaTitular);

        m.setCodigo(codigoNuevo);
        m.setNombre(nombre.trim());
        m.setCarrera(carrera);
        m.setAnio(anio);
        m.setDocenteTitular(titular);
        Materia saved = materiaRepository.save(m);
        log.info("Materia actualizada: id={}, codigo={}, titular_id={}",
                 saved.getId(), saved.getCodigo(), docenteTitularId);
        return saved;
    }

    @Transactional
    // Baja lógica; se bloquea si todavía cuelgan comisiones activas.
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
    // Reactiva la materia, siempre que su carrera esté activa.
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

    // Trae el titular validando tenant; devuelve null si no se eligió ninguno (es opcional).
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

    // Obtiene la carrera validando que pertenezca al tenant actual.
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

    /**
     * El anio de la materia no puede pasar la duracion de su carrera.
     *
     * <p>No se puede expresar como CHECK en la base porque haria falta una subconsulta a
     * carreras, asi que la regla vive aca. El mensaje nombra la carrera y su duracion: decir
     * solo "año inválido" obliga a ir a buscar contra que se comparo.
     */
    private void validarAnio(Short anio, Carrera carrera) {
        if (anio == null) {
            throw new IllegalArgumentException("Hay que indicar de qué año es la materia.");
        }
        Short duracion = carrera.getDuracionAnios();
        if (duracion != null && anio > duracion) {
            throw new IllegalArgumentException(
                "La carrera '" + carrera.getNombre() + "' dura " + duracion + " año"
                + (duracion == 1 ? "" : "s") + ", así que no puede tener materias de "
                + anio + "° año.");
        }
    }
}
