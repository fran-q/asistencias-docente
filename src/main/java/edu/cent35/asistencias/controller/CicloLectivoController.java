package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.model.EstadoCiclo;
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
 * Los ciclos lectivos y sus períodos: el año calendario de cursada (V023). Desde el listado se
 * abre el año que viene, se copia la oferta del anterior y se cierra el que terminó; desde la
 * pantalla de cada ciclo (V027) se ve qué tiene y se corrige su calendario.
 *
 * <p>Rol institucional en todo: definir el calendario académico decide cuándo el sistema toma
 * asistencia y cuándo genera ausencias, que no es una tarea administrativa del día a día.
 *
 * <p>Las fechas y los nombres de los formularios nuevos llegan como opcionales a propósito: un
 * campo vacío tiene que volver con el mensaje del servicio, no con un error 400.
 *
 * <p><b>Dos canales para los rechazos.</b> Los de un formulario —alta, datos del ciclo,
 * períodos— vuelven como {@code error} y la plantilla los muestra en la pantalla: son errores
 * de lo que se cargó, y un "dejaría afuera el 07/04/2026" tiene que quedar a la vista mientras
 * se corrige, no en un aviso que se va solo. Los de una acción —activar, cerrar, reabrir,
 * borrar, quitar un período— van como {@code flashError}, igual que en el resto del sistema.
 */
@Controller
@RequestMapping("/ciclos")
@PreAuthorize("hasRole('INSTITUCION')")
@RequiredArgsConstructor
@Slf4j
public class CicloLectivoController {

    // Activar y cerrar se disparan desde el listado o desde el detalle, y cada uno vuelve a
    // donde estaba. Se acepta solo esta palabra y no una direccion: un parametro que fuera una
    // URL seria una redireccion abierta.
    private static final String VOLVER_AL_DETALLE = "detalle";

    private final CicloLectivoService service;

    // Listado con sus periodos: los ciclos son pocos --uno por ano--, asi que entran todos en
    // una pantalla. Lo que cuelga de cada uno se ve en su detalle.
    @GetMapping
    public String listar(Model model) {
        model.addAttribute("ciclos", service.listar());
        model.addAttribute("hoy", LocalDate.now());
        return "academico/ciclo-list";
    }

    /**
     * La pantalla de un ciclo (V027): sus períodos, cuántas comisiones cuelgan de cada uno, los
     * días sin clase que caen adentro y lo que se puede hacer con él. Es también donde se edita.
     */
    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        CicloLectivoService.DetalleCiclo detalle = service.detalle(id);
        model.addAttribute("detalle", detalle);
        model.addAttribute("ciclo", detalle.ciclo());
        model.addAttribute("cerrado", detalle.ciclo().getEstado() == EstadoCiclo.CERRADO);
        model.addAttribute("enPreparacion", detalle.ciclo().getEstado() == EstadoCiclo.PREPARACION);
        model.addAttribute("hoy", LocalDate.now());
        return "academico/ciclo-detalle";
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
            // La plantilla lo mostraba dos veces --arriba de todo y dentro del formulario--;
            // quedo solo el del formulario, junto a lo que se cargo.
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ciclos";
    }

    // Corrige el ano y las fechas. El ano llega solo si el ciclo esta en preparacion: en otro
    // estado el campo va deshabilitado, el navegador no lo manda, y queda el que estaba.
    @PostMapping("/{id}/datos")
    public String actualizar(@PathVariable Long id,
                             @RequestParam(required = false) Short anio,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
                             RedirectAttributes redirect) {
        try {
            service.actualizar(id, anio, fechaInicio, fechaFin);
            redirect.addFlashAttribute("flashMensaje", "Datos del ciclo actualizados.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return alDetalle(id);
    }

    // Suma un periodo al ciclo. El mensaje no repite el nombre: ya se ve en la lista.
    @PostMapping("/{id}/periodos")
    public String agregarPeriodo(@PathVariable Long id,
                                 @RequestParam(required = false) String nombre,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
                                 RedirectAttributes redirect) {
        try {
            service.agregarPeriodo(id, nombre, fechaInicio, fechaFin);
            redirect.addFlashAttribute("flashMensaje", "Período agregado.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return alDetalle(id);
    }

    // Renombra un periodo o le cambia las fechas.
    @PostMapping("/{id}/periodos/{periodoId}")
    public String editarPeriodo(@PathVariable Long id,
                                @PathVariable Long periodoId,
                                @RequestParam(required = false) String nombre,
                                @RequestParam(required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
                                @RequestParam(required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
                                RedirectAttributes redirect) {
        try {
            service.editarPeriodo(id, periodoId, nombre, fechaInicio, fechaFin);
            redirect.addFlashAttribute("flashMensaje", "Período actualizado.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return alDetalle(id);
    }

    // Quita un periodo sin comisiones. Borrado fisico: ver CicloLectivoService.quitarPeriodo.
    @PostMapping("/{id}/periodos/{periodoId}/quitar")
    public String quitarPeriodo(@PathVariable Long id,
                                @PathVariable Long periodoId,
                                RedirectAttributes redirect) {
        try {
            service.quitarPeriodo(id, periodoId);
            redirect.addFlashAttribute("flashMensaje", "Período quitado.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        }
        return alDetalle(id);
    }

    // Pone el ciclo en curso. Solo uno a la vez.
    @PostMapping("/{id}/activar")
    public String activar(@PathVariable Long id,
                          @RequestParam(required = false) String volver,
                          RedirectAttributes redirect) {
        try {
            service.activar(id);
            redirect.addFlashAttribute("flashMensaje",
                "Ciclo activado. Ya se puede tomar asistencia contra su oferta.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        }
        return volverA(id, volver);
    }

    // Cierra el ciclo: la estructura queda congelada, las asistencias no.
    @PostMapping("/{id}/cerrar")
    public String cerrar(@PathVariable Long id,
                         @RequestParam(required = false) String volver,
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
        return volverA(id, volver);
    }

    // Reabre el ultimo ciclo cerrado. Queda en preparacion: activarlo es un paso aparte.
    @PostMapping("/{id}/reabrir")
    public String reabrir(@PathVariable Long id,
                          @AuthenticationPrincipal UsuarioAutenticado principal,
                          RedirectAttributes redirect) {
        try {
            service.reabrir(id, principal == null ? null : principal.getUsuarioId());
            redirect.addFlashAttribute("flashMensaje",
                "Ciclo reabierto: quedó en preparación. Para volver a tomar asistencia contra "
                + "su oferta, activalo.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        }
        return alDetalle(id);
    }

    // Borra un ciclo cargado por error. Si sale bien, ya no hay detalle al que volver.
    @PostMapping("/{id}/borrar")
    public String borrar(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.borrar(id);
            redirect.addFlashAttribute("flashMensaje", "Ciclo borrado.");
            return "redirect:/ciclos";
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
            return alDetalle(id);
        }
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

    // Un ciclo o un periodo de otra institucion responde "no encontrado" y vuelve al listado,
    // sin pantalla de error: el mensaje ya dice todo lo que hay que decir.
    @ExceptionHandler(EntityNotFoundException.class)
    public String noEncontrado(EntityNotFoundException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", ex.getMessage());
        return "redirect:/ciclos";
    }

    private String alDetalle(Long id) {
        return "redirect:/ciclos/" + id;
    }

    private String volverA(Long id, String volver) {
        return VOLVER_AL_DETALLE.equals(volver) ? alDetalle(id) : "redirect:/ciclos";
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
