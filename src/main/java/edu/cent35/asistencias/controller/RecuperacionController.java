package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.dto.RecuperacionCompletarFormDto;
import edu.cent35.asistencias.dto.RecuperacionInicioFormDto;
import edu.cent35.asistencias.service.CodigoVerificacionService;
import edu.cent35.asistencias.service.VerificacionCuentaService;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * Recuperación de contraseña sin intervención del superadmin, para quien perdió el acceso y no
 * puede autenticarse. Es la única zona de la aplicación abierta sin sesión además del login, y
 * por eso responde siempre lo mismo exista o no la cuenta: si contestara distinto, alcanzaría
 * con probar direcciones para averiguar quiénes tienen cuenta.
 */
@Controller
@RequestMapping("/recuperar")
@RequiredArgsConstructor
@Slf4j
public class RecuperacionController {

    // A quien se le esta recuperando la contrasena. Vive en la sesion y no en la URL: si viajara
    // por parametro, cualquiera podria pedir el cambio de otra cuenta escribiendo otro id.
    private static final String SESION_USUARIO = "RECUPERACION_USUARIO_ID";

    private final VerificacionCuentaService verificacionService;

    // Paso 1: pide el usuario o el correo.
    @GetMapping
    public String inicio(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new RecuperacionInicioFormDto());
        }
        return "auth/recuperar-inicio";
    }

    // Emite el codigo si la cuenta existe, pero avanza igual al paso 2 en cualquier caso.
    @PostMapping
    public String pedirCodigo(@Valid @ModelAttribute("form") RecuperacionInicioFormDto form,
                              BindingResult binding,
                              HttpServletRequest request,
                              HttpSession session) {
        if (binding.hasErrors()) {
            return "auth/recuperar-inicio";
        }

        Optional<Long> usuarioId = verificacionService.iniciarRecuperacion(
            form.getUsuarioOEmail(), CuentaController.ipDe(request));

        // Se guarda solo si existe; si no, el paso 2 se muestra igual y cualquier codigo falla.
        usuarioId.ifPresent(id -> session.setAttribute(SESION_USUARIO, id));
        return "redirect:/recuperar/codigo";
    }

    // Paso 2: pide el codigo y la contrasena nueva.
    @GetMapping("/codigo")
    public String formCodigo(HttpSession session, Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new RecuperacionCompletarFormDto());
        }
        model.addAttribute("emailEnmascarado", emailEnmascarado(session));
        return "auth/recuperar-codigo";
    }

    // Cierra el flujo: valida el codigo y fija la contrasena nueva.
    @PostMapping("/codigo")
    public String completar(@Valid @ModelAttribute("form") RecuperacionCompletarFormDto form,
                            BindingResult binding,
                            HttpSession session,
                            Model model,
                            RedirectAttributes redirect) {

        if (!form.coincide()) {
            binding.rejectValue("confirmacion", "error.confirmacion", "Las contraseñas no coinciden");
        }
        if (binding.hasErrors()) {
            model.addAttribute("emailEnmascarado", emailEnmascarado(session));
            return "auth/recuperar-codigo";
        }

        Object id = session.getAttribute(SESION_USUARIO);
        if (id == null) {
            // Nunca hubo una cuenta detras de este flujo, o la sesion caduco.
            binding.reject("error.global", "El código no es correcto o el pedido venció. Empezá de nuevo.");
            model.addAttribute("emailEnmascarado", "");
            return "auth/recuperar-codigo";
        }

        CodigoVerificacionService.Resultado resultado = verificacionService.completarRecuperacion(
            (Long) id, form.getCodigo(), form.getNuevaPassword());

        if (resultado == CodigoVerificacionService.Resultado.OK) {
            // La sesion del flujo se cierra si o si: el codigo ya se consumio.
            session.removeAttribute(SESION_USUARIO);
            redirect.addFlashAttribute("flashMensaje",
                "Tu contraseña se cambió. Ya podés iniciar sesión.");
            return "redirect:/login";
        }

        binding.reject("error.global", CuentaController.mensajeDe(resultado));
        model.addAttribute("emailEnmascarado", emailEnmascarado(session));
        return "auth/recuperar-codigo";
    }

    // Muestra a donde se envio el codigo sin revelar la direccion entera.
    private String emailEnmascarado(HttpSession session) {
        Object id = session.getAttribute(SESION_USUARIO);
        return id == null ? "" : verificacionService.emailEnmascaradoDeRecuperacion((Long) id);
    }
}
