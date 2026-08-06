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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
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

    @Value("${app.biometria.margen-minimo}")
    private double margenMinimo;

    @Value("${app.biometria.umbral-confianza}")
    private double umbralConfianza;

    // Cache: modeloFacialId → recognizer ya cargado y listo para predict.
    private final ConcurrentHashMap<Long, LBPHFaceRecognizer> cache = new ConcurrentHashMap<>();

    // Cuando se uso por ultima vez cada entrada del cache.
    private final ConcurrentHashMap<Long, Long> ultimoUso = new ConcurrentHashMap<>();

    // Cuantos minutos puede quedar un modelo descifrado en memoria sin que nadie lo use.
    @Value("${app.biometria.cache-minutos-inactividad}")
    private long minutosInactividad;

    // Identifica el rostro contra los modelos activos del tenant. Cada intento loguea una linea
    // CALIBRACION con distancia y tiempos, que es la fuente de Documentacion/7-informes/calibracion-umbral.md.
    @Transactional(readOnly = true)
    public IdentificacionResultadoDto identificar(byte[] imagenBytes) {
        Long tenantId = TenantContext.getRequired();
        long inicioNs = System.nanoTime();

        DeteccionRostroService.Extraccion extraccion =
            deteccionRostroService.extraerRostroNormalizado(imagenBytes, tamanoRostro);
        if (extraccion.hayVarios()) {
            return IdentificacionResultadoDto.variosRostros(extraccion.cantidadRostros());
        }
        if (!extraccion.sirve()) {
            return IdentificacionResultadoDto.sinRostro();
        }
        DeteccionRostroService.RostroExtraido extraido = extraccion.rostro();
        long finDeteccionNs = System.nanoTime();

        try {
            List<ModeloFacial> modelos = modeloFacialRepository.findActivosDelTenant(tenantId);
            if (modelos.isEmpty()) {
                return IdentificacionResultadoDto.noHayModelos(
                    extraido.x(), extraido.y(), extraido.ancho(), extraido.alto());
            }
            sincronizarCache(modelos);

            // Se guarda el mejor Y el segundo mejor. La diferencia entre ambos es el MARGEN, y
            // es lo unico que permite ver si el sistema estuvo cerca de confundir a dos
            // docentes: un acierto con margen de 3 puntos esta a un frame de convertirse en un
            // falso positivo, y mirando solo la mejor distancia eso no se nota.
            double mejorDistancia = Double.MAX_VALUE;
            ModeloFacial mejorMatch = null;
            double segundaDistancia = Double.MAX_VALUE;
            ModeloFacial segundoMatch = null;

            for (ModeloFacial m : modelos) {
                LBPHFaceRecognizer recognizer = cache.get(m.getId());
                if (recognizer == null) continue;

                try (IntPointer label = new IntPointer(1);
                     DoublePointer confidence = new DoublePointer(1)) {
                    recognizer.predict(extraido.rostro(), label, confidence);
                    double distancia = confidence.get(0);
                    if (distancia < mejorDistancia) {
                        segundaDistancia = mejorDistancia;
                        segundoMatch = mejorMatch;
                        mejorDistancia = distancia;
                        mejorMatch = m;
                    } else if (distancia < segundaDistancia) {
                        segundaDistancia = distancia;
                        segundoMatch = m;
                    }
                }
            }

            long finComparacionNs = System.nanoTime();
            long msDeteccion   = (finDeteccionNs - inicioNs) / 1_000_000;
            long msComparacion = (finComparacionNs - finDeteccionNs) / 1_000_000;
            long msTotal       = (finComparacionNs - inicioNs) / 1_000_000;

            Veredicto veredicto = mejorMatch == null
                ? Veredicto.NO_REGISTRADO
                : decidir(mejorDistancia, segundoMatch == null ? null : segundaDistancia);
            boolean reconocido = veredicto == Veredicto.ACEPTADO;

            // El mejor candidato se registra SIEMPRE, se lo haya aceptado o no: cuando el
            // sistema rechaza a alguien que si estaba, saber contra quien se acerco es lo que
            // permite entender por que fallo.
            String margen = segundoMatch == null
                ? "-"                                   // habia un solo modelo: no hay con que comparar
                : String.format("%.1f", segundaDistancia - mejorDistancia);

            log.info("CALIBRACION reconocido={} mejorDocente={} mejorDistancia={} "
                     + "segundoDocente={} segundaDistancia={} margen={} "
                     + "umbral={} modelosComparados={} msDeteccion={} msComparacion={} msTotal={}",
                     reconocido,
                     mejorMatch == null ? "-" : mejorMatch.getDocente().getId(),
                     mejorMatch == null ? "-" : String.format("%.1f", mejorDistancia),
                     segundoMatch == null ? "-" : segundoMatch.getDocente().getId(),
                     segundoMatch == null ? "-" : String.format("%.1f", segundaDistancia),
                     margen,
                     umbralConfianza, modelos.size(),
                     msDeteccion, msComparacion, msTotal);

            if (reconocido) {
                Docente d = mejorMatch.getDocente();
                return IdentificacionResultadoDto.match(
                    d.getId(), d.getNombreCompleto(),
                    mejorMatch.getId(), mejorDistancia,
                    extraido.x(), extraido.y(), extraido.ancho(), extraido.alto());
            }
            // Se distingue por que se rechazo: "no estas registrado" y "no puedo
            // distinguirte de otro" son problemas distintos y se resuelven distinto.
            if (veredicto == Veredicto.AMBIGUO) {
                log.warn("Identificacion ambigua: docentes {} y {} quedaron a {} de distancia "
                         + "entre si (minimo exigido {})",
                         mejorMatch.getDocente().getId(),
                         segundoMatch.getDocente().getId(),
                         String.format("%.1f", segundaDistancia - mejorDistancia), margenMinimo);
                return IdentificacionResultadoDto.ambiguo(mejorDistancia,
                    extraido.x(), extraido.y(), extraido.ancho(), extraido.alto());
            }
            return IdentificacionResultadoDto.noReconocido(mejorDistancia,
                extraido.x(), extraido.y(), extraido.ancho(), extraido.alto());

        } finally {
            extraido.rostro().close();
        }
    }

    /** Qué se resolvió sobre el mejor candidato. */
    enum Veredicto { ACEPTADO, NO_REGISTRADO, AMBIGUO }

    /**
     * Decide si el mejor candidato se acepta, aplicando las dos condiciones.
     *
     * <p>Está aparte del resto para poder probarla sin levantar OpenCV: es la regla que
     * separa un reconocimiento válido de un falso positivo, así que conviene poder fijarla
     * con casos concretos.
     *
     * @param segundaDistancia distancia del segundo mejor, o null si solo había un modelo
     */
    Veredicto decidir(double mejorDistancia, Double segundaDistancia) {
        // LBPH siempre devuelve el mas parecido, aunque el parecido sea pesimo: no existe
        // la respuesta "no conozco a esta persona". El umbral es lo unico que la construye.
        if (mejorDistancia > umbralConfianza) {
            return Veredicto.NO_REGISTRADO;
        }
        // Con un solo modelo cargado no hay segundo contra quien medir. Vale la pena saber
        // que en ese caso el sistema esta en su punto mas debil: la unica defensa es el
        // umbral, y cualquiera que se le parezca lo suficiente entra.
        if (segundaDistancia == null) {
            return Veredicto.ACEPTADO;
        }
        // Un empate no es una identificacion: cual gana lo decide una sombra, no la cara.
        if (segundaDistancia - mejorDistancia < margenMinimo) {
            return Veredicto.AMBIGUO;
        }
        return Veredicto.ACEPTADO;
    }

    /**
     * Descarta los modelos que llevan rato sin usarse.
     *
     * <p>El cache guarda el dato biometrico <b>descifrado</b>: mientras una entrada vive, el
     * embedding esta en memoria en claro. Que se quede ahi mientras el pase corre es el
     * precio de que ande rapido; que se quede toda la noche despues de la ultima clase no
     * compra nada y solo alarga la ventana en la que ese dato existe sin cifrar.
     *
     * <p>Hace falta un barrido programado y no alcanza con limpiar dentro de
     * {@code sincronizarCache}: ese metodo corre cuando alguien identifica, y el momento en
     * que hay que soltar la memoria es justamente cuando ya nadie identifica.
     */
    @Scheduled(fixedDelayString = "${app.biometria.cache-barrido-ms}")
    public void descartarModelosInactivos() {
        long limite = System.currentTimeMillis() - minutosInactividad * 60_000L;
        for (Map.Entry<Long, Long> e : ultimoUso.entrySet()) {
            if (e.getValue() > limite) continue;
            LBPHFaceRecognizer rec = cache.remove(e.getKey());
            ultimoUso.remove(e.getKey());
            if (rec != null) {
                rec.close();
                log.info("Modelo facial id={} descartado del cache tras {} min sin uso.",
                         e.getKey(), minutosInactividad);
            }
        }
    }

    // Saca el recognizer del cache y libera su memoria nativa; sin esto, tras un borrado ARCO
    // una copia en memoria seguiria reconociendo al docente hasta el proximo reinicio.
    public void evictarModelo(Long modeloFacialId) {
        ultimoUso.remove(modeloFacialId);
        LBPHFaceRecognizer rec = cache.remove(modeloFacialId);
        if (rec != null) {
            rec.close();
            log.info("Cache evict explicito del modelo facial id={} (supresion)", modeloFacialId);
        }
    }

    // Deja en el cache exactamente los modelos activos: carga los nuevos y descarta los viejos.
    private void sincronizarCache(List<ModeloFacial> modelosActivos) {
        Set<Long> idsActivos = modelosActivos.stream()
            .map(ModeloFacial::getId)
            .collect(Collectors.toSet());

        // Descartar los que ya no están activos.
        Set<Long> aRemover = new HashSet<>(cache.keySet());
        aRemover.removeAll(idsActivos);
        for (Long id : aRemover) {
            ultimoUso.remove(id);
            LBPHFaceRecognizer viejo = cache.remove(id);
            if (viejo != null) {
                viejo.close();
                log.debug("Cache evict modelo facial id={}", id);
            }
        }

        // Cargar los nuevos y marcar a todos como usados recien: si estan sincronizados es
        // porque alguien acaba de identificar contra ellos.
        long ahora = System.currentTimeMillis();
        for (ModeloFacial m : modelosActivos) {
            cache.computeIfAbsent(m.getId(), id -> cargar(m));
            ultimoUso.put(m.getId(), ahora);
        }
    }

    // Descifra el modelo guardado y lo deserializa a un recognizer listo para comparar.
    private LBPHFaceRecognizer cargar(ModeloFacial m) {
        byte[] descifrado = cifradoService.descifrar(m.getEmbeddingCifrado());
        LBPHFaceRecognizer rec = motorLbph.deserializar(descifrado);
        log.info("Modelo facial cargado en cache: id={}, docente_id={}",
                 m.getId(), m.getDocente().getId());
        return rec;
    }
}
