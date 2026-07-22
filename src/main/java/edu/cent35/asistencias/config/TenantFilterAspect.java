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
 * Aspecto que activa el filtro de Hibernate {@code "tenant"} en cada
 * metodo transaccional de los modulos de aplicacion.
 * <p>
 * El filtro hace que toda query JPA contra entidades anotadas con
 * {@code @Filter(name = "tenant")} incluya automaticamente
 * {@code WHERE institucion_id = :institucionId}, usando el valor de
 * {@link TenantContext}.
 * <p>
 * <b>Cuando se ejecuta</b>: corre en cada metodo publico de los beans
 * anotados con {@code @Service} (donde vive la logica de negocio).
 * <p>
 * <b>Historia del pointcut (leccion aprendida, TD-007)</b>: originalmente
 * apuntaba a {@code edu.cent35.asistencias..application..*}, que era
 * correcto con la organizacion package-by-feature. Al reorganizar el
 * proyecto a package-by-layer (ADR-0006) los paquetes {@code application/}
 * desaparecieron y el pointcut dejo de coincidir con nada: el aspecto
 * quedo <b>silenciosamente inactivo</b> y la capa 1 de la defensa
 * multi-tenant murio sin que ningun test lo detectara (los tests son
 * unitarios con Mockito y no ejercitan Hibernate). Se cambio a un
 * pointcut por <b>anotacion</b> ({@code @Service}) en vez de por nombre
 * de paquete, justamente para que un futuro renombre de paquetes no lo
 * vuelva a romper.
 * <p>
 * Solo activa el filtro si:
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
 * {@link edu.cent35.asistencias.config.JpaConfig}.
 */
@Aspect
@Component
@Slf4j
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

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
