package edu.cent35.asistencias.institucion.web;

import edu.cent35.asistencias.institucion.application.MiInstitucionService;
import edu.cent35.asistencias.institucion.domain.Institucion;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Vista y edicion de "mi institucion" (la del SUPERADMIN_INSTITUCION
 * logueado).
 * <p>
 * Restringido a rol SUPERADMIN_INSTITUCION. El ADMIN comun no accede
 * a esta pantalla.
 */
@Controller
@RequestMapping("/mi-institucion")
@PreAuthorize("hasRole('SUPERADMIN_INSTITUCION')")
@RequiredArgsConstructor
@Slf4j
public class MiInstitucionController {

    private static final String VIEW = "institucion/mi-institucion";
    private static final String FORM_ATTR = "form";
    private static final String ENTIDAD_ATTR = "institucion";

    private final MiInstitucionService service;

    @GetMapping
    public String view(Model model) {
        Institucion inst = service.getMiInstitucion();
        model.addAttribute(ENTIDAD_ATTR, inst);
        if (!model.containsAttribute(FORM_ATTR)) {
            model.addAttribute(FORM_ATTR, InstitucionFormDto.from(inst));
        }
        return VIEW;
    }

    @PostMapping
    public String update(@Valid InstitucionFormDto form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            model.addAttribute(ENTIDAD_ATTR, service.getMiInstitucion());
            model.addAttribute(FORM_ATTR, form);
            return VIEW;
        }

        try {
            service.actualizar(form);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Conflicto al actualizar mi institucion: {}", ex.getMostSpecificCause().getMessage());
            binding.reject("error.global",
                "El nombre o CUIT ya esta en uso por otra institucion. Cambialo y volve a guardar.");
            model.addAttribute(ENTIDAD_ATTR, service.getMiInstitucion());
            model.addAttribute(FORM_ATTR, form);
            return VIEW;
        }

        redirect.addFlashAttribute("flashMensaje", "Datos de la institucion actualizados correctamente.");
        return "redirect:/mi-institucion";
    }
}
