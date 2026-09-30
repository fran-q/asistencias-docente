package edu.cent35.asistencias.controller;
import edu.cent35.asistencias.dto.*;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.service.ClaveRecuperacionService;
import edu.cent35.asistencias.service.InstalacionService;
import edu.cent35.asistencias.service.MiInstitucionService;
import edu.cent35.asistencias.service.RespaldoService;
import edu.cent35.asistencias.model.Institucion;
import jakarta.validation.Valid;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Vista y edición de los datos de la propia institución, restringida al rol INSTITUCION.
 * Nunca recibe un id por parámetro: la institución sale del TenantContext, así que no hay
 * forma de pedir la de otro.
 *
 * <p>Primero se lee y después se edita, como la ficha del docente: la pantalla abría directo
 * en el formulario, y quien entraba a consultar un dato quedaba parado sobre campos que
 * cambian la institución entera.
 */
@Controller
@RequestMapping("/mi-institucion")
@PreAuthorize("hasRole('INSTITUCION')")
@RequiredArgsConstructor
@Slf4j
public class MiInstitucionController {

    private static final String VIEW = "institucion/mi-institucion";
    private static final String VIEW_EDITAR = "institucion/mi-institucion-editar";
    private static final String FORM_ATTR = "form";
    private static final String ENTIDAD_ATTR = "institucion";

    private final MiInstitucionService service;
    private final InstalacionService instalacion;
    private final ClaveRecuperacionService claveRecuperacion;
    private final RespaldoService respaldoService;

    // Muestra los datos de la institución del usuario logueado, de solo lectura.
    @GetMapping
    public String view(Model model) {
        model.addAttribute(ENTIDAD_ATTR, service.getMiInstitucion());
        return VIEW;
    }

    // El formulario, precargado con los datos vigentes.
    @GetMapping("/editar")
    public String editar(Model model) {
        Institucion inst = service.getMiInstitucion();
        model.addAttribute(ENTIDAD_ATTR, inst);
        if (!model.containsAttribute(FORM_ATTR)) {
            model.addAttribute(FORM_ATTR, InstitucionFormDto.from(inst));
        }
        return VIEW_EDITAR;
    }

    // Guarda los cambios; si el nombre o el CUIT ya son de otra institución, lo informa.
    @PostMapping("/editar")
    public String update(@Valid InstitucionFormDto form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            model.addAttribute(ENTIDAD_ATTR, service.getMiInstitucion());
            model.addAttribute(FORM_ATTR, form);
            return VIEW_EDITAR;
        }

        try {
            service.actualizar(form);
        } catch (DataIntegrityViolationException ex) {
            // Se atrapa aca en vez de dejarlo subir al manejador global para no perder lo que
            // la persona ya habia tipeado: el formulario se vuelve a dibujar con sus valores.
            log.warn("Conflicto al actualizar mi institucion: {}", ex.getMostSpecificCause().getMessage());
            binding.reject("error.global", ManejadorDeColisiones.traducir(ex));
            model.addAttribute(ENTIDAD_ATTR, service.getMiInstitucion());
            model.addAttribute(FORM_ATTR, form);
            return VIEW_EDITAR;
        }

        // Vuelve a la ficha: ahí se ve lo que quedó guardado.
        redirect.addFlashAttribute("flashMensaje", "Datos de la institución actualizados correctamente.");
        return "redirect:/mi-institucion";
    }

    /**
     * Genera una clave de recuperación nueva y la muestra una sola vez.
     *
     * <p>Existe porque la clave se entrega en la configuración inicial y de ahí en más solo
     * queda su hash: sin esta pantalla, perder el papel sería perder la instalación. Al generar
     * una nueva la anterior deja de servir en el mismo acto, que es lo que hace que sirva
     * también cuando la clave no se perdió sino que la vio quien no correspondía.
     *
     * <p>Solo en una instalación autónoma: donde hay correo, la recuperación va por código y
     * esta clave no existe.
     */
    @PostMapping("/clave-recuperacion")
    public String regenerarClave(RedirectAttributes flash) {
        if (!instalacion.autonoma()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Institucion mia = service.getMiInstitucion();
        String clave = claveRecuperacion.generar(mia.getId());
        flash.addFlashAttribute("claveRecuperacion", clave);
        flash.addFlashAttribute("flashMensaje",
            "Clave nueva generada. La anterior dejó de servir.");
        return "redirect:/mi-institucion";
    }


    /**
     * Baja una copia de la base, para que respaldar no dependa de abrir una terminal (ADR-0022).
     *
     * <p>Devuelve {@code Object} porque tiene dos finales legítimos: el archivo, o la vuelta a
     * esta misma pantalla con el motivo por el cual no se pudo. Un respaldo que falla tiene que
     * decir qué pasó —la herramienta no está, la base no responde— y no dejar a alguien mirando
     * una página de error genérica creyendo que ya tiene su copia.
     *
     * <p>Solo en una instalación autónoma: el volcado se lleva la base entera, y donde hay
     * varias instituciones eso sería el dato de todas en manos de una.
     */
    @GetMapping("/respaldo")
    public Object respaldo(RedirectAttributes flash) throws IOException {
        if (!instalacion.autonoma()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        Path archivo;
        try {
            archivo = respaldoService.generar();
        } catch (IllegalStateException ex) {
            log.error("No se pudo generar el respaldo", ex);
            flash.addFlashAttribute("flashError", ex.getMessage());
            return "redirect:/mi-institucion";
        }

        Resource cuerpo = new InputStreamResource(flujoQueSeBorra(archivo));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + respaldoService.nombreSugerido() + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(Files.size(archivo))
            .body(cuerpo);
    }

    // El temporal se borra cuando termina de enviarse, pase lo que pase: si se borrara antes de
    // devolver la respuesta no habria nada que mandar, y si no se borrara nunca, cada respaldo
    // dejaria una copia entera de la base en el disco de la maquina.
    private InputStream flujoQueSeBorra(Path archivo) throws IOException {
        return new FilterInputStream(Files.newInputStream(archivo)) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    Files.deleteIfExists(archivo);
                }
            }
        };
    }

}
