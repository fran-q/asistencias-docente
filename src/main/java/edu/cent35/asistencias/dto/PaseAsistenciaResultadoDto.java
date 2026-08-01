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
 * @param confirmando        true mientras se sostiene la identidad y todavía no se marcó
 * @param progresoMs         milisegundos ya sostenidos, para dibujar el avance
 * @param objetivoMs         milisegundos que hay que sostener en total
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
    boolean confirmando,
    Long progresoMs,
    Long objetivoMs,
    String mensaje
) {

    // No se detectó ninguna cara en el frame.
    public static PaseAsistenciaResultadoDto sinRostro() {
        return new PaseAsistenciaResultadoDto(
            false, false, null, null, null,
            null, null, null, null,
            false, false, null, null,
            false, null, null,
            "No se detectó ningún rostro.");
    }

    /**
     * Se detectó una cara pero no se aceptó la identificación.
     *
     * <p>El motivo viaja tal cual lo dio la identificación en vez de un texto fijo, porque
     * "no estás registrado" y "no puedo distinguirte de otro" son problemas distintos: el
     * primero se resuelve registrando el rostro y el segundo, acercándose a la cámara.
     */
    public static PaseAsistenciaResultadoDto noReconocido(double distancia, String motivo,
                                                          int x, int y, int ancho, int alto) {
        return new PaseAsistenciaResultadoDto(
            true, false, null, null, distancia,
            x, y, ancho, alto,
            false, false, null, null,
            false, null, null,
            motivo);
    }

    /**
     * Se rechazó sin poder señalar a nadie en particular: es el caso de varias personas
     * en cuadro. Va sin recuadro a propósito, porque marcar a una de ellas sugeriría que
     * el sistema la eligió.
     */
    public static PaseAsistenciaResultadoDto rechazadoSinRecuadro(String motivo) {
        return new PaseAsistenciaResultadoDto(
            true, false, null, null, null,
            null, null, null, null,
            false, false, null, null,
            false, null, null,
            motivo);
    }

    // Se identificó al docente, pero en este momento no tiene ninguna clase en curso.
    public static PaseAsistenciaResultadoDto reconocidoSinClase(Long docenteId, String nombre,
                                                                double distancia,
                                                                int x, int y, int ancho, int alto,
                                                                String motivo) {
        return new PaseAsistenciaResultadoDto(
            true, true, docenteId, nombre, distancia,
            x, y, ancho, alto,
            false, false, null, null,
            false, null, null,
            motivo);
    }

    // Se identificó al docente pero todavía no se sostuvo lo suficiente como para marcar.
    // No se devuelve el nombre a propósito: mostrarlo antes de confirmar es justamente lo
    // que hace que alguien vea el nombre equivocado en pantalla durante un parpadeo.
    public static PaseAsistenciaResultadoDto confirmando(double distancia,
                                                         int x, int y, int ancho, int alto,
                                                         long progresoMs, long objetivoMs) {
        return new PaseAsistenciaResultadoDto(
            true, true, null, null, distancia,
            x, y, ancho, alto,
            false, false, null, null,
            true, progresoMs, objetivoMs,
            "Sostené la posición…");
    }

    // Se identificó al docente y quedó registrada su asistencia.
    public static PaseAsistenciaResultadoDto marcado(Long docenteId, String nombre, double distancia,
                                                     int x, int y, int ancho, int alto,
                                                     boolean yaEstaba, String estado, String claseLabel) {
        String prefijo = yaEstaba ? "Ya estaba marcado: " : "Asistencia marcada: ";
        return new PaseAsistenciaResultadoDto(
            true, true, docenteId, nombre, distancia,
            x, y, ancho, alto,
            true, yaEstaba, estado, claseLabel,
            false, null, null,
            prefijo + estado + " en " + claseLabel);
    }
}
