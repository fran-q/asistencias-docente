package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.dto.CapturaImagenDto;
import edu.cent35.asistencias.dto.ConfirmacionIdentidad;
import edu.cent35.asistencias.dto.KioscoResultadoDto;
import edu.cent35.asistencias.interceptor.PuestoCapturaInterceptor;
import edu.cent35.asistencias.model.PuestoCaptura;
import edu.cent35.asistencias.service.FrenoDeKioscoService;
import edu.cent35.asistencias.service.PaseAsistenciaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Base64;

/**
 * Toma de asistencia en un equipo que opera <b>sin sesión abierta</b>, para los turnos en que
 * no hay personal administrativo presente (RF-84, ADR-0019).
 * <p>
 * <b>Que no exija login no significa que esté abierto.</b> Antes de llegar acá, la petición
 * pasó por {@code KioscoTenantInterceptor} —que solo publica la institución si la credencial
 * del equipo resuelve— y por {@code PuestoCapturaInterceptor}, que la rechaza si ese equipo no
 * está autorizado. La autenticación se reemplaza por la credencial del equipo, no se elimina.
 * <p>
 * Este controlador <b>no</b> registra rostros: esa ruta sigue exigiendo sesión (RF-86). Marcar
 * sin supervisión registra un hecho; enrolar sin supervisión crea una identidad.
 */
@Controller
@RequestMapping("/kiosco")
@RequiredArgsConstructor
@Slf4j
public class KioscoController {

    private final PaseAsistenciaService paseAsistenciaService;
    private final FrenoDeKioscoService freno;

    // Nombre del atributo de sesion donde vive la racha de confirmacion.
    private static final String RACHA = "kioscoConfirmacionIdentidad";

    /**
     * La pantalla desatendida: cámara, resultado y nada más.
     *
     * <p>Su plantilla <b>no</b> extiende {@code layout/base}: ese layout trae la barra lateral
     * con accesos a docentes, reportes y configuración, y esta pantalla queda encendida sin
     * nadie vigilándola. Un kiosco no puede ser una puerta al resto del sistema.
     */
    @GetMapping
    public String pantalla() {
        return "asistencia/kiosco";
    }

    /**
     * Recibe un cuadro del loop, identifica y registra la entrada o la salida.
     *
     * <p>El tope de peticiones va <b>antes</b> de tocar la imagen: lo que hay que evitar es el
     * trabajo de reconocimiento, que es lo caro. Frenar después de correr OpenCV no protegería
     * de nada.
     */
    @PostMapping("/marcar")
    @ResponseBody
    public ResponseEntity<?> marcar(@RequestBody CapturaImagenDto captura,
                                    HttpSession sesion,
                                    HttpServletRequest request) {
        PuestoCaptura puesto = puestoDe(request);

        if (!freno.permitir(puesto == null ? null : puesto.getId())) {
            // 429 y no 403: el equipo está autorizado, lo que pasa es que pidió de más. El
            // JavaScript tiene que poder distinguir "esperá" de "no tenés permiso".
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(KioscoResultadoDto.noReconocido(
                    "Demasiadas solicitudes seguidas. Esperá un momento.", null, null, null, null));
        }

        byte[] imagen;
        try {
            imagen = decodificarDataUrl(captura.imagen());
        } catch (IllegalArgumentException ex) {
            log.warn("Captura inválida en /kiosco/marcar: {}", ex.getMessage());
            return ResponseEntity.ok(KioscoResultadoDto.sinRostro());
        }

        return ResponseEntity.ok(
            paseAsistenciaService.pasarEnKiosco(imagen, rachaDe(sesion), puesto));
    }

    /**
     * La racha de confirmación, en la sesión HTTP.
     *
     * <p>Sin login igual hay {@code HttpSession}: la crea el contenedor y sirve para no perder
     * el avance entre cuadro y cuadro. No es una sesión <i>autenticada</i> —no hay usuario
     * detrás— y por eso no alcanza para nada más que esto.
     */
    private ConfirmacionIdentidad rachaDe(HttpSession sesion) {
        ConfirmacionIdentidad racha = (ConfirmacionIdentidad) sesion.getAttribute(RACHA);
        if (racha == null) {
            racha = new ConfirmacionIdentidad();
            sesion.setAttribute(RACHA, racha);
        }
        return racha;
    }

    // El equipo que ya resolvio y valido PuestoCapturaInterceptor (RF-89).
    private static PuestoCaptura puestoDe(HttpServletRequest request) {
        Object p = request.getAttribute(PuestoCapturaInterceptor.ATRIBUTO_PUESTO);
        return (p instanceof PuestoCaptura puesto) ? puesto : null;
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
