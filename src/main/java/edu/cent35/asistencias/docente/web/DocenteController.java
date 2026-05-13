package edu.cent35.asistencias.docente.web;

import edu.cent35.asistencias.docente.application.DocenteService;
import edu.cent35.asistencias.docente.domain.Docente;
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

import java.time.LocalDate;
import java.util.List;

/**
 * CRUD de Docentes (RF-07). Roles INSTITUCION y ADMIN.
 */
@Controller
@RequestMapping("/docentes")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class DocenteController {

    private final DocenteService service;

    @GetMapping
    public String listar(Model model) {
        List<DocenteListItemDto> items = service.listar().stream()
            .map(DocenteListItemDto::from)
            .toList();
        model.addAttribute("docentes", items);
        return "docente/list";
    }

    @GetMapping("/nuevo")
    public String formNuevo(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", DocenteFormDto.builder()
                .fechaAlta(LocalDate.now())
                .build());
        }
        model.addAttribute("modo", "crear");
        return "docente/form";
    }

    @PostMapping("/nuevo")
    public String crear(@Valid @ModelAttribute("form") DocenteFormDto form,
                        BindingResult binding,
                        Model model,
                        RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            model.addAttribute("modo", "crear");
            return "docente/form";
        }
        try {
            Docente d = service.crear(form.getDni(), form.getLegajo(), form.getNombre(),
                form.getApellido(), form.getEmail(), form.getTelefono(), form.getFechaAlta());
            redirect.addFlashAttribute("flashMensaje",
                "Docente '" + d.getNombreCompleto() + "' creado correctamente.");
            return "redirect:/docentes";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            model.addAttribute("modo", "crear");
            return "docente/form";
        }
    }

    @GetMapping("/{id}/editar")
    public String formEditar(@PathVariable Long id, Model model) {
        Docente d = service.buscarPorId(id);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", DocenteFormDto.from(d));
        }
        model.addAttribute("docente", d);
        model.addAttribute("modo", "editar");
        return "docente/form";
    }

    @PostMapping("/{id}/editar")
    public String actualizar(@PathVariable Long id,
                             @Valid @ModelAttribute("form") DocenteFormDto form,
                             BindingResult binding,
                             Model model,
                             RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            model.addAttribute("docente", service.buscarPorId(id));
            model.addAttribute("modo", "editar");
            return "docente/form";
        }
        try {
            service.actualizar(id, form.getDni(), form.getLegajo(), form.getNombre(),
                form.getApellido(), form.getEmail(), form.getTelefono(), form.getFechaAlta());
            redirect.addFlashAttribute("flashMensaje", "Docente actualizado correctamente.");
            return "redirect:/docentes";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            model.addAttribute("docente", service.buscarPorId(id));
            model.addAttribute("modo", "editar");
            return "docente/form";
        }
    }

    @PostMapping("/{id}/baja")
    public String darDeBaja(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.darDeBaja(id);
            redirect.addFlashAttribute("flashMensaje", "Docente dado de baja.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        }
        return "redirect:/docentes";
    }

    @PostMapping("/{id}/alta")
    public String darDeAlta(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.darDeAlta(id);
            redirect.addFlashAttribute("flashMensaje", "Docente reactivado.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        }
        return "redirect:/docentes";
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public String handleNotFound(EntityNotFoundException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", "El docente solicitado no existe.");
        return "redirect:/docentes";
    }
}
