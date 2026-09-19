package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.config.SecurityConfig;
import edu.cent35.asistencias.seguridad.SesionesActivasService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

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
@RequiredArgsConstructor
public class SesionController {

    private final SesionesActivasService sesionesActivas;

    @GetMapping("/sesion/mantener")
    public ResponseEntity<Void> mantener() {
        return ResponseEntity.noContent().build();
    }

    /**
     * El paso que ve quien entra con una cuenta que ya está usándose en otro equipo.
     *
     * <p>Muestra qué se va a cerrar antes de cerrarlo. Importa porque la cuenta de la
     * institución es una sola y la comparten: la sesión de al lado puede ser el equipo que está
     * tomando asistencia, y desplazarlo sin avisar apaga el pase en medio de una clase.
     *
     * <p>Si la otra sesión se cerró mientras tanto --se fueron, o vencio-- no hay nada que
     * decidir: se quita la marca y se sigue de largo, en vez de preguntar por algo que ya no
     * existe.
     */
    @GetMapping("/sesion/otra-abierta")
    public String otraAbierta(Authentication autenticacion, HttpServletRequest request,
                              Model model) {
        HttpSession sesion = request.getSession();
        List<SessionInformation> otras =
            sesionesActivas.otrasDe(autenticacion.getName(), sesion.getId());

        if (otras.isEmpty()) {
            sesion.removeAttribute(SecurityConfig.SESION_POR_CONFIRMAR);
            return "redirect:/";
        }

        model.addAttribute("cuantas", otras.size());
        // El registro de sesiones guarda la fecha como java.util.Date; la plantilla usa los
        // helpers de fecha-hora modernos, que no la entienden.
        model.addAttribute("ultimoUso", LocalDateTime.ofInstant(
            otras.get(0).getLastRequest().toInstant(), ZoneId.systemDefault()));
        model.addAttribute("usuario", autenticacion.getName());
        return "sesion/otra-abierta";
    }

    /**
     * Continuar acá: cierra las demás sesiones de la cuenta y libera esta.
     *
     * <p>Las otras no se enteran hasta su próximo pedido, que es cuando
     * {@code ConcurrentSessionFilter} las saca y las manda al login con el motivo. Ese pedido
     * puede tardar --una pantalla abierta sin tocar no pide nada--, y está bien: lo que importa
     * es que no pueda seguir operando, y para operar hay que pedir algo.
     */
    @PostMapping("/sesion/otra-abierta/continuar")
    public String continuarAca(Authentication autenticacion, HttpServletRequest request) {
        HttpSession sesion = request.getSession();
        sesionesActivas.desplazarOtras(autenticacion.getName(), sesion.getId());
        sesion.removeAttribute(SecurityConfig.SESION_POR_CONFIRMAR);
        return "redirect:/";
    }
}
