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

    /**
     * Hay más de una cara en el cuadro.
     *
     * <p>Se distingue de "no hay ninguna" porque para quien está frente a la cámara son cosas
     * opuestas, y avisar que no se detecta ningún rostro cuando hay dos personas mirando la
     * pantalla no ayuda a nadie a corregir.
     */
    public static IdentificacionResultadoDto variosRostros(int cantidad) {
        return new IdentificacionResultadoDto(true, false, null, null, null, null,
            null, null, null, null,
            "Hay " + cantidad + " personas en cuadro. Solo se puede marcar de a una: "
            + "que quede una sola frente a la cámara.");
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

    // Se detectó una cara pero ningún modelo quedó dentro del umbral: no está registrada.
    public static IdentificacionResultadoDto noReconocido(double mejorDistancia,
                                                          int x, int y, int ancho, int alto) {
        return new IdentificacionResultadoDto(true, false, null, null, null, mejorDistancia,
            x, y, ancho, alto, "Rostro no registrado.");
    }

    /**
     * El mejor candidato entró en el umbral, pero el segundo quedó demasiado cerca.
     *
     * <p>Se rechaza a propósito. LBPH no sabe decir "no conozco a esta persona": compara
     * contra cada modelo y devuelve el más parecido, aunque el parecido sea pobre. Cuando
     * dos modelos quedan empatados, cuál gana lo decide el ruido —un cambio de luz, una
     * sombra— y no la identidad. Marcar en esas condiciones es tirar una moneda.
     */
    public static IdentificacionResultadoDto ambiguo(double mejorDistancia,
                                                     int x, int y, int ancho, int alto) {
        return new IdentificacionResultadoDto(true, false, null, null, null, mejorDistancia,
            x, y, ancho, alto,
            "No se pudo distinguir entre dos rostros parecidos. Acercate y volvé a intentar.");
    }
}
