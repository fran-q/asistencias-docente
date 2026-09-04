package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.service.SeccionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Las pantallas intermedias de cada grupo del menú.
 *
 * <p>Existen por dos razones. La primera es que la miga de pan las nombraba y no llevaban a
 * ningún lado: "Inicio / Asistencias / Pase de asistencia" ofrecía volver a "Asistencias" y
 * esa página no existía. La segunda es que un grupo del menú es un lugar del sistema, y
 * tener que abrir el desplegable para saber qué hay adentro obliga a recordarlo.
 *
 * <p>Cuando el usuario tiene una sola pantalla en el grupo, el menú lleva directo a ella y
 * esta intermedia no se usa: ver una lista de una sola opción no ayuda a decidir nada.
 */
@Controller
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
public class SeccionController {

    private final SeccionService seccionService;

    // Carreras, materias, comisiones, horarios y grilla.
    @GetMapping("/academico")
    public String academico(Authentication auth, Model model) {
        return armar(SeccionService.Grupo.ACADEMICO, auth, model);
    }

    // Pase, listado del día y reportes.
    @GetMapping("/asistencia")
    public String asistencia(Authentication auth, Model model) {
        return armar(SeccionService.Grupo.ASISTENCIA, auth, model);
    }

    // Docentes y, para el rol INSTITUCION, usuarios y datos de la institución.
    @GetMapping("/personal")
    public String personal(Authentication auth, Model model) {
        return armar(SeccionService.Grupo.PERSONAL, auth, model);
    }

    private String armar(SeccionService.Grupo grupo, Authentication auth, Model model) {
        // Si en este grupo hay una sola pantalla, se redirige en vez de mostrar la lista.
        // Pasa con Personal para el rol ADMIN, que solo ve Docentes. Tambien cubre el caso
        // de que alguien llegue a /personal escribiendo la URL a mano.
        String destino = seccionService.destinoDelGrupo(grupo, auth);
        if (!destino.equals("/" + grupo.getRuta())) {
            return "redirect:" + destino;
        }
        model.addAttribute("grupo", grupo);
        model.addAttribute("pantallas", seccionService.pantallasDe(grupo, auth));
        return "seccion";
    }
}
