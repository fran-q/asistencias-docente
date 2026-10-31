package edu.cent35.asistencias.dto.biometria;

/**
 * Un modelo facial activo, sin su dato biométrico: alcanza para saber quién tiene modelo y contra
 * qué comparar. Existe porque la entidad trae siempre el modelo cifrado, y consultarla en cada
 * cuadro de la cámara movía todos los modelos de la institución una vez por segundo.
 */
public record ModeloActivo(Long modeloId, Long docenteId) {
}
