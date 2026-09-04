package edu.cent35.asistencias.model;

/**
 * Cómo terminó un {@link BloquePresencia} (RF-74, RF-79).
 * <p>
 * Coincide con el CHECK {@code ck_bloques_estado_cierre} de V019, y con
 * {@code ck_bloques_cierre_coherente}: solo {@link #ABIERTO} admite el bloque sin hora de
 * salida. {@link #SIN_CIERRE} no es un cierre más: es el estado que hace que el bloque
 * aparezca como pendiente en el panel de inicio hasta que alguien lo resuelva.
 */
public enum EstadoCierre {

    // El docente entró y todavía no se fue. Un docente no puede tener dos bloques en este
    // estado a la vez: lo garantiza el UNIQUE sobre la columna generada bloque_abierto_de.
    ABIERTO,

    // Se cerró porque el docente volvió a pasar por la cámara y fue reconocido.
    CERRADO_POR_ROSTRO,

    // Lo cerró un administrador a mano (RF-83). Es el camino cuando el reconocimiento
    // falla al salir, cuando el consentimiento dejó de estar vigente (RF-82) o cuando hay
    // que corregir una salida mal registrada.
    CERRADO_POR_ADMIN,

    // Nadie registró la salida y el job completó la hora para poder imputar la asistencia.
    // El bloque queda pendiente: la salida es obligatoria y su falta se informa, no se
    // descarta en silencio (RF-79).
    SIN_CIERRE
}
