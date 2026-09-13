package edu.cent35.asistencias.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Renovar la sesión sin recargar la pantalla.
 *
 * <p>Lo llama {@code sesion.js} en dos casos: cuando la persona está trabajando en una
 * pantalla sin que viaje nada al servidor --escribiendo un formulario largo, por ejemplo--, y
 * cuando aprieta "Seguir conectado" en el aviso de cierre. No hace nada a propósito: que el
 * pedido llegue con la sesión es lo que la renueva.
 *
 * <p>Sin sesión contesta 401 y no la redirección al login: está entre las rutas JSON de
 * {@code SecurityConfig}, porque del otro lado hay un {@code fetch} que tiene que poder
 * distinguir "renovada" de "ya estaba cerrada", y una redirección no se distingue.
 */
@Controller
public class SesionController {

    @GetMapping("/sesion/mantener")
    public ResponseEntity<Void> mantener() {
        return ResponseEntity.noContent().build();
    }
}
