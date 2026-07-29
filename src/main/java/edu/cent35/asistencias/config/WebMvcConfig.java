package edu.cent35.asistencias.config;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.config.TenantInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración de Spring MVC. Registra los dos interceptores que corren en cada request: el
 * que publica el tenant del usuario autenticado y el que impide operar sin haber verificado
 * el correo, salteando en ambos casos los estáticos y actuator.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String[] SIN_INTERCEPTAR = {
        "/css/**", "/js/**", "/img/**", "/webjars/**", "/actuator/**"
    };

    private final TenantInterceptor tenantInterceptor;
    private final VerificacionInterceptor verificacionInterceptor;

    // El orden importa: el tenant se publica primero, porque el bloqueo por verificacion
    // consulta la base y esa consulta tiene que correr con la institucion ya en contexto.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .excludePathPatterns(SIN_INTERCEPTAR)
                .order(1);
        registry.addInterceptor(verificacionInterceptor)
                .excludePathPatterns(SIN_INTERCEPTAR)
                .order(2);
    }
}
