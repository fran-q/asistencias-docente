package edu.cent35.asistencias.controller;
import edu.cent35.asistencias.dto.*;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.service.MiInstitucionService;
import edu.cent35.asistencias.model.Institucion;
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
 * Vista y edición de los datos de la propia institución, restringida al rol INSTITUCION.
 * Nunca recibe un id por parámetro: la institución sale del TenantContext, así que no hay
 * forma de pedir la de otro.
 *
 * <p>Primero se lee y después se edita, como la ficha del docente: la pantalla abría directo
 * en el formulario, y quien entraba a consultar un dato quedaba parado sobre campos que
 * cambian la institución entera.
 */
@Controller
@RequestMapping("/mi-institucion")
@PreAuthorize("hasRole('INSTITUCION')")
@RequiredArgsConstructor
@Slf4j
public class MiInstitucionController {

    private static final String VIEW = "institucion/mi-institucion";
    private static final String VIEW_EDITAR = "institucion/mi-institucion-editar";
    private static final String FORM_ATTR = "form";
    private static final String ENTIDAD_ATTR = "institucion";

    private final MiInstitucionService service;

    // Muestra los datos de la institución del usuario logueado, de solo lectura.
    @GetMapping
    public String view(Model model) {
        model.addAttribute(ENTIDAD_ATTR, service.getMiInstitucion());
        return VIEW;
    }

    // El formulario, precargado con los datos vigentes.
    @GetMapping("/editar")
    public String editar(Model model) {
        Institucion inst = service.getMiInstitucion();
        model.addAttribute(ENTIDAD_ATTR, inst);
        if (!model.containsAttribute(FORM_ATTR)) {
            model.addAttribute(FORM_ATTR, InstitucionFormDto.from(inst));
        }
        return VIEW_EDITAR;
    }

    // Guarda los cambios; si el nombre o el CUIT ya son de otra institución, lo informa.
    @PostMapping("/editar")
    public String update(@Valid InstitucionFormDto form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            model.addAttribute(ENTIDAD_ATTR, service.getMiInstitucion());
            model.addAttribute(FORM_ATTR, form);
            return VIEW_EDITAR;
        }

        try {
            service.actualizar(form);
        } catch (DataIntegrityViolationException ex) {
            // Se atrapa aca en vez de dejarlo subir al manejador global para no perder lo que
            // la persona ya habia tipeado: el formulario se vuelve a dibujar con sus valores.
            log.warn("Conflicto al actualizar mi institucion: {}", ex.getMostSpecificCause().getMessage());
            binding.reject("error.global", ManejadorDeColisiones.traducir(ex));
            model.addAttribute(ENTIDAD_ATTR, service.getMiInstitucion());
            model.addAttribute(FORM_ATTR, form);
            return VIEW_EDITAR;
        }

        // Vuelve a la ficha: ahí se ve lo que quedó guardado.
        redirect.addFlashAttribute("flashMensaje", "Datos de la institución actualizados correctamente.");
        return "redirect:/mi-institucion";
    }
}
