package edu.cent35.asistencias.academico.web;

import edu.cent35.asistencias.academico.application.CarreraService;
import edu.cent35.asistencias.academico.domain.Carrera;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * CRUD de Carreras (RF-11).
 * Accesible para roles INSTITUCION y ADMIN.
 */
@Controller
@RequestMapping("/carreras")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class CarreraController {

    private final CarreraService service;

    // ============================================================
    //  Listado
    // ============================================================
    @GetMapping
    public String listar(Model model) {
        List<CarreraListItemDto> items = service.listar().stream()
            .map(CarreraListItemDto::from)
            .toList();
        model.addAttribute("carreras", items);
        return "academico/carrera-list";
    }

    // ============================================================
    //  Alta
    // ============================================================
    @GetMapping("/nueva")
    public String formNueva(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new CarreraFormDto());
        }
        model.addAttribute("modo", "crear");
        return "academico/carrera-form";
    }

    @PostMapping("/nueva")
    public String crear(@Valid @ModelAttribute("form") CarreraFormDto form,
                        BindingResult binding,
                        Model model,
                        RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            model.addAttribute("modo", "crear");
            return "academico/carrera-form";
        }

        try {
            Carrera c = service.crear(form.getCodigo(), form.getNombre());
            redirect.addFlashAttribute("flashMensaje",
                "Carrera '" + c.getCodigo() + "' creada correctamente.");
            return "redirect:/carreras";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            model.addAttribute("modo", "crear");
            return "academico/carrera-form";
        }
    }

    // ============================================================
    //  Edición
    // ============================================================
    @GetMapping("/{id}/editar")
    public String formEditar(@PathVariable Long id, Model model) {
        Carrera c = service.buscarPorId(id);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", CarreraFormDto.from(c));
        }
        model.addAttribute("carrera", c);
        model.addAttribute("modo", "editar");
        return "academico/carrera-form";
    }

    @PostMapping("/{id}/editar")
    public String actualizar(@PathVariable Long id,
                             @Valid @ModelAttribute("form") CarreraFormDto form,
                             BindingResult binding,
                             Model model,
                             RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            model.addAttribute("carrera", service.buscarPorId(id));
            model.addAttribute("modo", "editar");
            return "academico/carrera-form";
        }

        try {
            service.actualizar(id, form.getCodigo(), form.getNombre());
            redirect.addFlashAttribute("flashMensaje", "Carrera actualizada correctamente.");
            return "redirect:/carreras";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            model.addAttribute("carrera", service.buscarPorId(id));
            model.addAttribute("modo", "editar");
            return "academico/carrera-form";
        }
    }

    // ============================================================
    //  Baja / Alta lógica
    // ============================================================
    @PostMapping("/{id}/baja")
    public String darDeBaja(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.darDeBaja(id);
            redirect.addFlashAttribute("flashMensaje", "Carrera dada de baja.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        }
        return "redirect:/carreras";
    }

    @PostMapping("/{id}/alta")
    public String darDeAlta(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.darDeAlta(id);
            redirect.addFlashAttribute("flashMensaje", "Carrera reactivada.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        }
        return "redirect:/carreras";
    }

    // ============================================================
    //  Manejador para cross-tenant / not found - camuflado
    // ============================================================
    @ExceptionHandler(EntityNotFoundException.class)
    public String handleNotFound(EntityNotFoundException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", "La carrera solicitada no existe.");
        return "redirect:/carreras";
    }
}
