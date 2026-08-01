package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.dto.AltaInstitucionFormDto;
import edu.cent35.asistencias.service.AltaInstitucionService;
import edu.cent35.asistencias.service.AltaPendiente;
import jakarta.servlet.http.HttpSession;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Alta de una institución nueva y su primera cuenta, en dos pasos: el formulario manda un
 * código al correo declarado y la institución se crea recién al validarlo. Es pública porque
 * corre antes de que exista el tenant, así que no hay sesión ni rol contra el cual autorizar.
 */
@Controller
@RequestMapping("/alta-institucion")
@RequiredArgsConstructor
@Slf4j
public class AltaInstitucionController {

    // Los datos en espera viven en la sesion: no hay institucion ni usuario a los cuales
    // asociarlos todavia, y su vida util son los minutos que dura el codigo.
    private static final String PENDIENTE = "altaInstitucionPendiente";

    private final AltaInstitucionService altaService;

    // Muestra el formulario vacío.
    @GetMapping
    public String formulario(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new AltaInstitucionFormDto());
        }
        return "auth/alta-institucion";
    }

    // Paso 1: valida los datos y manda el código; todavía no crea nada.
    @PostMapping
    public String enviarCodigo(@Valid @ModelAttribute("form") AltaInstitucionFormDto form,
                               BindingResult binding,
                               HttpSession sesion) {

        if (!form.coincide()) {
            binding.rejectValue("confirmacion", "error.confirmacion",
                                "Las contraseñas no coinciden");
        }
        if (binding.hasErrors()) {
            return "auth/alta-institucion";
        }

        try {
            sesion.setAttribute(PENDIENTE, altaService.iniciar(form));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            binding.reject("error.global", ex.getMessage());
            return "auth/alta-institucion";
        }
        return "redirect:/alta-institucion/codigo";
    }

    // Pantalla donde se tipea el código recibido.
    @GetMapping("/codigo")
    public String pantallaCodigo(HttpSession sesion, Model model, RedirectAttributes redirect) {
        AltaPendiente pendiente = (AltaPendiente) sesion.getAttribute(PENDIENTE);
        if (pendiente == null) {
            redirect.addFlashAttribute("flashError",
                "No hay ningún alta en curso. Empezá de nuevo.");
            return "redirect:/alta-institucion";
        }
        model.addAttribute("email", pendiente.getEmail());
        return "auth/alta-institucion-codigo";
    }

    // Paso 2: valida el código y recién ahí crea la institución con su cuenta.
    @PostMapping("/codigo")
    public String confirmar(@RequestParam(name = "codigo", required = false) String codigo,
                            HttpSession sesion,
                            Model model,
                            RedirectAttributes redirect) {

        AltaPendiente pendiente = (AltaPendiente) sesion.getAttribute(PENDIENTE);
        if (pendiente == null) {
            redirect.addFlashAttribute("flashError",
                "No hay ningún alta en curso. Empezá de nuevo.");
            return "redirect:/alta-institucion";
        }

        AltaInstitucionService.Rechazo resultado = altaService.comprobarCodigo(pendiente, codigo);
        if (resultado != AltaInstitucionService.Rechazo.CORRECTO) {
            // Cuando se agotan los intentos o vence, el alta se descarta entera: seguir
            // aceptando codigos sobre unos datos que ya no valen no lleva a ningun lado.
            if (resultado != AltaInstitucionService.Rechazo.INCORRECTO) {
                sesion.removeAttribute(PENDIENTE);
                redirect.addFlashAttribute("flashError", explicar(resultado));
                return "redirect:/alta-institucion";
            }
            model.addAttribute("email", pendiente.getEmail());
            model.addAttribute("error", explicar(resultado));
            return "auth/alta-institucion-codigo";
        }

        try {
            altaService.confirmar(pendiente);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            sesion.removeAttribute(PENDIENTE);
            redirect.addFlashAttribute("flashError", ex.getMessage());
            return "redirect:/alta-institucion";
        }

        sesion.removeAttribute(PENDIENTE);
        redirect.addFlashAttribute("flashMensaje",
            "Institución creada y correo confirmado. Ya podés entrar con el usuario que definiste.");
        return "redirect:/login";
    }

    // Traduce el motivo del rechazo a algo que se pueda leer en pantalla.
    private String explicar(AltaInstitucionService.Rechazo rechazo) {
        return switch (rechazo) {
            case INCORRECTO -> "El código no es correcto. Revisalo y volvé a intentar.";
            case VENCIDO -> "El código venció. Volvé a completar el formulario para recibir otro.";
            case SIN_INTENTOS -> "Se agotaron los intentos. Volvé a completar el formulario.";
            case CORRECTO -> "";
        };
    }
}
