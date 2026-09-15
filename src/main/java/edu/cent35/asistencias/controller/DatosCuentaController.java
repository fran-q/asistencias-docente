package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.dto.CambioDeCorreo;
import edu.cent35.asistencias.dto.CuentaPropiaFormDto;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.service.CodigoVerificacionService;
import edu.cent35.asistencias.service.ConfirmacionRequeridaException;
import edu.cent35.asistencias.service.UsuarioService;
import edu.cent35.asistencias.service.VerificacionCuentaService;
import edu.cent35.asistencias.validacion.UsuarioValido;
import edu.cent35.asistencias.validacion.UsuarioValidoValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Edición de los datos de la propia cuenta desde Mi cuenta: el usuario, el nombre y el correo.
 * El correo no se reemplaza al guardar: es por donde se recupera la contraseña, así que antes
 * pasa por un código al correo actual y otro al nuevo, y recién con el segundo cambia.
 *
 * <p>Igual que {@link CuentaController}, opera siempre sobre la cuenta logueada: el id sale del
 * principal y nunca de un parámetro, así que nadie puede tocar otra.
 */
@Controller
@RequestMapping("/mi-cuenta")
@RequiredArgsConstructor
@Slf4j
public class DatosCuentaController {

    private static final String VISTA_EDITAR = "cuenta/mi-cuenta-editar";
    private static final String VISTA_CORREO = "cuenta/cambio-correo";

    // Cuanto dura la autorizacion una vez validado el codigo del correo actual. La misma que la
    // del cambio de contrasena: es el tiempo de abrir el otro buzon y copiar un codigo.
    private static final Duration VENTANA = Duration.ofMinutes(10);

    private final UsuarioService usuarioService;
    private final VerificacionCuentaService verificacionService;

    // ========================================================================
    //  Los datos: usuario, nombre y correo
    // ========================================================================

