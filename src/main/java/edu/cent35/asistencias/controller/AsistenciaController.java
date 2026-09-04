package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.dto.*;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.service.AsistenciaService;
import edu.cent35.asistencias.service.DocenteService;
import edu.cent35.asistencias.service.HorarioService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Pantallas de asistencias: el listado del día, la carga manual para cuando el
 * reconocimiento falla y la justificación de ausencias (RF-22 a RF-26, RF-30). El listado
 * mezcla las marcas reales con las ausencias que el sistema calcula al vuelo.
 */
@Controller
@RequestMapping("/asistencias")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AsistenciaController {

    private final AsistenciaService asistenciaService;
    private final DocenteService docenteService;
    private final HorarioService horarioService;

    // ========================================================================
    //  Listado
    // ========================================================================

    @GetMapping
    public String listar(
            @RequestParam(name = "fecha", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(name = "estado", required = false) EstadoAsistencia estado,
            @RequestParam(name = "docenteId", required = false) Long docenteId,
            Model model) {

        LocalDate fechaFiltro = (fecha != null) ? fecha : LocalDate.now();

        List<AsistenciaListItemDto> todas = asistenciaService.listarDelDia(fechaFiltro);
        List<AsistenciaListItemDto> filtradas = todas.stream()
            .filter(a -> estado    == null || estado.equals(a.getEstado()))
            .filter(a -> docenteId == null || docenteId.equals(a.getDocenteId()))
            .toList();

        model.addAttribute("asistencias", filtradas);
        model.addAttribute("fecha", fechaFiltro);
        model.addAttribute("estadoFiltro", estado);
        model.addAttribute("docenteIdFiltro", docenteId);
        model.addAttribute("estadosPosibles", EstadoAsistencia.values());
        return "asistencia/list";
    }

    // ========================================================================
    //  Carga manual
    // ========================================================================

    @GetMapping("/manual/nueva")
    public String formManual(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", AsistenciaManualFormDto.builder()
                .fecha(LocalDate.now())
                .estado(EstadoAsistencia.PRESENTE)
                .build());
        }
        agregarDatosManualForm(model);
        return "asistencia/manual-nueva";
    }

    // Procesa la carga manual; si el service la rechaza, vuelve al formulario con el motivo.
    @PostMapping("/manual/nueva")
    public String crearManual(@Valid @ModelAttribute("form") AsistenciaManualFormDto form,
                              BindingResult binding,
                              @AuthenticationPrincipal UsuarioAutenticado principal,
                              Model model,
                              RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            agregarDatosManualForm(model);
            return "asistencia/manual-nueva";
        }
        try {
            Asistencia a = asistenciaService.marcarManual(
                form.getDocenteId(), form.getHorarioId(), form.getFecha(),
                form.getEstado(),
                form.getMotivoId(), form.getDetalleAdicional(),
                principal.getUsuarioId());
            redirect.addFlashAttribute("flashMensaje",
                "Asistencia cargada manualmente (id " + a.getId() + ").");
            return "redirect:/asistencias?fecha=" + form.getFecha();
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            agregarDatosManualForm(model);
            return "asistencia/manual-nueva";
        }
    }

    // Carga docentes, horarios y motivos que necesitan los combos del formulario.
    private void agregarDatosManualForm(Model model) {
        model.addAttribute("docentes", docenteService.listar());
        model.addAttribute("horarios", horarioService.listar());
        model.addAttribute("motivos", asistenciaService.motivosActivos());
        model.addAttribute("estadosPosibles", EstadoAsistencia.values());
    }

    // ========================================================================
    //  Justificación de ausencias
    // ========================================================================

    @GetMapping("/{id}/justificar")
    public String formJustificar(@PathVariable Long id, Model model,
                                 RedirectAttributes redirect) {
        if (asistenciaService.tieneJustificacion(id)) {
            redirect.addFlashAttribute("flashError",
                "Esta ausencia ya tiene justificación.");
            return "redirect:/asistencias";
        }
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new JustificacionAusenciaFormDto());
        }
        model.addAttribute("asistenciaId", id);
        return "asistencia/justificar";
    }

    // Adjunta una justificación a una ausencia ya registrada.
    @PostMapping("/{id}/justificar")
    public String justificar(@PathVariable Long id,
                             @Valid @ModelAttribute("form") JustificacionAusenciaFormDto form,
                             BindingResult binding,
                             @AuthenticationPrincipal UsuarioAutenticado principal,
                             Model model,
                             RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("asistenciaId", id);
            return "asistencia/justificar";
        }
        try {
            asistenciaService.justificarAusencia(id, form.getMotivo(),
                form.getDocumentoUrl(), principal.getUsuarioId());
            redirect.addFlashAttribute("flashMensaje", "Ausencia justificada correctamente.");
            return "redirect:/asistencias";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            model.addAttribute("asistenciaId", id);
            return "asistencia/justificar";
        }
    }

    // Si el id no existe o es de otra institución, avisa y vuelve al listado.
    @ExceptionHandler(EntityNotFoundException.class)
    public String handleNotFound(EntityNotFoundException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", "La asistencia solicitada no existe.");
        return "redirect:/asistencias";
    }
}
