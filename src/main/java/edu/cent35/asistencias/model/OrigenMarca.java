package edu.cent35.asistencias.model;

/**
 * De dónde salió la hora de entrada o de salida de un {@link BloquePresencia} (RF-74, RF-80).
 * <p>
 * No es lo mismo que {@link MetodoAsistencia}: acá existe un tercer caso, {@link #PRESUNTO},
 * que no es ni una marca automática ni una carga humana sino una hora que completó el
 * sistema para poder operar. Las tres tienen distinto valor probatorio y por eso se
 * distinguen: ver el CHECK {@code ck_bloques_entrada_modelo} de V019.
 */
public enum OrigenMarca {

    // El docente pasó por la cámara y el reconocimiento lo identificó. Es el único
    // origen que lleva modelo facial y confianza.
    AUTOMATICO,

    // La cargó un administrador, con motivo del catálogo (RF-83).
    MANUAL,

    // Nadie la registró: la completó el job tomando el fin de la última clase del bloque.
    // Sirve para imputar la asistencia, pero nunca se presenta como una hora observada.
    PRESUNTO
}
