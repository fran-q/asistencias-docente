package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.dto.*;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.service.PaseAsistenciaService;
import jakarta.servlet.http.HttpSession;
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
 * Pase de asistencia por reconocimiento facial (RF-17 a RF-19): la pantalla con la cámara y
 * el endpoint que consume su loop continuo. Cada frame que llega se identifica y, si hay
 * clase en curso, se marca la asistencia en el acto.
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

    // Nombre del atributo de sesion donde vive la racha de confirmacion del pase.
    private static final String RACHA = "paseConfirmacionIdentidad";

    // Recibe un frame del loop, identifica al docente e intenta marcar; responde JSON.
    @PostMapping("/marcar")
    @ResponseBody
    public PaseAsistenciaResultadoDto marcar(@RequestBody CapturaImagenDto captura,
                                             HttpSession sesion) {
        byte[] imagen;
        try {
            imagen = decodificarDataUrl(captura.imagen());
        } catch (IllegalArgumentException ex) {
            log.warn("Captura inválida en /asistencia/pase/marcar: {}", ex.getMessage());
            return PaseAsistenciaResultadoDto.sinRostro();
        }
        return paseAsistenciaService.pasar(imagen, rachaDe(sesion));
    }

    // La racha vive en la sesion, no en el navegador ni en un mapa del servidor: asi no se
    // puede saltear modificando el JavaScript, y se limpia sola cuando la sesion termina.
    private ConfirmacionIdentidad rachaDe(HttpSession sesion) {
        ConfirmacionIdentidad racha = (ConfirmacionIdentidad) sesion.getAttribute(RACHA);
        if (racha == null) {
            racha = new ConfirmacionIdentidad();
            sesion.setAttribute(RACHA, racha);
        }
        return racha;
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
