package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * La constancia del derecho de acceso (Ley 25.326, Resolución AAIP 255/2022): un PDF con todo
 * lo que la institución trata sobre esa persona, para entregarle ante un pedido formal.
 *
 * <p>Vivía en {@code /docentes/{id}/ficha/constancia}, colgando de una pantalla que reunía los
 * cuatro derechos ARCO. Esa pantalla desapareció: repetía los datos personales que la edición
 * del docente ya muestra --y deja corregir-- y enlazaba de vuelta para allá para ejercer los
 * otros tres derechos. Dos pantallas para la misma persona obligaban a adivinar en cuál estaba
 * lo que uno venía a hacer. La constancia, que era lo único propio, quedó acá.
 */
@Controller
@RequestMapping("/docentes/{docenteId}")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class ConstanciaDocenteController {

    private final DocenteService docenteService;
    private final ConsentimientoBiometricoService consentimientoService;
    private final ModeloFacialService modeloFacialService;
    private final ConstanciaArcoService constanciaService;
    private final MiInstitucionService miInstitucionService;

    // Constancia en PDF de lo que la institución trata sobre esta persona.
    @GetMapping("/constancia")
    public void constancia(@PathVariable Long docenteId,
                           @AuthenticationPrincipal UsuarioAutenticado principal,
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
