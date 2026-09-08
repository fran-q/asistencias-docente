package edu.cent35.asistencias.config;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.io.IOException;

/**
 * Configuración de Spring Security: form login en /login, logout que limpia la JSESSIONID,
 * BCrypt como encoder (RNF-06) y acceso libre solo a los estáticos y al health. Todo lo
 * demás exige autenticación, y el tenant lo publica después TenantInterceptor.
 * <p>
 * Son <b>dos cadenas</b>. La del kiosco va primera y cubre solo {@code /kiosco/**}: son las
 * únicas rutas que funcionan sin sesión, y lo que las autoriza es la credencial del equipo en
 * vez de un usuario (ADR-0019). La principal cubre todo lo demás y no cambió.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // habilita @PreAuthorize / @PostAuthorize en controllers y services
public class SecurityConfig {

    /**
     * Cadena propia para el kiosco: las únicas rutas que funcionan sin sesión (RF-84,
     * ADR-0019).
     *
     * <p><b>Va en una cadena aparte y no como una excepción de la principal</b> por dos
     * motivos. El alcance de lo que corre sin autenticar tiene que leerse de un vistazo, sin
     * rastrear excepciones dentro de una lista larga. Y el CSRF necesita otra configuración,
     * que no se puede tener por ruta dentro de una misma cadena.
     *
     * <p><b>El token CSRF va en cookie y no en sesión.</b> El default de Spring lo guarda en
     * la {@code HttpSession}, que acá expira a los 30 minutos: un kiosco encendido todo el día
     * en un turno tranquilo se quedaría sin token y los POST empezarían a fallar con 403 sin
     * que nadie entienda por qué. En cookie no depende de la sesión.
     *
     * <p><b>Esto no deja el endpoint abierto.</b> Que Spring Security no exija autenticación no
     * significa que cualquiera entre: {@code PuestoCapturaInterceptor} sigue exigiendo la
     * credencial de un equipo autorizado, y {@code KioscoTenantInterceptor} solo publica la
     * institución si esa credencial resuelve. Sin cookie de puesto válida no hay institución y
     * la petición muere ahí. La autenticación se reemplaza por la credencial del equipo, no se
     * elimina.
     *
     * <p>La cookie del puesto es {@code SameSite=Strict}, así que un sitio externo no puede
     * hacer que el navegador la envíe: eso ya bloquea el CSRF clásico. El token es la segunda
     * capa, no la única.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain kioscoFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/kiosco", "/kiosco/**")
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.csrfTokenRepository(
                CookieCsrfTokenRepository.withHttpOnlyFalse()));
        return http.build();
    }

    // Arma la cadena de filtros: qué es público, cómo se entra y cómo se sale.
    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // /recuperar/** va abierto por necesidad: quien perdio la contrasena no puede
                // autenticarse para pedirla. El flujo se protege con el codigo de un solo uso.
                //
                // /alta-institucion/** tambien: crear una institucion ocurre ANTES de que
                // exista el tenant, asi que no hay sesion ni rol contra el cual autorizar. Lo
                // que la protege es el codigo que se manda al correo declarado, que ademas
                // impide que la institucion llegue a crearse sin esa direccion comprobada
                // (ADR-0010).
                .requestMatchers("/login", "/recuperar/**", "/alta-institucion/**",
                                 "/alta-institucion",
                                 // /fonts/** va con el resto de lo estatico. Sin esta linea
                                 // la peticion de cada .woff2 cae en anyRequest().authenticated()
                                 // y se responde con la redireccion al login: el navegador
                                 // recibe HTML donde esperaba una fuente, @font-face falla y la
                                 // aplicacion vuelve a la tipografia del sistema sin decir por
                                 // que. Cuesta de encontrar porque no rompe nada visible.
                                 "/css/**", "/js/**", "/img/**", "/fonts/**",
                                 "/webjars/**", "/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                // Tras un login fallido preservamos SOLO el usuario intentado
                // (nunca la contrasenia) para reponerlo en el formulario. Mejora
                // la usabilidad (RNF-21/24) sin exponer credenciales: la
                // contrasenia se vuelve a escribir.
                .failureHandler((request, response, exception) -> {
                    String usuario = request.getParameter("username");
                    if (usuario != null && !usuario.isBlank()) {
                        request.getSession().setAttribute("ULTIMO_USUARIO_LOGIN", usuario);
                    }
                    response.sendRedirect(request.getContextPath() + "/login?error");
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessHandler(SecurityConfig::despuesDeCerrarSesion)
                // Solo la de sesion. La del puesto identifica la MAQUINA y tiene que
                // sobrevivir: es lo que deja al kiosco funcionando despues de cerrar sesion.
                .deleteCookies("JSESSIONID")
                .permitAll()
            );
        return http.build();
    }

    /**
     * A dónde va la persona después de cerrar sesión: al login, o al kiosco si venía de
     * dejar el equipo tomando asistencia sin supervisión (RF-84).
     *
     * <p><b>Por qué el kiosco se entra cerrando la sesión y no abriendo una pestaña.</b> Si la
     * sesión quedara viva detrás de la pantalla desatendida, bastaría apretar Atrás para
     * entrar al sistema entero con la cuenta de quien lo dejó andando — que es exactamente la
     * práctica que el kiosco vino a reemplazar. Cerrarla al entrar hace que Atrás caiga en el
     * login, y deja al kiosco operando con lo único que tiene que autorizarlo: la credencial
     * del equipo.
     *
     * <p>El destino <b>no</b> sale de la petición: se compara contra un valor fijo y se elige
     * entre dos rutas escritas acá. Tomar una URL del formulario sería un redirect abierto, y
     * el logout es una de las páginas más fáciles de hacerle abrir a alguien desde afuera.
     */
    private static void despuesDeCerrarSesion(HttpServletRequest request,
                                              HttpServletResponse response,
                                              Authentication authentication) throws IOException {
        boolean alKiosco = "kiosco".equals(request.getParameter("destino"));
        response.sendRedirect(request.getContextPath() + (alKiosco ? "/kiosco" : "/login?logout"));
    }

    // BCrypt: las contraseñas nunca se guardan ni se comparan en texto plano (RNF-06).
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
