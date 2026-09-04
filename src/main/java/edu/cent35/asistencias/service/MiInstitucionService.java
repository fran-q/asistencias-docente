package edu.cent35.asistencias.service;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.dto.InstitucionFormDto;
import edu.cent35.asistencias.config.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Permite al rol INSTITUCION ver y editar los datos de su propia institución, nunca la de
 * otra. El id sale siempre del TenantContext y no de un parámetro del request, que es lo
 * que evita un IDOR; el control de rol lo pone además el controlador.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MiInstitucionService {

    private final InstitucionRepository institucionRepository;

    // Devuelve la institución del tenant actual.
    @Transactional(readOnly = true)
    public Institucion getMiInstitucion() {
        Long tenantId = TenantContext.getRequired();
        return institucionRepository.findById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Institucion no encontrada para tenantId=" + tenantId));
    }

    // Edita los datos de la institución actual; falla si el nombre o el CUIT ya son de otra.
    @Transactional
    public Institucion actualizar(InstitucionFormDto dto) {
        Long tenantId = TenantContext.getRequired();
        Institucion inst = institucionRepository.findById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Institucion no encontrada para tenantId=" + tenantId));

        inst.setNombre(dto.getNombre().trim());
        inst.setCuit(edu.cent35.asistencias.model.Cuit.normalizar(dto.getCuit()));
        inst.setDireccion(blankToNull(dto.getDireccion()));
        inst.setEmailContacto(blankToNull(dto.getEmailContacto()));
        inst.setTelefonoContacto(blankToNull(dto.getTelefonoContacto()));

        // El umbral se registra aparte cuando cambia (RF-76). No es un dato de contacto: decide
        // qué clases quedan dentro del mismo bloque de presencia, y por lo tanto qué acredita el
        // sistema. Si mañana un reporte no cuadra, el valor viejo tiene que estar en algún lado.
        Short umbralAnterior = inst.getUmbralSeparacionMin();
        Short umbralNuevo = dto.getUmbralSeparacionMin();
        if (!java.util.Objects.equals(umbralAnterior, umbralNuevo)) {
            log.info("RF-76: umbral de separacion de la institucion id={} pasa de {} a {} minutos",
                     tenantId, umbralAnterior, umbralNuevo);
        }
        inst.setUmbralSeparacionMin(umbralNuevo);

        Institucion saved = institucionRepository.save(inst);
        log.info("Institucion id={} actualizada por superadmin", tenantId);
        return saved;
    }

    // Convierte strings vacios o whitespace a null para mantener limpio el dato.
    private static String blankToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
