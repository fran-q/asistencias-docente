package edu.cent35.asistencias.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita la ejecucion de tareas programadas ({@code @Scheduled}) en la
 * aplicacion. Introducido para el job de generacion de ausencias (RF-19).
 * <p>
 * Los hilos del scheduler NO pasan por {@code TenantInterceptor}: cualquier
 * job multi-tenant debe setear {@code TenantContext} manualmente por
 * institucion y limpiarlo al final (ver javadoc de {@code TenantContext}).
 */
@Configuration
@EnableScheduling
public class PlanificacionConfig {
}