    // El formulario, precargado con los datos de la cuenta.
    @GetMapping("/editar")
    public String editar(@AuthenticationPrincipal UsuarioAutenticado principal, Model model) {
        Usuario u = usuarioService.buscarPorId(principal.getUsuarioId());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", CuentaPropiaFormDto.from(u));
        }
        prepararEdicion(u, model);
        return VISTA_EDITAR;
    }

    /**
     * Guarda el usuario y el nombre, y si el correo cambió arranca el cambio con el código al
     * correo actual.
     *
     * <p>Lo demás no espera al correo: son datos independientes, y hacer depender el nombre de
     * un código que puede tardar en llegar no protege nada.
     */
    @PostMapping("/editar")
    public String guardar(@AuthenticationPrincipal UsuarioAutenticado principal,
                          @Valid @ModelAttribute("form") CuentaPropiaFormDto form,
                          BindingResult binding,
                          Model model,
                          HttpServletRequest request,
                          HttpSession sesion,
                          RedirectAttributes redirect) {

        Usuario u = usuarioService.buscarPorId(principal.getUsuarioId());

        // La regla del usuario se pide solo si cambia: una cuenta anterior a ella --"..." es un
        // usuario que existe-- tiene que poder corregir su nombre sin que la obliguen a cambiar
        // tambien como entra.
        String usuarioNuevo = form.getUsername() == null ? "" : form.getUsername().trim();
        if (!usuarioNuevo.isEmpty() && !usuarioNuevo.equals(u.getUsername())) {
            String problema = UsuarioValidoValidator.problema(usuarioNuevo);
            if (problema != null) {
                binding.rejectValue("username", "UsuarioValido", problema);
            }
        }
        if (!u.esCuentaInstitucional() && (form.getNombre() == null || form.getNombre().isBlank())) {
            binding.rejectValue("nombre", "NotBlank", "El nombre es obligatorio");
        }

        String correoNuevo = form.getEmail() == null ? "" : form.getEmail().trim();
        boolean cambiaElCorreo = !correoNuevo.isEmpty() && !correoNuevo.equalsIgnoreCase(u.getEmail());
        if (cambiaElCorreo && !binding.hasFieldErrors("email")) {
            // Antes de guardar nada: si ese correo no sirve, que se entere con el formulario
            // todavia en pantalla y no despues de esperar un codigo.
            String problema = verificacionService.problemaConCorreoNuevo(u.getId(), correoNuevo);
            if (problema != null) {
                binding.rejectValue("email", "Disponible", problema);
            }
        }

        if (binding.hasErrors()) {
            prepararEdicion(u, model);
            return VISTA_EDITAR;
        }

        String antes = u.getUsername() + "|" + u.getNombreParaMostrar();
        Usuario guardado;
        try {
            guardado = usuarioService.actualizarPropia(
                u.getId(), usuarioNuevo, form.getNombre(), form.getApellido(), form.isConfirmado());
        } catch (ConfirmacionRequeridaException ex) {
            // Esta persona ademas da clases: su nombre se ve en la ficha del docente y en los
            // listados de asistencia. Se avisa antes de escribir, como en la edicion de usuarios.
            Map<String, String> campos = new LinkedHashMap<>();
            campos.put("username", form.getUsername());
            campos.put("email", form.getEmail());
            campos.put("nombre", form.getNombre());
            campos.put("apellido", form.getApellido());
            campos.values().removeIf(Objects::isNull);

            model.addAttribute("impacto", ex.getImpacto());
            model.addAttribute("camposOcultos", campos);
            model.addAttribute("accion", "/mi-cuenta/editar");
            model.addAttribute("volverA", "/mi-cuenta/editar");
            return "identidad/confirmar";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            prepararEdicion(u, model);
            return VISTA_EDITAR;
        }

        // La sesion sigue siendo la misma: el usuario de la barra se refresca aca, en vez de
        // pedirle a la persona que vuelva a entrar para verlo.
        principal.actualizarDatos(guardado);
        boolean cambioAlgoMas =
            !antes.equals(guardado.getUsername() + "|" + guardado.getNombreParaMostrar());

        if (!cambiaElCorreo) {
            redirect.addFlashAttribute("flashMensaje",
                cambioAlgoMas ? "Tus datos quedaron actualizados." : "No había cambios para guardar.");
            return "redirect:/mi-cuenta";
        }

        sesion.setAttribute(CambioDeCorreo.SESION, new CambioDeCorreo(u.getId(), correoNuevo));
        enviarAlCorreoActual(guardado, correoNuevo, request, redirect,
            cambioAlgoMas ? "Tus otros datos ya quedaron guardados. " : "");
        return "redirect:/mi-cuenta/correo";
    }

    // Lo que la pantalla de edicion necesita ademas del formulario.
    private void prepararEdicion(Usuario u, Model model) {
        model.addAttribute("usuario", u);
        model.addAttribute("esInstitucion", u.esCuentaInstitucional());
        // El patron del navegador va solo si el usuario de hoy ya cumple la regla. Si no, el
        // campo quedaria invalido desde que se abre y no dejaria guardar aunque nadie lo toque.
        model.addAttribute("patronUsuario",
            UsuarioValidoValidator.problema(u.getUsername()) == null ? UsuarioValido.PATRON_HTML : null);
        model.addAttribute("avisoUsuario", UsuarioValido.AVISO);
    }

    // ========================================================================
    //  El correo: un codigo al actual y otro al nuevo
    //
    //  POST /editar           guarda lo demas y manda el codigo al correo ACTUAL
    //  POST /correo/actual    valida ese codigo y manda otro al correo NUEVO
    //  POST /correo/nuevo     valida el segundo, y recien ahi la cuenta cambia de correo
    //
    //  El estado vive en la sesion (CambioDeCorreo). Hasta el ultimo paso no cambio nada:
    //  abandonar a mitad de camino deja la cuenta exactamente como estaba.
    // ========================================================================

    // La pantalla del paso en curso. Sin un cambio pendiente vuelve a Mi cuenta.
    @GetMapping("/correo")
    public String correo(@AuthenticationPrincipal UsuarioAutenticado principal,
                         HttpSession sesion,
                         Model model) {
        CambioDeCorreo cambio = pendienteDe(principal, sesion);
        if (cambio == null) {
            return "redirect:/mi-cuenta";
        }
        model.addAttribute("cambio", cambio);
        model.addAttribute("correoActual",
            usuarioService.buscarPorId(principal.getUsuarioId()).getEmail());
        return VISTA_CORREO;
    }

    // Paso 2: valida el codigo del correo actual y manda el del nuevo.
    @PostMapping("/correo/actual")
    public String validarCorreoActual(@AuthenticationPrincipal UsuarioAutenticado principal,
                                      @RequestParam(name = "codigo", required = false) String codigo,
                                      HttpServletRequest request,
                                      HttpSession sesion,
                                      RedirectAttributes redirect) {
        CambioDeCorreo cambio = pendienteDe(principal, sesion);
        if (cambio == null) {
            return "redirect:/mi-cuenta";
        }

        CodigoVerificacionService.Resultado resultado =
            verificacionService.validarCodigoDelCorreoActual(principal.getUsuarioId(), codigo);
        if (resultado != CodigoVerificacionService.Resultado.OK) {
            redirect.addFlashAttribute("error", CuentaController.mensajeDe(resultado));
            return "redirect:/mi-cuenta/correo";
        }

        cambio.autorizarHasta(LocalDateTime.now().plus(VENTANA));
        // Se vuelve a poner para que la sesion registre que el objeto cambio.
        sesion.setAttribute(CambioDeCorreo.SESION, cambio);
        enviarAlCorreoNuevo(principal.getUsuarioId(), cambio, request, redirect);
        return "redirect:/mi-cuenta/correo";
    }

    // Paso 3: valida el codigo del correo nuevo y, si es correcto, la cuenta pasa a usarlo.
    @PostMapping("/correo/nuevo")
    public String confirmarCorreoNuevo(@AuthenticationPrincipal UsuarioAutenticado principal,
                                       @RequestParam(name = "codigo", required = false) String codigo,
                                       HttpSession sesion,
                                       RedirectAttributes redirect) {
        CambioDeCorreo cambio = pendienteDe(principal, sesion);
        if (cambio == null) {
            return "redirect:/mi-cuenta";
        }
        if (!cambio.estaAutorizado()) {
            // Se comprueba en el servidor y no confiando en que la pantalla haya mostrado el paso
            // correcto: este formulario se puede mandar sin haber pasado por el anterior, y
            // saltearlo es justo lo que el codigo al correo actual existe para impedir.
            cambio.desautorizar();
            sesion.setAttribute(CambioDeCorreo.SESION, cambio);
            redirect.addFlashAttribute("flashError",
                "Primero hay que confirmar el código de tu correo actual. Pedí uno nuevo.");
            return "redirect:/mi-cuenta/correo";
        }

        try {
            CodigoVerificacionService.Resultado resultado = verificacionService.confirmarCorreoNuevo(
                principal.getUsuarioId(), cambio.getCorreoNuevo(), codigo);
            if (resultado != CodigoVerificacionService.Resultado.OK) {
                redirect.addFlashAttribute("error", CuentaController.mensajeDe(resultado));
                return "redirect:/mi-cuenta/correo";
            }
        } catch (IllegalArgumentException ex) {
            // Otra cuenta de la institucion tomo ese correo mientras llegaban los codigos.
            sesion.removeAttribute(CambioDeCorreo.SESION);
            redirect.addFlashAttribute("flashError", ex.getMessage());
            return "redirect:/mi-cuenta";
        }

        sesion.removeAttribute(CambioDeCorreo.SESION);
        redirect.addFlashAttribute("flashMensaje",
            "Listo, tu cuenta ahora usa " + cambio.getCorreoNuevo() + ".");
        return "redirect:/mi-cuenta";
    }

    // Manda otro codigo para el paso en curso.
    @PostMapping("/correo/reenviar")
    public String reenviar(@AuthenticationPrincipal UsuarioAutenticado principal,
                           HttpServletRequest request,
                           HttpSession sesion,
                           RedirectAttributes redirect) {
        CambioDeCorreo cambio = pendienteDe(principal, sesion);
        if (cambio == null) {
            return "redirect:/mi-cuenta";
        }
        if (cambio.estaAutorizado()) {
            enviarAlCorreoNuevo(principal.getUsuarioId(), cambio, request, redirect);
        } else {
            // Sin autorizacion vigente se vuelve al primer paso: una ventana vencida no se
            // estira pidiendo otro codigo al correo nuevo.
            cambio.desautorizar();
            sesion.setAttribute(CambioDeCorreo.SESION, cambio);
            enviarAlCorreoActual(usuarioService.buscarPorId(principal.getUsuarioId()),
                                 cambio.getCorreoNuevo(), request, redirect, "");
        }
        return "redirect:/mi-cuenta/correo";
    }

    // Abandona el cambio: la cuenta sigue con su correo de siempre.
    @PostMapping("/correo/cancelar")
    public String cancelar(HttpSession sesion, RedirectAttributes redirect) {
        sesion.removeAttribute(CambioDeCorreo.SESION);
        redirect.addFlashAttribute("flashMensaje",
            "Cancelaste el cambio de correo: la cuenta sigue con el de antes.");
        return "redirect:/mi-cuenta";
    }

    // El cambio pendiente de ESTA cuenta, o null. Uno de otra cuenta se descarta: la sesion
    // conserva sus atributos si alguien vuelve a entrar con otro usuario en el mismo navegador.
    static CambioDeCorreo pendienteDe(UsuarioAutenticado principal, HttpSession sesion) {
        Object guardado = sesion.getAttribute(CambioDeCorreo.SESION);
        if (guardado instanceof CambioDeCorreo cambio
                && cambio.getUsuarioId().equals(principal.getUsuarioId())) {
            return cambio;
        }
        if (guardado != null) {
            sesion.removeAttribute(CambioDeCorreo.SESION);
        }
        return null;
    }

    // Manda el codigo al correo actual y deja el aviso para la pantalla siguiente.
    private void enviarAlCorreoActual(Usuario u, String correoNuevo, HttpServletRequest request,
                                      RedirectAttributes redirect, String prefijo) {
        try {
            verificacionService.iniciarCambioDeCorreo(u.getId(), correoNuevo, CuentaController.ipDe(request));
            redirect.addFlashAttribute("flashMensaje",
                prefijo + "Te mandamos un código a " + u.getEmail() + " para confirmar que sos vos.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("flashError", prefijo + ex.getMessage());
        } catch (RuntimeException ex) {
            // Tipicamente el SMTP no responde. Se avisa en vez de decir "revisa tu correo".
            log.warn("No se pudo enviar el codigo al correo actual: {}", ex.toString());
            redirect.addFlashAttribute("flashError",
                prefijo + "No pudimos enviar el correo en este momento. Probá de nuevo en un rato.");
        }
    }

    // Manda el codigo al correo nuevo y deja el aviso para la pantalla siguiente.
    private void enviarAlCorreoNuevo(Long usuarioId, CambioDeCorreo cambio, HttpServletRequest request,
                                     RedirectAttributes redirect) {
        try {
            verificacionService.enviarCodigoAlCorreoNuevo(
                usuarioId, cambio.getCorreoNuevo(), CuentaController.ipDe(request));
            redirect.addFlashAttribute("flashMensaje",
                "Listo. Ahora te mandamos otro código a " + cambio.getCorreoNuevo() + ".");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("No se pudo enviar el codigo al correo nuevo: {}", ex.toString());
            redirect.addFlashAttribute("flashError",
                "No pudimos enviar el correo en este momento. Probá de nuevo en un rato.");
        }
    }
}
