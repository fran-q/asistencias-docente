package edu.cent35.asistencias.controller;
import edu.cent35.asistencias.dto.*;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.service.MateriaService;
import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.Materia;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Pantallas de alta, edición, baja y reactivación de materias (RF-12), para los roles
 * INSTITUCION y ADMIN. Cada acción vuelve al listado con un mensaje del resultado, y los
 * errores de validación reabren el formulario con lo que el usuario ya había cargado.
 */
@Controller
@RequestMapping("/materias")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class MateriaController {

    private final MateriaService service;

    // Muestra el listado de las materias.
    @GetMapping
    public String listar(Model model) {
        List<MateriaListItemDto> items = service.listar().stream()
            .map(MateriaListItemDto::from)
            .toList();
        model.addAttribute("materias", items);
        return "academico/materia-list";
    }

    // Abre el formulario de alta vacío.
    @GetMapping("/nueva")
    public String formNueva(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new MateriaFormDto());
        }
        prepararDatosForm(model, null);
        model.addAttribute("modo", "crear");
        return "academico/materia-form";
    }

    // Procesa el alta; si la validación falla vuelve al formulario con lo ya cargado.
    @PostMapping("/nueva")
    public String crear(@Valid @ModelAttribute("form") MateriaFormDto form,
                        BindingResult binding,
                        Model model,
                        RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            prepararDatosForm(model, null);
            model.addAttribute("modo", "crear");
            return "academico/materia-form";
        }

        try {
            Materia m = service.crear(form.getCodigo(), form.getNombre(),
                form.getCarreraId(), form.getAnio(), form.getDocenteTitularId());
            redirect.addFlashAttribute("flashMensaje",
                "Materia '" + m.getCodigo() + "' creada correctamente.");
            return "redirect:/materias";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            prepararDatosForm(model, null);
            model.addAttribute("modo", "crear");
            return "academico/materia-form";
        }
    }

    // Abre el formulario de edición con los datos actuales.
    @GetMapping("/{id}/editar")
    public String formEditar(@PathVariable Long id, Model model) {
        Materia m = service.buscarPorId(id);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", MateriaFormDto.from(m));
        }
        model.addAttribute("materia", m);
        prepararDatosForm(model, m);
        model.addAttribute("modo", "editar");
        return "academico/materia-form";
    }

    // Procesa la edición; si la validación falla vuelve al formulario.
    @PostMapping("/{id}/editar")
    public String actualizar(@PathVariable Long id,
                             @Valid @ModelAttribute("form") MateriaFormDto form,
                             BindingResult binding,
                             Model model,
                             RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            Materia m = service.buscarPorId(id);
            model.addAttribute("materia", m);
            prepararDatosForm(model, m);
            model.addAttribute("modo", "editar");
            return "academico/materia-form";
        }

        try {
            service.actualizar(id, form.getCodigo(), form.getNombre(),
                form.getCarreraId(), form.getAnio(), form.getDocenteTitularId());
            redirect.addFlashAttribute("flashMensaje", "Materia actualizada correctamente.");
            return "redirect:/materias";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            Materia m = service.buscarPorId(id);
            model.addAttribute("materia", m);
            prepararDatosForm(model, m);
            model.addAttribute("modo", "editar");
            return "academico/materia-form";
        }
    }

    // Da de baja la materia y vuelve al listado con el resultado.
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

    // Reactiva la materia y vuelve al listado con el resultado.
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

    // Si el id no existe o es de otra institución, avisa y vuelve al listado.
    @ExceptionHandler(EntityNotFoundException.class)
    public String handleNotFound(EntityNotFoundException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", "La materia solicitada no existe.");
        return "redirect:/materias";
    }

    // Carga los combos del formulario, sumando el valor actual si quedó inactivo (si no, no se vería).
    private void prepararDatosForm(Model model, Materia materiaActual) {
        List<Carrera> carreras = new ArrayList<>(service.carrerasActivasParaSelector());
        if (materiaActual != null && materiaActual.getCarrera() != null
                && Boolean.FALSE.equals(materiaActual.getCarrera().getActivo())
                && carreras.stream().noneMatch(c -> c.getId().equals(materiaActual.getCarrera().getId()))) {
            carreras.add(materiaActual.getCarrera());
        }
        model.addAttribute("carreras", carreras);

        List<Docente> docentes = new ArrayList<>(service.docentesActivosParaSelector());
        if (materiaActual != null && materiaActual.getDocenteTitular() != null
                && Boolean.FALSE.equals(materiaActual.getDocenteTitular().getActivo())
                && docentes.stream().noneMatch(d -> d.getId().equals(materiaActual.getDocenteTitular().getId()))) {
            docentes.add(materiaActual.getDocenteTitular());
        }
        model.addAttribute("docentes", docentes);
    }
}
