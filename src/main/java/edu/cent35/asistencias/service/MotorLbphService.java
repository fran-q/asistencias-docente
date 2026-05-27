package edu.cent35.asistencias.service;

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

/**
 * Motor de reconocimiento facial basado en LBPH (Local Binary Patterns
 * Histograms) de OpenCV. Encapsula todo el uso directo de
 * {@code LBPHFaceRecognizer}.
 * <p>
 * Diseño "un modelo por docente" (ADR-0007): cada modelo se entrena solo
 * con las capturas de una persona. Por eso todas las imágenes de
 * entrenamiento llevan la misma etiqueta {@link #LABEL_DOCENTE}: en la
 * predicción lo que importa no es la etiqueta sino la distancia de
 * confianza.
 */
@Service
@Slf4j
public class MotorLbphService {

    /**
     * Etiqueta única usada al entrenar. Como cada modelo es de un solo
     * docente, el valor concreto es irrelevante; lo relevante de
     * {@code predict()} es la distancia, no el label.
     */
    public static final int LABEL_DOCENTE = 1;

    /**
     * Entrena un modelo LBPH con los rostros dados y lo devuelve serializado.
     *
     * @param rostros lista de rostros ya normalizados (gris, mismo tamaño)
     * @return el modelo LBPH serializado (formato YAML de OpenCV)
     */
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

    /**
     * Reconstruye un {@link LBPHFaceRecognizer} a partir del modelo
     * previamente entrenado y comprimido. Usado en la fase de
     * identificación (Fase D).
     */
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
     * Serializa un recognizer ya entrenado a bytes. OpenCV solo sabe
     * guardar a un archivo, así que se usa uno temporal de paso.
     */
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

    /**
     * Comprime con gzip. El YAML de OpenCV tiene mucha repetición (números
     * en texto) y comprime muy bien — típicamente 5-10x. Esto evita que el
     * INSERT supere el {@code max_allowed_packet} de MariaDB.
     */
    private byte[] comprimir(byte[] datos) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(datos);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo comprimir el modelo LBPH.", e);
        }
        return out.toByteArray();
    }

    /** Descomprime gzip. Inverso de {@link #comprimir(byte[])}. */
    private byte[] descomprimir(byte[] comprimido) {
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(comprimido))) {
            return gz.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo descomprimir el modelo LBPH.", e);
        }
    }

    private void borrarTemporal(File tmp) {
        if (tmp == null) return;
        try {
            Files.deleteIfExists(tmp.toPath());
        } catch (IOException e) {
            log.warn("No se pudo borrar el temporal del modelo LBPH: {}", tmp, e);
        }
    }
}
