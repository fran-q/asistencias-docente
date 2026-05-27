package edu.cent35.asistencias.controller;
import edu.cent35.asistencias.dto.*;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.service.DocenteService;
import edu.cent35.asistencias.service.ConsentimientoBiometricoService;
import edu.cent35.asistencias.service.ModeloFacialService;
import edu.cent35.asistencias.model.EstadoConsentimiento;
import edu.cent35.asistencias.model.Docente;
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
    private final ConsentimientoBiometricoService consentimientoService;
    private final ModeloFacialService modeloFacialService;

    @GetMapping
    public String listar(Model model) {
        var estados = consentimientoService.estadosPorDocenteEnTenant();
        List<DocenteListItemDto> items = service.listar().stream()
            .map(d -> DocenteListItemDto.from(
                d,
                estados.getOrDefault(d.getId(), EstadoConsentimiento.NUNCA_OTORGADO)))
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
        agregarDatosEdicion(id, model);
        return "docente/form";
    }

    @PostMapping("/{id}/editar")
    public String actualizar(@PathVariable Long id,
                             @Valid @ModelAttribute("form") DocenteFormDto form,
                             BindingResult binding,
                             Model model,
                             RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            agregarDatosEdicion(id, model);
            return "docente/form";
        }
        try {
            service.actualizar(id, form.getDni(), form.getLegajo(), form.getNombre(),
                form.getApellido(), form.getEmail(), form.getTelefono(), form.getFechaAlta());
            redirect.addFlashAttribute("flashMensaje", "Docente actualizado correctamente.");
            return "redirect:/docentes";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            agregarDatosEdicion(id, model);
            return "docente/form";
        }
    }

    /**
     * Pobla los atributos de modelo necesarios para renderizar
     * {@code docente/form} en modo editar: datos del docente, estado del
     * consentimiento biométrico (Sprint 3) y del modelo facial (Sprint 4).
     */
    private void agregarDatosEdicion(Long id, Model model) {
        model.addAttribute("docente", service.buscarPorId(id));
        model.addAttribute("modo", "editar");
        // Consentimiento biometrico (Sprint 3 Fase D)
        model.addAttribute("estadoConsentimiento", consentimientoService.estadoActual(id));
        consentimientoService.vigenteDe(id)
            .ifPresent(c -> model.addAttribute("consentimientoVigente", c));
        // Modelo facial (Sprint 4 Fase C)
        model.addAttribute("tieneModeloFacial", modeloFacialService.tieneModeloActivo(id));
        modeloFacialService.modeloActivoDe(id)
            .ifPresent(m -> model.addAttribute("modeloFacial", m));
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
