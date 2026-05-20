package edu.cent35.asistencias.repository;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.ConsentimientoBiometrico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a {@code consentimientos_biometricos}.
 * <p>
 * <b>Multi-tenant</b>: el filtro Hibernate {@code "tenant"} no aplica
 * directamente porque la entidad no tiene {@code institucion_id} propio.
 * Las queries que cruzan tenants pasan por JOIN con {@code Docente}, que si
 * tiene el filtro - igual asi, en el service validamos explicitamente para
 * cumplir con el principio de defensa en profundidad (TD-003).
 */
public interface ConsentimientoBiometricoRepository extends JpaRepository<ConsentimientoBiometrico, Long> {

    /**
     * Consentimiento vigente del docente, si existe.
     * Solo deberia haber uno (lo garantiza el service).
     */
    Optional<ConsentimientoBiometrico> findByDocenteIdAndVigenteTrue(Long docenteId);

    /**
     * Ultimo registro del docente, vigente o revocado.
     * Usado para inferir el estado actual cuando puede no haber vigente.
     */
    Optional<ConsentimientoBiometrico> findTopByDocenteIdOrderByFechaConsentimientoDescIdDesc(Long docenteId);

    /** Historial completo del docente, ordenado del mas nuevo al mas viejo. */
    List<ConsentimientoBiometrico> findByDocenteIdOrderByFechaConsentimientoDescIdDesc(Long docenteId);

    /**
     * Cuenta cuantos docentes activos del tenant tienen consentimiento vigente.
     * Util para badges agregados / dashboards.
     */
    @Query("""
        SELECT COUNT(DISTINCT c.docente.id)
        FROM ConsentimientoBiometrico c
        WHERE c.vigente = true
          AND c.docente.institucionId = :tenantId
          AND c.docente.activo = true
    """)
    long countDocentesConVigenteEnTenant(@Param("tenantId") Long tenantId);

    /**
     * Proyeccion para alimentar el badge "Consentimiento" del listado de
     * docentes en una sola query (evita N+1). Devuelve, por cada docente
     * con al menos un consentimiento en el tenant, el id del docente y
     * el flag {@code vigente} del registro mas reciente.
     */
    @Query("""
        SELECT c.docente.id AS docenteId, c.vigente AS vigente
        FROM ConsentimientoBiometrico c
        WHERE c.docente.institucionId = :tenantId
          AND c.id = (
              SELECT MAX(c2.id) FROM ConsentimientoBiometrico c2
              WHERE c2.docente.id = c.docente.id
          )
    """)
    List<UltimoEstadoConsentimientoView> findUltimoEstadoPorDocenteEnTenant(@Param("tenantId") Long tenantId);

    /** Proyeccion liviana para {@link #findUltimoEstadoPorDocenteEnTenant}. */
    interface UltimoEstadoConsentimientoView {
        Long getDocenteId();
        Boolean getVigente();
    }
}
