package edu.cent35.asistencias.config;
import edu.cent35.asistencias.interceptor.VerificacionInterceptor;
import edu.cent35.asistencias.interceptor.PuestoCapturaInterceptor;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.interceptor.TenantInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración de Spring MVC. Registra los interceptores que corren en cada request: el que
 * publica el tenant del usuario autenticado, el que impide operar sin haber verificado el
 * correo y el que restringe la captura biométrica a los puestos autorizados, salteando en
 * todos los casos los estáticos y actuator.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    // /fonts/** va con el resto de los estáticos. No alcanza con permitirlo en
    // SecurityConfig: los interceptores corren después, sobre cualquier ruta que no esté
    // en esta lista, incluidas las que atiende el manejador de recursos. El de verificación
    // respondía cada .woff2 con la redirección al login, así que el navegador recibía HTML
    // donde esperaba una fuente y la aplicación caía a la tipografía del sistema sin avisar.
    private static final String[] SIN_INTERCEPTAR = {
        "/css/**", "/js/**", "/img/**", "/fonts/**", "/webjars/**", "/actuator/**"
    };

    /**
     * Las rutas que solo funcionan desde un puesto autorizado (ADR-0015).
     *
     * <p>Se nombran acá, en la configuración, y no adentro del interceptor: así el alcance
     * del control se lee de un vistazo junto al resto del ruteo, en vez de estar escondido
     * en una condición.
     *
     * <p>{@code /docentes/*}{@code /rostro/registrar} y no {@code /rostro/**}: la supresión
     * del dato biométrico vive bajo el mismo prefijo y NO se restringe. Es un derecho ARCO
     * y no puede depender de estar frente a una máquina determinada.
     */
    private static final String[] SOLO_EN_PUESTO = {
        "/asistencia/pase", "/asistencia/pase/**",
        "/reconocimiento/**",
        "/docentes/*/rostro/registrar"
    };

    private final TenantInterceptor tenantInterceptor;
    private final VerificacionInterceptor verificacionInterceptor;
    private final PuestoCapturaInterceptor puestoCapturaInterceptor;

    // El orden importa: el tenant se publica primero, porque los otros dos consultan la base
    // y esas consultas tienen que correr con la institucion ya en contexto. El puesto va
    // ultimo: no tiene sentido exigir un equipo autorizado a una cuenta que todavia no puede
    // operar el sistema porque no verifico su correo.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .excludePathPatterns(SIN_INTERCEPTAR)
                .order(1);
        registry.addInterceptor(verificacionInterceptor)
                .excludePathPatterns(SIN_INTERCEPTAR)
                .order(2);
        registry.addInterceptor(puestoCapturaInterceptor)
                .addPathPatterns(SOLO_EN_PUESTO)
                .order(3);
    }
}
