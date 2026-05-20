package edu.cent35.asistencias.service;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.model.ConsentimientoBiometrico;
import edu.cent35.asistencias.model.EstadoConsentimiento;
import edu.cent35.asistencias.model.MetodoConsentimiento;
import edu.cent35.asistencias.repository.ConsentimientoBiometricoRepository;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Operaciones sobre el consentimiento biometrico del docente. Cubre RF-10
 * y RNF-13 (Ley 25.326 + Resolucion AAIP 255/2022).
 * <p>
 * <b>Modelo</b>: cada docente puede tener varios registros historicos
 * (otorga -> revoca -> otorga). El "estado actual" se calcula con el
 * registro mas reciente. Solo puede haber UN registro vigente por docente
 * - garantizado a nivel aplicacion (no a nivel DB porque MariaDB no soporta
 * indices unicos parciales del estilo {@code WHERE vigente = true}).
 * <p>
 * <b>Multi-tenant</b>: el consentimiento se valida via el docente padre
 * (mismo patron que {@code Comision}). El service nunca toca un docente que
 * no sea del tenant actual.
 * <p>
 * <b>Quien otorga/revoca</b>: en Sprint 3 lo hace el admin (rol
 * {@code INSTITUCION} o {@code ADMIN}) en representacion del docente, que
 * firma en papel. En Sprint 4 se habilitara que el docente acepte por si
 * mismo via login propio (metodo {@code DIGITAL}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConsentimientoBiometricoService {

    private final ConsentimientoBiometricoRepository consentimientoRepository;
    private final DocenteRepository docenteRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * Estado actual del consentimiento del docente.
     * Devuelve {@link EstadoConsentimiento#NUNCA_OTORGADO} si nunca hubo
     * ningun registro, {@code ACTIVO} si el ultimo esta vigente, o
     * {@code REVOCADO} si el ultimo fue revocado.
     */
    @Transactional(readOnly = true)
    public EstadoConsentimiento estadoActual(Long docenteId) {
        Docente docente = obtenerDocenteValidado(docenteId);
        return consentimientoRepository
            .findTopByDocenteIdOrderByFechaConsentimientoDescIdDesc(docente.getId())
            .map(c -> Boolean.TRUE.equals(c.getVigente())
                ? EstadoConsentimiento.ACTIVO
                : EstadoConsentimiento.REVOCADO)
            .orElse(EstadoConsentimiento.NUNCA_OTORGADO);
    }

    /** Consentimiento vigente del docente, si existe. */
    @Transactional(readOnly = true)
    public Optional<ConsentimientoBiometrico> vigenteDe(Long docenteId) {
        Docente docente = obtenerDocenteValidado(docenteId);
        Optional<ConsentimientoBiometrico> opt =
            consentimientoRepository.findByDocenteIdAndVigenteTrue(docente.getId());
        opt.ifPresent(this::touchAsociacionesLazy);
        return opt;
    }

    /**
     * Estado del consentimiento de cada docente del tenant actual.
     * Pensado para el listado de docentes (una sola query, evita N+1).
     * Si un docente no esta en el Map devuelto, se interpreta como
     * {@link EstadoConsentimiento#NUNCA_OTORGADO}.
     */
    @Transactional(readOnly = true)
    public Map<Long, EstadoConsentimiento> estadosPorDocenteEnTenant() {
        Long tenantId = TenantContext.getRequired();
        Map<Long, EstadoConsentimiento> resultado = new HashMap<>();
        consentimientoRepository.findUltimoEstadoPorDocenteEnTenant(tenantId)
            .forEach(row -> resultado.put(
                row.getDocenteId(),
                Boolean.TRUE.equals(row.getVigente())
                    ? EstadoConsentimiento.ACTIVO
                    : EstadoConsentimiento.REVOCADO));
        return resultado;
    }

    /** Historial completo del docente (del mas nuevo al mas viejo). */
    @Transactional(readOnly = true)
    public List<ConsentimientoBiometrico> historialDe(Long docenteId) {
        Docente docente = obtenerDocenteValidado(docenteId);
        List<ConsentimientoBiometrico> historial = consentimientoRepository
            .findByDocenteIdOrderByFechaConsentimientoDescIdDesc(docente.getId());
        historial.forEach(this::touchAsociacionesLazy);
        return historial;
    }

    /**
     * Inicializa las asociaciones LAZY que el template Thymeleaf necesita
     * leer despues de que la transaccion cierre. Requerido porque
     * {@code spring.jpa.open-in-view=false}.
     */
    private void touchAsociacionesLazy(ConsentimientoBiometrico c) {
        if (c.getRegistradoPor() != null) c.getRegistradoPor().getUsername();
        if (c.getRevocadoPor() != null) c.getRevocadoPor().getUsername();
    }

    /**
     * Registra un consentimiento nuevo para el docente. Bloquea si ya hay
     * uno vigente (hay que revocar primero).
     *
     * @param docenteId       a quien pertenece
     * @param metodo          ESCRITO (admin en representacion) o DIGITAL
     * @param fechaFirma      cuando lo firmo el docente (puede ser pasada)
     * @param ip              IP del admin que esta cargando (audit forense)
     * @param userAgent       User-Agent del admin (audit forense)
     * @param documentoUrl    opcional: URL al PDF escaneado
     * @param usuarioActualId id del admin logueado
     */
    @Transactional
    public ConsentimientoBiometrico otorgar(
            Long docenteId,
            MetodoConsentimiento metodo,
            LocalDateTime fechaFirma,
            String ip,
            String userAgent,
            String documentoUrl,
            Long usuarioActualId) {

        Docente docente = obtenerDocenteValidado(docenteId);

        if (Boolean.FALSE.equals(docente.getActivo())) {
            throw new IllegalArgumentException(
                "No se puede otorgar consentimiento a un docente inactivo. Reactivalo primero.");
        }

        if (consentimientoRepository.findByDocenteIdAndVigenteTrue(docente.getId()).isPresent()) {
            throw new IllegalArgumentException(
                "El docente ya tiene un consentimiento vigente. Revocalo primero si querés cargar uno nuevo.");
        }

        if (metodo == null) {
            throw new IllegalArgumentException("El método de firma es obligatorio.");
        }

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime fechaConsentimiento = (fechaFirma != null) ? fechaFirma : ahora;
        if (fechaConsentimiento.isAfter(ahora)) {
            throw new IllegalArgumentException(
                "La fecha de firma no puede ser futura.");
        }

        Usuario admin = usuarioRepository.findById(usuarioActualId)
            .orElseThrow(() -> new EntityNotFoundException("Usuario actual no encontrado: " + usuarioActualId));

        ConsentimientoBiometrico c = ConsentimientoBiometrico.builder()
            .docente(docente)
            .versionTerminos(TextoConsentimiento.VERSION_ACTUAL)
            .metodo(metodo)
            .documentoUrl(trimToNull(documentoUrl))
            .fechaConsentimiento(fechaConsentimiento)
            .vigente(true)
            .registradoPor(admin)
            .ipOtorgamiento(trimToNull(ip))
            .userAgentOtorgamiento(trimToNull(userAgent))
            .build();

        ConsentimientoBiometrico saved = consentimientoRepository.save(c);
        log.info("Consentimiento otorgado: id={}, docente_id={}, version={}, metodo={}, registrado_por={}",
                 saved.getId(), docente.getId(), saved.getVersionTerminos(), saved.getMetodo(), admin.getId());
        return saved;
    }

    /**
     * Revoca el consentimiento vigente del docente. Audita IP/UA del admin
     * que ejecuta la revocacion (no del docente).
     *
     * @param docenteId         a quien pertenece
     * @param motivo            texto libre opcional (derecho ARCO)
     * @param ip                IP del admin (audit)
     * @param userAgent         User-Agent del admin (audit)
     * @param usuarioActualId   id del admin logueado
     */
    @Transactional
    public ConsentimientoBiometrico revocar(
            Long docenteId,
            String motivo,
            String ip,
            String userAgent,
            Long usuarioActualId) {

        Docente docente = obtenerDocenteValidado(docenteId);

        ConsentimientoBiometrico vigente = consentimientoRepository
            .findByDocenteIdAndVigenteTrue(docente.getId())
            .orElseThrow(() -> new IllegalArgumentException(
                "El docente no tiene un consentimiento vigente para revocar."));

        Usuario admin = usuarioRepository.findById(usuarioActualId)
            .orElseThrow(() -> new EntityNotFoundException("Usuario actual no encontrado: " + usuarioActualId));

        vigente.setVigente(false);
        vigente.setFechaRevocacion(LocalDateTime.now());
        vigente.setMotivoRevocacion(trimToNull(motivo));
        vigente.setRevocadoPor(admin);
        vigente.setIpRevocacion(trimToNull(ip));
        vigente.setUserAgentRevocacion(trimToNull(userAgent));

        ConsentimientoBiometrico saved = consentimientoRepository.save(vigente);
        log.info("Consentimiento revocado: id={}, docente_id={}, revocado_por={}",
                 saved.getId(), docente.getId(), admin.getId());
        return saved;
    }

    // ----------------------------------------------------------------------

    /**
     * Carga el docente validando que sea del tenant actual.
     * Si no, tira {@code EntityNotFoundException} igual que si no existiera
     * (defensa: no revelar a otro tenant que el id existe).
     */
    private Docente obtenerDocenteValidado(Long docenteId) {
        Long tenantId = TenantContext.getRequired();
        Docente d = docenteRepository.findById(docenteId)
            .orElseThrow(() -> new EntityNotFoundException("Docente no encontrado: " + docenteId));
        if (!tenantId.equals(d.getInstitucionId())) {
            log.warn("Cross-tenant blocked: tenant {} intento operar sobre docente id={} (tenant {})",
                     tenantId, docenteId, d.getInstitucionId());
            throw new EntityNotFoundException("Docente no encontrado");
        }
        return d;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
