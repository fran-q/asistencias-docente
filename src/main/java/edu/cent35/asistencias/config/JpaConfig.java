package edu.cent35.asistencias.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Configuración de JPA y transacciones. Adelanta el aspecto de @Transactional (menor número
 * = más externo) para que corra antes que TenantFilterAspect: así, cuando el filtro de tenant
 * entra a actuar, ya hay transacción abierta y una Session de Hibernate donde activarlo.
 */
@Configuration
@EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE - 100)
public class JpaConfig {
}
