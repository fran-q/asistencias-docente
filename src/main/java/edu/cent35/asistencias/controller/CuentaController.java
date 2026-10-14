package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.dto.institucion.CambioPasswordFormDto;
import edu.cent35.asistencias.dto.institucion.CodigoFormDto;
import edu.cent35.asistencias.model.institucion.Usuario;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.service.CodigoVerificacionService;
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
import edu.cent35.asistencias.service.UsuarioService;
import edu.cent35.asistencias.service.VerificacionCuentaService;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Pantalla de la propia cuenta: sus datos, la confirmación del correo y el cambio de contraseña.
 * Está abierta a cualquier rol autenticado porque siempre opera sobre la cuenta logueada: el id
 * sale del principal y nunca de un parámetro, así que nadie puede tocar otra.
 *
 * <p>La edición de esos datos vive aparte, en {@link DatosCuentaController}.
 */
@Controller
@RequestMapping("/mi-cuenta")
@RequiredArgsConstructor
@Slf4j
public class CuentaController {

    private final VerificacionCuentaService verificacionService;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;

    // El reloj de la ventana que abre el código para cambiar la contraseña.
    private Clock clock = Clock.systemDefaultZone();

    // Muestra los datos de la cuenta y si el correo ya fue confirmado.
    @GetMapping
    public String ver(@AuthenticationPrincipal UsuarioAutenticado principal, HttpSession sesion,
                      Model model) {
        prepararModelo(principal, model);
        // Un cambio de correo a mitad de camino: la pantalla ofrece retomarlo.
        model.addAttribute("cambioDeCorreo", DatosCuentaController.pendienteDe(principal, sesion));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new CodigoFormDto());
        }
        if (!model.containsAttribute("formPassword")) {
            model.addAttribute("formPassword", new CambioPasswordFormDto());
        }
        return "cuenta/mi-cuenta";
    }

    // Emite un código nuevo y lo manda al correo de la cuenta.
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

    // Confirma el correo si el código ingresado es el correcto.
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
    //  Cambio de la propia contraseña, en dos pasos
    //
    //  Paso 1  POST /password/codigo     pide el código y lo manda al correo
    //  Paso 2  POST /password/verificar  comprueba el código y abre la ventana
    //  Paso 3  POST /password            fija la contraseña nueva, dos veces
    //
    //  Por qué en dos pasos y no todo junto. Se puede pedir el código y la
    //  contraseña en un mismo formulario, pero entonces un código mal tipeado
    //  descarta también la contraseña recién escrita y hay que rehacer todo.
    //  Separarlos hace que cada error cueste solo su propio paso.
    // ========================================================================

    // Cuánto dura la autorización una vez validado el código. Corta a propósito: es el
    // tiempo de escribir una contraseña, no el de dejar la pantalla abierta toda la tarde.
    private static final Duration VENTANA_CAMBIO = Duration.ofMinutes(10);

    // Marca en la sesión de que cuenta y hasta cuando quedó autorizado el cambio. Se guarda
    // el id además del vencimiento porque una sesión puede cambiar de usuario al re-loguear,
    // y una autorización emitida para otra cuenta no puede seguir valiendo.
    private static final String AUTORIZACION = "cambioPasswordAutorizadoPara";
    private static final String AUTORIZACION_VENCE = "cambioPasswordVence";

    // Paso 1: manda el código al correo de la cuenta.
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

    // Paso 2: comprueba el código. Recién si es correcto se habilita el formulario.
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
        sesion.setAttribute(AUTORIZACION_VENCE, LocalDateTime.now(clock).plus(VENTANA_CAMBIO));
        redirect.addFlashAttribute("pasoPassword", "nueva");
        return "redirect:/mi-cuenta";
    }

    // Paso 3: fija la contraseña nueva. Sin la autorización del paso 2 no hace nada.
    @PostMapping("/password")
    public String cambiarPassword(@AuthenticationPrincipal UsuarioAutenticado principal,
                                  @Valid @ModelAttribute("formPassword") CambioPasswordFormDto form,
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
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // IllegalState es el límite de 24 horas. Llega hasta acá solo si trabaron la cuenta
            // entre el paso 1 y este, porque el paso 1 ya lo comprueba; aun así hay que
            // atajarlo, o el cambio pasaría igual por una ventana que se cerró en el medio.
            binding.rejectValue("nuevaPassword", "error.igual", ex.getMessage());
            redirect.addFlashAttribute(
                "org.springframework.validation.BindingResult.formPassword", binding);
            redirect.addFlashAttribute("formPassword", form);
            redirect.addFlashAttribute("pasoPassword", "nueva");
            return "redirect:/mi-cuenta";
        }

        // La autorización se consume: sirvio para un cambio y para uno solo.
        sesion.removeAttribute(AUTORIZACION);
        sesion.removeAttribute(AUTORIZACION_VENCE);
        redirect.addFlashAttribute("flashMensaje", "Contraseña cambiada correctamente.");
        return "redirect:/mi-cuenta";
    }

    // Si esta sesión validó un código para ESTA cuenta y la ventana sigue abierta.
    private boolean estaAutorizado(UsuarioAutenticado principal, HttpSession sesion) {
        Object para = sesion.getAttribute(AUTORIZACION);
        Object vence = sesion.getAttribute(AUTORIZACION_VENCE);
        return para instanceof Long id
            && id.equals(principal.getUsuarioId())
            && vence instanceof LocalDateTime cuando
            && LocalDateTime.now(clock).isBefore(cuando);
    }
}
