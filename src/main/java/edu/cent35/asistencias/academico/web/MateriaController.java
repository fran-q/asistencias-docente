package edu.cent35.asistencias.academico.web;

import edu.cent35.asistencias.academico.application.MateriaService;
import edu.cent35.asistencias.academico.domain.Materia;
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
 * CRUD de Materias (RF-12).
 * Accesible para roles INSTITUCION y ADMIN.
 */
@Controller
@RequestMapping("/materias")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class MateriaController {

    private final MateriaService service;

    @GetMapping
    public String listar(Model model) {
        List<MateriaListItemDto> items = service.listar().stream()
            .map(MateriaListItemDto::from)
            .toList();
        model.addAttribute("materias", items);
        return "academico/materia-list";
    }

    @GetMapping("/nueva")
    public String formNueva(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new MateriaFormDto());
        }
        model.addAttribute("carreras", service.carrerasActivasParaSelector());
        model.addAttribute("modo", "crear");
        return "academico/materia-form";
    }

    @PostMapping("/nueva")
    public String crear(@Valid @ModelAttribute("form") MateriaFormDto form,
                        BindingResult binding,
                        Model model,
                        RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            model.addAttribute("carreras", service.carrerasActivasParaSelector());
            model.addAttribute("modo", "crear");
            return "academico/materia-form";
        }

        try {
            Materia m = service.crear(form.getCodigo(), form.getNombre(), form.getCarreraId());
            redirect.addFlashAttribute("flashMensaje",
                "Materia '" + m.getCodigo() + "' creada correctamente.");
            return "redirect:/materias";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            model.addAttribute("carreras", service.carrerasActivasParaSelector());
            model.addAttribute("modo", "crear");
            return "academico/materia-form";
        }
    }

    @GetMapping("/{id}/editar")
    public String formEditar(@PathVariable Long id, Model model) {
        Materia m = service.buscarPorId(id);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", MateriaFormDto.from(m));
        }
        model.addAttribute("materia", m);
        // En edicion incluimos la carrera actual aunque este inactiva
        // (asi el select muestra el valor seleccionado)
        List<edu.cent35.asistencias.academico.domain.Carrera> opciones =
            new java.util.ArrayList<>(service.carrerasActivasParaSelector());
        if (m.getCarrera() != null && Boolean.FALSE.equals(m.getCarrera().getActivo())
                && opciones.stream().noneMatch(c -> c.getId().equals(m.getCarrera().getId()))) {
            opciones.add(m.getCarrera());
        }
        model.addAttribute("carreras", opciones);
        model.addAttribute("modo", "editar");
        return "academico/materia-form";
    }

    @PostMapping("/{id}/editar")
    public String actualizar(@PathVariable Long id,
                             @Valid @ModelAttribute("form") MateriaFormDto form,
                             BindingResult binding,
                             Model model,
                             RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            model.addAttribute("materia", service.buscarPorId(id));
            model.addAttribute("carreras", service.carrerasActivasParaSelector());
            model.addAttribute("modo", "editar");
            return "academico/materia-form";
        }

        try {
            service.actualizar(id, form.getCodigo(), form.getNombre(), form.getCarreraId());
            redirect.addFlashAttribute("flashMensaje", "Materia actualizada correctamente.");
            return "redirect:/materias";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            model.addAttribute("materia", service.buscarPorId(id));
            model.addAttribute("carreras", service.carrerasActivasParaSelector());
            model.addAttribute("modo", "editar");
            return "academico/materia-form";
        }
    }

    @PostMapping("/{id}/baja")
    public String darDeBaja(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.darDeBaja(id);
            redirect.addFlashAttribute("flashMensaje", "Materia dada de baja.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        }
        return "redirect:/materias";
    }

    @PostMapping("/{id}/alta")
    public String darDeAlta(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.darDeAlta(id);
            redirect.addFlashAttribute("flashMensaje", "Materia reactivada.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        }
        return "redirect:/materias";
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public String handleNotFound(EntityNotFoundException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", "La materia solicitada no existe.");
        return "redirect:/materias";
    }
}
