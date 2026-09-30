package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.dto.RecuperacionPorClaveFormDto;
import edu.cent35.asistencias.service.ClaveRecuperacionService;
import edu.cent35.asistencias.service.InstalacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Restablecer la contraseña de la cuenta institucional con la clave de recuperación, que es
 * como se sale del paso en una instalación autónoma: sin SMTP, el camino por correo no existe
 * (TD-009, ADR-0022).
 *
 * <p><b>Solo en una instalación autónoma.</b> Fuera de ahí devuelve 404: en un despliegue con
 * correo la recuperación va por código, y tener dos caminos abiertos sería agrandar la
 * superficie del sistema sin que nadie lo necesite.
 */
@Controller
@RequestMapping("/recuperar/clave")
@RequiredArgsConstructor
@Slf4j
public class RecuperacionClaveController {

    private static final String VIEW = "auth/recuperar-clave";

    private final InstalacionService instalacion;
    private final ClaveRecuperacionService claveService;

    // El formulario vacío.
    @GetMapping
    public String formulario(Model model) {
        exigirInstalacionAutonoma();
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new RecuperacionPorClaveFormDto());
        }
        return VIEW;
    }

    // Comprueba la clave y asienta la contraseña nueva.
    @PostMapping
    public String restablecer(@Valid @ModelAttribute("form") RecuperacionPorClaveFormDto form,
                              BindingResult binding,
                              RedirectAttributes flash) {
        exigirInstalacionAutonoma();

        if (!form.coincide()) {
            binding.rejectValue("confirmacion", "error.confirmacion",
                                "Las contraseñas no coinciden");
        }
        if (binding.hasErrors()) {
            return VIEW;
        }

        ClaveRecuperacionService.Resultado resultado =
            claveService.restablecer(form.getUsername(), form.getClave(), form.getNuevaPassword());

        switch (resultado) {
            case OK -> {
                flash.addFlashAttribute("flashMensaje",
                    "Tu contraseña se cambió. Ya podés iniciar sesión.");
                return "redirect:/login";
            }
            // Cuenta inexistente y clave equivocada dan el mismo mensaje: separarlos convertiría
            // esta pantalla en una forma de averiguar qué usuarios existen.
            case SIN_CUENTA, CLAVE_INCORRECTA -> binding.reject("error.global",
                "El usuario o la clave de recuperación no son correctos.");
            case SIN_CLAVE -> binding.reject("error.global",
                "Esta institución no tiene clave de recuperación. La genera la configuración "
                + "inicial de la instalación.");
        }
        return VIEW;
    }

    private void exigirInstalacionAutonoma() {
        if (!instalacion.autonoma()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
