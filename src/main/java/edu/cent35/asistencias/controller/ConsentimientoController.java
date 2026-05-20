package edu.cent35.asistencias.controller;
import edu.cent35.asistencias.dto.*;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.service.DocenteService;
import edu.cent35.asistencias.service.ConsentimientoBiometricoService;
import edu.cent35.asistencias.service.TextoConsentimiento;
import edu.cent35.asistencias.model.EstadoConsentimiento;
import edu.cent35.asistencias.model.MetodoConsentimiento;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.config.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

/**
 * Endpoints para otorgar / revocar consentimiento biometrico. Roles
 * INSTITUCION y ADMIN. Cumple RF-10 (Ley 25.326, Resolucion AAIP 255/2022).
 * <p>
 * En Sprint 3 solo se expone {@code otorgar}; la revocacion llega en
 * sub-fase D.3.
 */
@Controller
@RequestMapping("/docentes/{docenteId}/consentimiento")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class ConsentimientoController {

    private final DocenteService docenteService;
    private final ConsentimientoBiometricoService consentimientoService;

    @GetMapping("/otorgar")
    public String formOtorgar(@PathVariable Long docenteId,
                              Model model,
                              RedirectAttributes redirect) {

        Docente docente = docenteService.buscarPorId(docenteId); // valida tenant

        if (Boolean.FALSE.equals(docente.getActivo())) {
            redirect.addFlashAttribute("flashError",
                "El docente está inactivo. Reactivalo antes de cargar consentimiento.");
            return "redirect:/docentes/" + docenteId + "/editar";
        }

        EstadoConsentimiento estado = consentimientoService.estadoActual(docenteId);
        if (estado == EstadoConsentimiento.ACTIVO) {
            redirect.addFlashAttribute("flashError",
                "El docente ya tiene un consentimiento vigente. Revocalo primero si querés cargar uno nuevo.");
            return "redirect:/docentes/" + docenteId + "/editar";
        }

        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new ConsentimientoOtorgarFormDto());
        }
        model.addAttribute("docente", docente);
        model.addAttribute("estadoActual", estado);
        model.addAttribute("versionTexto", TextoConsentimiento.VERSION_ACTUAL);
        model.addAttribute("textoCuerpo", TextoConsentimiento.CUERPO);
        return "docente/consentimiento-otorgar";
    }

    @PostMapping("/otorgar")
    public String otorgar(@PathVariable Long docenteId,
                          @Valid @ModelAttribute("form") ConsentimientoOtorgarFormDto form,
                          BindingResult binding,
                          HttpServletRequest request,
                          @AuthenticationPrincipal CustomUserDetails principal,
                          Model model,
                          RedirectAttributes redirect) {

        Docente docente = docenteService.buscarPorId(docenteId); // valida tenant

        if (binding.hasErrors()) {
            model.addAttribute("docente", docente);
            model.addAttribute("estadoActual", consentimientoService.estadoActual(docenteId));
            model.addAttribute("versionTexto", TextoConsentimiento.VERSION_ACTUAL);
            model.addAttribute("textoCuerpo", TextoConsentimiento.CUERPO);
            return "docente/consentimiento-otorgar";
        }

        try {
            // Form simplificado: por ahora siempre ESCRITO + fecha = ahora.
            // Cuando se necesite cargar consentimientos retroactivos o
            // distinguir metodos, se expanden los campos del DTO.
            consentimientoService.otorgar(
                docenteId,
                MetodoConsentimiento.ESCRITO,
                LocalDateTime.now(),
                extraerIp(request),
                request.getHeader("User-Agent"),
                null, // documentoUrl - opcional, se podra cargar despues
                principal.getUsuarioId()
            );
            redirect.addFlashAttribute("flashMensaje",
                "Consentimiento biométrico registrado para " + docente.getNombreCompleto() + ".");
            return "redirect:/docentes/" + docenteId + "/editar";

        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            model.addAttribute("docente", docente);
            model.addAttribute("estadoActual", consentimientoService.estadoActual(docenteId));
            model.addAttribute("versionTexto", TextoConsentimiento.VERSION_ACTUAL);
            model.addAttribute("textoCuerpo", TextoConsentimiento.CUERPO);
            return "docente/consentimiento-otorgar";
        }
    }

    // ========================================================================
    //  Revocacion
    // ========================================================================

    @GetMapping("/revocar")
    public String formRevocar(@PathVariable Long docenteId,
                              Model model,
                              RedirectAttributes redirect) {

        Docente docente = docenteService.buscarPorId(docenteId); // valida tenant

        var vigenteOpt = consentimientoService.vigenteDe(docenteId);
        if (vigenteOpt.isEmpty()) {
            redirect.addFlashAttribute("flashError",
                "El docente no tiene un consentimiento vigente para revocar.");
            return "redirect:/docentes/" + docenteId + "/editar";
        }

        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new ConsentimientoRevocarFormDto());
        }
        model.addAttribute("docente", docente);
        model.addAttribute("consentimientoVigente", vigenteOpt.get());
        return "docente/consentimiento-revocar";
    }

    @PostMapping("/revocar")
    public String revocar(@PathVariable Long docenteId,
                          @Valid @ModelAttribute("form") ConsentimientoRevocarFormDto form,
                          BindingResult binding,
                          HttpServletRequest request,
                          @AuthenticationPrincipal CustomUserDetails principal,
                          Model model,
                          RedirectAttributes redirect) {

        Docente docente = docenteService.buscarPorId(docenteId); // valida tenant

        if (binding.hasErrors()) {
            model.addAttribute("docente", docente);
            consentimientoService.vigenteDe(docenteId)
                .ifPresent(c -> model.addAttribute("consentimientoVigente", c));
            return "docente/consentimiento-revocar";
        }

        try {
            consentimientoService.revocar(
                docenteId,
                form.getMotivo(),
                extraerIp(request),
                request.getHeader("User-Agent"),
                principal.getUsuarioId()
            );
            redirect.addFlashAttribute("flashMensaje",
                "Consentimiento biométrico revocado para " + docente.getNombreCompleto() + ".");
            return "redirect:/docentes/" + docenteId + "/editar";

        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            model.addAttribute("docente", docente);
            consentimientoService.vigenteDe(docenteId)
                .ifPresent(c -> model.addAttribute("consentimientoVigente", c));
            return "docente/consentimiento-revocar";
        }
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public String handleNotFound(EntityNotFoundException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", "El docente solicitado no existe.");
        return "redirect:/docentes";
    }

    /**
     * Extrae la IP real del cliente. Si la app se despliega tras un proxy
     * reverso (nginx, Apache, ALB), respetar {@code X-Forwarded-For};
     * caso contrario {@code getRemoteAddr()}.
     * En desarrollo local devuelve la IP del request directo (suele ser
     * {@code 127.0.0.1} o {@code 0:0:0:0:0:0:0:1}).
     */
    private static String extraerIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For puede ser "client, proxy1, proxy2" -> usar el primero
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
