package edu.cent35.asistencias.dto;

/**
 * Resultado de identificar un rostro contra los modelos faciales activos
 * del tenant (Sprint 4 Fase D).
 *
 * @param rostroDetectado true si la imagen contiene un rostro válido
 * @param reconocido      true si además se identificó a un docente bajo el umbral
 * @param docenteId       id del docente identificado, o null
 * @param docenteNombre   "Apellido, Nombre" del docente, o null
 * @param distancia       distancia LBPH del mejor match (menor = más parecido), o null
 * @param mensaje         texto descriptivo para mostrar al usuario
 */
public record IdentificacionResultadoDto(
    boolean rostroDetectado,
    boolean reconocido,
    Long docenteId,
    String docenteNombre,
    Double distancia,
    Integer x,
    Integer y,
    Integer ancho,
    Integer alto,
    String mensaje
) {

    public static IdentificacionResultadoDto sinRostro() {
        return new IdentificacionResultadoDto(false, false, null, null, null,
            null, null, null, null, "No se detectó ningún rostro.");
    }

    public static IdentificacionResultadoDto noHayModelos(int x, int y, int ancho, int alto) {
        return new IdentificacionResultadoDto(true, false, null, null, null,
            x, y, ancho, alto, "Ningún docente tiene modelo facial registrado.");
    }

    public static IdentificacionResultadoDto match(Long docenteId, String nombre, double distancia,
                                                   int x, int y, int ancho, int alto) {
        return new IdentificacionResultadoDto(true, true, docenteId, nombre, distancia,
            x, y, ancho, alto, "Rostro presente: " + nombre);
    }

    public static IdentificacionResultadoDto noReconocido(double mejorDistancia,
                                                          int x, int y, int ancho, int alto) {
        return new IdentificacionResultadoDto(true, false, null, null, mejorDistancia,
            x, y, ancho, alto, "Rostro no reconocido.");
    }
}
