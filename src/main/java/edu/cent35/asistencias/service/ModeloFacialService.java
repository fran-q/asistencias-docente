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
 * Registro del modelo biométrico facial de un docente (RF-08, RF-09).
 * <p>
 * Orquesta el pipeline de registro: valida que el docente tenga
 * consentimiento ACTIVO (ADR-0005), extrae el rostro de cada captura,
 * entrena el modelo LBPH, lo cifra y lo persiste. Soporta re-registro
 * (RF-09): el modelo anterior se da de baja, no se borra.
 * <p>
 * <b>Multi-tenant</b>: valida el docente contra el tenant actual (mismo
 * patrón que el resto de servicios sobre entidades hijas de Docente).
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
    private final MotorLbphService motorLbph;
    private final CifradoBiometricoService cifradoService;
    private final IdentificacionFacialService identificacionFacialService;

    @Value("${app.biometria.minimo-capturas-validas}")
    private int minimoCapturasValidas;

    @Value("${app.biometria.tamano-rostro}")
    private int tamanoRostro;

    @Value("${app.biometria.duracion-grabacion-seg}")
    private int duracionGrabacionSeg;

    @Value("${app.biometria.intervalo-captura-ms}")
    private int intervaloCapturaMs;

    // Cuántos frames válidos como mínimo hace falta para entrenar.
    public int getMinimoCapturasValidas() {
        return minimoCapturasValidas;
    }

    // Duración (segundos) de la grabación que pide la UI.
    public int getDuracionGrabacionSeg() {
        return duracionGrabacionSeg;
    }

    // Intervalo (ms) entre frames durante la grabación.
    public int getIntervaloCapturaMs() {
        return intervaloCapturaMs;
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

    // {@code true} si el docente tiene un modelo facial activo.
    @Transactional(readOnly = true)
    public boolean tieneModeloActivo(Long docenteId) {
        Docente docente = obtenerDocenteValidado(docenteId);
        return modeloFacialRepository.findByDocenteIdAndActivoTrue(docente.getId()).isPresent();
    }

    /**
     * {@code true} si el docente tiene ALGUN modelo facial en la BD, activo
     * o historico. Lo usa la UI para saber si corresponde ofrecer la
     * supresion ARCO (que borra tambien los historicos).
     */
    @Transactional(readOnly = true)
    public boolean tieneModelos(Long docenteId) {
        Docente docente = obtenerDocenteValidado(docenteId);
        return !modeloFacialRepository
            .findByDocenteIdOrderByFechaRegistroDescIdDesc(docente.getId()).isEmpty();
    }

    /**
     * Supresion FISICA de todos los datos biometricos del docente
     * (derecho ARCO de Cancelacion - RNF-14, Ley 25.326).
     * <p>
     * <b>Por que DELETE fisico y no baja logica</b>: la baja logica es la
     * regla general del sistema, pero el vector biometrico es la excepcion
     * documentada. Una fila con {@code activo=false} sigue conteniendo el
     * dato sensible; ante una supresion, el dato tiene que desaparecer de
     * verdad. Se borran TODOS los modelos del docente (activo e historicos).
     * <p>
     * <b>Que se conserva</b>: las asistencias historicas. La FK
     * {@code asistencias.modelo_facial_id} es {@code ON DELETE SET NULL}
     * (V001), asi que los registros administrativos quedan intactos con la
     * referencia biometrica en NULL — ya no son dato biometrico.
     * <p>
     * Ademas se evicta el recognizer del cache en memoria: sin esto, una
     * copia deserializada seguiria pudiendo reconocer al docente hasta el
     * proximo reinicio.
     *
     * @return cantidad de modelos suprimidos
     */
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

    /**
     * Registra (o re-registra) el modelo facial de un docente.
     *
     * @param docenteId        a quién pertenece
     * @param capturas         imágenes en bytes (una por cada captura pedida)
     * @param usuarioActualId  admin que ejecuta el registro
     */
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

        // De TODAS las capturas, nos quedamos solo con las que tienen un
        // rostro claro y único. El resto se descarta sin fallar (la grabación
        // continua de 30s suele incluir frames con la cara borrosa o parcial).
        List<Mat> rostros = new ArrayList<>();
        try {
            int descartadas = 0;
            for (byte[] cap : capturas) {
                DeteccionRostroService.RostroExtraido extraido = deteccionRostroService
                    .extraerRostroNormalizado(cap, tamanoRostro);
                if (extraido == null) {
                    descartadas++;
                } else {
                    rostros.add(extraido.rostro());
                }
            }
            log.info("Registro facial docente {}: {} frames recibidos, {} válidos, {} descartados.",
                     docenteId, capturas.size(), rostros.size(), descartadas);

            if (rostros.size() < minimoCapturasValidas) {
                throw new IllegalArgumentException(
                    "No se detectó tu cara de forma estable durante la grabación. "
                    + "Necesitamos al menos " + minimoCapturasValidas
                    + " capturas válidas y solo conseguimos " + rostros.size()
                    + ". Volvé a intentar mirando de frente y con buena luz.");
            }

            byte[] modeloSerializado = motorLbph.entrenar(rostros);
            byte[] modeloCifrado = cifradoService.cifrar(modeloSerializado);

            // RF-09: el modelo anterior se da de baja, no se borra (historial).
            modeloFacialRepository.findByDocenteIdAndActivoTrue(docenteId)
                .ifPresent(anterior -> {
                    anterior.setActivo(false);
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
