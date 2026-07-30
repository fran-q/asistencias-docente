package edu.cent35.asistencias.model;

import lombok.Getter;

/**
 * Las poses que se le piden a la persona durante el registro guiado del rostro, en orden.
 * Existen para inducir variedad: un modelo entrenado con la misma pose repetida no tolera
 * después que el docente se pare apenas distinto frente a la cámara del pase.
 */
@Getter
public enum EtapaCaptura {

    // Los giros son SUAVES a proposito. El Haar Cascade que entrena y reconoce es de rostro
    // frontal: con un perfil marcado directamente no encuentra la cara, y si llegara a
    // encontrar algo seria un recorte mal alineado que ensucia el modelo en vez de mejorarlo.
    FRENTE("Mirá de frente a la cámara",
           "Cara centrada, expresión natural."),

    LEVE_IZQUIERDA("Girá apenas la cabeza a tu izquierda",
                   "Poco, como para mirar a alguien que está al lado. Sin llegar a perfil."),

    LEVE_DERECHA("Ahora girá apenas a tu derecha",
                 "Lo mismo para el otro lado, sin exagerar."),

    LEVE_ARRIBA("Levantá un poco el mentón",
                "Apenas, como si miraras algo un poco más alto que la cámara."),

    MAS_CERCA("Acercate un paso a la cámara",
              "Que la cara ocupe más del cuadro, sin salirse.");

    private final String instruccion;
    private final String detalle;

    EtapaCaptura(String instruccion, String detalle) {
        this.instruccion = instruccion;
        this.detalle = detalle;
    }
}
