package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.dto.biometria.IdentificacionResultadoDto;
import edu.cent35.asistencias.dto.biometria.ModeloActivo;
import edu.cent35.asistencias.model.biometria.ModeloFacial;
import edu.cent35.asistencias.model.persona.Docente;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.ModeloFacialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.opencv_core.Mat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Identifica a un docente a partir de una imagen, comparándola contra los
 * modelos faciales activos del tenant (Sprint 4 Fase D).
 * <p>
 * Mantiene un <b>cache en memoria</b> de los histogramas de cada modelo.
 * Sin cache, cada identificación tendría que descifrar + descomprimir +
 * deserializar TODOS los modelos del tenant, lo que mata el rendimiento del
 * loop de identificación continua (varias llamadas por segundo).
 * <p>
 * El cache se sincroniza en cada llamada: si aparecen nuevos modelos activos
 * los carga, y si algunos dejaron de estar activos (por re-registro o baja
 * del docente) los descarta automáticamente. Es uno solo para todas las
 * instituciones, así que cada sincronización toca únicamente las entradas de
 * la institución que está identificando.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdentificacionFacialService {

    private final ModeloFacialRepository modeloFacialRepository;
    private final DocenteRepository docenteRepository;
    private final DeteccionRostroService deteccionRostroService;
    private final MotorLbphService motorLbph;
    private final CifradoBiometricoService cifradoService;

    @Value("${app.biometria.tamano-rostro}")
    private int tamanoRostro;

    @Value("${app.biometria.margen-minimo}")
    private double margenMinimo;

    @Value("${app.biometria.umbral-confianza}")
    private double umbralConfianza;

    // Una entrada del cache, con su institución para no tocar nunca las de otra.
    private record ModeloEnMemoria(Long institucionId, MotorLbphService.Histogramas histogramas) { }

    // Cache: modeloFacialId → histogramas ya cargados y listos para comparar.
    private final ConcurrentHashMap<Long, ModeloEnMemoria> cache = new ConcurrentHashMap<>();

    // Cuando se uso por última vez cada entrada del cache.
    private final ConcurrentHashMap<Long, Long> ultimoUso = new ConcurrentHashMap<>();

    /**
     * Para que nadie libere unos histogramas mientras otro hilo los está comparando.
     *
     * <p>Son memoria nativa: compararlos después de liberarlos no da una excepción. Puede dar una
     * distancia inventada sin ningún aviso, o voltear la JVM con todas las instituciones adentro.
     * Comparar toma el candado de lectura, así que
     * varias cámaras comparan a la vez; liberar toma el de escritura y espera a que terminen.
     * Liberar es raro: una baja, una revocación, un modelo que dejó de usarse.
     */
    private final ReadWriteLock usoDeHistogramas = new ReentrantReadWriteLock();

    // Cuántos minutos puede quedar un modelo descifrado en memoria sin que nadie lo use.
    @Value("${app.biometria.cache-minutos-inactividad}")
    private long minutosInactividad;

    // El reloj del cache: un modelo sin uso por más de minutosInactividad se suelta.
    private Clock clock = Clock.systemDefaultZone();

    // Solo para tests: fija el reloj.
    void setClock(Clock clock) {
        this.clock = clock;
    }

    // Identifica el rostro contra los modelos activos del tenant. Cada intento loguea una línea
    // CALIBRACION con distancia y tiempos, que es la fuente de Documentacion/5-informes/calibracion-umbral.md.
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
            List<ModeloActivo> modelos = modeloFacialRepository.activosDelTenant(tenantId);
            if (modelos.isEmpty()) {
                return IdentificacionResultadoDto.noHayModelos(
                    extraido.x(), extraido.y(), extraido.ancho(), extraido.alto());
            }
            sincronizarCache(tenantId, modelos);

            // Se guarda el mejor Y el segundo mejor. La diferencia entre ambos es el MARGEN, y
            // es lo único que permite ver si el sistema estuvo cerca de confundir a dos
            // docentes: un acierto con margen de 3 puntos esta a un frame de convertirse en un
            // falso positivo, y mirando solo la mejor distancia eso no se nota.
            double mejorDistancia = Double.MAX_VALUE;
            ModeloActivo mejorMatch = null;
            double segundaDistancia = Double.MAX_VALUE;
            ModeloActivo segundoMatch = null;

            // Sincronizar va antes del candado y no adentro: puede liberar modelos, y eso espera a
            // los que están comparando. Este hilo no puede estar de los dos lados.
            usoDeHistogramas.readLock().lock();
            try (Mat histogramaDelRostro = motorLbph.histogramaDe(extraido.rostro())) {
                for (ModeloActivo m : modelos) {
                    ModeloEnMemoria enMemoria = cache.get(m.modeloId());
                    if (enMemoria == null) continue;

                    double distancia = motorLbph.distancia(enMemoria.histogramas(), histogramaDelRostro);
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
            } finally {
                usoDeHistogramas.readLock().unlock();
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
            // sistema rechaza a alguien que sí estaba, saber contra quién se acercó es lo que
            // permite entender por qué falló.
            String margen = segundoMatch == null
                ? "-"                                   // había un solo modelo: no hay con que comparar
                : String.format("%.1f", segundaDistancia - mejorDistancia);

            log.info("CALIBRACION reconocido={} mejorDocente={} mejorDistancia={} "
                     + "segundoDocente={} segundaDistancia={} margen={} "
                     + "umbral={} modelosComparados={} msDeteccion={} msComparacion={} msTotal={}",
                     reconocido,
                     mejorMatch == null ? "-" : mejorMatch.docenteId(),
                     mejorMatch == null ? "-" : String.format("%.1f", mejorDistancia),
                     segundoMatch == null ? "-" : segundoMatch.docenteId(),
                     segundoMatch == null ? "-" : String.format("%.1f", segundaDistancia),
                     margen,
                     umbralConfianza, modelos.size(),
                     msDeteccion, msComparacion, msTotal);

            if (reconocido) {
                // Recién acá se busca a la persona: en la mayoría de los cuadros no hay nadie que
                // nombrar. Con su institución, igual que la lista de modelos.
                Docente d = docenteRepository.buscarDelTenant(tenantId, mejorMatch.docenteId())
                    .orElseThrow();
                return IdentificacionResultadoDto.match(
                    d.getId(), d.getNombreCompleto(), d.getApellido(),
                    mejorMatch.modeloId(), mejorDistancia,
                    extraido.x(), extraido.y(), extraido.ancho(), extraido.alto());
            }
            // Se distingue por qué se rechazó: "no estás registrado" y "no puedo
            // distinguirte de otro" son problemas distintos y se resuelven distinto.
            if (veredicto == Veredicto.AMBIGUO) {
                log.warn("Identificacion ambigua: docentes {} y {} quedaron a {} de distancia "
                         + "entre si (minimo exigido {})",
                         mejorMatch.docenteId(),
                         segundoMatch.docenteId(),
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
        // LBPH siempre devuelve el más parecido, aunque el parecido sea pésimo: no existe
        // la respuesta "no conozco a esta persona". El umbral es lo único que la construye.
        if (mejorDistancia > umbralConfianza) {
            return Veredicto.NO_REGISTRADO;
        }
        // Con un solo modelo cargado no hay segundo contra quien medir. Vale la pena saber
        // que en ese caso el sistema está en su punto más débil: la única defensa es el
        // umbral, y cualquiera que se le parezca lo suficiente entra.
        if (segundaDistancia == null) {
            return Veredicto.ACEPTADO;
        }
        // Un empate no es una identificación: cuál gana lo decide una sombra, no la cara.
        if (segundaDistancia - mejorDistancia < margenMinimo) {
            return Veredicto.AMBIGUO;
        }
        return Veredicto.ACEPTADO;
    }

    /**
     * Descarta los modelos que llevan rato sin usarse.
     *
     * <p>El cache guarda el dato biométrico <b>descifrado</b>: mientras una entrada vive, el
     * embedding está en memoria en claro. Que se quede ahí mientras el pase corre es el
     * precio de que ande rápido; que se quede toda la noche después de la última clase no
     * compra nada y solo alarga la ventana en la que ese dato existe sin cifrar.
     *
     * <p>Hace falta un barrido programado y no alcanza con limpiar dentro de
     * {@code sincronizarCache}: ese método corre cuando alguien identifica, y el momento en
     * que hay que soltar la memoria es justamente cuando ya nadie identifica.
     */
    @Scheduled(fixedDelayString = "${app.biometria.cache-barrido-ms}")
    public void descartarModelosInactivos() {
        long limite = clock.millis() - minutosInactividad * 60_000L;
        for (Map.Entry<Long, Long> e : ultimoUso.entrySet()) {
            if (e.getValue() > limite) continue;
            if (soltar(e.getKey())) {
                log.info("Modelo facial id={} descartado del cache tras {} min sin uso.",
                         e.getKey(), minutosInactividad);
            }
        }
    }

    // Saca el modelo del cache y libera su memoria nativa; sin esto, tras un borrado ARCO
    // una copia en memoria seguiría reconociendo al docente hasta el próximo reinicio.
    public void evictarModelo(Long modeloFacialId) {
        if (soltar(modeloFacialId)) {
            log.info("Cache evict explicito del modelo facial id={} (supresion)", modeloFacialId);
        }
    }

    // Saca un modelo del cache y libera su memoria cuando nadie lo esté comparando. Dice si estaba.
    private boolean soltar(Long modeloFacialId) {
        ultimoUso.remove(modeloFacialId);
        ModeloEnMemoria enMemoria = cache.remove(modeloFacialId);
        if (enMemoria == null) {
            return false;
        }
        usoDeHistogramas.writeLock().lock();
        try {
            enMemoria.histogramas().close();
        } finally {
            usoDeHistogramas.writeLock().unlock();
        }
        return true;
    }

    /**
     * Deja en el cache exactamente los modelos activos de esta institución: carga los nuevos y
     * descarta los viejos.
     *
     * <p><b>Mira solo las entradas de la institución que identifica.</b> El cache es de todas, y
     * antes se descartaba todo lo que no estuviera en la lista de esta: con dos instituciones
     * tomando asistencia a la vez, cada cuadro de una le vaciaba los modelos a la otra, que tenía
     * que volver a descifrarlos, y hasta se los liberaba mientras los estaba usando.
     *
     * <p>El dato cifrado se pide solo para los que faltan. En régimen no hay ninguno, y esta
     * consulta no corre.
     */
    private void sincronizarCache(Long tenantId, List<ModeloActivo> modelosActivos) {
        Set<Long> idsActivos = modelosActivos.stream()
            .map(ModeloActivo::modeloId)
            .collect(Collectors.toSet());

        // Descartar los de esta institución que ya no están activos.
        cache.forEach((id, enMemoria) -> {
            if (tenantId.equals(enMemoria.institucionId()) && !idsActivos.contains(id)) {
                soltar(id);
                log.debug("Cache evict modelo facial id={}", id);
            }
        });

        List<Long> faltantes = new ArrayList<>();
        for (Long id : idsActivos) {
            if (!cache.containsKey(id)) faltantes.add(id);
        }
        if (!faltantes.isEmpty()) {
            for (ModeloFacial m : modeloFacialRepository.findAllById(faltantes)) {
                cache.computeIfAbsent(m.getId(), id -> cargar(tenantId, m));
            }
        }

        // Marcar a todos como usados recién: si están sincronizados es porque alguien acaba de
        // identificar contra ellos.
        long ahora = clock.millis();
        for (Long id : idsActivos) {
            ultimoUso.put(id, ahora);
        }
    }

    // Descifra el modelo guardado y se queda con sus histogramas, listos para comparar.
    private ModeloEnMemoria cargar(Long tenantId, ModeloFacial m) {
        byte[] descifrado = cifradoService.descifrar(m.getEmbeddingCifrado());
        MotorLbphService.Histogramas histogramas = motorLbph.cargarHistogramas(descifrado);
        log.info("Modelo facial cargado en cache: id={}, institucion_id={}", m.getId(), tenantId);
        return new ModeloEnMemoria(tenantId, histogramas);
    }
}
