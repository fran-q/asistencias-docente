package edu.cent35.asistencias.academico.web;

import edu.cent35.asistencias.academico.application.ComisionService;
import edu.cent35.asistencias.academico.domain.Comision;
import edu.cent35.asistencias.academico.domain.Materia;
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

import java.util.ArrayList;
import java.util.List;

/**
 * CRUD de Comisiones (RF-13). Roles INSTITUCION y ADMIN.
 */
@Controller
@RequestMapping("/comisiones")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class ComisionController {

    private final ComisionService service;

    @GetMapping
    public String listar(Model model) {
        List<ComisionListItemDto> items = service.listar().stream()
            .map(ComisionListItemDto::from)
            .toList();
        model.addAttribute("comisiones", items);
        return "academico/comision-list";
    }

    @GetMapping("/nueva")
    public String formNueva(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new ComisionFormDto());
        }
        prepararDatosForm(model, null);
        model.addAttribute("modo", "crear");
        return "academico/comision-form";
    }

    @PostMapping("/nueva")
    public String crear(@Valid @ModelAttribute("form") ComisionFormDto form,
                        BindingResult binding,
                        Model model,
                        RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            prepararDatosForm(model, null);
            model.addAttribute("modo", "crear");
            return "academico/comision-form";
        }

        try {
            Comision c = service.crear(form.getCodigo(), form.getMateriaId(),
                form.getCupo(), form.getDocenteAsignadoId());
            redirect.addFlashAttribute("flashMensaje",
                "Comisión '" + c.getCodigo() + "' creada correctamente.");
            return "redirect:/comisiones";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            prepararDatosForm(model, null);
            model.addAttribute("modo", "crear");
            return "academico/comision-form";
        }
    }

    @GetMapping("/{id}/editar")
    public String formEditar(@PathVariable Long id, Model model) {
        Comision c = service.buscarPorId(id);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", ComisionFormDto.from(c));
        }
        model.addAttribute("comision", c);
        prepararDatosForm(model, c);
        model.addAttribute("modo", "editar");
        return "academico/comision-form";
    }

    @PostMapping("/{id}/editar")
    public String actualizar(@PathVariable Long id,
                             @Valid @ModelAttribute("form") ComisionFormDto form,
                             BindingResult binding,
                             Model model,
                             RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            Comision c = service.buscarPorId(id);
            model.addAttribute("comision", c);
            prepararDatosForm(model, c);
            model.addAttribute("modo", "editar");
            return "academico/comision-form";
        }

        try {
            service.actualizar(id, form.getCodigo(), form.getMateriaId(),
                form.getCupo(), form.getDocenteAsignadoId());
            redirect.addFlashAttribute("flashMensaje", "Comisión actualizada correctamente.");
            return "redirect:/comisiones";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            Comision c = service.buscarPorId(id);
            model.addAttribute("comision", c);
            prepararDatosForm(model, c);
            model.addAttribute("modo", "editar");
            return "academico/comision-form";
        }
    }

    @PostMapping("/{id}/baja")
    public String darDeBaja(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.darDeBaja(id);
            redirect.addFlashAttribute("flashMensaje", "Comisión dada de baja.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        }
        return "redirect:/comisiones";
    }

    @PostMapping("/{id}/alta")
    public String darDeAlta(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.darDeAlta(id);
            redirect.addFlashAttribute("flashMensaje", "Comisión reactivada.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        }
        return "redirect:/comisiones";
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public String handleNotFound(EntityNotFoundException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", "La comisión solicitada no existe.");
        return "redirect:/comisiones";
    }

    /**
     * Carga al modelo las materias y docentes disponibles para el form.
     * Si se está editando una comisión cuya materia/docente quedaron inactivos,
     * los sumamos a sus listas para que el select los muestre.
     */
    private void prepararDatosForm(Model model, Comision comisionActual) {
        List<Materia> materias = new ArrayList<>(service.materiasActivasParaSelector());
        if (comisionActual != null && comisionActual.getMateria() != null
                && Boolean.FALSE.equals(comisionActual.getMateria().getActivo())
                && materias.stream().noneMatch(m -> m.getId().equals(comisionActual.getMateria().getId()))) {
            materias.add(comisionActual.getMateria());
        }
        model.addAttribute("materias", materias);

        List<Docente> docentes = new ArrayList<>(service.docentesActivosParaSelector());
        if (comisionActual != null && comisionActual.getDocenteAsignado() != null
                && Boolean.FALSE.equals(comisionActual.getDocenteAsignado().getActivo())
                && docentes.stream().noneMatch(d -> d.getId().equals(comisionActual.getDocenteAsignado().getId()))) {
            docentes.add(comisionActual.getDocenteAsignado());
        }
        model.addAttribute("docentes", docentes);
    }
}
