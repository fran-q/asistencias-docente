package edu.cent35.asistencias.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * {@link edu.cent35.asistencias.shared.multitenant.TenantInterceptor}
 * en cada request autenticado.
 */
@Configuration
@EnableWebSecurity
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
                .failureUrl("/login?error")
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
