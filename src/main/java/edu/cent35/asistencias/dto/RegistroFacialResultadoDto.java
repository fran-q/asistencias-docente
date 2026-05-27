package edu.cent35.asistencias.dto;

/**
 * Resultado de un intento de registro de modelo facial (Sprint 4 Fase C).
 * Se serializa a JSON como respuesta del endpoint de registro.
 *
 * @param exito   true si el modelo se registró correctamente
 * @param mensaje texto para mostrarle al usuario
 */
public record RegistroFacialResultadoDto(boolean exito, String mensaje) {

    public static RegistroFacialResultadoDto ok(String mensaje) {
        return new RegistroFacialResultadoDto(true, mensaje);
    }

    public static RegistroFacialResultadoDto error(String mensaje) {
        return new RegistroFacialResultadoDto(false, mensaje);
    }
}
