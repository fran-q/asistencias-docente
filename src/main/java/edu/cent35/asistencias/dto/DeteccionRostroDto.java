package edu.cent35.asistencias.dto;

/**
 * Resultado de una detección de rostro, con la medición de calidad que decide si la captura
 * sirve para entrenar. La secuencia guiada de registro se apoya en {@code apta} para saber
 * cuándo capturar sola, y en {@code mensaje} para decir qué corregir mientras tanto.
 *
 * @param rostroDetectado   true si se detectó al menos un rostro
 * @param cantidadRostros   cuántos rostros se detectaron en la imagen
 * @param x                 coord. X del recuadro del rostro más grande (null si no hay)
 * @param y                 coord. Y del recuadro (null si no hay)
 * @param ancho             ancho del recuadro (null si no hay)
 * @param alto              alto del recuadro (null si no hay)
 * @param apta              true si la captura cumple nitidez, luz y encuadre
 * @param mensaje           qué corregir, en segunda persona; o la confirmación si ya sirve
 * @param nitidez           varianza del Laplaciano del recorte (null si no hay rostro)
 * @param brillo            brillo medio del recorte, sobre 255 (null si no hay rostro)
 * @param contraste         desvío estándar del recorte (null si no hay rostro)
 * @param porcentajeCuadro  qué porcentaje del cuadro ocupa el rostro (null si no hay rostro)
 */
public record DeteccionRostroDto(
    boolean rostroDetectado,
    int cantidadRostros,
    Integer x,
    Integer y,
    Integer ancho,
    Integer alto,
    boolean apta,
    String mensaje,
    Double nitidez,
    Double brillo,
    Double contraste,
    Double porcentajeCuadro
) {

    // Atajo para construir un resultado sin rostro detectado.
    public static DeteccionRostroDto sinRostro(String mensaje) {
        return new DeteccionRostroDto(
            false, 0, null, null, null, null, false, mensaje, null, null, null, null);
    }
}
