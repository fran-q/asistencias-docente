package edu.cent35.asistencias.controller;
import edu.cent35.asistencias.dto.*;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.service.GrillaService;
import edu.cent35.asistencias.model.Carrera;
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
 * Vista de calendario semanal con los horarios de una carrera (RF-14). Es solo lectura: los
 * bloques enlazan al formulario de edición del horario correspondiente.
 */
@Controller
@RequestMapping("/grilla")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
public class GrillaController {

    private final GrillaService grillaService;

    // Arma la grilla semanal de la carrera elegida, o de la primera si no se eligió ninguna.
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
        model.addAttribute("dias", edu.cent35.asistencias.model.DiaSemana.values());

        return "academico/grilla";
    }

    // Si la carrera no existe o es de otra institución, avisa y vuelve a la grilla.
    @ExceptionHandler(EntityNotFoundException.class)
    public String handleNotFound(EntityNotFoundException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", "La carrera solicitada no existe.");
        return "redirect:/grilla";
    }
}
