package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.seguridad.CookiePuesto;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.PuestoCaptura;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.service.PuestoCapturaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Los equipos autorizados a capturar datos biométricos (ADR-0015): la pantalla que se ve al
 * llegar desde un equipo no autorizado, y el alta y baja de puestos.
 *
 * <p>La pantalla de rechazo es también donde se designa el equipo. Es a propósito: quien se
 * topa con el bloqueo desde la máquina que corresponde y tiene autoridad para habilitarla no
 * tiene por qué salir a buscar la opción a otro lado. La pared dice cómo abrirse, y solo a
 * quien puede abrirla.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class PuestoCapturaController {

    private static final String VIEW = "puesto/requerido";

    // A donde vuelven los POST. Es una constante y no un parametro con el origen: un destino
    // de redirect que venga del formulario habria que validarlo contra una lista blanca, y
    // olvidarse de hacerlo es como se abre un redirect abierto. La pantalla de gestion sirve
    // igual de bien para las dos entradas.
    private static final String VUELTA = "/puestos";

    private final PuestoCapturaService puestoService;
    private final UsuarioRepository usuarioRepository;

    /**
     * La pantalla de gestión, a la que se llega por el menú. Misma vista que el rechazo pero
     * sin el encabezado que explica un bloqueo: acá nadie chocó contra nada, vino a mirar
     * qué equipos están habilitados.
     */
    @GetMapping("/puestos")
    @PreAuthorize("hasRole('INSTITUCION')")
    public String gestion(Model model, @AuthenticationPrincipal UsuarioAutenticado principal,
                          HttpServletRequest request) {
        return armar(model, principal, false, request);
    }

    /**
     * Explica por qué la pantalla anterior no se abrió y, si la cuenta puede, ofrece designar
     * este equipo. No es un 403 seco: la persona necesita entender que el sistema funciona y
     * que le falta estar en otra máquina, no que algo se rompió.
     *
     * <p>A diferencia de /puestos, esta no exige el rol institucional: quien se topó con el
     * bloqueo tiene derecho a saber por qué, sea cual sea su rol. Lo que el rol decide es si
     * además ve el formulario para autorizar el equipo.
     */
    @GetMapping("/puesto-requerido")
    public String requerido(Model model, @AuthenticationPrincipal UsuarioAutenticado principal,
                            HttpServletRequest request) {
        return armar(model, principal, true, request);
    }

    private String armar(Model model, UsuarioAutenticado principal, boolean bloqueado,
                         HttpServletRequest request) {
        Long institucionId = TenantContext.getRequired();

        boolean hayAlguno = puestoService.contarHabilitados(institucionId) > 0;
        boolean desdePuesto = vieneDePuestoAutorizado(request, institucionId);

        model.addAttribute("bloqueado", bloqueado);
        model.addAttribute("puestos", puestoService.listar(institucionId));
        model.addAttribute("hayAlguno", hayAlguno);
        // Designar un equipo es autorizar el tratamiento de datos sensibles en esa maquina:
        // lo decide la cuenta institucional, no cualquier administrativo.
        model.addAttribute("puedeDesignar", tieneRolInstitucion(principal));
        // El formulario solo aparece cuando designar es realmente posible: para el primer
        // puesto, o estando en uno ya autorizado. Es la misma regla que aplica el service,
        // repetida en la vista para no ofrecer un boton que va a fallar.
        model.addAttribute("puedeAutorizarEsteEquipo",
            tieneRolInstitucion(principal) && (!hayAlguno || desdePuesto));
        return VIEW;
    }

    // Si la peticion trae la cookie de un puesto habilitado de esta institucion.
    private boolean vieneDePuestoAutorizado(HttpServletRequest request, Long institucionId) {
        return CookiePuesto.leer(request)
            .flatMap(token -> puestoService.verificar(token, institucionId))
            .isPresent();
    }

    /**
     * Registra el equipo desde el que se está llamando y le deja la cookie.
     *
     * <p>No recibe ningún identificador de máquina: el equipo que se designa es,
     * necesariamente, el que envía esta petición. No hay forma de habilitar una máquina a
     * distancia, que es justamente lo que le da sentido al control.
     */
    @PostMapping("/puestos/designar")
    @PreAuthorize("hasRole('INSTITUCION')")
    public String designar(@RequestParam String nombre,
                           @AuthenticationPrincipal UsuarioAutenticado principal,
                           HttpServletRequest request,
                           HttpServletResponse response,
                           RedirectAttributes redirect) {

        Long institucionId = TenantContext.getRequired();
        Usuario designante = usuarioRepository.findById(principal.getUsuarioId()).orElse(null);

        try {
            PuestoCapturaService.PuestoDesignado alta = puestoService.designar(
                institucionId, nombre, designante,
                vieneDePuestoAutorizado(request, institucionId));

            CookiePuesto.escribir(request, response, alta.getTokenEnClaro());
            redirect.addFlashAttribute("flashMensaje",
                "Este equipo quedó autorizado como \"" + alta.getPuesto().getNombre()
                + "\". Ya podés tomar asistencia desde acá.");
            return "redirect:/asistencia/pase";

        } catch (IllegalArgumentException e) {
            // Va como "error" y no como "flashError": el aviso flotante sirve para el
            // resultado de una accion terminada, pero esto es un formulario que hay que
            // corregir, y el mensaje tiene que quedar al lado del campo.
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:" + VUELTA;
        }
    }

    /**
     * Revoca un puesto. Si es el equipo desde el que se está llamando, además le borra la
     * cookie: dejarla sería guardar una credencial que ya no sirve.
     */
    @PostMapping("/puestos/{id}/revocar")
    @PreAuthorize("hasRole('INSTITUCION')")
    public String revocar(@PathVariable Long id,
                          HttpServletRequest request,
                          HttpServletResponse response,
                          RedirectAttributes redirect) {

        Long institucionId = TenantContext.getRequired();
        boolean esEsteEquipo = esteEquipoEs(id, institucionId, request);

        try {
            puestoService.revocar(id, institucionId);
            if (esEsteEquipo) {
                CookiePuesto.borrar(request, response);
            }
            redirect.addFlashAttribute("flashMensaje", "Puesto revocado.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:" + VUELTA;
    }

    // Si el puesto que se esta revocando es el de esta misma maquina. Se resuelve ANTES de
    // revocar: despues la verificacion ya no lo encontraria y no habria como saberlo.
    private boolean esteEquipoEs(Long puestoId, Long institucionId, HttpServletRequest request) {
        return CookiePuesto.leer(request)
            .flatMap(token -> puestoService.verificar(token, institucionId))
            .map(PuestoCaptura::getId)
            .filter(puestoId::equals)
            .isPresent();
    }

    private boolean tieneRolInstitucion(UsuarioAutenticado principal) {
        if (principal == null) {
            return false;
        }
        List<String> roles = principal.getAuthorities().stream()
            .map(a -> a.getAuthority())
            .toList();
        return roles.contains("ROLE_INSTITUCION");
    }
}
