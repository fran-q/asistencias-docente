package edu.cent35.asistencias.model;

/**
 * Cómo se fue el docente, con el mismo margen de tolerancia que clasifica su llegada
 * (RF-78, ADR-0018).
 * <p>
 * Es deliberadamente una dimensión aparte de {@link EstadoAsistencia}, que sigue
 * describiendo cómo llegó: un mismo registro tiene que poder decir que el docente llegó
 * tarde <b>y además</b> se retiró antes, que es justamente el caso que interesa detectar.
 * Un único campo no puede expresar las dos cosas.
 */
public enum EstadoSalida {

    // Se fue dentro del margen: desde hora_fin menos la tolerancia en adelante.
    EN_HORA,

    // Se fue antes de ese margen. No altera el estado de la asistencia (RF-78).
    ANTICIPADA,

    // No hubo marca de salida y la hora la presumió el sistema. Distinto de ANTICIPADA:
    // acá no se está afirmando que se haya ido antes, sino que no se sabe cuándo se fue.
    SIN_MARCA
}
