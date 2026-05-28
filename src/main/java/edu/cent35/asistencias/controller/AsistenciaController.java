package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.dto.*;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.service.AsistenciaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * Listado de asistencias del día (RF-30). Incluye los AUSENTE calculados
 * para horarios cuyo {@code hora_fin} ya pasó y no tienen marca.
 */
@Controller
@RequestMapping("/asistencias")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AsistenciaController {

    private final AsistenciaService asistenciaService;

    @GetMapping
    public String listar(
            @RequestParam(name = "fecha", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(name = "estado", required = false) EstadoAsistencia estado,
            @RequestParam(name = "docenteId", required = false) Long docenteId,
            Model model) {

        LocalDate fechaFiltro = (fecha != null) ? fecha : LocalDate.now();

        List<AsistenciaListItemDto> todas = asistenciaService.listarDelDia(fechaFiltro);

        // Filtros en memoria (cantidad chica para un día puntual).
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
}
