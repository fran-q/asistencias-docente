package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.model.PeriodoLectivo;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.service.CicloLectivoService;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Los ciclos lectivos y sus períodos: el año calendario de cursada (V023). Desde acá se abre el
 * año que viene, se copia la oferta del anterior y se cierra el que terminó.
 *
 * <p>Rol institucional en todo: definir el calendario académico decide cuándo el sistema toma
 * asistencia y cuándo genera ausencias, que no es una tarea administrativa del día a día.
 */
@Controller
@RequestMapping("/ciclos")
@PreAuthorize("hasRole('INSTITUCION')")
@RequiredArgsConstructor
@Slf4j
public class CicloLectivoController {

    private final CicloLectivoService service;

    // Listado con sus periodos. Es la pantalla completa: los ciclos son pocos --uno por ano--
    // asi que no hace falta separar un detalle.
    @GetMapping
    public String listar(Model model) {
        model.addAttribute("ciclos", service.listar());
        model.addAttribute("hoy", LocalDate.now());
        return "academico/ciclo-list";
    }

    /**
     * Crea el ciclo con sus períodos.
     *
     * <p>Los períodos llegan como tres listas paralelas —nombres, inicios y fines— porque el
     * formulario los agrega dinámicamente y no se sabe cuántos van a venir. Se recorren por
     * índice y se cortan por el más corto: un envío manipulado con listas de distinto largo
     * produciría períodos a medio armar.
     */
    @PostMapping
    public String crear(@RequestParam Short anio,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
                        @RequestParam(name = "periodoNombre", required = false) List<String> nombres,
                        @RequestParam(name = "periodoInicio", required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) List<LocalDate> inicios,
                        @RequestParam(name = "periodoFin", required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) List<LocalDate> fines,
                        RedirectAttributes redirect) {
        try {
            service.crear(anio, fechaInicio, fechaFin, armarPeriodos(nombres, inicios, fines));
            redirect.addFlashAttribute("flashMensaje", "Ciclo lectivo " + anio + " creado.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ciclos";
    }

    // Cambia las fechas del ciclo. Los periodos se editan por su cuenta.
    @PostMapping("/{id}/fechas")
    public String actualizarFechas(@PathVariable Long id,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
                                   RedirectAttributes redirect) {
        try {
            service.actualizarFechas(id, fechaInicio, fechaFin);
            redirect.addFlashAttribute("flashMensaje", "Fechas actualizadas.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        }
        return "redirect:/ciclos";
    }

    // Pone el ciclo en curso. Solo uno a la vez.
    @PostMapping("/{id}/activar")
    public String activar(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.activar(id);
            redirect.addFlashAttribute("flashMensaje",
                "Ciclo activado. Ya se puede tomar asistencia contra su oferta.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        }
        return "redirect:/ciclos";
    }

    // Cierra el ciclo: la estructura queda congelada, las asistencias no.
    @PostMapping("/{id}/cerrar")
    public String cerrar(@PathVariable Long id,
                         @AuthenticationPrincipal UsuarioAutenticado principal,
                         RedirectAttributes redirect) {
        try {
            service.cerrar(id, principal == null ? null : principal.getUsuarioId());
            redirect.addFlashAttribute("flashMensaje",
                "Ciclo cerrado. Su oferta ya no se puede modificar; las asistencias de ese "
                + "año se pueden seguir corrigiendo.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        }
        return "redirect:/ciclos";
    }

    /**
     * Copia la oferta de un ciclo al otro.
     *
     * <p>Cuenta lo que quedó afuera además de lo que entró: un "listo" a secas dejaría creer
     * que se copió todo cuando puede haber comisiones sin período equivalente en el destino.
     */
    @PostMapping("/{id}/copiar-desde")
    public String copiarOferta(@PathVariable Long id,
                               @RequestParam Long origenId,
                               RedirectAttributes redirect) {
        try {
            CicloLectivoService.ResultadoCopia r = service.copiarOferta(origenId, id);

            StringBuilder msg = new StringBuilder("Se copiaron ")
                .append(r.comisiones()).append(" comisión(es) y ")
                .append(r.horarios()).append(" horario(s).");
            if (r.hayPendientes()) {
                msg.append(" Quedaron ").append(r.sinPeriodoEquivalente().size())
                   .append(" sin copiar porque el ciclo destino no tiene un período con ese "
                           + "nombre: ")
                   .append(String.join("; ", r.sinPeriodoEquivalente()));
            }
            redirect.addFlashAttribute(r.hayPendientes() ? "flashError" : "flashMensaje",
                                       msg.toString());
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        }
        return "redirect:/ciclos";
    }

    // Un ciclo de otra institucion responde "no encontrado" y vuelve al listado, sin pantalla
    // de error: el mensaje ya dice todo lo que hay que decir.
    @ExceptionHandler(EntityNotFoundException.class)
    public String noEncontrado(EntityNotFoundException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", ex.getMessage());
        return "redirect:/ciclos";
    }

    // Junta las tres listas paralelas del formulario en periodos, cortando por la mas corta.
    private List<PeriodoLectivo> armarPeriodos(List<String> nombres, List<LocalDate> inicios,
                                               List<LocalDate> fines) {
        List<PeriodoLectivo> periodos = new ArrayList<>();
        if (nombres == null || inicios == null || fines == null) {
            return periodos;
        }
        int cuantos = Math.min(nombres.size(), Math.min(inicios.size(), fines.size()));
        for (int i = 0; i < cuantos; i++) {
            if (nombres.get(i) == null || nombres.get(i).isBlank()) {
                continue;                       // una fila vacia del formulario no es un periodo
            }
            periodos.add(PeriodoLectivo.builder()
                .nombre(nombres.get(i).trim())
                .fechaInicio(inicios.get(i))
                .fechaFin(fines.get(i))
                .orden((short) (i + 1))
                .build());
        }
        return periodos;
    }
}
