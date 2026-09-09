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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

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
            // El token CSRF va en cookie y no en sesion, por el mismo motivo que ya vale para
            // el kiosco: el default de Spring lo guarda en la HttpSession, que expira a los 30
            // minutos.
            //
            // Aca ademas es lo que hace que el 401 de abajo llegue a existir. Guardado en
            // sesion, el token muere junto con ella, asi que el POST del pase se rechazaba
            // primero por CSRF: 403, reenvio a /error y desde ahi la redireccion al login, sin
            // que la entrada de autenticacion llegara a ver la ruta original. Medido: el
            // matcher recibia /error. En cookie el token sobrevive, el filtro de CSRF deja
            // pasar y recien entonces se evalua la sesion, que es lo que falta de verdad.
            .csrf(csrf -> csrf.csrfTokenRepository(
                CookieCsrfTokenRepository.withHttpOnlyFalse()))
            // Sin esto, una sesion vencida contesta el POST de fetch con la redireccion al
            // login. Ver el javadoc de sesionVencidaEnApi.
            .exceptionHandling(ex -> ex.authenticationEntryPoint(entradaDeAutenticacion()))
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
     * Los endpoints que contestan JSON y viven detrás de la sesión.
     *
     * <p>Se enumeran por ruta y no por la anotación {@code @ResponseBody}, como sí hace
     * {@code PuestoCapturaInterceptor} para resolver lo mismo un escalón más adentro. No es
     * por gusto: los filtros de Spring Security corren <b>antes</b> del DispatcherServlet, así
     * que en ese punto todavía no existe el {@code HandlerMethod} donde mirar la anotación.
     *
     * <p>La contrapartida es que una ruta que se mueva deja de coincidir sin avisar y vuelve a
     * fallar en silencio. Es el precio de acotar el cambio a estas tres: la alternativa —dejar
     * que la entrada propia sea también el comportamiento por defecto— alcanzaba a toda
     * petición sin sesión que no pidiera HTML, mucho más de lo que hace falta.
     *
     * <p><b>Por qué el matcher se escribe a mano.</b> La versión con
     * {@code PathPatternRequestMatcher} coincidía en MockMvc y <b>no</b> contra el servidor
     * real: los tests daban 401 y el navegador seguía recibiendo la redirección. Ese matcher
     * necesita la ruta ya parseada por el DispatcherServlet, y esta entrada corre en la cadena
     * de filtros, antes. Comparar la URI a mano se comporta igual en los dos lados, que es lo
     * único que importa cuando lo que se está arreglando es justamente un fallo mudo.
     */
    /**
     * Quién contesta cada petición sin autenticar: el 401 con JSON para los endpoints de
     * {@link #ENDPOINTS_JSON}, la redirección al login para todo lo demás.
     *
     * <p>El delegador se arma acá y no con {@code defaultAuthenticationEntryPointFor}, que
     * parece hacer lo mismo y no lo hace. Con ese método, Spring construye el delegador solo
     * si no hay una entrada explícita, y toma <b>la primera registrada</b> como comportamiento
     * por defecto: la entrada de las tres rutas terminaba respondiendo también a cualquier otra
     * petición sin sesión que no pidiera HTML. Agregarle encima un
     * {@code authenticationEntryPoint} explícito tampoco sirve —ese gana entero y descarta el
     * mapeo, así que las tres rutas volvían a redirigir—. Las dos variantes las detectó la
     * suite: la primera rompió tres tests que ya existían, la segunda rompió los nuevos.
     *
     * <p>Construyéndolo a mano, el default es el que se elige y no el que queda.
     */
    private static AuthenticationEntryPoint entradaDeAutenticacion() {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> porRuta = new LinkedHashMap<>();
        porRuta.put(ENDPOINTS_JSON, SecurityConfig::sesionVencidaEnApi);

        DelegatingAuthenticationEntryPoint entrada = new DelegatingAuthenticationEntryPoint(porRuta);
        entrada.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));
        return entrada;
    }

    private static final RequestMatcher ENDPOINTS_JSON = peticion -> {
        if (!"POST".equalsIgnoreCase(peticion.getMethod())) return false;
        String ruta = peticion.getRequestURI().substring(peticion.getContextPath().length());
        return ruta.equals("/asistencia/pase/marcar")
            || ruta.startsWith("/reconocimiento/")
            || (ruta.startsWith("/docentes/") && ruta.endsWith("/rostro/registrar"));
    };

    /**
     * Qué se le contesta a una llamada JSON cuando la sesión ya no está.
     *
     * <p>Por defecto, Spring Security responde toda petición sin autenticar con una
     * redirección al login. Para una pantalla eso es lo correcto; para un {@code fetch} es un
     * desastre silencioso: {@code fetch} sigue la redirección, recibe el HTML del login con
     * estado 200, y del lado del cliente {@code response.ok} da <b>true</b>. El
     * {@code response.json()} que viene después revienta con un error de sintaxis, y el
     * {@code catch} de la pantalla lo toma por un corte de red pasajero y no lo muestra.
     *
     * <p>El resultado, en el pase: la cámara queda encendida mandando un cuadro por segundo,
     * no se registra ninguna asistencia y la pantalla no dice nada. Con la clase esperando.
     *
     * <p>Un 401 con cuerpo JSON deja que cada pantalla lo trate como ya trata el 403 del
     * puesto revocado: frenar el bucle, apagar la cámara y decir qué pasó. El cuerpo tiene el
     * mismo formato que usa {@code PuestoCapturaInterceptor} para que del otro lado se lean
     * igual.
     */
    private static void sesionVencidaEnApi(HttpServletRequest request,
                                           HttpServletResponse response,
                                           AuthenticationException excepcion) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
            "{\"error\":\"SESION_VENCIDA\","
          + "\"mensaje\":\"Se cerró la sesión. Volvé a entrar para seguir.\"}");
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
