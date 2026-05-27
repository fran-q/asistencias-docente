package edu.cent35.asistencias.dto;

import java.util.List;

/**
 * Conjunto de capturas del rostro de un docente enviadas para registrar
 * su modelo facial (Sprint 4 Fase C).
 * <p>
 * Cada elemento es un <i>data URL</i> base64 ({@code data:image/jpeg;base64,...})
 * tal como lo produce {@code canvas.toDataURL()} en el navegador.
 *
 * @param capturas lista de data URLs base64, una por cada captura pedida
 */
public record RegistroFacialDto(List<String> capturas) {
}
