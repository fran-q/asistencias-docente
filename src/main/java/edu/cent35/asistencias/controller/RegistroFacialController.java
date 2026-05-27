package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.dto.*;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.service.ConsentimientoBiometricoService;
import edu.cent35.asistencias.service.DocenteService;
import edu.cent35.asistencias.service.ModeloFacialService;
import edu.cent35.asistencias.config.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Base64;
import java.util.List;

/**
 * Registro del modelo facial de un docente (RF-08, RF-09). Roles
 * INSTITUCION y ADMIN.
 * <p>
 * El registro exige que el docente tenga un consentimiento biométrico
 * ACTIVO (ADR-0005): sin consentimiento no se puede tomar la huella facial.
 */
@Controller
@RequestMapping("/docentes/{docenteId}/rostro")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class RegistroFacialController {

    private final DocenteService docenteService;
    private final ModeloFacialService modeloFacialService;
    private final ConsentimientoBiometricoService consentimientoService;

    /** Pantalla de captura: webcam + N capturas del rostro. */
    @GetMapping("/registrar")
    public String pantallaRegistro(@PathVariable Long docenteId,
                                   Model model,
                                   RedirectAttributes redirect) {
        Docente docente = docenteService.buscarPorId(docenteId); // valida tenant

        if (Boolean.FALSE.equals(docente.getActivo())) {
            redirect.addFlashAttribute("flashError",
                "El docente está inactivo. Reactivalo antes de registrar su rostro.");
            return "redirect:/docentes/" + docenteId + "/editar";
        }

        boolean consentimientoActivo =
            consentimientoService.estadoActual(docenteId) == EstadoConsentimiento.ACTIVO;

        model.addAttribute("docente", docente);
        model.addAttribute("consentimientoActivo", consentimientoActivo);
        model.addAttribute("yaRegistrado", modeloFacialService.tieneModeloActivo(docenteId));
        model.addAttribute("duracionGrabacionSeg", modeloFacialService.getDuracionGrabacionSeg());
        model.addAttribute("intervaloCapturaMs", modeloFacialService.getIntervaloCapturaMs());
        model.addAttribute("minimoCapturasValidas", modeloFacialService.getMinimoCapturasValidas());
        return "reconocimiento/registrar-rostro";
    }

    /**
     * Recibe las capturas del rostro, entrena el modelo y lo persiste.
     * Respuesta JSON (el flujo de captura es JavaScript).
     */
    @PostMapping("/registrar")
    @ResponseBody
    public RegistroFacialResultadoDto registrar(@PathVariable Long docenteId,
                                                @RequestBody RegistroFacialDto body,
                                                @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            List<byte[]> capturas = body.capturas().stream()
                .map(RegistroFacialController::decodificarDataUrl)
                .toList();
            modeloFacialService.registrar(docenteId, capturas, principal.getUsuarioId());
            return RegistroFacialResultadoDto.ok(
                "Modelo facial registrado correctamente.");
        } catch (IllegalArgumentException ex) {
            log.warn("Registro facial rechazado para docente {}: {}", docenteId, ex.getMessage());
            return RegistroFacialResultadoDto.error(ex.getMessage());
        } catch (EntityNotFoundException ex) {
            return RegistroFacialResultadoDto.error("El docente solicitado no existe.");
        } catch (RuntimeException ex) {
            log.error("Error inesperado registrando el rostro del docente {}", docenteId, ex);
            return RegistroFacialResultadoDto.error(
                "Ocurrió un error procesando las imágenes. Intentá de nuevo.");
        }
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public String handleNotFound(EntityNotFoundException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", "El docente solicitado no existe.");
        return "redirect:/docentes";
    }

    /**
     * Convierte un data URL base64 ({@code data:image/jpeg;base64,XXXX})
     * en bytes de imagen.
     */
    private static byte[] decodificarDataUrl(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            throw new IllegalArgumentException("Una de las capturas llegó vacía.");
        }
        int coma = dataUrl.indexOf(',');
        String base64 = (coma >= 0) ? dataUrl.substring(coma + 1) : dataUrl;
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Una de las capturas no es una imagen válida.");
        }
    }
}
