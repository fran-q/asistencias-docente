package edu.cent35.asistencias.service;

import edu.cent35.asistencias.dto.ConfirmacionIdentidad;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Exige que el mismo docente se sostenga varios segundos frente a la cámara antes de marcar
 * su asistencia. Es la respuesta a que un cambio de iluminación haga oscilar el
 * reconocimiento entre dos personas parecidas: una marca equivocada queda en el registro
 * administrativo, y sacarla después es más caro que esperar tres segundos.
 */
@Service
@Slf4j
public class VentanaConfirmacionService {

    @Value("${app.biometria.confirmacion.ventana-ms}")
    private long ventanaMs;

    @Value("${app.biometria.confirmacion.lecturas-minimas}")
    private int lecturasMinimas;

    @Value("${app.biometria.confirmacion.hueco-maximo-ms}")
    private long huecoMaximoMs;

    /**
     * Cómo va la confirmación después de sumar una lectura.
     *
     * @param confirmado true si ya se puede marcar
     * @param progreso   milisegundos sostenidos hasta ahora, para mostrar el avance
     */
    public record Estado(boolean confirmado, long progreso, long objetivo) {}

    /**
     * Registra que en este instante se identificó a {@code docenteId} y responde si la
     * identidad ya está confirmada.
     *
     * <p>La racha se corta en dos casos: si aparece otro docente —que es exactamente el
     * síntoma que se quiere frenar— o si pasa demasiado tiempo sin lecturas, porque eso
     * significa que la persona se fue del cuadro y volvió.
     */
    public Estado registrar(ConfirmacionIdentidad confirmacion, Long docenteId, long ahora) {
        boolean cambioDePersona = !docenteId.equals(confirmacion.getDocente());
        boolean huboUnHueco = confirmacion.getDocente() != null
            && (ahora - confirmacion.ultimoInstante()) > huecoMaximoMs;

        if (cambioDePersona || huboUnHueco) {
            if (cambioDePersona && confirmacion.getDocente() != null) {
                log.debug("Confirmacion reiniciada: la camara paso de docente {} a docente {} "
                          + "antes de completar la ventana",
                          confirmacion.getDocente(), docenteId);
            }
            confirmacion.reiniciarCon(docenteId, ahora);
        } else {
            confirmacion.sumar(ahora);
        }

        long sostenido = confirmacion.getDuracionMs();
        boolean confirmado = confirmacion.getLecturas() >= lecturasMinimas
                          && sostenido >= ventanaMs;

        return new Estado(confirmado, Math.min(sostenido, ventanaMs), ventanaMs);
    }

    // Corta la racha. Se llama al marcar y cuando no hay ningun rostro en el cuadro.
    public void cortar(ConfirmacionIdentidad confirmacion) {
        confirmacion.limpiar();
    }
}
