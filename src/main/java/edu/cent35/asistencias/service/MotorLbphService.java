package edu.cent35.asistencias.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_face.LBPHFaceRecognizer;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.bytedeco.opencv.global.opencv_core.CV_32SC1;
import static org.bytedeco.opencv.global.opencv_imgproc.HISTCMP_CHISQR_ALT;
import static org.bytedeco.opencv.global.opencv_imgproc.compareHist;

/**
 * Motor de reconocimiento facial basado en LBPH (Local Binary Patterns
 * Histograms) de OpenCV. Encapsula todo el uso directo de
 * {@code LBPHFaceRecognizer}.
 * <p>
 * Diseño "un modelo por docente" (ADR-0007): cada modelo se entrena solo
 * con las capturas de una persona. Por eso todas las imágenes de
 * entrenamiento llevan la misma etiqueta {@link #LABEL_DOCENTE}: al comparar
 * lo que importa no es la etiqueta sino la distancia.
 */
@Service
@Slf4j
public class MotorLbphService {

    // Etiqueta fija de entrenamiento: cada modelo es de un solo docente, así que lo que importa
    // de predict() es la distancia y no el label.
    public static final int LABEL_DOCENTE = 1;

    /**
     * Carga el reconocedor al arrancar, para que una biblioteca faltante frene el sistema acá.
     *
     * <p>Sin esto OpenCV carga el módulo recién con la primera identificación. En un servidor sin
     * GTK 2 (ADR-0007, aprendizaje 9) el sistema arrancaba, la salud daba bien, y la falla aparecía
     * con el primer docente frente a la cámara. Un despliegue roto tiene que romper al desplegarlo.
     */
    @PostConstruct
    void comprobarQueOpenCvCarga() {
        try (LBPHFaceRecognizer recognizer = LBPHFaceRecognizer.create()) {
            log.info("Biblioteca de reconocimiento lista: LBPH con radio {}.", recognizer.getRadius());
        }
    }

    // Entrena un modelo LBPH con los rostros normalizados y lo devuelve ya serializado.
    public byte[] entrenar(List<Mat> rostros) {
        if (rostros == null || rostros.isEmpty()) {
            throw new IllegalArgumentException("Se necesita al menos un rostro para entrenar.");
        }

        int n = rostros.size();
        try (LBPHFaceRecognizer recognizer = LBPHFaceRecognizer.create();
             MatVector imagenes = new MatVector(n);
             Mat labels = new Mat(n, 1, CV_32SC1)) {

            IntBuffer labelsBuffer = labels.createBuffer();
            for (int i = 0; i < n; i++) {
                imagenes.put(i, rostros.get(i));
                labelsBuffer.put(i, LABEL_DOCENTE);
            }

            recognizer.train(imagenes, labels);
            byte[] yaml = serializar(recognizer);
            byte[] comprimido = comprimir(yaml);
            log.info("Modelo LBPH entrenado con {} rostros: {} bytes YAML → {} bytes gzip ({}%).",
                     n, yaml.length, comprimido.length,
                     yaml.length == 0 ? 0 : (comprimido.length * 100 / yaml.length));
            return comprimido;
        }
    }

    // Reconstruye el recognizer a partir del modelo entrenado y comprimido que hay en la base.
    public LBPHFaceRecognizer deserializar(byte[] modeloComprimido) {
        byte[] yaml = descomprimir(modeloComprimido);
        File tmp = null;
        try {
            tmp = File.createTempFile("lbph-modelo", ".yml");
            Files.write(tmp.toPath(), yaml);
            LBPHFaceRecognizer recognizer = LBPHFaceRecognizer.create();
            recognizer.read(tmp.getAbsolutePath());
            return recognizer;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo deserializar el modelo LBPH.", e);
        } finally {
            borrarTemporal(tmp);
        }
    }

    /**
     * Los histogramas de un modelo guardado, que es lo único que hace falta para comparar contra él.
     *
     * <p>El reconocedor se cierra enseguida: los histogramas quedan vivos en el vector, porque
     * OpenCV cuenta las referencias de sus matrices.
     */
    public Histogramas cargarHistogramas(byte[] modeloComprimido) {
        try (LBPHFaceRecognizer recognizer = deserializar(modeloComprimido)) {
            return new Histogramas(recognizer.getHistograms());
        }
    }

