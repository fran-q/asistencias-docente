package edu.cent35.asistencias.service;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.DiaSemana;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.config.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Gestiona las franjas horarias semanales de cada comisión (RF-14): alta, edición,
 * baja y reactivación. Valida que la comisión sea del tenant actual, que la hora de
 * fin sea posterior a la de inicio y que la franja no se superponga con otra de la
 * misma comisión el mismo día.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HorarioService {

    private static final short MAX_TOLERANCIA_MIN = 120;

    private final HorarioRepository horarioRepository;
    private final ComisionRepository comisionRepository;

    // Lista los horarios del tenant actual, recorriendo sus comisiones (TD-003).
    @Transactional(readOnly = true)
    public List<Horario> listar() {
        Long tenantId = TenantContext.getRequired();
        List<Comision> comisiones = comisionRepository.findAllDelTenant(tenantId);
        return comisiones.stream()
            .flatMap(c -> {
                // Touch para inicializar los lazy antes de que los use el template.
                if (c.getMateria() != null) {
                    c.getMateria().getCodigo();
                    if (c.getMateria().getCarrera() != null) c.getMateria().getCarrera().getCodigo();
                }
                return horarioRepository.findByComisionIdOrderByDiaSemanaAscHoraInicioAsc(c.getId()).stream();
            })
            .toList();
    }

    // Busca un horario del tenant actual; responde "no encontrado" si es de otra institución.
    @Transactional(readOnly = true)
    public Horario buscarPorId(Long id) {
        Long tenantId = TenantContext.getRequired();
        Horario h = horarioRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Horario no encontrado: " + id));
        // El tenant del horario se deduce por su cadena comision -> materia.
        if (h.getComision() == null
                || h.getComision().getMateria() == null
                || !tenantId.equals(h.getComision().getMateria().getInstitucionId())) {
            log.warn("Cross-tenant blocked: tenant {} intento acceder horario id={}", tenantId, id);
            throw new EntityNotFoundException("Horario no encontrado");
        }
        // Touch para que el template pueda renderizar comisión y materia.
        h.getComision().getCodigo();
        h.getComision().getMateria().getCodigo();
        if (h.getComision().getMateria().getCarrera() != null) {
            h.getComision().getMateria().getCarrera().getCodigo();
        }
        return h;
    }

    // Crea una franja en una comisión activa, validando horas, tolerancia, vigencia y solapamiento.
    @Transactional
    public Horario crear(Long comisionId, DiaSemana dia, LocalTime horaInicio, LocalTime horaFin,
                         Short toleranciaMin, LocalDate vigenteDesde, LocalDate vigenteHasta) {

        Long tenantId = TenantContext.getRequired();
        Comision comision = obtenerComisionValidada(comisionId, tenantId);

        if (Boolean.FALSE.equals(comision.getActivo())) {
            throw new IllegalArgumentException(
                "La comisión seleccionada está inactiva. Reactivala antes de crear horarios.");
        }
        if (Boolean.FALSE.equals(comision.getMateria().getActivo())) {
            throw new IllegalArgumentException(
                "La materia '" + comision.getMateria().getCodigo() + "' está inactiva.");
        }

        validarHoras(horaInicio, horaFin);
        validarTolerancia(toleranciaMin);
        validarVigencia(vigenteDesde, vigenteHasta);
        validarSinSolapamiento(comisionId, dia, horaInicio, horaFin, null);

        Horario h = Horario.builder()
            .comision(comision)
            .horaInicio(horaInicio)
            .horaFin(horaFin)
            .toleranciaMin(toleranciaMin == null ? (short) 15 : toleranciaMin)
            .vigenteDesde(vigenteDesde)
            .vigenteHasta(vigenteHasta)
            .activo(true)
            .build();
        h.setDia(dia);

        Horario saved = horarioRepository.save(h);
        log.info("Horario creado: id={}, comision_id={}, dia={}, {}-{}",
                 saved.getId(), comisionId, dia, horaInicio, horaFin);
        return saved;
    }

    // Modifica una franja existente y vuelve a correr todas las validaciones de alta.
    @Transactional
    public Horario actualizar(Long id, Long comisionId, DiaSemana dia,
                              LocalTime horaInicio, LocalTime horaFin, Short toleranciaMin,
                              LocalDate vigenteDesde, LocalDate vigenteHasta) {

        Horario h = buscarPorId(id);
        Long tenantId = TenantContext.getRequired();
        Comision comision = obtenerComisionValidada(comisionId, tenantId);

        boolean cambiaComision = !comision.getId().equals(h.getComision().getId());
        if (cambiaComision && Boolean.FALSE.equals(comision.getActivo())) {
            throw new IllegalArgumentException(
                "La nueva comisión está inactiva. Elegí una activa.");
        }

        validarHoras(horaInicio, horaFin);
        validarTolerancia(toleranciaMin);
        validarVigencia(vigenteDesde, vigenteHasta);
        validarSinSolapamiento(comisionId, dia, horaInicio, horaFin, id);

        h.setComision(comision);
        h.setDia(dia);
        h.setHoraInicio(horaInicio);
        h.setHoraFin(horaFin);
        h.setToleranciaMin(toleranciaMin == null ? (short) 15 : toleranciaMin);
        h.setVigenteDesde(vigenteDesde);
        h.setVigenteHasta(vigenteHasta);
        Horario saved = horarioRepository.save(h);
        log.info("Horario actualizado: id={}, dia={}, {}-{}", saved.getId(), dia, horaInicio, horaFin);
        return saved;
    }

    // Desactiva un horario sin borrarlo, para no perder las asistencias que lo referencian.
    @Transactional
    public void darDeBaja(Long id) {
        Horario h = buscarPorId(id);
        if (Boolean.FALSE.equals(h.getActivo())) {
            throw new IllegalArgumentException("El horario ya está inactivo.");
        }
        h.setActivo(false);
        horarioRepository.save(h);
        log.info("Horario dado de baja: id={}", id);
    }

    // Reactiva un horario, revalidando que no se solape con otro creado mientras estaba de baja.
    @Transactional
    public void darDeAlta(Long id) {
        Horario h = buscarPorId(id);
        if (Boolean.TRUE.equals(h.getActivo())) {
            throw new IllegalArgumentException("El horario ya está activo.");
        }
        if (Boolean.FALSE.equals(h.getComision().getActivo())) {
            throw new IllegalArgumentException(
                "La comisión de este horario está inactiva. Reactivala primero.");
        }
        validarSinSolapamiento(h.getComision().getId(), h.getDia(),
                               h.getHoraInicio(), h.getHoraFin(), id);
        h.setActivo(true);
        horarioRepository.save(h);
        log.info("Horario reactivado: id={}", id);
    }

    // ============================================================
    //  Validaciones privadas
    // ============================================================

    // Trae una comisión asegurando que sea del tenant actual.
    private Comision obtenerComisionValidada(Long comisionId, Long tenantId) {
        Comision c = comisionRepository.findById(comisionId)
            .orElseThrow(() -> new IllegalArgumentException("La comisión seleccionada no existe."));
        if (c.getMateria() == null || !tenantId.equals(c.getMateria().getInstitucionId())) {
            log.warn("Cross-tenant blocked: tenant {} intento usar comision id={}", tenantId, comisionId);
            throw new IllegalArgumentException("La comisión seleccionada no existe.");
        }
        // Touch para inicializar los lazy.
        c.getMateria().getCodigo();
        if (c.getMateria().getCarrera() != null) c.getMateria().getCarrera().getCodigo();
        return c;
    }

    // Exige ambas horas y que la de fin sea posterior a la de inicio.
    private void validarHoras(LocalTime inicio, LocalTime fin) {
        if (inicio == null || fin == null) {
            throw new IllegalArgumentException("Hora de inicio y de fin son obligatorias.");
        }
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException(
                "La hora de fin (" + fin + ") debe ser posterior a la hora de inicio (" + inicio + ").");
        }
    }

    // Acepta null (se guarda 15 por defecto) o un valor entre 0 y 120 minutos.
    private void validarTolerancia(Short toleranciaMin) {
        if (toleranciaMin == null) return;
        if (toleranciaMin < 0 || toleranciaMin > MAX_TOLERANCIA_MIN) {
            throw new IllegalArgumentException(
                "La tolerancia debe estar entre 0 y " + MAX_TOLERANCIA_MIN + " minutos.");
        }
    }

    // Exige fecha de inicio de vigencia y que la de fin no sea anterior.
    private void validarVigencia(LocalDate desde, LocalDate hasta) {
        if (desde == null) {
            throw new IllegalArgumentException("La fecha de inicio de vigencia es obligatoria.");
        }
        if (hasta != null && hasta.isBefore(desde)) {
            throw new IllegalArgumentException(
                "La fecha de fin de vigencia (" + hasta + ") no puede ser anterior a la de inicio (" + desde + ").");
        }
    }

    // Rechaza la franja si pisa a otra de la misma comisión el mismo día.
    private void validarSinSolapamiento(Long comisionId, DiaSemana dia,
                                        LocalTime inicio, LocalTime fin, Long excludeId) {
        Byte diaNum = dia == null ? null : dia.getNumero();
        if (diaNum == null) {
            throw new IllegalArgumentException("El día de la semana es obligatorio.");
        }
        List<Horario> solapes = horarioRepository.findSolapamientos(comisionId, diaNum, inicio, fin, excludeId);
        if (!solapes.isEmpty()) {
            Horario primero = solapes.get(0);
            throw new IllegalArgumentException(
                "Se superpone con otra franja en la misma comisión el " + dia.getEtiqueta() +
                " (" + primero.getHoraInicio() + " - " + primero.getHoraFin() + ").");
        }
    }
}
