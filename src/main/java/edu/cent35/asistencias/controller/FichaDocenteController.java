package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.config.CustomUserDetails;
import edu.cent35.asistencias.model.ConsentimientoBiometrico;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.service.ConsentimientoBiometricoService;
import edu.cent35.asistencias.service.ConstanciaArcoService;
import edu.cent35.asistencias.service.DocenteService;
import edu.cent35.asistencias.service.MiInstitucionService;
import edu.cent35.asistencias.service.ModeloFacialService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Ficha completa del docente: todo lo que el sistema sabe de esa persona.
 *
 * <p>Las cuatro operaciones ya existían, pero repartidas: los datos personales en el
 * formulario del docente, la oposición dentro del consentimiento, la cancelación escondida
 * en la pantalla de registro del rostro. Un derecho que hay que ir a buscar a tres lugares
 * distintos es un derecho que en la práctica no se ejerce, y ante un pedido formal la
 * institución tiene que poder responderlo sin recorrer el menú entero.
 *
 * <p>Esta pantalla no agrega operaciones nuevas: reúne las que hay, muestra en un solo lugar
 * todo lo que el sistema sabe de la persona y emite la constancia.
 */
@Controller
@RequestMapping("/docentes/{docenteId}/ficha")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class FichaDocenteController {

    private final DocenteService docenteService;
    private final ConsentimientoBiometricoService consentimientoService;
    private final ModeloFacialService modeloFacialService;
    private final ConstanciaArcoService constanciaService;
    private final MiInstitucionService miInstitucionService;

    // Todo lo que el sistema sabe del docente, con los cuatro derechos a mano.
    @GetMapping
    public String pantalla(@PathVariable Long docenteId, Model model) {
        Docente d = docenteService.buscarPorId(docenteId);

        model.addAttribute("docente", d);
        model.addAttribute("estadoConsentimiento", consentimientoService.estadoActual(docenteId));
        model.addAttribute("historial", consentimientoService.historialDe(docenteId));
        model.addAttribute("tieneModelo", modeloFacialService.tieneModeloActivo(docenteId));
        model.addAttribute("tieneModelos", modeloFacialService.tieneModelos(docenteId));
        return "docente/ficha";
    }

    // Constancia en PDF de lo que la institución trata sobre esta persona.
    @GetMapping("/constancia")
    public void constancia(@PathVariable Long docenteId,
                           @AuthenticationPrincipal CustomUserDetails principal,
                           HttpServletResponse response) throws IOException {
        Docente d = docenteService.buscarPorId(docenteId);
        List<ConsentimientoBiometrico> historial = consentimientoService.historialDe(docenteId);
        boolean tieneModelo = modeloFacialService.tieneModeloActivo(docenteId);

        String institucion = miInstitucionService.getMiInstitucion().getNombre();
        String operador = principal != null ? principal.getUsername() : "sistema";

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
            "attachment; filename=\"" + constanciaService.nombreArchivo(d) + "\"");

        try (OutputStream out = response.getOutputStream()) {
            constanciaService.escribir(out, d, institucion, operador, historial, tieneModelo);
            out.flush();
        }
    }
}
