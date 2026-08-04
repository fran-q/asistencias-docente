package edu.cent35.asistencias.service;

import edu.cent35.asistencias.model.PropositoCodigo;
import edu.cent35.asistencias.model.Usuario;

/**
 * Por dónde sale el código de un solo uso hacia la persona.
 *
 * <p>Existe como interfaz para que el resto del sistema no sepa si el código viaja por correo,
 * por consola o mañana por un servicio de mensajería. Los flujos que la usan —alta de
 * institución, verificación de cuenta, recuperación de contraseña— hacen siempre lo mismo:
 * generan el código, lo guardan cifrado y piden que salga. Cómo sale es configuración.
 *
 * <p>Esto no es una abstracción por las dudas: durante el prototipo el correo depende de tener
 * un SMTP levantado, y cuando no lo está el flujo entero queda inutilizable. Poder cambiar el
 * canal por una propiedad es lo que permite probar la aplicación sin infraestructura.
 */
public interface CanalDeCodigos {

    /**
     * Hace llegar el código al destinatario.
     *
     * @throws RuntimeException si el canal no pudo entregarlo. El flujo tiene que enterarse:
     *                          decirle a alguien que revise su correo cuando el mensaje nunca
     *                          salió lo deja esperando algo que no va a llegar.
     */
    void enviarCodigo(Usuario usuario, PropositoCodigo proposito, String email, String codigo);

    /** Nombre corto del canal, para los logs y para el aviso de arranque. */
    String nombre();
}
