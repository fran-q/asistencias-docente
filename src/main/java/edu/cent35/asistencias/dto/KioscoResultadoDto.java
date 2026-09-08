package edu.cent35.asistencias.dto;

/**
 * Lo que el kiosco le responde a la pantalla desatendida (RF-87, ADR-0019).
 * <p>
 * <b>Es un DTO aparte y no el del pase con campos vacíos.</b> Lo que define al kiosco es qué
 * datos <i>no</i> salen del servidor, y eso se sostiene mejor con un tipo que no tiene dónde
 * ponerlos: acá no existe un campo para el nombre completo, así que nadie puede llenarlo por
 * descuido más adelante.
 *
 * @param rostroDetectado  true si había una cara en el cuadro
 * @param reconocido       true si se identificó a un docente
 * @param apellido         <b>solo el apellido</b>, nunca el nombre completo. Alcanza para que
 *                         el docente verifique que el sistema lo reconoció a él —el algoritmo
 *                         se equivoca de persona y sin esa verificación el error pasa
 *                         inadvertido— sin dejar la nómina del personal a la vista de
 *                         cualquiera que pase por secretaría
 * @param tipoDeMarca      "ENTRADA" o "SALIDA" cuando quedó registrada; null si no
 * @param detalle          el horario de la jornada o la clase, sin identificar a nadie más
 * @param x,y,ancho,alto   recuadro del rostro, para dibujarlo sobre el video
 * @param confirmando      true mientras la identidad se sostiene y todavía no se marcó
 * @param progresoMs       milisegundos ya sostenidos
 * @param objetivoMs       milisegundos que hay que sostener
 * @param mensaje          texto para la pantalla
 */
public record KioscoResultadoDto(
    boolean rostroDetectado,
    boolean reconocido,
    String apellido,
    boolean registrada,
    String tipoDeMarca,
    String detalle,
    Integer x,
    Integer y,
    Integer ancho,
    Integer alto,
    boolean confirmando,
    Long progresoMs,
    Long objetivoMs,
    String mensaje
) {

    // No hay nadie en el cuadro.
    public static KioscoResultadoDto sinRostro() {
        return new KioscoResultadoDto(false, false, null, false, null, null,
            null, null, null, null, false, null, null,
            "Acercate a la cámara.");
    }

    /**
     * Se vio una cara pero no se aceptó la identificación.
     *
     * <p>El motivo viaja tal cual lo dio la identificación —"no estás registrado" y "no puedo
     * distinguirte de otro" se resuelven distinto—, pero sin nombrar a nadie: en una pantalla
     * desatendida, decir a quién se pareció sería peor que no decir nada.
     */
    public static KioscoResultadoDto noReconocido(String motivo,
                                                  Integer x, Integer y, Integer ancho, Integer alto) {
        return new KioscoResultadoDto(true, false, null, false, null, null,
            x, y, ancho, alto, false, null, null, motivo);
    }

    // Se identificó pero la identidad todavía no se sostuvo lo suficiente (ADR-0013).
    public static KioscoResultadoDto confirmando(int x, int y, int ancho, int alto,
                                                 long progresoMs, long objetivoMs) {
        // Sin apellido a proposito: mostrarlo antes de confirmar es lo que hace que alguien
        // vea el apellido equivocado durante un parpadeo.
        return new KioscoResultadoDto(true, true, null, false, null, null,
            x, y, ancho, alto, true, progresoMs, objetivoMs,
            "Sostené la posición…");
    }

    // Quedó registrada la entrada o la salida.
    public static KioscoResultadoDto registrada(String apellido, String tipoDeMarca, String detalle,
                                                Integer x, Integer y, Integer ancho, Integer alto,
                                                String mensaje) {
        return new KioscoResultadoDto(true, true, apellido, true, tipoDeMarca, detalle,
            x, y, ancho, alto, false, null, null, mensaje);
    }

    // Se reconoció a la persona pero no se registró nada: no hay clase, falta esperar, etc.
    public static KioscoResultadoDto rechazada(String apellido, String motivo,
                                               Integer x, Integer y, Integer ancho, Integer alto) {
        return new KioscoResultadoDto(true, true, apellido, false, null, null,
            x, y, ancho, alto, false, null, null, motivo);
    }
}
