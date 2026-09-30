package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.dto.AltaInstitucionFormDto;
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
 * Configuración inicial de una instalación local: crea la institución y su cuenta
 * administradora sin código al correo, porque en esa máquina no hay correo (ADR-0022).
 *
 * <p>Es pública por el mismo motivo que el alta de institución —ocurre antes de que exista el
 * tenant, así que no hay sesión ni rol contra el cual autorizar— pero lo que la protege es otra
 * cosa: solo responde mientras la base no tenga ninguna institución, y únicamente si la
 * instalación la habilitó. Fuera de eso no devuelve "no autorizado" sino <b>404</b>: una
 * pantalla que no corresponde no tiene por qué admitir que existió alguna vez.
 */
@Controller
@RequestMapping("/instalacion")
@RequiredArgsConstructor
@Slf4j
public class InstalacionController {

    private static final String CLAVE = "claveRecuperacion";

    private final InstalacionService instalacion;

    // Muestra el formulario. 404 si la instalación ya está configurada o el asistente no va.
    @GetMapping
    public String formulario(Model model) {
        exigirQueCorresponda();
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new AltaInstitucionFormDto());
        }
        return "auth/instalacion";
    }

    /**
     * Crea la institución y su cuenta, y muestra la clave de recuperación.
     *
     * <p>No deja la sesión abierta a propósito: que la primera cosa que se haga con la cuenta
     * recién creada sea entrar con ella comprueba, ahí mismo y no la semana que viene, que la
     * contraseña quedó como se esperaba.
     */
    @PostMapping
    public String crear(@Valid @ModelAttribute("form") AltaInstitucionFormDto form,
                        BindingResult binding,
                        RedirectAttributes flash) {
        exigirQueCorresponda();

        if (!form.coincide()) {
            binding.rejectValue("confirmacion", "error.confirmacion",
                                "Las contraseñas no coinciden");
        }
        if (binding.hasErrors()) {
            return "auth/instalacion";
        }

        InstalacionService.PrimeraInstitucion creada;
        try {
            creada = instalacion.crearPrimeraInstitucion(form);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            binding.reject("error.global", ex.getMessage());
            return "auth/instalacion";
        }

        // En flash y no en la URL ni en la sesión: es un dato que se muestra una vez y no tiene
        // que quedar en el historial del navegador ni sobrevivir a un F5.
        flash.addFlashAttribute(CLAVE, creada.claveRecuperacion());
        return "redirect:/instalacion/clave";
    }

    /**
     * Muestra la clave de recuperación recién generada. Se ve una sola vez: de la clave solo
     * queda el hash, así que ni esta pantalla ni ninguna otra pueden volver a mostrarla.
     *
     * <p>No la protege {@code disponible()} —a esta altura ya hay una institución y el asistente
     * se apagó—, la protege el flash: sin él no hay nada que mostrar y se va al login. Por eso
     * recargar no la repite.
     */
    @GetMapping("/clave")
    public String clave(@ModelAttribute(CLAVE) String clave) {
        if (clave == null || clave.isBlank()) {
            return "redirect:/login";
        }
        return "auth/instalacion-clave";
    }

    // El 404 se arma acá y no en el interceptor: el interceptor decide a dónde mandar a quien
    // todavía no configuró nada, y esto decide qué pasa con quien pide una pantalla que ya no
    // corresponde. Son dos preguntas distintas sobre el mismo estado.
    private void exigirQueCorresponda() {
        if (!instalacion.disponible()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
