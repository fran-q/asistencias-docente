package edu.cent35.asistencias.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.Duration;

/**
 * Cuánto dura la sesión, para que la pantalla pueda avisar antes de que se cierre.
 *
 * <p>Sale de la misma propiedad que usa el servidor y no se repite en la plantilla: si alguien
 * cambia {@code server.servlet.session.timeout}, el aviso de {@code sesion.js} sigue cayendo
 * poco antes del cierre real y no poco antes de un número viejo.
 */
@ControllerAdvice
public class SesionAdvice {

    private final long sesionSegundos;

    public SesionAdvice(@Value("${server.servlet.session.timeout:30m}") Duration duracion) {
        this.sesionSegundos = duracion.toSeconds();
    }

    @ModelAttribute("sesionSegundos")
    public long sesionSegundos() {
        return sesionSegundos;
    }
}
