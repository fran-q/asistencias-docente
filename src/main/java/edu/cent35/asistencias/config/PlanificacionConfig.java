package edu.cent35.asistencias.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita las tareas programadas (@Scheduled), que se usan para el job de generación de
 * ausencias (RF-19). Ojo: los hilos del scheduler no pasan por TenantInterceptor, así que
 * cada job multi-tenant tiene que setear y limpiar el TenantContext a mano.
 */
@Configuration
@EnableScheduling
public class PlanificacionConfig {
}
