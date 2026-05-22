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

/**
 * Detección de rostros en imágenes usando OpenCV (Haar Cascade).
 * Cubre la primera etapa del pipeline de reconocimiento facial (Sprint 4):
 * dada una imagen, ubicar si hay un rostro y dónde.
 * <p>
 * No persiste nada ni hace reconocimiento (identificar de quién es el
 * rostro) — eso son las fases C y D. Esta clase solo responde "hay cara
 * acá y está en estas coordenadas".
 * <p>
 * El clasificador Haar se carga una vez al iniciar la aplicación desde
 * {@code resources/opencv/haarcascade_frontalface_default.xml}.
 */
@Service
@Slf4j
public class DeteccionRostroService {

    /** Lado mínimo (px) de un rostro para considerarlo válido; descarta ruido. */
    private static final int LADO_MINIMO_ROSTRO = 80;

    private CascadeClassifier clasificadorRostro;

    /**
     * Carga el Haar Cascade al arrancar. {@link CascadeClassifier} necesita
     * una ruta de archivo en disco, así que el XML del classpath se copia a
     * un archivo temporal.
     */
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

    /**
     * Detecta rostros en una imagen ya decodificada (JPEG/PNG en bytes).
     *
     * @param imagenBytes contenido binario de la imagen
     * @return resumen de la detección: cuántos rostros y el bounding box
     *         del más grande (el más cercano a la cámara)
     */
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

    /** Devuelve el rectángulo de mayor área dentro del vector. */
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
