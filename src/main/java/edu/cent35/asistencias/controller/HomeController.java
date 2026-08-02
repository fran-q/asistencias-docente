package edu.cent35.asistencias.controller;
import edu.cent35.asistencias.dto.*;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.service.PanelInicioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Páginas comunes a toda la aplicación: el login y el panel de inicio. El login repone el
 * usuario tipeado cuando el intento anterior falló, para no obligar a escribirlo de nuevo.
 * El inicio no repite los accesos del menú: muestra cómo viene el día y qué falta cargar.
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final PanelInicioService panelInicioService;

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

    // Panel de inicio: como viene el dia y que quedo a medio cargar (RF-60).
    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        model.addAttribute("username", authentication.getName());
        model.addAttribute("authorities", authentication.getAuthorities());
        model.addAttribute("panel", panelInicioService.armar());
        return "home";
    }
}
