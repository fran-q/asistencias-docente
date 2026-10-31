package edu.cent35.asistencias.service;

import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_face.LBPHFaceRecognizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;

/**
 * La comparación por histogramas contra OpenCV real, con imágenes sintéticas.
 *
 * <p>El umbral y el margen se calibraron con mediciones de {@code predict}. Si la distancia de este
 * camino se separara de la de {@code predict}, esa calibración dejaría de valer sin que nada
 * falle: el sistema empezaría a aceptar y a rechazar otras caras.
 */
class MotorLbphServiceTest {

    private final MotorLbphService motor = new MotorLbphService();
    private final Random azar = new Random(42);

    @Test
    @DisplayName("La distancia es la misma que da predict, contra cualquier modelo")
    void laDistanciaEsLaDePredict() {
        for (int i = 0; i < 5; i++) {
            byte[] modelo = motor.entrenar(capturas(5));
            try (Mat rostro = imagen();
                 LBPHFaceRecognizer reconocedor = motor.deserializar(modelo);
                 MotorLbphService.Histogramas histogramas = motor.cargarHistogramas(modelo);
                 Mat histogramaDelRostro = motor.histogramaDe(rostro);
                 IntPointer etiqueta = new IntPointer(1);
                 DoublePointer distanciaDePredict = new DoublePointer(1)) {

                reconocedor.predict(rostro, etiqueta, distanciaDePredict);

                assertThat(motor.distancia(histogramas, histogramaDelRostro))
                    .isCloseTo(distanciaDePredict.get(0), within(1e-9));
            }
        }
    }

    @Test
    @DisplayName("Una captura de entrenamiento queda a distancia cero de su propio modelo")
    void unaCapturaPropiaDaCero() {
        List<Mat> capturas = capturas(3);
        byte[] modelo = motor.entrenar(capturas);

        try (MotorLbphService.Histogramas histogramas = motor.cargarHistogramas(modelo);
             Mat histogramaDelRostro = motor.histogramaDe(capturas.get(1))) {
            assertThat(motor.distancia(histogramas, histogramaDelRostro)).isCloseTo(0.0, within(1e-9));
        }
    }

    private List<Mat> capturas(int cuantas) {
        List<Mat> capturas = new ArrayList<>();
        for (int i = 0; i < cuantas; i++) {
            capturas.add(imagen());
        }
        return capturas;
    }

    // Una imagen del tamaño del rostro normalizado, con ruido: alcanza para que los histogramas
    // difieran, y lo que se compara es la cuenta y no el reconocimiento de una cara.
    private Mat imagen() {
        Mat imagen = new Mat(200, 200, CV_8UC1);
        byte[] pixeles = new byte[200 * 200];
        azar.nextBytes(pixeles);
        imagen.data().put(pixeles);
        return imagen;
    }
}
