package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.dto.*;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.service.PaseAsistenciaService;
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
 * Pase de asistencia automático por reconocimiento facial (RF-17 a RF-19).
 * Pantalla y endpoint del loop continuo.
 * <p>
 * Reemplaza a la antigua pantalla {@code /reconocimiento/prueba}: en lugar
 * de sólo identificar, marca la asistencia cuando reconoce.
 */
@Controller
@RequestMapping("/asistencia/pase")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class PaseAsistenciaController {

    private final PaseAsistenciaService paseAsistenciaService;

    // Pantalla del pase: webcam + loop de reconocimiento + marca automática.
    @GetMapping
    public String pantalla() {
        return "asistencia/pase";
    }

    /**
     * Recibe un frame, identifica al docente, intenta marcar asistencia y
     * responde con el resultado combinado. JSON.
     */
    @PostMapping("/marcar")
    @ResponseBody
    public PaseAsistenciaResultadoDto marcar(@RequestBody CapturaImagenDto captura) {
        byte[] imagen;
        try {
            imagen = decodificarDataUrl(captura.imagen());
        } catch (IllegalArgumentException ex) {
            log.warn("Captura inválida en /asistencia/pase/marcar: {}", ex.getMessage());
            return PaseAsistenciaResultadoDto.sinRostro();
        }
        return paseAsistenciaService.pasar(imagen);
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
