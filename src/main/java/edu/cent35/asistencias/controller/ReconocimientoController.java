package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.dto.*;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.service.DeteccionRostroService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Base64;

/**
 * Reconocimiento facial (RF-08, RF-09). Roles INSTITUCION y ADMIN.
 * <p>
 * En Sprint 4 Fase B sólo expone la detección de rostro: una pantalla de
 * prueba que enciende la webcam y verifica si el sistema "ve" una cara.
 * El registro del modelo facial (Fase C) y la identificación (Fase D) se
 * agregan después.
 */
@Controller
@RequestMapping("/reconocimiento")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class ReconocimientoController {

    private final DeteccionRostroService deteccionRostroService;

    /** Pantalla de prueba: webcam + botón para detectar rostro. */
    @GetMapping("/prueba")
    public String pantallaPrueba() {
        return "reconocimiento/prueba";
    }

    /**
     * Recibe un frame capturado desde el navegador y responde si se
     * detectó un rostro. Respuesta JSON.
     */
    @PostMapping("/detectar")
    @ResponseBody
    public DeteccionRostroDto detectar(@RequestBody CapturaImagenDto captura) {
        byte[] imagen;
        try {
            imagen = decodificarDataUrl(captura.imagen());
        } catch (IllegalArgumentException ex) {
            log.warn("Captura de imagen inválida en /reconocimiento/detectar: {}", ex.getMessage());
            return DeteccionRostroDto.sinRostro(
                "La imagen capturada no es válida. Intentá de nuevo.");
        }
        return deteccionRostroService.detectar(imagen);
    }

    /**
     * Convierte un data URL base64 ({@code data:image/jpeg;base64,XXXX})
     * en el arreglo de bytes de la imagen.
     *
     * @throws IllegalArgumentException si el formato o el base64 son inválidos
     */
    private byte[] decodificarDataUrl(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            throw new IllegalArgumentException("data URL vacío");
        }
        int coma = dataUrl.indexOf(',');
        String base64 = (coma >= 0) ? dataUrl.substring(coma + 1) : dataUrl;
        return Base64.getDecoder().decode(base64);
    }
}
