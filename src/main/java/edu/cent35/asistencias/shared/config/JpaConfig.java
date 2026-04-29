package edu.cent35.asistencias.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Configuracion de JPA / transacciones.
 * <p>
 * <b>Por que el {@code order = LOWEST_PRECEDENCE - 100}</b>:
 * por default el aspecto de {@code @Transactional} corre con
 * {@code Ordered.LOWEST_PRECEDENCE} (ultimo en la cadena). Si nuestro
 * {@link edu.cent35.asistencias.shared.multitenant.TenantFilterAspect}
 * tiene el mismo orden, no es deterministico cual corre primero.
 * <p>
 * Bajamos el orden de {@code @Transactional} para que el aspecto de
 * transacciones corra <b>antes</b> (mas externo) que el filtro de tenant.
 * Asi, cuando el aspecto del tenant entra en accion, ya hay una transaccion
 * abierta y un {@code Session} de Hibernate disponible para activar el
 * filtro.
 * <p>
 * Spring AOP order semantics: <b>menor numero = mayor precedencia = mas
 * externo en la cadena</b>.
 */
@Configuration
@EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE - 100)
public class JpaConfig {
}
