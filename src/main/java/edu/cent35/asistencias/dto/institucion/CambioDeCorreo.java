package edu.cent35.asistencias.dto.institucion;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Cambio del propio correo esperando sus dos códigos: el del correo actual y el del nuevo. Vive
 * en la sesión y no en la base porque hasta el segundo código no cambió nada: la cuenta sigue
 * con su correo de siempre, y abandonar el cambio a mitad de camino no deja nada que limpiar.
 */
public class CambioDeCorreo implements Serializable {

    private static final long serialVersionUID = 1L;

    // Nombre del atributo de sesión donde se guarda.
    public static final String SESION = "cambioDeCorreo";

    private final Long usuarioId;
    private final String correoNuevo;
    private LocalDateTime autorizadoHasta;

    public CambioDeCorreo(Long usuarioId, String correoNuevo) {
        this.usuarioId = usuarioId;
        this.correoNuevo = correoNuevo;
    }

    // De que cuenta es. Una sesión puede cambiar de usuario al volver a entrar, y un cambio
    // pedido desde otra cuenta no puede seguir valiendo.
    public Long getUsuarioId() {
        return usuarioId;
    }

    // La dirección a la que la cuenta quiere pasar.
    public String getCorreoNuevo() {
        return correoNuevo;
    }

    // Marca validado el código del correo actual: hasta ese momento se puede confirmar el nuevo.
    public void autorizarHasta(LocalDateTime hasta) {
        this.autorizadoHasta = hasta;
    }

    // Si el correo actual ya se confirmó y la ventana sigue abierta.
    public boolean estaAutorizado(LocalDateTime ahora) {
        return autorizadoHasta != null && ahora.isBefore(autorizadoHasta);
    }

    // Si el correo actual se había confirmado pero la ventana ya paso: hay que volver a empezar.
    public boolean autorizacionVencida(LocalDateTime ahora) {
        return autorizadoHasta != null && !estaAutorizado(ahora);
    }

    // Vuelve al primer paso, el del correo actual.
    public void desautorizar() {
        this.autorizadoHasta = null;
    }
}
