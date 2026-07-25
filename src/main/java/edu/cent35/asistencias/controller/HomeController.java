package edu.cent35.asistencias.controller;
import edu.cent35.asistencias.dto.*;
import edu.cent35.asistencias.model.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Páginas comunes a toda la aplicación: el login y el panel de inicio. El login repone el
 * usuario tipeado cuando el intento anterior falló, para no obligar a escribirlo de nuevo.
 */
@Controller
public class HomeController {

    // Muestra el login, reponiendo el usuario del intento fallido anterior (nunca la contraseña).
    @GetMapping("/login")
    public String login(HttpServletRequest request, Model model) {
        // Si venimos de un login fallido, reponemos el usuario que se intento
        // (lo guardo el failureHandler de SecurityConfig). Se consume una sola
        // vez para que no quede pegado en logins posteriores.
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object ultimo = session.getAttribute("ULTIMO_USUARIO_LOGIN");
            if (ultimo != null) {
                model.addAttribute("ultimoUsuario", ultimo);
                session.removeAttribute("ULTIMO_USUARIO_LOGIN");
            }
        }
        return "auth/login";
    }

    // Panel de inicio con los accesos rápidos según el rol.
    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        model.addAttribute("username", authentication.getName());
        model.addAttribute("authorities", authentication.getAuthorities());
        return "home";
    }
}
