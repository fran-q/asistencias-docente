package edu.cent35.asistencias.service;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static org.bytedeco.opencv.global.opencv_core.CV_64F;
import static org.bytedeco.opencv.global.opencv_core.absdiff;
import static org.bytedeco.opencv.global.opencv_core.mean;
import static org.bytedeco.opencv.global.opencv_core.meanStdDev;
import static org.bytedeco.opencv.global.opencv_imgproc.Laplacian;

/**
 * Mide si una captura sirve para entrenar un modelo facial: nitidez, luz y encuadre. Antes
 * de esto alcanzaba con que el detector encontrara una cara, así que un cuadro borroso o a
 * contraluz entraba al entrenamiento en silencio y degradaba el modelo sin dejar rastro.
 */
@Service
@Slf4j
public class CalidadCapturaService {

    @Value("${app.biometria.calidad.nitidez-minima}")
    private double nitidezMinima;

    @Value("${app.biometria.calidad.brillo-minimo}")
    private double brilloMinimo;

    @Value("${app.biometria.calidad.brillo-maximo}")
    private double brilloMaximo;

    @Value("${app.biometria.calidad.contraste-minimo}")
    private double contrasteMinimo;

    @Value("${app.biometria.calidad.porcentaje-cuadro-minimo}")
    private double porcentajeCuadroMinimo;

    @Value("${app.biometria.calidad.porcentaje-cuadro-maximo}")
    private double porcentajeCuadroMaximo;

    @Value("${app.biometria.calidad.diferencia-minima}")
    private double diferenciaMinima;

    /**
     * Lo medido sobre una captura, más el veredicto ya resuelto.
     *
     * @param motivo qué corregir, en segunda persona y accionable; null si la captura sirve
     */
    public record Medicion(
        boolean apta,
        String motivo,
        double nitidez,
        double brillo,
        double contraste,
        double porcentajeCuadro
    ) {}

    /**
     * Evalúa el recorte del rostro dentro de su cuadro.
     *
     * @param grisOriginal imagen completa en gris SIN ecualizar, porque la ecualización
     *                     aplana el histograma y volvería inútil medir el brillo
     * @param rostro       recuadro del rostro dentro de esa imagen
     */
    public Medicion evaluar(Mat grisOriginal, Rect rostro) {
        double areaRostro = (double) rostro.width() * rostro.height();
        double areaCuadro = (double) grisOriginal.cols() * grisOriginal.rows();
        double porcentaje = areaCuadro == 0 ? 0 : (areaRostro / areaCuadro) * 100.0;

        double nitidez;
        double brillo;
        double contraste;

        try (Mat roi = new Mat(grisOriginal, rostro)) {
            nitidez = varianzaDelLaplaciano(roi);
            try (Mat media = new Mat(); Mat desvio = new Mat()) {
                meanStdDev(roi, media, desvio);
                brillo = media.createIndexer().getDouble(0);
                contraste = desvio.createIndexer().getDouble(0);
            }
        }

        // El orden importa: se devuelve UN solo motivo, el más urgente. Una lista de cinco
        // cosas para corregir a la vez no la sigue nadie parado frente a la camara.
        String motivo = null;
        if (porcentaje < porcentajeCuadroMinimo) {
            motivo = "Acercate un poco más a la cámara.";
        } else if (porcentaje > porcentajeCuadroMaximo) {
            motivo = "Alejate un poco de la cámara.";
        } else if (brillo < brilloMinimo) {
            motivo = "Hay poca luz. Buscá un lugar más iluminado.";
        } else if (brillo > brilloMaximo) {
            motivo = "Hay demasiada luz de frente o estás a contraluz.";
        } else if (contraste < contrasteMinimo) {
            motivo = "La imagen sale plana. Evitá tener una ventana o luz fuerte detrás.";
        } else if (nitidez < nitidezMinima) {
            motivo = "Quedate quieto un segundo, la imagen sale movida.";
        }

        return new Medicion(motivo == null, motivo, nitidez, brillo, contraste, porcentaje);
    }

    /**
     * Qué tan distintos son dos rostros ya normalizados, de 0 a 255.
     *
     * <p>Es lo que impide que la secuencia guiada se complete sin que la persona se haya
     * movido: sin esta comprobación, cinco etapas con la misma pose entrenarían igual de mal
     * que la grabación de treinta segundos que había antes.
     */
    public double diferencia(Mat unRostro, Mat otroRostro) {
        try (Mat delta = new Mat()) {
            absdiff(unRostro, otroRostro, delta);
            Scalar promedio = mean(delta);
            return promedio.get(0);
        }
    }

    // true si el rostro es suficientemente distinto de todos los ya aceptados.
    public boolean esNovedoso(Mat candidato, Iterable<Mat> yaAceptados) {
        for (Mat aceptado : yaAceptados) {
            if (diferencia(candidato, aceptado) < diferenciaMinima) {
                return false;
            }
        }
        return true;
    }

    public double getDiferenciaMinima() {
        return diferenciaMinima;
    }

    // Varianza del Laplaciano: mide cuánto borde tiene la imagen. Una foto movida o
    // desenfocada pierde los bordes finos, asi que su varianza cae.
    private double varianzaDelLaplaciano(Mat gris) {
        try (Mat laplaciano = new Mat(); Mat media = new Mat(); Mat desvio = new Mat()) {
            Laplacian(gris, laplaciano, CV_64F);
            meanStdDev(laplaciano, media, desvio);
            double d = desvio.createIndexer().getDouble(0);
            return d * d;
        }
    }
}
