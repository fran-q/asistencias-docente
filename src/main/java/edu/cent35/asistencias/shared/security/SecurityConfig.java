package edu.cent35.asistencias.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuracion provisoria de Spring Security para Sprint 0.
 * <p>
 * Provee:
 * <ul>
 *   <li>Un usuario in-memory ({@code admin/admin}) para validar el flujo de login.</li>
 *   <li>BCrypt como algoritmo de hash de contrasenas (RNF-06).</li>
 *   <li>Form login con pagina personalizada en {@code /login}.</li>
 *   <li>Recursos estaticos ({@code /css/**}, {@code /js/**}) y healthcheck publicos.</li>
 *   <li>Todo el resto requiere autenticacion.</li>
 * </ul>
 * <p>
 * En Sprint 1 esta clase sera reemplazada por una version que:
 * <ul>
 *   <li>Lee usuarios desde la tabla {@code usuarios} via {@code UserDetailsService} real.</li>
 *   <li>Aplica autorizacion por rol (RF-03).</li>
 *   <li>Setea el {@code TenantContext} con el {@code institucion_id} del usuario logueado.</li>
 * </ul>
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

    /**
     * SOLO PARA SPRINT 0: usuarios in-memory para probar el flujo de login.
     * En Sprint 1 sera reemplazado por una implementacion que lea desde la BD.
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails admin = User.builder()
            .username("admin")
            .password(encoder.encode("admin"))
            .roles("ADMIN")
            .build();
        return new InMemoryUserDetailsManager(admin);
    }
}
