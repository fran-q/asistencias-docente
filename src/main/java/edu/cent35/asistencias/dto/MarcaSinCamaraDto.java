package edu.cent35.asistencias.dto;

/**
 * Lo que manda la pantalla del pase para registrar una marca sin cámara (V029).
 *
 * <p>No viene "entrada" ni "salida": eso lo decide el servidor mirando si el docente tiene un
 * bloque abierto, igual que cuando pasa la cara (ADR-0017). Dejarlo elegir desde el navegador
 * abriría la puerta a registrar una salida de alguien que nunca entró.
 *
 * @param docenteId a quién se le registra la marca
 * @param motivoId  motivo del catálogo compartido con la carga manual (RF-23)
 * @param detalle   texto libre; obligatorio cuando el motivo es OTRO
 */
public record MarcaSinCamaraDto(
    Long docenteId,
    Short motivoId,
    String detalle
) {}
