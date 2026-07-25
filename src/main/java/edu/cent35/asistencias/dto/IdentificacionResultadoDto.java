package edu.cent35.asistencias.dto;

/**
 * Resultado de identificar un rostro contra los modelos faciales activos
 * del tenant (Sprint 4 Fase D).
 *
 * @param rostroDetectado true si la imagen contiene un rostro válido
 * @param reconocido      true si además se identificó a un docente bajo el umbral
 * @param docenteId       id del docente identificado, o null
 * @param docenteNombre   "Apellido, Nombre" del docente, o null
 * @param modeloFacialId  id del modelo LBPH que produjo el mejor match (sprint 5: lo usa el pase de asistencia)
 * @param distancia       distancia LBPH del mejor match (menor = más parecido), o null
 * @param x               coord. X del bounding box del rostro detectado
 * @param y               coord. Y
 * @param ancho           ancho del bounding box
 * @param alto            alto del bounding box
 * @param mensaje         texto descriptivo
 */
public record IdentificacionResultadoDto(
    boolean rostroDetectado,
    boolean reconocido,
    Long docenteId,
    String docenteNombre,
    Long modeloFacialId,
    Double distancia,
    Integer x,
    Integer y,
    Integer ancho,
    Integer alto,
    String mensaje
) {

    // No se detectó ninguna cara en el frame (o se detectó más de una).
    public static IdentificacionResultadoDto sinRostro() {
        return new IdentificacionResultadoDto(false, false, null, null, null, null,
            null, null, null, null, "No se detectó ningún rostro.");
    }

    // La institución todavía no tiene ningún rostro registrado contra el cual comparar.
    public static IdentificacionResultadoDto noHayModelos(int x, int y, int ancho, int alto) {
        return new IdentificacionResultadoDto(true, false, null, null, null, null,
            x, y, ancho, alto, "Ningún docente tiene modelo facial registrado.");
    }

    // Hubo coincidencia por debajo del umbral: se identificó al docente.
    public static IdentificacionResultadoDto match(Long docenteId, String nombre,
                                                   Long modeloFacialId, double distancia,
                                                   int x, int y, int ancho, int alto) {
        return new IdentificacionResultadoDto(true, true, docenteId, nombre,
            modeloFacialId, distancia,
            x, y, ancho, alto, "Rostro presente: " + nombre);
    }

    // Se detectó una cara pero ningún modelo quedó dentro del umbral.
    public static IdentificacionResultadoDto noReconocido(double mejorDistancia,
                                                          int x, int y, int ancho, int alto) {
        return new IdentificacionResultadoDto(true, false, null, null, null, mejorDistancia,
            x, y, ancho, alto, "Rostro no reconocido.");
    }
}
