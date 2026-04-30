package edu.cent35.asistencias.institucion.application;

import edu.cent35.asistencias.institucion.domain.Institucion;
import edu.cent35.asistencias.institucion.infrastructure.InstitucionRepository;
import edu.cent35.asistencias.institucion.web.InstitucionFormDto;
import edu.cent35.asistencias.shared.multitenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operaciones sobre la institucion del usuario logueado ("mi institucion").
 * <p>
 * El SUPERADMIN_INSTITUCION puede ver y editar los datos de SU
 * institucion - jamas la de otra. La proteccion es doble:
 * <ol>
 *   <li>{@link TenantContext#getRequired()} provee el id que se usa
 *       para fetch (no aceptamos id externo, evita IDOR).</li>
 *   <li>El controlador exige rol SUPERADMIN_INSTITUCION via
 *       {@code @PreAuthorize}.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MiInstitucionService {

    private final InstitucionRepository institucionRepository;

    /**
     * Devuelve la institucion del tenant actual.
     */
    @Transactional(readOnly = true)
    public Institucion getMiInstitucion() {
        Long tenantId = TenantContext.getRequired();
        return institucionRepository.findById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Institucion no encontrada para tenantId=" + tenantId));
    }

    /**
     * Actualiza los datos editables de la institucion del tenant actual.
     *
     * @throws DataIntegrityViolationException si el nombre o cuit nuevo
     *         colisiona con el de otra institucion existente.
     */
    @Transactional
    public Institucion actualizar(InstitucionFormDto dto) {
        Long tenantId = TenantContext.getRequired();
        Institucion inst = institucionRepository.findById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Institucion no encontrada para tenantId=" + tenantId));

        inst.setNombre(dto.getNombre().trim());
        inst.setCuit(blankToNull(dto.getCuit()));
        inst.setDireccion(blankToNull(dto.getDireccion()));
        inst.setEmailContacto(blankToNull(dto.getEmailContacto()));
        inst.setTelefonoContacto(blankToNull(dto.getTelefonoContacto()));

        Institucion saved = institucionRepository.save(inst);
        log.info("Institucion id={} actualizada por superadmin", tenantId);
        return saved;
    }

    /** Convierte strings vacios o whitespace a null para mantener limpio el dato. */
    private static String blankToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
