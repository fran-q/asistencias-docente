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

    /**
     * Avisa que un pedido de código no derivó en ningún envío, y por qué.
     *
     * <p><b>Para qué existe.</b> La recuperación de contraseña contesta lo mismo exista o no la
     * cuenta (ADR-0009, decisión 5), así que cuando no sale ningún código la pantalla no lo dice
     * —y no debe decirlo—. El problema es que la terminal tampoco lo decía: el pedido se perdía
     * en silencio y quien estaba probando el flujo no podía distinguir "no existe esa cuenta" de
     * "el canal está roto". Este aviso separa esos dos casos sin tocar lo que ve el navegador.
     *
     * <p><b>Por omisión no hace nada</b>, y tiene que seguir siendo así en el canal de correo:
     * no hay a quién escribirle —la casilla puede no existir— y avisarle a la persona sería
     * exactamente revelar lo que la decisión oculta. Lo implementa el canal de consola, que ya
     * es de desarrollo y donde el código sale en claro de todos modos.
     *
     * @param identificador lo que se tipeó en la pantalla: un usuario, un correo, o nada
     * @param motivo por qué no se emitió, en texto llano y para que lo lea una persona
     */
    default void noSeEmitio(String identificador, String motivo) {
        // Sin destinatario no hay nada que enviar. Ver el javadoc: el silencio es la conducta
        // correcta para el canal de correo, no un hueco por completar.
    }

    /** Nombre corto del canal, para los logs y para el aviso de arranque. */
    String nombre();
}
