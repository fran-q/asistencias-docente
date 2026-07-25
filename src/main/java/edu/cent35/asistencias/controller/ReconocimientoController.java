package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.dto.*;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.service.DeteccionRostroService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Base64;

/**
 * Endpoints auxiliares de reconocimiento facial.
 * <p>
 * En Sprint 4 esta clase también tenía la pantalla de prueba y el endpoint
 * de identificación. En Sprint 5 ambos quedaron incorporados al
 * {@link PaseAsistenciaController} (que adicionalmente marca asistencia
 * cuando reconoce). Acá sobrevive sólo {@code /detectar}, que lo usa el
 * JS de registro facial para dibujar el recuadro amarillo en vivo sobre
 * el rostro durante la captura.
 */
@Controller
@RequestMapping("/reconocimiento")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class ReconocimientoController {

    private final DeteccionRostroService deteccionRostroService;

    /**
     * Detecta rostros en una imagen y devuelve cuántos hay y el bounding box
     * del más grande. <i>No identifica</i> de quién es: solo "hay cara acá".
     * Lo usa {@code registro-facial.js} para mostrar el recuadro mientras
     * se graba el rostro del docente.
     */
    @PostMapping("/detectar")
    @ResponseBody
    public DeteccionRostroDto detectar(@RequestBody CapturaImagenDto captura) {
        byte[] imagen;
        try {
            imagen = decodificarDataUrl(captura.imagen());
        } catch (IllegalArgumentException ex) {
            log.warn("Captura inválida en /reconocimiento/detectar: {}", ex.getMessage());
            return DeteccionRostroDto.sinRostro(
                "La imagen capturada no es válida. Intentá de nuevo.");
        }
        return deteccionRostroService.detectar(imagen);
    }

    // Convierte un data URL base64 en los bytes de la imagen.
    private static byte[] decodificarDataUrl(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            throw new IllegalArgumentException("data URL vacío");
        }
        int coma = dataUrl.indexOf(',');
        String base64 = (coma >= 0) ? dataUrl.substring(coma + 1) : dataUrl;
        return Base64.getDecoder().decode(base64);
    }
}
