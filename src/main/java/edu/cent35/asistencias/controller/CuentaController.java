package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
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
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.Duration;
import java.time.LocalDateTime;
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
    public String ver(@AuthenticationPrincipal UsuarioAutenticado principal, Model model) {
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
    public String enviarCodigo(@AuthenticationPrincipal UsuarioAutenticado principal,
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
    public String verificar(@AuthenticationPrincipal UsuarioAutenticado principal,
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
    private void prepararModelo(UsuarioAutenticado principal, Model model) {
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

    // ========================================================================
    //  Cambio de la propia contrasena, en dos pasos
    //
    //  Paso 1  POST /password/codigo     pide el codigo y lo manda al correo
    //  Paso 2  POST /password/verificar  comprueba el codigo y abre la ventana
    //  Paso 3  POST /password            fija la contrasena nueva, dos veces
    //
    //  Por que en dos pasos y no todo junto. Se puede pedir el codigo y la
    //  contrasena en un mismo formulario, pero entonces un codigo mal tipeado
    //  descarta tambien la contrasena recien escrita y hay que rehacer todo.
    //  Separarlos hace que cada error cueste solo su propio paso.
    // ========================================================================

    // Cuanto dura la autorizacion una vez validado el codigo. Corta a proposito: es el
    // tiempo de escribir una contrasena, no el de dejar la pantalla abierta toda la tarde.
    private static final Duration VENTANA_CAMBIO = Duration.ofMinutes(10);

    // Marca en la sesion de que cuenta y hasta cuando quedo autorizado el cambio. Se guarda
    // el id ademas del vencimiento porque una sesion puede cambiar de usuario al re-loguear,
    // y una autorizacion emitida para otra cuenta no puede seguir valiendo.
    private static final String AUTORIZACION = "cambioPasswordAutorizadoPara";
    private static final String AUTORIZACION_VENCE = "cambioPasswordVence";

    // Paso 1: manda el codigo al correo de la cuenta.
    @PostMapping("/password/codigo")
    public String pedirCodigoDeCambio(@AuthenticationPrincipal UsuarioAutenticado principal,
                                      HttpServletRequest request,
                                      HttpSession sesion,
                                      RedirectAttributes redirect) {
        sesion.removeAttribute(AUTORIZACION);
        sesion.removeAttribute(AUTORIZACION_VENCE);
        try {
            verificacionService.enviarCodigoParaCambiarPassword(
                principal.getUsuarioId(), ipDe(request));
            redirect.addFlashAttribute("flashMensaje",
                "Te enviamos un código a tu correo. Vence en unos minutos.");
            redirect.addFlashAttribute("pasoPassword", "codigo");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("No se pudo enviar el codigo de cambio de contrasena: {}", ex.toString());
            redirect.addFlashAttribute("flashError",
                "No pudimos enviar el correo en este momento. Probá de nuevo en un rato.");
        }
        return "redirect:/mi-cuenta";
    }

    // Paso 2: comprueba el codigo. Recien si es correcto se habilita el formulario.
    @PostMapping("/password/verificar")
    public String verificarCodigoDeCambio(@AuthenticationPrincipal UsuarioAutenticado principal,
                                          @RequestParam(name = "codigo", required = false) String codigo,
                                          HttpSession sesion,
                                          RedirectAttributes redirect) {

        CodigoVerificacionService.Resultado resultado =
            verificacionService.validarCodigoParaCambiarPassword(principal.getUsuarioId(), codigo);

        if (resultado != CodigoVerificacionService.Resultado.OK) {
            redirect.addFlashAttribute("errorCodigoPassword", mensajeDe(resultado));
            redirect.addFlashAttribute("pasoPassword", "codigo");
            return "redirect:/mi-cuenta";
        }

        sesion.setAttribute(AUTORIZACION, principal.getUsuarioId());
        sesion.setAttribute(AUTORIZACION_VENCE, LocalDateTime.now().plus(VENTANA_CAMBIO));
        redirect.addFlashAttribute("pasoPassword", "nueva");
        return "redirect:/mi-cuenta";
    }

    // Paso 3: fija la contrasena nueva. Sin la autorizacion del paso 2 no hace nada.
    @PostMapping("/password")
    public String cambiarPassword(@AuthenticationPrincipal UsuarioAutenticado principal,
                                  @Valid @ModelAttribute("formPassword") CambioPasswordDto form,
                                  BindingResult binding,
                                  HttpSession sesion,
                                  RedirectAttributes redirect) {

        if (!estaAutorizado(principal, sesion)) {
            // Se comprueba en el servidor y no confiando en que la pantalla haya mostrado el
            // paso correcto: el formulario del paso 3 se puede enviar directamente.
            sesion.removeAttribute(AUTORIZACION);
            sesion.removeAttribute(AUTORIZACION_VENCE);
            redirect.addFlashAttribute("flashError",
                "La autorización venció o no se validó ningún código. Empezá de nuevo.");
            return "redirect:/mi-cuenta";
        }

        if (!form.coincide()) {
            binding.rejectValue("confirmacion", "error.match", "Las contraseñas no coinciden");
        }
        if (binding.hasErrors()) {
            redirect.addFlashAttribute(
                "org.springframework.validation.BindingResult.formPassword", binding);
            redirect.addFlashAttribute("formPassword", form);
            redirect.addFlashAttribute("pasoPassword", "nueva");
            return "redirect:/mi-cuenta";
        }

        try {
            usuarioService.fijarPasswordPropia(principal.getUsuarioId(), form.getNuevaPassword());
        } catch (IllegalArgumentException ex) {
            binding.rejectValue("nuevaPassword", "error.igual", ex.getMessage());
            redirect.addFlashAttribute(
                "org.springframework.validation.BindingResult.formPassword", binding);
            redirect.addFlashAttribute("formPassword", form);
            redirect.addFlashAttribute("pasoPassword", "nueva");
            return "redirect:/mi-cuenta";
        }

        // La autorizacion se consume: sirvio para un cambio y para uno solo.
        sesion.removeAttribute(AUTORIZACION);
        sesion.removeAttribute(AUTORIZACION_VENCE);
        redirect.addFlashAttribute("flashMensaje", "Contraseña cambiada correctamente.");
        return "redirect:/mi-cuenta";
    }

    // Si esta sesion valido un codigo para ESTA cuenta y la ventana sigue abierta.
    private boolean estaAutorizado(UsuarioAutenticado principal, HttpSession sesion) {
        Object para = sesion.getAttribute(AUTORIZACION);
        Object vence = sesion.getAttribute(AUTORIZACION_VENCE);
        return para instanceof Long id
            && id.equals(principal.getUsuarioId())
            && vence instanceof LocalDateTime cuando
            && LocalDateTime.now().isBefore(cuando);
    }
}
