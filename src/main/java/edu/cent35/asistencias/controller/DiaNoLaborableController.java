package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.model.TipoDiaNoLaborable;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.service.DiaNoLaborableService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/**
 * Los días de adentro del ciclo en los que no se dicta clase (V024): feriados, receso, jornadas
 * institucionales. El job de ausencias los saltea.
 *
 * <p>Rol institucional, igual que los ciclos: es la otra mitad de la misma decisión —cuándo hay
 * clases— y marcar un día de más silencia las ausencias de toda la institución.
 */
@Controller
@RequestMapping("/dias-sin-clase")
@PreAuthorize("hasRole('INSTITUCION')")
@RequiredArgsConstructor
@Slf4j
public class DiaNoLaborableController {

    private final DiaNoLaborableService service;

    /**
     * El listado de un año.
     *
     * <p>Por año y no completo porque los feriados se cargan y se revisan por año: mostrar
     * todos juntos convertiría la pantalla en una lista que crece para siempre. El tipo y la
     * búsqueda filtran en el navegador lo que ya vino (V028).
     */
    @GetMapping
    public String listar(@RequestParam(name = "anio", required = false) Integer anio,
                         Model model) {
        int elegido = anio != null ? anio : LocalDate.now().getYear();
        model.addAttribute("anioSeleccionado", elegido);
        model.addAttribute("dias", service.listarDelAnio(elegido));
        model.addAttribute("anioActual", LocalDate.now().getYear());
        model.addAttribute("tipos", TipoDiaNoLaborable.values());
        return "academico/dia-sin-clase-list";
    }

    /**
     * Marca un día, o todos los de un rango si viene "hasta".
     *
     * <p>El tipo es opcional para Spring a propósito: sin elegir llega vacío, se convierte en
     * null y el servicio lo pide con un mensaje, en vez de un 400 sin explicación.
     */
    @PostMapping
    public String crear(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                        @RequestParam(required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                        @RequestParam(required = false) TipoDiaNoLaborable tipo,
                        @RequestParam String motivo,
                        @AuthenticationPrincipal UsuarioAutenticado principal,
                        RedirectAttributes redirect) {
        try {
            DiaNoLaborableService.Resultado r = service.marcar(fecha, hasta, tipo, motivo,
                principal == null ? null : principal.getUsuarioId());
            redirect.addFlashAttribute("flashMensaje", mensaje(r));
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/dias-sin-clase?anio=" + fecha.getYear();
    }

    // Lo que se marco y lo que se salteo porque ya estaba: si no se dice, quien cargo un receso
    // de doce dias y ve once filas nuevas no sabe si se perdio uno.
    private static String mensaje(DiaNoLaborableService.Resultado r) {
        String listo = r.marcados() == 1
            ? "Listo. Ese día no va a generar ausencias automáticas."
            : "Listo. Se marcaron " + r.marcados() + " días: no van a generar ausencias automáticas.";
        if (r.yaEstaban() == 0) return listo;
        return listo + (r.yaEstaban() == 1
            ? " Uno ya estaba cargado y quedó como estaba."
            : " " + r.yaEstaban() + " ya estaban cargados y quedaron como estaban.");
    }

    // Borrado fisico: no hay nada que dependa de esta fila. Ver DiaNoLaborableService.
    @PostMapping("/{id}/borrar")
    public String borrar(@PathVariable Long id,
                         @RequestParam(name = "anio", required = false) Integer anio,
                         RedirectAttributes redirect) {
        service.borrar(id);
        redirect.addFlashAttribute("flashMensaje",
            "Día borrado. Las ausencias que no se generaron ese día no se generan ahora.");
        return "redirect:/dias-sin-clase" + (anio != null ? "?anio=" + anio : "");
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public String noEncontrado(EntityNotFoundException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", ex.getMessage());
        return "redirect:/dias-sin-clase";
    }
}
