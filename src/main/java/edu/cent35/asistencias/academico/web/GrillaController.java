package edu.cent35.asistencias.academico.web;

import edu.cent35.asistencias.academico.application.GrillaService;
import edu.cent35.asistencias.academico.domain.Carrera;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Vista grilla horaria semanal por carrera (RF-14 visual).
 * GET /grilla[?carreraId=N]
 */
@Controller
@RequestMapping("/grilla")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
public class GrillaController {

    private final GrillaService grillaService;

    @GetMapping
    public String mostrar(@RequestParam(name = "carreraId", required = false) Long carreraId,
                          Model model) {

        List<Carrera> carreras = grillaService.carrerasActivasParaSelector();
        model.addAttribute("carreras", carreras);

        // Auto-seleccionar la primera si no se paso nada
        if (carreraId == null && !carreras.isEmpty()) {
            carreraId = carreras.get(0).getId();
        }

        if (carreraId != null) {
            GrillaSemanalDto grilla = grillaService.cargarGrillaPara(carreraId);
            model.addAttribute("grilla", grilla);
        }
        model.addAttribute("carreraIdSeleccionada", carreraId);

        // Dias para el header de la grilla (1=Lunes ... 7=Domingo)
        model.addAttribute("dias", edu.cent35.asistencias.academico.domain.DiaSemana.values());

        return "academico/grilla";
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public String handleNotFound(EntityNotFoundException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", "La carrera solicitada no existe.");
        return "redirect:/grilla";
    }
}
