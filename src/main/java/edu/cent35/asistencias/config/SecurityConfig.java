package edu.cent35.asistencias.config;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de Spring Security: form login en /login, logout que limpia la JSESSIONID,
 * BCrypt como encoder (RNF-06) y acceso libre solo a los estáticos y al health. Todo lo
 * demás exige autenticación, y el tenant lo publica después TenantInterceptor.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // habilita @PreAuthorize / @PostAuthorize en controllers y services
public class SecurityConfig {

    // Arma la cadena de filtros: qué es público, cómo se entra y cómo se sale.
    @Bean
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
                                 "/css/**", "/js/**", "/img/**",
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
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("JSESSIONID")
                .permitAll()
            );
        return http.build();
    }

    // BCrypt: las contraseñas nunca se guardan ni se comparan en texto plano (RNF-06).
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
