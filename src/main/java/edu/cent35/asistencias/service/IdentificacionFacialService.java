package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.dto.IdentificacionResultadoDto;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.ModeloFacial;
import edu.cent35.asistencias.repository.ModeloFacialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_face.LBPHFaceRecognizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Identifica a un docente a partir de una imagen, comparándola contra los
 * modelos faciales activos del tenant (Sprint 4 Fase D).
 * <p>
 * Mantiene un <b>cache en memoria</b> de los recognizers LBPH ya cargados.
 * Sin cache, cada identificación tendría que descifrar + descomprimir +
 * deserializar TODOS los modelos del tenant, lo que mata el rendimiento del
 * loop de identificación continua (varias llamadas por segundo).
 * <p>
 * El cache se sincroniza en cada llamada: si aparecen nuevos modelos activos
 * los carga, y si algunos dejaron de estar activos (por re-registro o baja
 * del docente) los descarta automáticamente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdentificacionFacialService {

    private final ModeloFacialRepository modeloFacialRepository;
    private final DeteccionRostroService deteccionRostroService;
    private final MotorLbphService motorLbph;
    private final CifradoBiometricoService cifradoService;

    @Value("${app.biometria.tamano-rostro}")
    private int tamanoRostro;

    @Value("${app.biometria.umbral-confianza}")
    private double umbralConfianza;

    // Cache: modeloFacialId → recognizer ya cargado y listo para predict.
    private final ConcurrentHashMap<Long, LBPHFaceRecognizer> cache = new ConcurrentHashMap<>();

    /**
     * Identifica el rostro presente en {@code imagenBytes} contra los modelos
     * faciales activos del tenant actual.
     * <p>
     * <b>Instrumentacion de calibracion (RF-16 / RNF-01)</b>: cada intento
     * con rostro detectado loguea una linea {@code CALIBRACION} con la
     * distancia del mejor match y los tiempos parciales. Esas lineas son la
     * fuente de datos del protocolo {@code docs/calibracion-umbral.md}.
     * Nota: la primera llamada tras arrancar incluye descifrado y
     * deserializacion de los modelos (cache frio); para medir tiempos usar
     * las llamadas siguientes (cache caliente).
     */
    @Transactional(readOnly = true)
    public IdentificacionResultadoDto identificar(byte[] imagenBytes) {
        Long tenantId = TenantContext.getRequired();
        long inicioNs = System.nanoTime();

        DeteccionRostroService.RostroExtraido extraido =
            deteccionRostroService.extraerRostroNormalizado(imagenBytes, tamanoRostro);
        if (extraido == null) {
            return IdentificacionResultadoDto.sinRostro();
        }
        long finDeteccionNs = System.nanoTime();

        try {
            List<ModeloFacial> modelos = modeloFacialRepository.findActivosDelTenant(tenantId);
            if (modelos.isEmpty()) {
                return IdentificacionResultadoDto.noHayModelos(
                    extraido.x(), extraido.y(), extraido.ancho(), extraido.alto());
            }
            sincronizarCache(modelos);

            double mejorDistancia = Double.MAX_VALUE;
            ModeloFacial mejorMatch = null;

            for (ModeloFacial m : modelos) {
                LBPHFaceRecognizer recognizer = cache.get(m.getId());
                if (recognizer == null) continue;

                try (IntPointer label = new IntPointer(1);
                     DoublePointer confidence = new DoublePointer(1)) {
                    recognizer.predict(extraido.rostro(), label, confidence);
                    double distancia = confidence.get(0);
                    if (distancia < mejorDistancia) {
                        mejorDistancia = distancia;
                        mejorMatch = m;
                    }
                }
            }

            long finComparacionNs = System.nanoTime();
            long msDeteccion   = (finDeteccionNs - inicioNs) / 1_000_000;
            long msComparacion = (finComparacionNs - finDeteccionNs) / 1_000_000;
            long msTotal       = (finComparacionNs - inicioNs) / 1_000_000;

            boolean reconocido = mejorMatch != null && mejorDistancia <= umbralConfianza;
            log.info("CALIBRACION reconocido={} docenteId={} distancia={} umbral={} modelosComparados={} msDeteccion={} msComparacion={} msTotal={}",
                     reconocido,
                     reconocido ? mejorMatch.getDocente().getId() : "-",
                     String.format("%.1f", mejorDistancia),
                     umbralConfianza, modelos.size(),
                     msDeteccion, msComparacion, msTotal);

            if (reconocido) {
                Docente d = mejorMatch.getDocente();
                return IdentificacionResultadoDto.match(
                    d.getId(), d.getNombreCompleto(),
                    mejorMatch.getId(), mejorDistancia,
                    extraido.x(), extraido.y(), extraido.ancho(), extraido.alto());
            }
            return IdentificacionResultadoDto.noReconocido(mejorDistancia,
                extraido.x(), extraido.y(), extraido.ancho(), extraido.alto());

        } finally {
            extraido.rostro().close();
        }
    }

    /**
     * Saca del cache (y libera la memoria nativa) el recognizer de un modelo
     * puntual. Lo usa la <b>supresion fisica ARCO</b> (RNF-14): al borrar el
     * vector de la BD hay que asegurarse de que tampoco quede una copia
     * deserializada en memoria capaz de seguir reconociendo al docente.
     */
    public void evictarModelo(Long modeloFacialId) {
        LBPHFaceRecognizer rec = cache.remove(modeloFacialId);
        if (rec != null) {
            rec.close();
            log.info("Cache evict explicito del modelo facial id={} (supresion)", modeloFacialId);
        }
    }

    /**
     * Asegura que el cache contenga exactamente los recognizers de los
     * modelos activos pasados: carga los nuevos y descarta los que ya no
     * deberían estar.
     */
    private void sincronizarCache(List<ModeloFacial> modelosActivos) {
        Set<Long> idsActivos = modelosActivos.stream()
            .map(ModeloFacial::getId)
            .collect(Collectors.toSet());

        // Descartar los que ya no están activos.
        Set<Long> aRemover = new HashSet<>(cache.keySet());
        aRemover.removeAll(idsActivos);
        for (Long id : aRemover) {
            LBPHFaceRecognizer viejo = cache.remove(id);
            if (viejo != null) {
                viejo.close();
                log.debug("Cache evict modelo facial id={}", id);
            }
        }

        // Cargar los nuevos.
        for (ModeloFacial m : modelosActivos) {
            cache.computeIfAbsent(m.getId(), id -> cargar(m));
        }
    }

    private LBPHFaceRecognizer cargar(ModeloFacial m) {
        byte[] descifrado = cifradoService.descifrar(m.getEmbeddingCifrado());
        LBPHFaceRecognizer rec = motorLbph.deserializar(descifrado);
        log.info("Modelo facial cargado en cache: id={}, docente_id={}",
                 m.getId(), m.getDocente().getId());
        return rec;
    }
}
