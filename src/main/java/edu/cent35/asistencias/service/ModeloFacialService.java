package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoConsentimiento;
import edu.cent35.asistencias.model.ModeloFacial;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.ModeloFacialRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.opencv_core.Mat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Registra el modelo biométrico facial del docente (RF-08, RF-09): exige consentimiento
 * ACTIVO, extrae el rostro de cada captura, entrena el LBPH, lo cifra y lo guarda. Al
 * re-registrar el modelo viejo se da de baja en vez de borrarse, salvo en una supresión
 * ARCO, que es el único caso donde el dato biométrico se elimina de verdad.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModeloFacialService {

    private static final String ALGORITMO = "LBPH";
    private static final String VERSION_OPENCV = "4.10.0";

    private final ModeloFacialRepository modeloFacialRepository;
    private final DocenteRepository docenteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConsentimientoBiometricoService consentimientoService;
    private final DeteccionRostroService deteccionRostroService;
    private final CalidadCapturaService calidadService;
    private final MotorLbphService motorLbph;
    private final CifradoBiometricoService cifradoService;
    private final IdentificacionFacialService identificacionFacialService;

    @Value("${app.biometria.minimo-capturas-validas}")
    private int minimoCapturasValidas;

    @Value("${app.biometria.tamano-rostro}")
    private int tamanoRostro;

    @Value("${app.biometria.captura.capturas-por-etapa}")
    private int capturasPorEtapa;

    // Cuántos frames válidos como mínimo hace falta para entrenar.
    public int getMinimoCapturasValidas() {
        return minimoCapturasValidas;
    }

    // Cuantas capturas se toman de cada pose de la secuencia guiada.
    public int getCapturasPorEtapa() {
        return capturasPorEtapa;
    }

    // Modelo facial activo del docente, si tiene uno.
    @Transactional(readOnly = true)
    public Optional<ModeloFacial> modeloActivoDe(Long docenteId) {
        Docente docente = obtenerDocenteValidado(docenteId);
        Optional<ModeloFacial> opt =
            modeloFacialRepository.findByDocenteIdAndActivoTrue(docente.getId());
        opt.ifPresent(this::touchLazy);
        return opt;
    }

    // true si el docente tiene un modelo facial activo.
    @Transactional(readOnly = true)
    public boolean tieneModeloActivo(Long docenteId) {
        Docente docente = obtenerDocenteValidado(docenteId);
        return modeloFacialRepository.findByDocenteIdAndActivoTrue(docente.getId()).isPresent();
    }

    // Indica si el docente tiene algun modelo, activo o historico; la UI lo usa para ofrecer ARCO.
    @Transactional(readOnly = true)
    public boolean tieneModelos(Long docenteId) {
        Docente docente = obtenerDocenteValidado(docenteId);
        return !modeloFacialRepository
            .findByDocenteIdOrderByFechaRegistroDescIdDesc(docente.getId()).isEmpty();
    }

    // Borrado FISICO de todos los modelos del docente (derecho ARCO de cancelacion, RNF-14).
    // Es la excepcion a la baja logica del sistema: una fila inactiva seguiria conteniendo el
    // dato biometrico. Las asistencias se conservan porque la FK es ON DELETE SET NULL, y ademas
    // se evicta el recognizer del cache para que no siga reconociendo desde memoria.
    @Transactional
    public int suprimirDatosBiometricos(Long docenteId, Long usuarioActualId) {
        Docente docente = obtenerDocenteValidado(docenteId);

        List<ModeloFacial> modelos = modeloFacialRepository
            .findByDocenteIdOrderByFechaRegistroDescIdDesc(docente.getId());
        if (modelos.isEmpty()) {
            throw new IllegalArgumentException(
                "El docente no tiene datos biométricos para suprimir.");
        }

        // 1) Evictar del cache ANTES de borrar (defensa en profundidad).
        modelos.forEach(m -> identificacionFacialService.evictarModelo(m.getId()));

        // 2) DELETE fisico. La FK de asistencias es ON DELETE SET NULL:
        //    el historial administrativo se conserva.
        modeloFacialRepository.deleteAll(modelos);
        modeloFacialRepository.flush();

        log.info("ARCO: supresion fisica de {} modelo(s) biometrico(s) del docente {} ejecutada por usuario {}",
                 modelos.size(), docenteId, usuarioActualId);
        return modelos.size();
    }

    // Registra o re-registra el modelo facial del docente a partir de las capturas tomadas.
    @Transactional
    public ModeloFacial registrar(Long docenteId, List<byte[]> capturas, Long usuarioActualId) {
        Docente docente = obtenerDocenteValidado(docenteId);

        if (Boolean.FALSE.equals(docente.getActivo())) {
            throw new IllegalArgumentException(
                "El docente está inactivo. Reactivalo antes de registrar su rostro.");
        }

        // El consentimiento biométrico vigente es condición indispensable.
        if (consentimientoService.estadoActual(docenteId) != EstadoConsentimiento.ACTIVO) {
            throw new IllegalArgumentException(
                "El docente no tiene un consentimiento biométrico vigente. "
                + "Cargá el consentimiento antes de registrar su rostro.");
        }

        if (capturas == null || capturas.isEmpty()) {
            throw new IllegalArgumentException(
                "No se recibió ninguna captura. Repetí la grabación.");
        }

        // El navegador ya filtro con estos mismos criterios antes de mandar, pero se
        // revalida igual: el cliente decide CUANDO capturar, no si la captura sirve.
        // Ademas se exige que los recortes sean distintos entre si, que es lo unico que
        // convierte a la secuencia guiada en algo mas que cinco veces la misma pose.
        List<Mat> rostros = new ArrayList<>();
        try {
            int sinRostro = 0;
            int variasPersonas = 0;
            int malaCalidad = 0;
            int repetidas = 0;

            for (byte[] cap : capturas) {
                DeteccionRostroService.Extraccion extraccion = deteccionRostroService
                    .extraerRostroNormalizado(cap, tamanoRostro);
                if (extraccion.hayVarios()) {
                    variasPersonas++;
                    continue;
                }
                DeteccionRostroService.RostroExtraido extraido = extraccion.rostro();
                if (extraido == null) {
                    sinRostro++;
                    continue;
                }
                if (extraido.calidad() != null && !extraido.calidad().apta()) {
                    malaCalidad++;
                    extraido.rostro().close();
                    continue;
                }
                if (!calidadService.esNovedoso(extraido.rostro(), rostros)) {
                    repetidas++;
                    extraido.rostro().close();
                    continue;
                }
                rostros.add(extraido.rostro());
            }

            log.info("Registro facial docente {}: {} recibidas, {} aceptadas "
                     + "({} sin rostro, {} con varias personas, {} de mala calidad, {} repetidas).",
                     docenteId, capturas.size(), rostros.size(),
                     sinRostro, variasPersonas, malaCalidad, repetidas);

            if (rostros.size() < minimoCapturasValidas) {
                throw new IllegalArgumentException(explicarPorQueNoAlcanza(
                    rostros.size(), sinRostro, variasPersonas, malaCalidad, repetidas));
            }

            byte[] modeloSerializado = motorLbph.entrenar(rostros);
            byte[] modeloCifrado = cifradoService.cifrar(modeloSerializado);

            // RF-09: el modelo anterior se da de baja, no se borra (historial).
            modeloFacialRepository.findByDocenteIdAndActivoTrue(docenteId)
                .ifPresent(anterior -> {
                    anterior.setActivo(false);
                    anterior.setFechaBaja(java.time.LocalDateTime.now());
                    anterior.setFechaBaja(LocalDateTime.now());
                    modeloFacialRepository.save(anterior);
                    log.info("Modelo facial anterior dado de baja: id={}", anterior.getId());
                });

            Usuario admin = usuarioRepository.findById(usuarioActualId)
                .orElseThrow(() -> new EntityNotFoundException(
                    "Usuario actual no encontrado: " + usuarioActualId));

            ModeloFacial modelo = ModeloFacial.builder()
                .docente(docente)
                .embeddingCifrado(modeloCifrado)
                .algoritmo(ALGORITMO)
                .versionAlgoritmo(VERSION_OPENCV)
                .dimensiones((short) tamanoRostro)
                .activo(true)
                .registradoPor(admin)
                .build();

            ModeloFacial guardado = modeloFacialRepository.save(modelo);
            log.info("Modelo facial registrado: id={}, docente_id={}, capturas={}",
                     guardado.getId(), docenteId, rostros.size());
            return guardado;

        } finally {
            // Liberar la memoria nativa de los rostros de OpenCV.
            rostros.forEach(Mat::close);
        }
    }

    // Historial de modelos del docente, del más nuevo al más viejo.
    @Transactional(readOnly = true)
    public List<ModeloFacial> historialDe(Long docenteId) {
        Docente docente = obtenerDocenteValidado(docenteId);
        List<ModeloFacial> historial =
            modeloFacialRepository.findByDocenteIdOrderByFechaRegistroDescIdDesc(docente.getId());
        historial.forEach(this::touchLazy);
        return historial;
    }

    // ------------------------------------------------------------------------

    // Explica por que fallo segun cual haya sido el descarte dominante. Antes el mensaje
    // era siempre el mismo ("no se detecto tu cara de forma estable"), que no le decia a
    // nadie que corregir y obligaba a repetir a ciegas.
    private String explicarPorQueNoAlcanza(int aceptadas, int sinRostro, int variasPersonas,
                                           int malaCalidad, int repetidas) {
        String base = "Quedaron " + aceptadas + " capturas utiles y hacen falta al menos "
                    + minimoCapturasValidas + ". ";

        int peor = Math.max(Math.max(sinRostro, variasPersonas),
                            Math.max(malaCalidad, repetidas));
        if (peor == 0) {
            return base + "Volvé a intentarlo.";
        }
        if (peor == variasPersonas) {
            return base + "En la mayoría había más de una persona en cuadro. El registro es "
                 + "de un docente por vez: pedile al resto que se corra de la cámara.";
        }
        if (peor == repetidas) {
            return base + "La mayoría salieron casi idénticas entre sí: hay que moverse "
                 + "de verdad entre una etapa y la siguiente, aunque sea poco. Un modelo "
                 + "entrenado con la misma pose repetida después no reconoce a nadie que "
                 + "se pare distinto.";
        }
        if (peor == malaCalidad) {
            return base + "La mayoría salieron borrosas o mal iluminadas. Buscá un lugar "
                 + "con luz pareja, sin ventana ni lámpara fuerte detrás, y esperá a estar "
                 + "quieto antes de cada captura.";
        }
        return base + "En la mayoría no se llegó a detectar la cara. Mirá de frente a la "
             + "cámara, sin barbijo ni gorra, y que no haya otra persona en cuadro.";
    }

    private Docente obtenerDocenteValidado(Long docenteId) {
        Long tenantId = TenantContext.getRequired();
        Docente d = docenteRepository.findById(docenteId)
            .orElseThrow(() -> new EntityNotFoundException("Docente no encontrado: " + docenteId));
        if (!tenantId.equals(d.getInstitucionId())) {
            log.warn("Cross-tenant blocked: tenant {} intento operar sobre docente id={} (tenant {})",
                     tenantId, docenteId, d.getInstitucionId());
            throw new EntityNotFoundException("Docente no encontrado");
        }
        return d;
    }

    // Inicializa el lazy que la UI lee fuera de transacción (open-in-view=false).
    private void touchLazy(ModeloFacial m) {
        if (m.getRegistradoPor() != null) {
            m.getRegistradoPor().getUsername();
        }
    }
}
