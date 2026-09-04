package edu.cent35.asistencias.controller;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import java.util.Objects;
import java.util.Map;
import java.util.LinkedHashMap;
import edu.cent35.asistencias.service.ConfirmacionRequeridaException;
import edu.cent35.asistencias.dto.*;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.service.DocenteService;
import edu.cent35.asistencias.service.ConsentimientoBiometricoService;
import edu.cent35.asistencias.service.ModeloFacialService;
import edu.cent35.asistencias.model.EstadoConsentimiento;
import edu.cent35.asistencias.model.Docente;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/**
 * Pantallas de alta, edición, baja y reactivación de docentes (RF-07), para los roles
 * INSTITUCION y ADMIN. El listado además muestra el estado del consentimiento biométrico
 * de cada docente, que es lo que habilita registrarle el modelo facial.
 */
@Controller
@RequestMapping("/docentes")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class DocenteController {

    private final DocenteService service;
    private final ConsentimientoBiometricoService consentimientoService;
    private final ModeloFacialService modeloFacialService;

    // Muestra el listado de los docentes.
    @GetMapping
    public String listar(Model model) {
        var estados = consentimientoService.estadosPorDocenteEnTenant();
        List<DocenteListItemDto> items = service.listar().stream()
            .map(d -> DocenteListItemDto.from(
                d,
                estados.getOrDefault(d.getId(), EstadoConsentimiento.NUNCA_OTORGADO),
                // Solo tiene sentido preguntarlo para los que siguen activos: a un docente
                // ya dado de baja no se le ofrece la baja.
                Boolean.TRUE.equals(d.getActivo()) ? service.motivoQueImpideLaBaja(d.getId()) : null))
            .toList();
        model.addAttribute("docentes", items);
        // Tope del selector de fecha de baja: se resuelve en el servidor porque la fecha de
        // la maquina del cliente puede estar corrida.
        model.addAttribute("hoy", LocalDate.now());
        return "docente/list";
    }

    // Abre el formulario de alta vacío.
    @GetMapping("/nuevo")
    public String formNuevo(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", DocenteFormDto.builder().build());
        }
        model.addAttribute("modo", "crear");
        return "docente/form";
    }

    // Procesa el alta; si la validación falla vuelve al formulario con lo ya cargado.
    @PostMapping("/nuevo")
    public String crear(@Valid @ModelAttribute("form") DocenteFormDto form,
                        BindingResult binding,
                        Model model,
                        @AuthenticationPrincipal UsuarioAutenticado actual,
                        RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            model.addAttribute("modo", "crear");
            return "docente/form";
        }
        try {
            Docente d = service.crear(form.getDni(), form.getLegajo(), form.getNombre(),
                form.getApellido(), form.getEmail(), form.getTelefono(), form.isConfirmado(),
                actual.getUsuarioId());
            redirect.addFlashAttribute("flashMensaje",
                "Docente '" + d.getNombreCompleto() + "' creado correctamente.");
            return "redirect:/docentes";
        } catch (ConfirmacionRequeridaException ex) {
            // El DNI ya pertenece a alguien. No se escribió nada: se muestra a quién alcanza y
            // el mismo formulario vuelve por su cuenta si alguien confirma.
            prepararConfirmacion(model, ex, form, "/docentes/nuevo", "/docentes/nuevo");
            return "identidad/confirmar";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            model.addAttribute("modo", "crear");
            return "docente/form";
        }
    }

    /**
     * Deja en el modelo lo que necesita la pantalla de confirmación.
     *
     * <p>El formulario entero viaja como campos ocultos en vez de guardarse en la sesión: si
     * alguien abandona la pantalla, no queda ningún alta a medias esperando, y el segundo intento
     * llega por el mismo endpoint que el primero, con la confirmación puesta.
     */
    private void prepararConfirmacion(Model model, ConfirmacionRequeridaException ex,
                                      DocenteFormDto form, String accion, String volverA) {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("dni", form.getDni());
        campos.put("legajo", form.getLegajo());
        campos.put("nombre", form.getNombre());
        campos.put("apellido", form.getApellido());
        campos.put("email", form.getEmail());
        campos.put("telefono", form.getTelefono());
        campos.values().removeIf(Objects::isNull);

        model.addAttribute("impacto", ex.getImpacto());
        model.addAttribute("camposOcultos", campos);
        model.addAttribute("accion", accion);
        model.addAttribute("volverA", volverA);
    }

    // Abre el formulario de edición con los datos actuales.
    @GetMapping("/{id}/editar")
    public String formEditar(@PathVariable Long id, Model model) {
        Docente d = service.buscarPorId(id);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", DocenteFormDto.from(d));
        }
        agregarDatosEdicion(id, model);
        return "docente/form";
    }

    // Procesa la edición; si la validación falla vuelve al formulario.
    @PostMapping("/{id}/editar")
    public String actualizar(@PathVariable Long id,
                             @Valid @ModelAttribute("form") DocenteFormDto form,
                             BindingResult binding,
                             Model model,
                             @AuthenticationPrincipal UsuarioAutenticado actual,
                             RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            agregarDatosEdicion(id, model);
            return "docente/form";
        }
        try {
            service.actualizar(id, form.getDni(), form.getLegajo(), form.getNombre(),
                form.getApellido(), form.getEmail(), form.getTelefono(), form.isConfirmado(),
                actual.getUsuarioId());
            redirect.addFlashAttribute("flashMensaje", "Docente actualizado correctamente.");
            return "redirect:/docentes";
        } catch (ConfirmacionRequeridaException ex) {
            // Esta persona tambien tiene cuenta: el cambio de nombre se ve en pantallas que quien
            // edita no esta mirando, asi que se avisa antes de escribir.
            prepararConfirmacion(model, ex, form,
                "/docentes/" + id + "/editar", "/docentes/" + id + "/editar");
            return "identidad/confirmar";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            agregarDatosEdicion(id, model);
            return "docente/form";
        }
    }

    // Carga lo que necesita la pantalla de edición: datos, consentimiento y modelo facial.
    private void agregarDatosEdicion(Long id, Model model) {
        model.addAttribute("docente", service.buscarPorId(id));
        model.addAttribute("modo", "editar");
        // Consentimiento biometrico (Sprint 3 Fase D)
        model.addAttribute("estadoConsentimiento", consentimientoService.estadoActual(id));
        consentimientoService.vigenteDe(id)
            .ifPresent(c -> model.addAttribute("consentimientoVigente", c));
        // Modelo facial (Sprint 4 Fase C)
        model.addAttribute("tieneModeloFacial", modeloFacialService.tieneModeloActivo(id));
        modeloFacialService.modeloActivoDe(id)
            .ifPresent(m -> model.addAttribute("modeloFacial", m));
        // Supresion ARCO (RNF-14): tambien contempla modelos historicos
        model.addAttribute("tieneModelosBiometricos", modeloFacialService.tieneModelos(id));
        // Por que no se puede dar de baja, o null si se puede. La pantalla lo usa para
        // avisar de una vez en lugar de abrir un cuadro de confirmacion que va a fallar.
        model.addAttribute("motivoQueImpideLaBaja", service.motivoQueImpideLaBaja(id));
        // Tope del selector de fecha de baja, resuelto en el servidor: el reloj del
        // cliente puede estar corrido.
        model.addAttribute("hoy", LocalDate.now());
    }

    // Da de baja el docente en la fecha indicada y vuelve al listado con el resultado.
    // Si el campo no llega, se asume hoy: el formulario lo manda, pero un POST directo no.
    @PostMapping("/{id}/baja")
    public String darDeBaja(@PathVariable Long id,
                            @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaBaja,
                            @AuthenticationPrincipal UsuarioAutenticado actual,
                            RedirectAttributes redirect) {
        try {
            service.darDeBaja(id, fechaBaja != null ? fechaBaja : LocalDate.now(),
                actual.getUsuarioId());
            redirect.addFlashAttribute("flashMensaje", "Docente dado de baja.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        }
        return "redirect:/docentes";
    }

    // Reactiva el docente y vuelve al listado con el resultado.
    @PostMapping("/{id}/alta")
    public String darDeAlta(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.darDeAlta(id);
            redirect.addFlashAttribute("flashMensaje", "Docente reactivado.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("flashError", ex.getMessage());
        }
        return "redirect:/docentes";
    }

    // Si el id no existe o es de otra institución, avisa y vuelve al listado.
    @ExceptionHandler(EntityNotFoundException.class)
    public String handleNotFound(EntityNotFoundException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", "El docente solicitado no existe.");
        return "redirect:/docentes";
    }
}
