package edu.cent35.asistencias.controller;
import edu.cent35.asistencias.dto.*;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.service.ComisionService;
import edu.cent35.asistencias.service.HorarioService;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.DiaSemana;
import edu.cent35.asistencias.model.Horario;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Pantallas de alta, edición, baja y reactivación de franjas horarias (RF-14), para los
 * roles INSTITUCION y ADMIN. Los choques de superposición llegan como error de negocio del
 * service y se muestran sobre el mismo formulario.
 */
@Controller
@RequestMapping("/horarios")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class HorarioController {

    private final HorarioService horarioService;
    private final ComisionService comisionService;

    // Muestra el listado de los horarios.
    @GetMapping
    public String listar(Model model) {
        List<HorarioListItemDto> items = horarioService.listar().stream()
            .map(HorarioListItemDto::from)
            .toList();
        model.addAttribute("horarios", items);
        return "academico/horario-list";
    }

    // Abre el formulario de alta vacío.
    @GetMapping("/nuevo")
    public String formNuevo(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", HorarioFormDto.builder()
                .toleranciaMin((short) 15)
                .vigenteDesde(LocalDate.now())
                .build());
        }
        prepararDatosForm(model, null);
        model.addAttribute("modo", "crear");
        return "academico/horario-form";
    }

    // Procesa el alta; si la validación falla vuelve al formulario con lo ya cargado.
    @PostMapping("/nuevo")
    public String crear(@Valid @ModelAttribute("form") HorarioFormDto form,
                        BindingResult binding,
                        Model model,
                        RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            prepararDatosForm(model, null);
            model.addAttribute("modo", "crear");
            return "academico/horario-form";
        }

        try {
            Horario h = horarioService.crear(
                form.getComisionId(), form.getDia(),
                form.getHoraInicio(), form.getHoraFin(),
                form.getToleranciaMin(),
                form.getVigenteDesde(), form.getVigenteHasta()
            );
            redirect.addFlashAttribute("flashMensaje",
                "Horario del " + form.getDia().getEtiqueta() +
                " (" + form.getHoraInicio() + " - " + form.getHoraFin() + ") creado correctamente.");
            return "redirect:/horarios";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            prepararDatosForm(model, null);
            model.addAttribute("modo", "crear");
            return "academico/horario-form";
        }
    }

    // Abre el formulario de edición con los datos actuales.
    @GetMapping("/{id}/editar")
    public String formEditar(@PathVariable Long id, Model model) {
        Horario h = horarioService.buscarPorId(id);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", HorarioFormDto.from(h));
        }
        model.addAttribute("horario", h);
        prepararDatosForm(model, h.getComision());
        model.addAttribute("modo", "editar");
        return "academico/horario-form";
    }

    // Procesa la edición; si la validación falla vuelve al formulario.
    @PostMapping("/{id}/editar")
    public String actualizar(@PathVariable Long id,
                             @Valid @ModelAttribute("form") HorarioFormDto form,
                             BindingResult binding,
                             Model model,
                             RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            Horario h = horarioService.buscarPorId(id);
            model.addAttribute("horario", h);
            prepararDatosForm(model, h.getComision());
            model.addAttribute("modo", "editar");
            return "academico/horario-form";
        }

        try {
            horarioService.actualizar(
                id, form.getComisionId(), form.getDia(),
                form.getHoraInicio(), form.getHoraFin(),
                form.getToleranciaMin(),
                form.getVigenteDesde(), form.getVigenteHasta()
            );
            redirect.addFlashAttribute("flashMensaje", "Horario actualizado correctamente.");
            return "redirect:/horarios";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            Horario h = horarioService.buscarPorId(id);
            model.addAttribute("horario", h);
            prepararDatosForm(model, h.getComision());
            model.addAttribute("modo", "editar");
            return "academico/horario-form";
        }
    }

    // Da de baja el horario y vuelve al listado con el resultado.
    @PostMapping("/{id}/baja")
    public String darDeBaja(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            horarioService.darDeBaja(id);
            redirect.addFlashAttribute("flashMensaje", "Horario dado de baja.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        }
        return "redirect:/horarios";
    }

    // Reactiva el horario y vuelve al listado con el resultado.
    @PostMapping("/{id}/alta")
    public String darDeAlta(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            horarioService.darDeAlta(id);
            redirect.addFlashAttribute("flashMensaje", "Horario reactivado.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        }
        return "redirect:/horarios";
    }

    // Si el id no existe o es de otra institución, avisa y vuelve al listado.
    @ExceptionHandler(EntityNotFoundException.class)
    public String handleNotFound(EntityNotFoundException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", "El horario solicitado no existe.");
        return "redirect:/horarios";
    }

    // Carga las comisiones disponibles + los días de semana en el modelo.
    private void prepararDatosForm(Model model, Comision comisionActual) {
        List<Comision> opciones = new ArrayList<>(comisionService.comisionesActivasParaSelector());
        if (comisionActual != null && Boolean.FALSE.equals(comisionActual.getActivo())
                && opciones.stream().noneMatch(c -> c.getId().equals(comisionActual.getId()))) {
            opciones.add(comisionActual);
        }
        model.addAttribute("comisiones", opciones);
        model.addAttribute("dias", DiaSemana.values());
    }
}
