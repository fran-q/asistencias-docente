package edu.cent35.asistencias.model;

/**
 * Para que se emitio un código de un solo uso. Todos los flujos comparten el mismo ciclo de vida
 * y las mismas defensas, así que se distinguen por este valor en vez de por tablas separadas.
 */
public enum PropositoCodigo {

    // Confirmar que la persona controla el buzón que declaró en su cuenta.
    VERIFICACION_EMAIL("Verificación de correo"),

    // Permitir fijar una contraseña nueva sin intervención del superadmin.
    RECUPERACION_PASSWORD("Recuperación de contraseña"),

    // Revocar el puesto de captura desde una maquina que no es ese puesto. La regla es que
    // solo se revoca desde el propio equipo; esto es la salida para cuando esa maquina se
    // rompio o se formateo, y exige el buzon de la institucion ademas de su contrasena.
    REVOCACION_PUESTO("Revocación del puesto de captura"),

    // Autorizar el cambio del correo de la cuenta. Va al correo ACTUAL, igual que el cambio de
    // contraseña: si alcanzara con confirmar el nuevo, quien encuentra una sesión abierta
    // pondría su propio correo y después recuperaría la contraseña con él.
    CAMBIO_EMAIL("Cambio de correo"),

    // Confirmar la dirección nueva antes de que la cuenta pase a usarla. El código guarda esa
    // dirección: la cuenta cambia a la que efectivamente lo recibió, no a la que diga la sesión.
    EMAIL_NUEVO("Confirmación del correo nuevo");

    private final String etiqueta;

    PropositoCodigo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    // Nombre para mostrar en pantallas y correos.
    public String getEtiqueta() {
        return etiqueta;
    }
}
