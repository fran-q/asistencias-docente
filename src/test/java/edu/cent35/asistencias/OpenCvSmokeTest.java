package edu.cent35.asistencias;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_face.LBPHFaceRecognizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;

/**
 * Smoke test del entorno JavaCV / OpenCV (Sprint 4 Fase A).
 * <p>
 * No prueba logica de negocio: solo verifica que los <b>binarios nativos</b>
 * de OpenCV cargan correctamente en esta maquina y que el modulo
 * {@code opencv_face} (contrib) - necesario para {@code LBPHFaceRecognizer} -
 * esta disponible.
 * <p>
 * Si este test falla, el problema es de configuracion del entorno
 * (dependencia JavaCV, binarios nativos, plataforma) y hay que resolverlo
 * antes de avanzar con el reconocimiento facial. Ver ADR-0007.
 */
class OpenCvSmokeTest {

    @Test
    @DisplayName("OpenCV: los binarios nativos cargan y se puede operar una Mat")
    void opencvNativeLoads() {
        // Una Mat 3x3 de 8 bits / 1 canal llena de ceros.
        // Si los .dll nativos no cargaran, este constructor tiraria
        // UnsatisfiedLinkError.
        try (Mat m = new Mat(3, 3, CV_8UC1, new Scalar(0))) {
            assertThat(m.rows()).isEqualTo(3);
            assertThat(m.cols()).isEqualTo(3);
            assertThat(m.channels()).isEqualTo(1);
            assertThat(m.empty()).isFalse();
        }
    }

    @Test
    @DisplayName("opencv_face: LBPHFaceRecognizer se puede instanciar (modulo contrib presente)")
    void lbphRecognizerDisponible() {
        // create() falla si el modulo opencv_face no esta en el classpath
        // nativo - es decir, si el paquete de OpenCV no incluye los modulos
        // contrib.
        try (LBPHFaceRecognizer recognizer = LBPHFaceRecognizer.create()) {
            assertThat(recognizer).isNotNull();
            assertThat(recognizer.isNull()).isFalse();
        }
    }
}
