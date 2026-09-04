package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.dto.CierreManualFormDto;
import edu.cent35.asistencias.model.BloquePresencia;
import edu.cent35.asistencias.service.AsistenciaService;
import edu.cent35.asistencias.service.BloquePresenciaService;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import jakarta.persistence.EntityNotFoundException;
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

import java.util.List;

/**
 * Pantalla de los bloques que quedaron sin marca de salida y el cierre manual de cada uno
 * (RF-79, RF-83). La salida es obligatoria, así que su falta no se descarta en silencio: se
 * acumula acá hasta que alguien la resuelve.
 * <p>
 * El cierre manual no es un caso de borde. Con el algoritmo actual el reconocimiento puede
 * fallar al salir por un cambio de iluminación respecto del momento de la entrada, y si el
 * docente revocó su consentimiento su rostro directamente no se puede usar (RF-82): en los
 * dos casos esta pantalla es el único camino.
 */
@Controller
@RequestMapping("/asistencias/bloques")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class BloquePresenciaController {

    private final BloquePresenciaService bloquePresenciaService;
    private final AsistenciaService asistenciaService;

    // Listado de bloques sin salida registrada, arrastrados entre días hasta resolverlos.
    @GetMapping("/pendientes")
    public String pendientes(Model model) {
        List<BloquePresencia> pendientes = bloquePresenciaService.pendientesDeCierre();
        model.addAttribute("pendientes", pendientes);
        model.addAttribute("motivos", asistenciaService.motivosActivos());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new CierreManualFormDto());
        }
        return "asistencia/bloques-pendientes";
    }

    /**
     * Cierra un bloque a mano o corrige su hora de salida.
     *
     * <p>Si la hora nueva deja clases afuera, el mensaje lo dice con todas las letras en vez
     * de borrarlas por su cuenta: quitar una marca de asistencia es otro acto administrativo,
     * y hacerlo de oficio dejaría al admin sin enterarse de lo que acaba de pasar.
     */
    @PostMapping("/{id}/cerrar")
    public String cerrar(@PathVariable Long id,
                         @Valid @ModelAttribute("form") CierreManualFormDto form,
                         BindingResult binding,
                         @AuthenticationPrincipal UsuarioAutenticado principal,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("bloqueEnEdicion", id);
            return pendientes(model);
        }
        try {
            BloquePresenciaService.ResultadoCierreManual r =
                bloquePresenciaService.cerrarManualmente(
                    id, form.getHoraSalida(), form.getMotivoId(), form.getDetalle(),
                    principal.getUsuarioId());

            StringBuilder msg = new StringBuilder()
                .append("Salida registrada a las ").append(form.getHoraSalida())
                .append(". Quedaron ").append(r.imputadas())
                .append(r.imputadas() == 1 ? " clase imputada." : " clases imputadas.");
            if (!r.fueraDeRango().isEmpty()) {
                msg.append(" Atención: ").append(r.fueraDeRango().size())
                   .append(r.fueraDeRango().size() == 1
                       ? " asistencia quedó" : " asistencias quedaron")
                   .append(" fuera del horario nuevo y siguen marcadas. Revisalas en el listado.");
            }
            redirect.addFlashAttribute("flashMensaje", msg.toString());
            return "redirect:/asistencias/bloques/pendientes";

        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            model.addAttribute("bloqueEnEdicion", id);
            return pendientes(model);
        }
    }

    // Un bloque de otra institución no existe para este tenant: se responde como tal.
    @ExceptionHandler(EntityNotFoundException.class)
    public String noEncontrado(EntityNotFoundException ex, RedirectAttributes redirect) {
        log.info("Bloque no encontrado o de otro tenant: {}", ex.getMessage());
        redirect.addFlashAttribute("flashError", "Ese bloque no existe.");
        return "redirect:/asistencias/bloques/pendientes";
    }
}
