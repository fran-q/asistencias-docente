package edu.cent35.asistencias.model.asistencia;

/**
 * Cómo se registró una asistencia: qué o quién dio por presente o ausente al docente.
 * <p>
 * Coincide con el CHECK {@code ck_asistencias_metodo} (V034). Cada valor dice una sola cosa:
 * hasta V034 las ausencias que genera el job compartían AUTOMATICO con las marcas de la cámara,
 * y el filtro por método mezclaba las dos.
 */
public enum MetodoAsistencia {

    // La cámara reconoció al docente. Es el único que lleva modelo facial y confianza, y nunca
    // es una ausencia: la cámara no registra a quien no vino.
    AUTOMATICO("Cámara"),

    // Un admin: la cargó después (RF-22) o registró la entrada sin cámara (ADR-0021), con
    // motivo del catálogo.
    MANUAL("Manual"),

    // La generó el job al terminar la clase sin ninguna marca (RF-19). Siempre es una ausencia,
    // y nunca tiene jornada.
    SISTEMA("Sistema");

    private final String etiqueta;

    MetodoAsistencia(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    // Nombre para mostrar en pantalla y en el PDF. El CSV lleva el valor, que es el dato crudo.
    public String getEtiqueta() {
        return etiqueta;
    }
}
