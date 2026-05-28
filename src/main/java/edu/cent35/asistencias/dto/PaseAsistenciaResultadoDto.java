package edu.cent35.asistencias.dto;

/**
 * Respuesta del endpoint de pase de asistencia automático.
 * <p>
 * Combina lo que devuelve la identificación facial (rostro detectado,
 * docente reconocido, bbox) con el resultado del intento de marcar
 * asistencia (estado, comisión, si ya estaba marcada).
 *
 * @param rostroDetectado    true si la imagen contiene un rostro válido
 * @param reconocido         true si se identificó a un docente bajo el umbral
 * @param docenteId          docente identificado, o null
 * @param docenteNombre      "Apellido, Nombre" del docente, o null
 * @param distancia          distancia LBPH del match (menor = más parecido), o null
 * @param x,y,ancho,alto     bounding box del rostro detectado
 * @param asistenciaMarcada  true si quedó una marca persistida (nueva o ya existente)
 * @param yaEstaba           true si la marca ya existía (idempotente)
 * @param estadoAsistencia   "PRESENTE" o "TARDE" (null si no se marcó)
 * @param claseLabel         "Comisión A - Matemática (18:00-20:00)" o similar (null si no se marcó)
 * @param mensaje            texto para mostrar al operador
 */
public record PaseAsistenciaResultadoDto(
    boolean rostroDetectado,
    boolean reconocido,
    Long docenteId,
    String docenteNombre,
    Double distancia,
    Integer x,
    Integer y,
    Integer ancho,
    Integer alto,
    boolean asistenciaMarcada,
    boolean yaEstaba,
    String estadoAsistencia,
    String claseLabel,
    String mensaje
) {

    public static PaseAsistenciaResultadoDto sinRostro() {
        return new PaseAsistenciaResultadoDto(
            false, false, null, null, null,
            null, null, null, null,
            false, false, null, null,
            "No se detectó ningún rostro.");
    }

    public static PaseAsistenciaResultadoDto noReconocido(double distancia,
                                                          int x, int y, int ancho, int alto) {
        return new PaseAsistenciaResultadoDto(
            true, false, null, null, distancia,
            x, y, ancho, alto,
            false, false, null, null,
            "Rostro no reconocido.");
    }

    public static PaseAsistenciaResultadoDto reconocidoSinClase(Long docenteId, String nombre,
                                                                double distancia,
                                                                int x, int y, int ancho, int alto,
                                                                String motivo) {
        return new PaseAsistenciaResultadoDto(
            true, true, docenteId, nombre, distancia,
            x, y, ancho, alto,
            false, false, null, null,
            motivo);
    }

    public static PaseAsistenciaResultadoDto marcado(Long docenteId, String nombre, double distancia,
                                                     int x, int y, int ancho, int alto,
                                                     boolean yaEstaba, String estado, String claseLabel) {
        String prefijo = yaEstaba ? "Ya estaba marcado: " : "Asistencia marcada: ";
        return new PaseAsistenciaResultadoDto(
            true, true, docenteId, nombre, distancia,
            x, y, ancho, alto,
            true, yaEstaba, estado, claseLabel,
            prefijo + estado + " en " + claseLabel);
    }
}
