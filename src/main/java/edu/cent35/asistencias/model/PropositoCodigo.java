package edu.cent35.asistencias.model;

/**
 * Para que se emitio un código de un solo uso. Los dos flujos comparten el mismo ciclo de vida
 * y las mismas defensas, así que se distinguen por este valor en vez de por tablas separadas.
 */
public enum PropositoCodigo {

    // Confirmar que la persona controla el buzón que declaró en su cuenta.
    VERIFICACION_EMAIL("Verificación de correo"),

    // Permitir fijar una contraseña nueva sin intervención del superadmin.
    RECUPERACION_PASSWORD("Recuperación de contraseña");

    private final String etiqueta;

    PropositoCodigo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    // Nombre para mostrar en pantallas y correos.
    public String getEtiqueta() {
        return etiqueta;
    }
}
