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

        long habilitados = puestoService.contarHabilitados(institucionId);
        boolean hayAlguno = habilitados > 0;
        boolean desdePuesto = vieneDePuestoAutorizado(request, institucionId);
        Short tope = puestoService.topeDeEquipos(institucionId);
        boolean hayCupo = tope == null || habilitados < tope;
        Long esteEquipo = idDeEsteEquipo(request, institucionId);

        model.addAttribute("bloqueado", bloqueado);
        model.addAttribute("puestos", puestoService.listar(institucionId));
        model.addAttribute("hayAlguno", hayAlguno);
        // Designar un equipo es autorizar el tratamiento de datos sensibles en esa maquina:
        // lo decide la cuenta institucional, no cualquier administrativo.
        model.addAttribute("puedeDesignar", tieneRolInstitucion(principal));
        // El formulario aparece cuando autorizar es realmente posible: esta maquina todavia
        // no es un equipo y queda cupo. Es la misma regla que aplica el service, repetida en
        // la vista para no ofrecer un boton que va a fallar.
        model.addAttribute("puedeAutorizarEsteEquipo",
            tieneRolInstitucion(principal) && esteEquipo == null && hayCupo);
        model.addAttribute("habilitados", habilitados);
        model.addAttribute("tope", tope);
        model.addAttribute("hayCupo", hayCupo);
        // Cual de los puestos listados es esta misma maquina. Sin esto la pantalla muestra
        // nombres y quien la mira no sabe si esta sentado en el equipo autorizado o no, que
        // es justo lo que necesita saber para revocarlo.
        model.addAttribute("idDeEsteEquipo", esteEquipo);
        model.addAttribute("desdePuesto", desdePuesto);
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
                institucionId, nombre, designante);

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
     * Revoca el puesto desde esa misma máquina, y le borra la cookie: dejarla sería guardar
     * una credencial que ya no sirve.
     *
     * <p><b>Desde V030 se revoca desde acá, sin estar en esa máquina.</b> Con una sola puerta
     * la regla anterior se sostenía —el equipo era uno y estaba a la vista—, pero con una
     * cámara por entrada obliga a caminar hasta la máquina que justamente puede estar rota o
     * robada, que es cuando más urge revocarla. Lo que autoriza sigue siendo la cuenta
     * institucional; lo que no cambió es que designar un equipo solo se puede desde él.
     */
    @PostMapping("/puestos/{id}/revocar")
    @PreAuthorize("hasRole('INSTITUCION')")
    public String revocar(@PathVariable Long id,
                          HttpServletRequest request,
                          HttpServletResponse response,
                          RedirectAttributes redirect) {

        Long institucionId = TenantContext.getRequired();

        try {
            puestoService.revocar(id, institucionId);
            // Si la maquina que revoca es la revocada, ademas se le saca la credencial: sin
            // esto seguiria mandando una cookie que ya no vale hasta que alguien la limpie.
            if (esteEquipoEs(id, institucionId, request)) {
                CookiePuesto.borrar(request, response);
            }
            redirect.addFlashAttribute("flashMensaje", "Equipo revocado.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:" + VUELTA;
    }

    // ========================================================================
    //  Modo kiosco: operar sin sesion abierta (RF-85, ADR-0019)
    // ========================================================================

    /**
     * Habilita el funcionamiento sin sesión en este mismo equipo.
     *
     * <p>Como al revocar, el equipo se resuelve desde la cookie y no desde el formulario: lo
     * que se habilita es la máquina en la que está sentada la persona que lo decide, no un
     * nombre elegido de una lista.
     */
    @PostMapping("/puestos/{id}/kiosco/habilitar")
    @PreAuthorize("hasRole('INSTITUCION')")
    public String habilitarKiosco(@PathVariable Long id,
                                  @AuthenticationPrincipal UsuarioAutenticado principal,
                                  HttpServletRequest request,
                                  RedirectAttributes redirect) {

        Long institucionId = TenantContext.getRequired();
        Usuario quien = usuarioRepository.findById(principal.getUsuarioId()).orElse(null);

        try {
            puestoService.habilitarKiosco(id, institucionId, quien,
                                          esteEquipoEs(id, institucionId, request));
            redirect.addFlashAttribute("flashMensaje",
                "Modo kiosco habilitado. Este equipo ya puede tomar asistencia sin que haya "
                + "ninguna sesión abierta.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:" + VUELTA;
    }

    /**
     * Apaga el funcionamiento sin sesión, sin revocar el equipo.
     *
     * <p>A diferencia de habilitar, funciona desde cualquier máquina. Apagarlo es lo que se
     * necesita cuando algo salió mal, y en ese momento lo más probable es no poder ir hasta
     * la máquina del kiosco.
     */
    @PostMapping("/puestos/{id}/kiosco/deshabilitar")
    @PreAuthorize("hasRole('INSTITUCION')")
    public String deshabilitarKiosco(@PathVariable Long id, RedirectAttributes redirect) {
        Long institucionId = TenantContext.getRequired();
        try {
            puestoService.deshabilitarKiosco(id, institucionId);
            redirect.addFlashAttribute("flashMensaje",
                "Modo kiosco deshabilitado. El equipo sigue autorizado: para tomar asistencia "
                + "ahora hace falta una sesión abierta.");
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

    /**
     * Elige la cámara de este equipo (V026).
     *
     * <p>Solo desde esa misma máquina, por lo que explica {@link PuestoCapturaService#elegirCamara}.
     * El mensaje no repite el nombre que mandó el navegador: es texto que viene de afuera, y la
     * tarjeta de la pantalla ya lo muestra escapado.
     */
    @PostMapping("/puestos/{id}/camara")
    @PreAuthorize("hasRole('INSTITUCION')")
    public String elegirCamara(@PathVariable Long id,
                               @RequestParam(name = "dispositivoId", required = false) String dispositivoId,
                               @RequestParam(name = "etiqueta", required = false) String etiqueta,
                               HttpServletRequest request,
                               RedirectAttributes redirect) {
        Long institucionId = TenantContext.getRequired();
        try {
            puestoService.elegirCamara(id, institucionId, dispositivoId, etiqueta,
                                       esteEquipoEs(id, institucionId, request));
            redirect.addFlashAttribute("flashMensaje",
                (dispositivoId == null || dispositivoId.isBlank())
                    ? "Listo. Este equipo va a usar la cámara predeterminada del sistema."
                    : "Listo. Este equipo ya captura con la cámara elegida.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:" + VUELTA;
    }

    // El id del puesto de esta misma maquina, o null si esta no es ningun puesto.
    private Long idDeEsteEquipo(HttpServletRequest request, Long institucionId) {
        return CookiePuesto.leer(request)
            .flatMap(token -> puestoService.verificar(token, institucionId))
            .map(PuestoCaptura::getId)
            .orElse(null);
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
