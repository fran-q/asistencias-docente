package edu.cent35.asistencias.shared.config;

import edu.cent35.asistencias.shared.multitenant.TenantInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuracion de Spring MVC.
 * <p>
 * Registra el {@link TenantInterceptor} para que corra en cada request
 * HTTP, seteando el {@link edu.cent35.asistencias.shared.multitenant.TenantContext}
 * a partir del usuario autenticado.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .excludePathPatterns("/css/**", "/js/**", "/img/**", "/webjars/**", "/actuator/**");
    }
}
