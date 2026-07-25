package edu.cent35.asistencias.config;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.config.TenantInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración de Spring MVC. Registra el TenantInterceptor para que corra en cada request
 * y publique el tenant del usuario autenticado, salteando los estáticos y actuator.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;

    // Aplica el interceptor a todo salvo estáticos y actuator, que no necesitan tenant.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .excludePathPatterns("/css/**", "/js/**", "/img/**", "/webjars/**", "/actuator/**");
    }
}
