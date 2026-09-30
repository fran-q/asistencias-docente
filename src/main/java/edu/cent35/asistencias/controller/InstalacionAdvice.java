package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.service.InstalacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Si esto es una instalación autónoma, para que las pantallas puedan ofrecer lo que corresponde
 * a esa modalidad y callar lo que ahí no funciona (ADR-0022).
 *
 * <p>Hoy lo usa el login, que en una instalación sin correo tiene que mandar "¿La olvidaste?" a
 * la recuperación por clave y no a la que pide un código que no va a llegar nunca, y Mi
 * institución, que es desde donde se genera una clave nueva.
 *
 * <p>Sale de la misma propiedad que decide el comportamiento del servidor, igual que
 * {@link SesionAdvice} con la duración de la sesión: una pantalla que decide esto por su cuenta
 * termina ofreciendo un camino que el backend no atiende.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class InstalacionAdvice {

    private final InstalacionService instalacion;

    @ModelAttribute("instalacionAutonoma")
    public boolean instalacionAutonoma() {
        return instalacion.autonoma();
    }
}
