package edu.cent35.asistencias.config;
import edu.cent35.asistencias.interceptor.VerificacionInterceptor;
import edu.cent35.asistencias.interceptor.KioscoTenantInterceptor;
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
        "/docentes/*/rostro/registrar",
        // El kiosco tambien es captura: exige equipo autorizado igual que el resto.
        "/kiosco", "/kiosco/**"
    };

    /**
     * Las rutas que pueden funcionar <b>sin sesión abierta</b>, resolviendo la institución
     * desde el equipo autorizado (RF-84, ADR-0019).
     *
     * <p>Es deliberadamente más chica que {@link #SOLO_EN_PUESTO}: el registro del rostro
     * queda afuera y sigue exigiendo sesión (RF-86). Marcar sin supervisión registra un
     * hecho; enrolar sin supervisión <b>crea una identidad</b>, y permitiría que cualquiera
     * registre su cara como la de un docente para después marcar por él.
     *
     * <p>Que sean dos listas separadas y no una con excepciones es a propósito: el alcance
     * de lo que puede correr sin sesión tiene que leerse de un vistazo.
     */
    private static final String[] PUEDEN_SIN_SESION = {
        "/kiosco", "/kiosco/**"
    };

    private final TenantInterceptor tenantInterceptor;
    private final VerificacionInterceptor verificacionInterceptor;
    private final PuestoCapturaInterceptor puestoCapturaInterceptor;
    private final KioscoTenantInterceptor kioscoTenantInterceptor;

    /**
     * El orden importa: el tenant se publica primero, porque los otros dos consultan la base y
     * esas consultas tienen que correr con la institución ya en contexto. El puesto va último:
     * no tiene sentido exigir un equipo autorizado a una cuenta que todavía no puede operar el
     * sistema porque no verificó su correo.
     *
     * <p>El del kiosco va en el orden 0, <b>antes</b> que el del tenant, y solo sobre las
     * rutas que pueden correr sin sesión. Si hay usuario autenticado no hace nada: así el
     * tenant de la sesión siempre pisa al del equipo, y no al revés. Sin ese orden, un
     * administrador de una institución trabajando en la máquina de otra terminaría operando
     * sobre los datos de la máquina.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(kioscoTenantInterceptor)
                .addPathPatterns(PUEDEN_SIN_SESION)
                .order(0);
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
