package edu.cent35.asistencias.dto;

/**
 * Imagen capturada desde la cámara del navegador, enviada al servidor.
 * <p>
 * El campo {@code imagen} llega como <i>data URL</i> en base64, con la
 * forma {@code data:image/jpeg;base64,XXXXX} (lo que produce
 * {@code canvas.toDataURL()} en el navegador).
 *
 * @param imagen data URL base64 de la imagen capturada
 */
public record CapturaImagenDto(String imagen) {
}
