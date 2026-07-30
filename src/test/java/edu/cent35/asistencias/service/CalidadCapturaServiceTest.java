package edu.cent35.asistencias.service;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;

/**
 * Cubre las mediciones que deciden si una captura sirve para entrenar. Se arman imágenes
 * sintéticas con la propiedad que se quiere probar —plana, oscura, nítida— porque así se
 * verifica cada umbral por separado, sin depender de una cámara ni de la luz del día.
 */
class CalidadCapturaServiceTest {

    private static final int LADO_CUADRO = 400;

    private CalidadCapturaService service;

    @BeforeEach
    void setUp() {
        service = new CalidadCapturaService();
        ReflectionTestUtils.setField(service, "nitidezMinima", 45.0);
        ReflectionTestUtils.setField(service, "brilloMinimo", 55.0);
        ReflectionTestUtils.setField(service, "brilloMaximo", 205.0);
        ReflectionTestUtils.setField(service, "contrasteMinimo", 22.0);
        ReflectionTestUtils.setField(service, "porcentajeCuadroMinimo", 6.0);
        ReflectionTestUtils.setField(service, "porcentajeCuadroMaximo", 55.0);
        ReflectionTestUtils.setField(service, "diferenciaMinima", 8.0);
    }

    // ========================================================================
    //  Encuadre
    // ========================================================================

    @Test
    @DisplayName("Un rostro diminuto pide acercarse, y lo dice antes que cualquier otra cosa")
    void rostroChico() {
        try (Mat cuadro = tablero(LADO_CUADRO)) {
            // 40x40 sobre 400x400 = 1% del cuadro, muy por debajo del 6% minimo.
            CalidadCapturaService.Medicion m = service.evaluar(cuadro, new Rect(0, 0, 40, 40));

            assertThat(m.apta()).isFalse();
            assertThat(m.motivo()).contains("Acercate");
            assertThat(m.porcentajeCuadro()).isLessThan(6.0);
        }
    }

    @Test
    @DisplayName("Un rostro que ocupa casi todo el cuadro pide alejarse")
    void rostroDemasiadoGrande() {
        try (Mat cuadro = tablero(LADO_CUADRO)) {
            // 380x380 sobre 400x400 = 90%, por encima del 55% maximo.
            CalidadCapturaService.Medicion m = service.evaluar(cuadro, new Rect(0, 0, 380, 380));

            assertThat(m.apta()).isFalse();
            assertThat(m.motivo()).contains("Alejate");
        }
    }

    // ========================================================================
    //  Luz
    // ========================================================================

    @Test
    @DisplayName("Una imagen oscura se rechaza por falta de luz")
    void imagenOscura() {
        try (Mat cuadro = uniforme(LADO_CUADRO, 20)) {
            CalidadCapturaService.Medicion m = service.evaluar(cuadro, zonaCentral());

            assertThat(m.apta()).isFalse();
            assertThat(m.motivo()).contains("poca luz");
            assertThat(m.brillo()).isLessThan(55.0);
        }
    }

    @Test
    @DisplayName("Una imagen quemada se rechaza por exceso de luz")
    void imagenQuemada() {
        try (Mat cuadro = uniforme(LADO_CUADRO, 245)) {
            CalidadCapturaService.Medicion m = service.evaluar(cuadro, zonaCentral());

            assertThat(m.apta()).isFalse();
            assertThat(m.motivo()).contains("demasiada luz");
        }
    }

    @Test
    @DisplayName("Una imagen sin relieve se rechaza por plana, aunque el brillo sea correcto")
    void imagenPlana() {
        // Gris medio: el brillo esta perfecto, pero no hay ninguna variacion.
        try (Mat cuadro = uniforme(LADO_CUADRO, 128)) {
            CalidadCapturaService.Medicion m = service.evaluar(cuadro, zonaCentral());

            assertThat(m.apta()).isFalse();
            assertThat(m.motivo()).contains("plana");
            assertThat(m.brillo()).isBetween(55.0, 205.0);
            assertThat(m.contraste()).isLessThan(22.0);
        }
    }

    // ========================================================================
    //  Nitidez
    // ========================================================================

    @Test
    @DisplayName("Una imagen con bordes marcados pasa todos los controles")
    void imagenNitida() {
        try (Mat cuadro = tablero(LADO_CUADRO)) {
            CalidadCapturaService.Medicion m = service.evaluar(cuadro, zonaCentral());

            assertThat(m.apta())
                .as("un tablero tiene bordes, brillo medio y contraste de sobra: %s", m.motivo())
                .isTrue();
            assertThat(m.motivo()).isNull();
            assertThat(m.nitidez()).isGreaterThan(45.0);
        }
    }

    // ========================================================================
    //  Diferencia entre capturas
    // ========================================================================

    @Test
    @DisplayName("Dos capturas idénticas no se consideran poses distintas")
    void capturasIdenticas() {
        try (Mat uno = tablero(200); Mat otro = tablero(200)) {
            assertThat(service.diferencia(uno, otro)).isZero();
            assertThat(service.esNovedoso(otro, java.util.List.of(uno)))
                .as("es lo que impide completar las cinco etapas sin haberse movido")
                .isFalse();
        }
    }

    @Test
    @DisplayName("Dos capturas distintas sí cuentan como poses distintas")
    void capturasDistintas() {
        try (Mat claro = uniforme(200, 200); Mat oscuro = uniforme(200, 40)) {
            assertThat(service.diferencia(claro, oscuro)).isGreaterThan(8.0);
            assertThat(service.esNovedoso(oscuro, java.util.List.of(claro))).isTrue();
        }
    }

    @Test
    @DisplayName("Basta parecerse a UNA de las aceptadas para quedar descartada")
    void bastaParecerseAUna() {
        try (Mat aceptadaA = uniforme(200, 200);
             Mat aceptadaB = uniforme(200, 40);
             Mat candidata  = uniforme(200, 201)) {

            // Es distinta de aceptadaB pero casi igual a aceptadaA: no aporta nada nuevo.
            assertThat(service.esNovedoso(candidata, java.util.List.of(aceptadaA, aceptadaB)))
                .isFalse();
        }
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    // Rostro centrado que ocupa el 25% del cuadro: dentro del rango aceptado.
    private Rect zonaCentral() {
        int lado = LADO_CUADRO / 2;
        return new Rect(LADO_CUADRO / 4, LADO_CUADRO / 4, lado, lado);
    }

    // Imagen de un solo tono: sin bordes y sin contraste.
    private Mat uniforme(int lado, int tono) {
        return new Mat(lado, lado, CV_8UC1, new Scalar(tono, 0, 0, 0));
    }

    // Tablero de ajedrez de 10 px: bordes marcados, brillo medio y contraste alto.
    private Mat tablero(int lado) {
        Mat m = new Mat(lado, lado, CV_8UC1, new Scalar(0, 0, 0, 0));
        var indexer = m.createIndexer();
        for (int y = 0; y < lado; y++) {
            for (int x = 0; x < lado; x++) {
                boolean claro = ((x / 10) + (y / 10)) % 2 == 0;
                ((org.bytedeco.javacpp.indexer.UByteIndexer) indexer)
                    .put(y, x, claro ? 255 : 0);
            }
        }
        return m;
    }
}
