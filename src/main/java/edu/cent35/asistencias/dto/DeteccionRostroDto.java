package edu.cent35.asistencias.dto;

/**
 * Resultado de una detección de rostro (Sprint 4 Fase B).
 * Se serializa a JSON como respuesta del endpoint de detección.
 *
 * @param rostroDetectado  true si se detectó al menos un rostro válido
 * @param cantidadRostros  cuántos rostros se detectaron en la imagen
 * @param x                coord. X del bounding box del rostro más grande (null si no hay)
 * @param y                coord. Y del bounding box (null si no hay)
 * @param ancho            ancho del bounding box (null si no hay)
 * @param alto             alto del bounding box (null si no hay)
 * @param mensaje          texto descriptivo para mostrar al usuario
 */
public record DeteccionRostroDto(
    boolean rostroDetectado,
    int cantidadRostros,
    Integer x,
    Integer y,
    Integer ancho,
    Integer alto,
    String mensaje
) {

    /** Atajo para construir un resultado sin rostro detectado. */
    public static DeteccionRostroDto sinRostro(String mensaje) {
        return new DeteccionRostroDto(false, 0, null, null, null, null, mensaje);
    }
}
