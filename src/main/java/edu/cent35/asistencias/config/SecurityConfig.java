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
 * Configuracion de Spring Security para autenticacion real (Sprint 1 Fase C).
 * <p>
 * Reemplaza el {@code InMemoryUserDetailsManager} provisorio del Sprint 0.
 * Spring Security autodetecta el {@link CustomUserDetailsService} declarado
 * en el modulo {@code shared.security} y lo usa para cargar usuarios desde
 * la tabla {@code usuarios}.
 * <p>
 * Provee:
 * <ul>
 *   <li>Form login en {@code /login} con redireccion a {@code /} al exito.</li>
 *   <li>Logout que limpia {@code JSESSIONID} y redirige a {@code /login?logout}.</li>
 *   <li>Recursos publicos: {@code /login}, {@code /css/**}, {@code /js/**},
 *       {@code /img/**}, {@code /webjars/**}, {@code /actuator/health}.</li>
 *   <li>Resto requiere autenticacion.</li>
 *   <li>BCrypt como {@link PasswordEncoder} (RNF-06).</li>
 * </ul>
 * <p>
 * El {@code TenantContext} se setea via
 * {@link edu.cent35.asistencias.config.TenantInterceptor}
 * en cada request autenticado.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // habilita @PreAuthorize / @PostAuthorize en controllers y services
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/js/**", "/img/**", "/webjars/**", "/actuator/health").permitAll()
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
