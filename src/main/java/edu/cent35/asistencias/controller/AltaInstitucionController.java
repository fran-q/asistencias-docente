package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.dto.AltaInstitucionFormDto;
import edu.cent35.asistencias.service.AltaInstitucionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Pantalla de alta de una institución nueva y su primera cuenta. Es pública porque corre
 * antes de que exista el tenant, así que no hay sesión ni rol contra el cual autorizar: lo
 * que la protege es la clave de instalación que pide el formulario.
 */
@Controller
@RequestMapping("/alta-institucion")
@RequiredArgsConstructor
@Slf4j
public class AltaInstitucionController {

    private final AltaInstitucionService altaService;

    // Muestra el formulario vacío.
    @GetMapping
    public String formulario(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new AltaInstitucionFormDto());
        }
        return "auth/alta-institucion";
    }

    // Da de alta la institución y su cuenta; ante cualquier error vuelve al formulario.
    @PostMapping
    public String darDeAlta(@Valid @ModelAttribute("form") AltaInstitucionFormDto form,
                            BindingResult binding,
                            RedirectAttributes redirect,
                            Model model) {

        if (!form.coincide()) {
            binding.rejectValue("confirmacion", "error.confirmacion",
                                "Las contraseñas no coinciden");
        }
        if (binding.hasErrors()) {
            return "auth/alta-institucion";
        }

        try {
            altaService.darDeAlta(form);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            binding.reject("error.global", ex.getMessage());
            return "auth/alta-institucion";
        }

        redirect.addFlashAttribute("flashMensaje",
            "Institución creada. Ya podés entrar con el usuario que definiste.");
        return "redirect:/login";
    }
}
