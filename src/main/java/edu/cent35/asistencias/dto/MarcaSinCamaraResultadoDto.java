package edu.cent35.asistencias.dto;

/**
 * En qué quedó una marca registrada sin cámara (V029).
 *
 * <p>Es un DTO propio y no el del pase por rostro: aquel lleva recuadro, distancia y avance de
 * la confirmación, que acá no existen porque no hubo ninguna imagen. Devolverlos en null sería
 * arrastrar seis campos vacíos y dejar que la pantalla adivine cuáles mirar.
 *
 * @param registrada    si quedó asentada. False es un rechazo con su motivo, no un error
 * @param tipoDeMarca   "ENTRADA" o "SALIDA"; null si no se registró. Son dos hechos opuestos y
 *                      la pantalla los tiene que poder distinguir de un vistazo (RF-20)
 * @param docenteNombre a quién se le registró, para que quien la cargó confirme que no se
 *                      equivocó de persona; null en los rechazos
 * @param mensaje       el texto que se muestra, ya armado
 */
public record MarcaSinCamaraResultadoDto(
    boolean registrada,
    String tipoDeMarca,
    String docenteNombre,
    String mensaje
) {

    public static MarcaSinCamaraResultadoDto entrada(String docenteNombre, String detalle) {
        return new MarcaSinCamaraResultadoDto(
            true, "ENTRADA", docenteNombre, "Entrada registrada a mano: " + detalle);
    }

    public static MarcaSinCamaraResultadoDto salida(String docenteNombre, String resumen) {
        return new MarcaSinCamaraResultadoDto(
            true, "SALIDA", docenteNombre, "Salida registrada a mano: " + resumen);
    }

    /**
     * No se registró, y el motivo dice por qué.
     *
     * <p>Lleva el nombre del docente igual que las otras dos. Acá la pantalla la mira quien
     * tiene sesión abierta y eligió a esa persona de una lista un segundo antes: no hay nada
     * que reservar, y sin el nombre el rechazo no dice de quién habla.
     */
    public static MarcaSinCamaraResultadoDto rechazada(String docenteNombre, String motivo) {
        return new MarcaSinCamaraResultadoDto(false, null, docenteNombre, motivo);
    }
}
