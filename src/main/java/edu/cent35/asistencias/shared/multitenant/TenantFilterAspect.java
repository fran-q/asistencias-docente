package edu.cent35.asistencias.shared.multitenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Aspecto que activa el filtro de Hibernate {@code "tenant"} en cada
 * metodo transaccional de los modulos de aplicacion.
 * <p>
 * El filtro hace que toda query JPA contra entidades anotadas con
 * {@code @Filter(name = "tenant")} incluya automaticamente
 * {@code WHERE institucion_id = :institucionId}, usando el valor de
 * {@link TenantContext}.
 * <p>
 * <b>Cuando se ejecuta</b>: corre en cada metodo dentro de paquetes
 * {@code application/} de cualquier modulo de dominio (donde viven los
 * services). Solo activa el filtro si:
 * <ol>
 *   <li>Hay una transaccion activa (lo controla Spring via
 *       {@link TransactionSynchronizationManager#isActualTransactionActive()}).</li>
 *   <li>Hay un tenant en {@link TenantContext}.</li>
 * </ol>
 * <p>
 * <b>Importante</b>: el orden de este aspecto es el default
 * ({@code LOWEST_PRECEDENCE}), de manera que corra <i>despues</i>
 * (mas interno) que el aspecto de {@code @Transactional} configurado
 * con menor precedencia en
 * {@link edu.cent35.asistencias.shared.config.JpaConfig}.
 */
@Aspect
@Component
@Slf4j
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* edu.cent35.asistencias..application..*(..))")
    public void enableTenantFilter() {
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