    /**
     * El histograma de un rostro, calculado igual que lo calcula {@code predict}.
     *
     * <p>Se calcula <b>una vez por cuadro</b>. Es la razón de que exista este método y de que no
     * se llame a {@code predict} de cada modelo: {@code predict} vuelve a calcular el histograma
     * del rostro cada vez, y eso era lo más caro de identificar. Medido con 400 docentes: 3,7 s
     * por cuadro con un {@code predict} por modelo, menos de un segundo así. El reconocedor de
     * paso es la única forma de pedírselo a OpenCV con los mismos parámetros.
     */
    public Mat histogramaDe(Mat rostro) {
        try (LBPHFaceRecognizer recognizer = LBPHFaceRecognizer.create();
             MatVector imagenes = new MatVector(1);
             Mat labels = new Mat(1, 1, CV_32SC1)) {
            imagenes.put(0, rostro);
            labels.<IntBuffer>createBuffer().put(0, LABEL_DOCENTE);
            recognizer.train(imagenes, labels);
            try (MatVector histogramas = recognizer.getHistograms();
                 Mat histograma = histogramas.get(0)) {
                return histograma.clone();
            }
        }
    }

    /**
     * La distancia de un rostro a un modelo: la menor contra cada una de sus capturas.
     *
     * <p>Es exactamente la cuenta de {@code predict} (chi-cuadrado alternativo, y se queda con el
     * mínimo), así que el umbral y el margen calibrados siguen valiendo. Lo sostiene
     * {@code MotorLbphServiceTest}, que compara las dos contra el mismo modelo.
     */
    public double distancia(Histogramas modelo, Mat histogramaDelRostro) {
        double menor = Double.MAX_VALUE;
        for (Mat histograma : modelo.matrices) {
            menor = Math.min(menor, compareHist(histograma, histogramaDelRostro, HISTCMP_CHISQR_ALT));
        }
        return menor;
    }

    /**
     * Los histogramas de un modelo, en memoria nativa y <b>en claro</b>: con ellos se puede
     * reconocer a la persona. Hay que cerrarlos cuando dejan de usarse.
     */
    public static final class Histogramas implements AutoCloseable {

        private final MatVector vector;
        private final Mat[] matrices;

        private Histogramas(MatVector vector) {
            this.vector = vector;
            this.matrices = new Mat[(int) vector.size()];
            for (int i = 0; i < matrices.length; i++) {
                matrices[i] = vector.get(i);
            }
        }

        // Libera la memoria nativa de las matrices y del vector.
        @Override
        public void close() {
            for (Mat matriz : matrices) {
                matriz.close();
            }
            vector.close();
        }
    }

    // Pasa el recognizer a bytes; va por un archivo temporal porque OpenCV solo guarda a disco.
    private byte[] serializar(LBPHFaceRecognizer recognizer) {
        File tmp = null;
        try {
            tmp = File.createTempFile("lbph-modelo", ".yml");
            recognizer.save(tmp.getAbsolutePath());
            return Files.readAllBytes(tmp.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo serializar el modelo LBPH.", e);
        } finally {
            borrarTemporal(tmp);
        }
    }

    // Comprime con gzip: el YAML de OpenCV baja 5-10x y así el INSERT no pasa el max_allowed_packet.
    private byte[] comprimir(byte[] datos) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(datos);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo comprimir el modelo LBPH.", e);
        }
        return out.toByteArray();
    }

    // Descomprime gzip. Inverso de comprimir(byte[]).
    private byte[] descomprimir(byte[] comprimido) {
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(comprimido))) {
            return gz.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo descomprimir el modelo LBPH.", e);
        }
    }

    // Borra el archivo temporal de paso, avisando por log si no se pudo.
    private void borrarTemporal(File tmp) {
        if (tmp == null) return;
        try {
            Files.deleteIfExists(tmp.toPath());
        } catch (IOException e) {
            log.warn("No se pudo borrar el temporal del modelo LBPH: {}", tmp, e);
        }
    }
}
