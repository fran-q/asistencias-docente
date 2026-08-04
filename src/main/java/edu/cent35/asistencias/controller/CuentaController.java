package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.config.CustomUserDetails;
import edu.cent35.asistencias.dto.CodigoFormDto;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.service.CodigoVerificacionService;
import edu.cent35.asistencias.service.VerificacionCuentaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import edu.cent35.asistencias.dto.CambioPasswordDto;
import edu.cent35.asistencias.service.UsuarioService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Pantalla de la propia cuenta, donde cada persona confirma que controla el correo que tiene
 * declarado. Está abierta a cualquier rol autenticado porque siempre opera sobre la cuenta
 * logueada: el id sale del principal y nunca de un parámetro, así que nadie puede tocar otra.
 */
@Controller
@RequestMapping("/mi-cuenta")
@RequiredArgsConstructor
@Slf4j
public class CuentaController {

    private final VerificacionCuentaService verificacionService;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;

    // Muestra el correo de la cuenta y si ya fue confirmado.
    @GetMapping
    public String ver(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        prepararModelo(principal, model);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new CodigoFormDto());
        }
        if (!model.containsAttribute("formPassword")) {
            model.addAttribute("formPassword", new CambioPasswordDto());
        }
        return "cuenta/mi-cuenta";
    }

    // Emite un codigo nuevo y lo manda al correo de la cuenta.
    @PostMapping("/enviar-codigo")
    public String enviarCodigo(@AuthenticationPrincipal CustomUserDetails principal,
                               HttpServletRequest request,
                               RedirectAttributes redirect) {
        try {
            verificacionService.enviarCodigoDeVerificacion(principal.getUsuarioId(), ipDe(request));
            redirect.addFlashAttribute("flashMensaje",
                "Te enviamos un código a tu correo. Vence en unos minutos.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        } catch (RuntimeException ex) {
            // Tipicamente el SMTP no responde. Se avisa en vez de decir "revisa tu correo".
            log.warn("No se pudo enviar el codigo de verificacion: {}", ex.toString());
            redirect.addFlashAttribute("flashError",
                "No pudimos enviar el correo en este momento. Probá de nuevo en un rato.");
        }
        return "redirect:/mi-cuenta";
    }

    // Confirma el correo si el codigo ingresado es el correcto.
    @PostMapping("/verificar")
    public String verificar(@AuthenticationPrincipal CustomUserDetails principal,
                            @Valid @ModelAttribute("form") CodigoFormDto form,
                            BindingResult binding,
                            Model model,
                            RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            prepararModelo(principal, model);
            return "cuenta/mi-cuenta";
        }

        CodigoVerificacionService.Resultado resultado =
            verificacionService.confirmarEmail(principal.getUsuarioId(), form.getCodigo());

        if (resultado == CodigoVerificacionService.Resultado.OK) {
            redirect.addFlashAttribute("flashMensaje", "Listo, tu correo quedó verificado.");
            return "redirect:/mi-cuenta";
        }

        binding.reject("error.global", mensajeDe(resultado));
        prepararModelo(principal, model);
        return "cuenta/mi-cuenta";
    }

    // Carga los datos de la cuenta logueada que la pantalla necesita mostrar.
    private void prepararModelo(CustomUserDetails principal, Model model) {
        Usuario usuario = usuarioRepository.findById(principal.getUsuarioId()).orElseThrow();
        model.addAttribute("usuario", usuario);
        model.addAttribute("verificado", usuario.getEmailVerificadoEn() != null);
    }

    // Traduce el resultado a algo que le sirva a la persona.
    static String mensajeDe(CodigoVerificacionService.Resultado resultado) {
        return switch (resultado) {
            case INCORRECTO -> "El código no es correcto. Revisá el correo y volvé a intentar.";
            case VENCIDO -> "El código venció. Pedí uno nuevo.";
            case SIN_INTENTOS -> "Se agotaron los intentos para este código. Pedí uno nuevo.";
            case INEXISTENTE -> "No hay ningún código pendiente. Pedí uno nuevo.";
            case OK -> "";
        };
    }

    // IP real del cliente, mirando X-Forwarded-For por si hay un proxy reverso delante.
    static String ipDe(HttpServletRequest request) {
        String reenviada = request.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // Cambio de la propia contrasena. Vive en Mi cuenta y no en el listado de usuarios
    // porque es la unica pantalla a la que todos los roles llegan sobre si mismos.
    @PostMapping("/password")
    public String cambiarPassword(@AuthenticationPrincipal CustomUserDetails principal,
                                  @Valid @ModelAttribute("formPassword") CambioPasswordDto form,
                                  BindingResult binding,
                                  RedirectAttributes redirect) {

        if (!form.coincide()) {
            binding.rejectValue("confirmacion", "error.match", "Las contraseñas no coinciden");
        }
        if (form.esLaMisma()) {
            binding.rejectValue("nuevaPassword", "error.igual",
                "La contraseña nueva tiene que ser distinta de la actual");
        }
        if (binding.hasErrors()) {
            redirect.addFlashAttribute(
                "org.springframework.validation.BindingResult.formPassword", binding);
            redirect.addFlashAttribute("formPassword", form);
            return "redirect:/mi-cuenta";
        }

        try {
            usuarioService.cambiarPasswordPropia(
                principal.getUsuarioId(), form.getActual(), form.getNuevaPassword());
            redirect.addFlashAttribute("flashMensaje", "Contraseña cambiada correctamente.");
        } catch (IllegalArgumentException ex) {
            binding.rejectValue("actual", "error.actual", ex.getMessage());
            redirect.addFlashAttribute(
                "org.springframework.validation.BindingResult.formPassword", binding);
            redirect.addFlashAttribute("formPassword", form);
        }
        return "redirect:/mi-cuenta";
    }
}
