package edu.cent35.asistencias.service;

import edu.cent35.asistencias.dto.DeteccionRostroDto;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_COLOR;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imdecode;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgproc.equalizeHist;
import static org.bytedeco.opencv.global.opencv_imgproc.resize;

/**
 * Primera etapa del pipeline facial: ubica rostros en una imagen con el Haar Cascade de
 * OpenCV, que se carga una sola vez al arrancar. Solo responde "hay cara acá y en estas
 * coordenadas": no identifica de quién es ni persiste nada.
 */
@Service
@Slf4j
public class DeteccionRostroService {

    // Lado mínimo (px) de un rostro para considerarlo válido; descarta ruido.
    private static final int LADO_MINIMO_ROSTRO = 80;

    private CascadeClassifier clasificadorRostro;

    // Carga el Haar Cascade al arrancar; el XML del classpath se copia a disco porque
    // CascadeClassifier solo acepta una ruta de archivo.
    @PostConstruct
    void cargarClasificador() {
        try {
            ClassPathResource recurso =
                new ClassPathResource("opencv/haarcascade_frontalface_default.xml");
            File tmp = File.createTempFile("haarcascade_frontalface", ".xml");
            tmp.deleteOnExit();
            try (InputStream in = recurso.getInputStream()) {
                Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            clasificadorRostro = new CascadeClassifier(tmp.getAbsolutePath());
            if (clasificadorRostro.empty()) {
                throw new IllegalStateException(
                    "El Haar Cascade se cargó vacío: " + tmp.getAbsolutePath());
            }
            log.info("Haar Cascade de rostros cargado correctamente.");
        } catch (IOException e) {
            throw new IllegalStateException(
                "No se pudo cargar el Haar Cascade de detección de rostros.", e);
        }
    }

    // Cuenta los rostros de la imagen y devuelve el recuadro del más grande (el más cercano).
    public DeteccionRostroDto detectar(byte[] imagenBytes) {
        if (imagenBytes == null || imagenBytes.length == 0) {
            return DeteccionRostroDto.sinRostro("No se recibió ninguna imagen.");
        }

        Mat codificada = null;
        Mat imagen = null;
        Mat gris = null;
        try (RectVector rostros = new RectVector()) {
            codificada = new Mat(new BytePointer(imagenBytes));
            imagen = imdecode(codificada, IMREAD_COLOR);
            if (imagen == null || imagen.empty()) {
                return DeteccionRostroDto.sinRostro(
                    "La imagen no se pudo procesar. Probá sacar otra foto.");
            }

            gris = new Mat();
            cvtColor(imagen, gris, COLOR_BGR2GRAY);
            equalizeHist(gris, gris);   // mejora el contraste para la detección

            clasificadorRostro.detectMultiScale(
                gris, rostros,
                1.1,                                      // scaleFactor
                5,                                        // minNeighbors
                0,                                        // flags
                new Size(LADO_MINIMO_ROSTRO, LADO_MINIMO_ROSTRO),  // minSize
                new Size()                                // maxSize (sin límite)
            );

            long cantidad = rostros.size();
            if (cantidad == 0) {
                return DeteccionRostroDto.sinRostro(
                    "No se detectó ningún rostro. Acercate y mirá de frente a la cámara.");
            }

            Rect masGrande = rostroMasGrande(rostros);
            return new DeteccionRostroDto(
                true,
                (int) cantidad,
                masGrande.x(), masGrande.y(), masGrande.width(), masGrande.height(),
                cantidad == 1
                    ? "Rostro detectado correctamente."
                    : "Se detectaron " + cantidad + " rostros. Debe haber solo una persona en cuadro."
            );
        } finally {
            if (gris != null)       gris.close();
            if (imagen != null)     imagen.close();
            if (codificada != null) codificada.close();
        }
    }

    // Rostro ya normalizado más sus coordenadas dentro de la imagen original.
    public record RostroExtraido(Mat rostro, int x, int y, int ancho, int alto) {}

    // Devuelve el rostro en gris y escalado a tamaño fijo; exige exactamente uno, si no null.
    // El que llama se tiene que encargar de cerrar el Mat.
    public RostroExtraido extraerRostroNormalizado(byte[] imagenBytes, int tamano) {
        if (imagenBytes == null || imagenBytes.length == 0) {
            return null;
        }
        Mat codificada = null;
        Mat imagen = null;
        Mat gris = null;
        try (RectVector rostros = new RectVector()) {
            codificada = new Mat(new BytePointer(imagenBytes));
            imagen = imdecode(codificada, IMREAD_COLOR);
            if (imagen == null || imagen.empty()) {
                return null;
            }
            gris = new Mat();
            cvtColor(imagen, gris, COLOR_BGR2GRAY);
            equalizeHist(gris, gris);

            clasificadorRostro.detectMultiScale(
                gris, rostros,
                1.1, 5, 0,
                new Size(LADO_MINIMO_ROSTRO, LADO_MINIMO_ROSTRO),
                new Size()
            );
            if (rostros.size() != 1) {
                return null;
            }

            Rect r = rostros.get(0);
            Mat rostroNormalizado = new Mat();
            try (Mat roi = new Mat(gris, r)) {
                resize(roi, rostroNormalizado, new Size(tamano, tamano));
            }
            return new RostroExtraido(rostroNormalizado, r.x(), r.y(), r.width(), r.height());
        } finally {
            if (gris != null)       gris.close();
            if (imagen != null)     imagen.close();
            if (codificada != null) codificada.close();
        }
    }

    // Devuelve el rectángulo de mayor área dentro del vector.
    // Elige el rostro de mayor área, que es el que está más cerca de la cámara.
    private Rect rostroMasGrande(RectVector rostros) {
        Rect mayor = rostros.get(0);
        long areaMayor = (long) mayor.width() * mayor.height();
        for (long i = 1; i < rostros.size(); i++) {
            Rect r = rostros.get(i);
            long area = (long) r.width() * r.height();
            if (area > areaMayor) {
                mayor = r;
                areaMayor = area;
            }
        }
        return mayor;
    }
}
