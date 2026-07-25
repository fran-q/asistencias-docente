package edu.cent35.asistencias.config;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Activa el filtro de Hibernate "tenant" en los beans @Service, para que toda query contra
 * entidades tenant-scoped agregue sola su WHERE institucion_id. El pointcut apunta a la
 * anotación y no a un nombre de paquete, porque reorganizar los paquetes ya lo dejó
 * silenciosamente inactivo una vez (TD-007, ver docs/TECH_DEBT.md).
 */
@Aspect
@Component
@Slf4j
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    // Activa el filtro solo si hay transacción abierta y un tenant en contexto.
    @Before("@within(org.springframework.stereotype.Service)")
    public void activarFiltroTenant() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        TenantContext.get().ifPresent(tenantId -> {
            try {
                Session session = entityManager.unwrap(Session.class);
                if (session.getEnabledFilter("tenant") == null) {
                    session.enableFilter("tenant").setParameter("institucionId", tenantId);
                    log.trace("Filtro 'tenant' activado con institucionId={}", tenantId);
                }
            } catch (Exception e) {
                log.warn("No se pudo activar el filtro 'tenant' (tenantId={}): {}",
                         tenantId, e.getMessage());
            }
        });
    }
}
