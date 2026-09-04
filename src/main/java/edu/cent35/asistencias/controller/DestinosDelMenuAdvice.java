package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.service.SeccionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Pone en el modelo de todas las pantallas a dónde lleva cada grupo del menú.
 *
 * <p>El destino depende del rol: si el usuario tiene una sola pantalla en el grupo, el menú
 * va derecho a ella. Eso no se puede resolver en la plantilla —Thymeleaf prohíbe llamar
 * clases estáticas por seguridad, y con razón— así que se calcula acá, una vez por petición,
 * y la barra de navegación solo lee el resultado.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class DestinosDelMenuAdvice {

    private final SeccionService seccionService;

    // Se agrega a todas las pantallas porque la barra de navegación está en todas.
    @ModelAttribute
    public void destinos(Authentication auth, Model model) {
        // El anónimo no ve la barra, así que calcular esto para él sería trabajo perdido.
        if (auth == null || !auth.isAuthenticated()) return;

        model.addAttribute("destinoAcademico",
            seccionService.destinoDelGrupo(SeccionService.Grupo.ACADEMICO, auth));
        model.addAttribute("destinoAsistencia",
            seccionService.destinoDelGrupo(SeccionService.Grupo.ASISTENCIA, auth));
        model.addAttribute("destinoPersonal",
            seccionService.destinoDelGrupo(SeccionService.Grupo.PERSONAL, auth));
    }
}
