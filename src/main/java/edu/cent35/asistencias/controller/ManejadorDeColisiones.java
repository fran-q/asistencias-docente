package edu.cent35.asistencias.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce las restricciones de la base a mensajes que se entienden, para que un dato repetido
 * nunca termine en una pantalla de error genérica. Es la última línea de defensa: cada servicio
 * ya valida antes, y lo que llega hasta acá es lo que se le escapó a esa validación.
 */
@ControllerAdvice
@Slf4j
public class ManejadorDeColisiones {

    // Cada restriccion de la base, con lo que significa para quien esta usando el sistema.
    // El nombre del indice es la clave porque es lo unico que el motor devuelve: el mensaje
    // crudo ("Duplicate entry '1-ECO-2024' for key 'uq_carreras_inst_codigo'") no sirve para
    // mostrar, pero alcanza para saber exactamente que se choco.
    private static final Map<String, String> MENSAJES = new LinkedHashMap<>();
    static {
        MENSAJES.put("uq_docentes_inst_dni",
            "Ya hay un docente cargado con ese DNI en esta institución.");
        MENSAJES.put("uq_docentes_inst_legajo",
            "Ya hay un docente cargado con ese legajo en esta institución.");
        MENSAJES.put("uq_usuarios_inst_username",
            "Ese nombre de usuario ya está tomado en esta institución. Elegí otro.");
        MENSAJES.put("uq_usuarios_inst_email",
            "Ya hay una cuenta con ese correo en esta institución. "
            + "La misma persona sí puede tener cuenta en otra institución con el mismo correo.");
        MENSAJES.put("uq_instituciones_nombre",
            "Ya hay una institución registrada con ese nombre. El nombre no se puede repetir "
            + "en todo el sistema.");
        MENSAJES.put("uq_instituciones_cuit",
            "Ya hay una institución registrada con ese CUIT. Revisá si no está cargado dos veces.");
        MENSAJES.put("uq_carreras_inst_codigo",
            "Ya hay una carrera con ese código en esta institución.");
        MENSAJES.put("uq_materias_inst_codigo",
            "Ya hay una materia con ese código en esta institución.");
        MENSAJES.put("uq_comisiones_materia_codigo",
            "Ya hay una comisión con ese código en esa materia.");
        MENSAJES.put("uq_asistencias_doc_horario_fecha",
            "Ese docente ya tiene una marca cargada para ese horario y esa fecha.");
        MENSAJES.put("uq_justificaciones_asistencia",
            "Esa ausencia ya tiene una justificación cargada.");
        MENSAJES.put("uq_asistencias_manuales_asistencia",
            "Esa asistencia ya figura como carga manual.");
        MENSAJES.put("ck_docentes_baja_posterior_al_alta",
            "La fecha de baja no puede ser anterior a la fecha de alta del docente.");
    }

    private static final String MENSAJE_GENERICO =
        "El dato que ingresaste choca con uno que ya existe. Revisá los campos que no se "
        + "pueden repetir y volvé a intentar.";

    /**
     * Devuelve a la pantalla anterior con el motivo del choque en vez de mostrar un error 500.
     * Se redirige y no se vuelve a dibujar el formulario porque desde acá no se sabe cuál era:
     * el precio es que se pierde lo tipeado, y por eso los servicios validan antes.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public String colision(DataIntegrityViolationException ex,
                           HttpServletRequest request,
                           RedirectAttributes redirect) throws DataIntegrityViolationException {

        // El pase de asistencia consulta por JSON: devolverle una redireccion a HTML rompe el
        // fetch del navegador con un error que no dice nada. Que siga su curso y termine en un
        // codigo de error, que es lo que ese cliente sabe leer.
        if (!esperaHtml(request)) throw ex;

        String causa = causaRaiz(ex);
        log.warn("Colision de integridad en {} {}: {}",
                 request.getMethod(), request.getRequestURI(), causa);

        redirect.addFlashAttribute("flashError", traducir(ex));
        return "redirect:" + volverA(request);
    }

    /**
     * Busca en el error qué restricción se violó y devuelve su explicación. Si es una que no
     * está en el mapa, entrega un mensaje general: preferible a filtrar el texto del motor,
     * que menciona nombres de tablas y valores concretos.
     */
    public static String traducir(Throwable ex) {
        String causa = causaRaiz(ex).toLowerCase();
        for (Map.Entry<String, String> e : MENSAJES.entrySet()) {
            if (causa.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return MENSAJE_GENERICO;
    }

    // Distingue al navegador pidiendo una pantalla de un fetch pidiendo datos. No alcanza con
    // mirar el Accept: fetch() manda "*/*" salvo que se le indique otra cosa, asi que quien
    // manda JSON en el cuerpo cuenta como cliente de datos aunque no lo declare al pedir.
    private static boolean esperaHtml(HttpServletRequest request) {
        String tipoEnviado = request.getContentType();
        if (tipoEnviado != null && tipoEnviado.contains("application/json")) return false;

        String accept = request.getHeader("Accept");
        return accept == null || !accept.contains("application/json") || accept.contains("text/html");
    }

    // Recorre la cadena de causas: el nombre del indice aparece en el error del driver, no
    // en el de Spring, que solo dice que hubo una violacion de integridad.
    private static String causaRaiz(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        Throwable t = ex;
        int vueltas = 0;
        while (t != null && vueltas++ < 10) {
            if (t.getMessage() != null) sb.append(t.getMessage()).append(' ');
            t = t.getCause();
        }
        return sb.toString();
    }

    /**
     * Decide a dónde volver. Se acepta el Referer solo si apunta a este mismo servidor: si se
     * redirigiera a donde diga, una página externa podría mandar a alguien —ya con la sesión
     * iniciada— a un formulario ajeno que imite al nuestro.
     */
    private static String volverA(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null) return "/";

        URI uri;
        try {
            uri = new URI(referer);
        } catch (URISyntaxException ex) {
            return "/";
        }

        // Si trae host, tiene que ser el mismo por el que entro el pedido.
        if (uri.getHost() != null && !uri.getHost().equalsIgnoreCase(request.getServerName())) {
            log.warn("Referer de otro host descartado: {}", referer);
            return "/";
        }

        String ruta = uri.getPath();
        if (ruta == null || !ruta.startsWith("/")) return "/";

        // La redireccion es relativa al contexto, asi que el prefijo se saca antes de sumarlo.
        String contexto = request.getContextPath();
        if (!contexto.isEmpty() && ruta.startsWith(contexto)) {
            ruta = ruta.substring(contexto.length());
        }
        return ruta.isEmpty() ? "/" : ruta;
    }
}
