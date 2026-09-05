package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.AsistenciaManual;
import edu.cent35.asistencias.model.BloquePresencia;
import edu.cent35.asistencias.model.DiaSemana;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoAsistencia;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.JustificacionAusencia;
import edu.cent35.asistencias.model.MetodoAsistencia;
import edu.cent35.asistencias.model.ModeloFacial;
import edu.cent35.asistencias.model.MotivoCargaManual;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.AsistenciaManualRepository;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.JustificacionAusenciaRepository;
import edu.cent35.asistencias.repository.ModeloFacialRepository;
import edu.cent35.asistencias.repository.MotivoCargaManualRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cent35.asistencias.dto.AsistenciaListItemDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Registra las asistencias del docente, sea por reconocimiento facial o por carga manual
 * (RF-17 a RF-21). Una marca automática solo entra si el docente tiene una clase corriendo
 * dentro de la ventana [hora_inicio - tolerancia, hora_fin], y queda PRESENTE mientras la
 * llegada entre en la tolerancia del horario —hacia los dos lados del inicio, ADR-0018— o
 * TARDE después; es idempotente por (docente, horario, fecha) y siempre valida que el
 * docente pertenezca al tenant actual.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsistenciaService {

    private final AsistenciaRepository asistenciaRepository;
    private final HorarioRepository horarioRepository;
    private final DocenteRepository docenteRepository;
    private final ModeloFacialRepository modeloFacialRepository;
    private final AsistenciaManualRepository asistenciaManualRepository;
    private final JustificacionAusenciaRepository justificacionAusenciaRepository;
    private final MotivoCargaManualRepository motivoCargaManualRepository;
    private final UsuarioRepository usuarioRepository;

    // Distancia LBPH máxima esperada; sirve para llevar la distancia cruda a un score 0-1.
    @Value("${app.biometria.umbral-confianza}")
    private double umbralDistancia;

    // Resultado de un intento de marcado: si quedó marca, cuál es, si ya existía y si no, por qué.
    public record ResultadoMarca(
        boolean marcada,
        Asistencia asistencia,
        boolean yaEstaba,
        String motivoNoMarca
    ) {
        public static ResultadoMarca creada(Asistencia a) {
            return new ResultadoMarca(true, a, false, null);
        }
        public static ResultadoMarca yaEstaba(Asistencia a) {
            return new ResultadoMarca(true, a, true, null);
        }
        public static ResultadoMarca sinClase(String motivo) {
            return new ResultadoMarca(false, null, false, motivo);
        }
    }

    // Marca por reconocimiento facial; si no hay clase o ya estaba marcada, no falla: lo informa.
    @Transactional
    public ResultadoMarca marcarAutomatica(Long docenteId,
                                           Long modeloFacialId,
                                           Double distanciaLbph) {
        return marcarAutomatica(docenteId, modeloFacialId, distanciaLbph, LocalDateTime.now());
    }

    // Misma marca pero con el instante recibido por parámetro, para poder testearla.
    @Transactional
    public ResultadoMarca marcarAutomatica(Long docenteId,
                                           Long modeloFacialId,
                                           Double distanciaLbph,
                                           LocalDateTime instante) {
        Long tenantId = TenantContext.getRequired();
        Docente docente = obtenerDocenteValidado(docenteId, tenantId);

        LocalDate fecha = instante.toLocalDate();
        LocalTime ahora = instante.toLocalTime();
        byte diaSemana = (byte) instante.getDayOfWeek().getValue(); // 1..7 ISO

        // La fecha va ademas del dia de la semana desde V023: el horario tiene que caer
        // dentro de su periodo, o el pase abriria un bloque por una clase de un cuatrimestre
        // ya terminado.
        List<Horario> horariosHoy = horarioRepository
            .findHoyParaDocente(docenteId, diaSemana, fecha, tenantId);

        Optional<Horario> enCurso = elegirHorarioEnCurso(horariosHoy, ahora, docenteId, fecha);
        if (enCurso.isEmpty()) {
            log.info("Marca rechazada: docente {} no tiene clase ahora (ningún horario en ventana)",
                     docenteId);
            return ResultadoMarca.sinClase(
                "No hay clase en este momento para " + docente.getNombreCompleto() + ".");
        }
        Horario horario = enCurso.get();

        // Idempotencia: si ya marcó esa clase hoy, devolvemos la marca existente.
        Optional<Asistencia> existente = asistenciaRepository
            .findByDocenteIdAndHorarioIdAndFecha(docenteId, horario.getId(), fecha);
        if (existente.isPresent()) {
            log.info("Marca duplicada ignorada: docente {} ya tenía marca para horario {} fecha {}",
                     docenteId, horario.getId(), fecha);
            return ResultadoMarca.yaEstaba(existente.get());
        }

        EstadoAsistencia estado = calcularEstado(horario, ahora);

        ModeloFacial modelo = (modeloFacialId == null) ? null
            : modeloFacialRepository.findById(modeloFacialId).orElse(null);

        Asistencia asistencia = Asistencia.builder()
            .docente(docente)
            .comision(horario.getComision())
            .horario(horario)
            .fecha(fecha)
            .horaRegistrada(ahora.withNano(0))
            .estado(estado)
            .metodo(MetodoAsistencia.AUTOMATICO)
            .modeloFacial(modelo)
            .confianza(distanciaLbph == null ? null : distanciaToConfianza(distanciaLbph))
            .build();
        asistencia.setInstitucionId(tenantId);

        try {
            Asistencia guardada = asistenciaRepository.saveAndFlush(asistencia);
            log.info("Asistencia AUTO marcada: id={}, docente={}, horario={}, fecha={}, estado={}",
                     guardada.getId(), docenteId, horario.getId(), fecha, estado);
            return ResultadoMarca.creada(guardada);
        } catch (DataIntegrityViolationException ex) {
            // Race condition: otro request acaba de insertar la marca para
            // el mismo (docente, horario, fecha). El UNIQUE de BD lo bloqueo.
            // Releemos y devolvemos como ya existente.
            log.info("Race condition al marcar asistencia: docente {} horario {} fecha {} - ya existía",
                     docenteId, horario.getId(), fecha);
            Asistencia ya = asistenciaRepository
                .findByDocenteIdAndHorarioIdAndFecha(docenteId, horario.getId(), fecha)
                .orElseThrow(() -> ex);  // si no la encontramos, propagamos el error original
            return ResultadoMarca.yaEstaba(ya);
        }
    }

    /**
     * Registra la asistencia de una clase que quedó cubierta por un bloque de presencia
     * (RF-81), y la deja apuntando a ese bloque.
     *
     * <p><b>El estado sale de {@code horaLlegada}, y por eso no se pasa por parámetro.</b> En
     * la clase donde el docente entró, esa hora es el momento real de la marca, así que puede
     * dar TARDE. En las clases siguientes del mismo bloque se pasa su propia
     * {@code hora_inicio}: el docente ya estaba adentro cuando empezaron, y la continuidad del
     * bloque es lo que acredita su presencia. Con eso el cálculo es uno solo y no hay un caso
     * especial que después alguien tenga que recordar.
     *
     * <p>Es idempotente por (docente, horario, fecha) igual que {@link #marcarAutomatica}: si
     * la clase ya tenía marca —porque el docente la registró por su cuenta, porque la cargó un
     * admin o porque el job ya pasó— se devuelve la que estaba y no se pisa nada.
     */
    @Transactional
    public Asistencia imputarDelBloque(BloquePresencia bloque, Horario horario,
                                       LocalTime horaLlegada) {
        Long docenteId = bloque.getDocente().getId();
        LocalDate fecha = bloque.getFecha();

        Optional<Asistencia> existente = asistenciaRepository
            .findByDocenteIdAndHorarioIdAndFecha(docenteId, horario.getId(), fecha);
        if (existente.isPresent()) {
            return existente.get();
        }

        Asistencia asistencia = Asistencia.builder()
            .docente(bloque.getDocente())
            .comision(horario.getComision())
            .horario(horario)
            .bloque(bloque)
            .fecha(fecha)
            .horaRegistrada(horaLlegada.withNano(0))
            .estado(calcularEstado(horario, horaLlegada))
            .metodo(MetodoAsistencia.AUTOMATICO)
            .modeloFacial(bloque.getModeloFacialEntrada())
            .confianza(bloque.getConfianzaEntrada())
            .build();
        asistencia.setInstitucionId(bloque.getInstitucionId());

        try {
            Asistencia guardada = asistenciaRepository.saveAndFlush(asistencia);
            log.info("Asistencia imputada del bloque {}: id={}, docente={}, horario={}, estado={}",
                     bloque.getId(), guardada.getId(), docenteId, horario.getId(),
                     guardada.getEstado());
            return guardada;
        } catch (DataIntegrityViolationException ex) {
            // Carrera con el job de ausencias o con una carga manual: el UNIQUE de la base la
            // resolvio. Releemos y devolvemos la que quedo.
            log.info("Imputacion descartada por marca concurrente: docente={}, horario={}, fecha={}",
                     docenteId, horario.getId(), fecha);
            return asistenciaRepository
                .findByDocenteIdAndHorarioIdAndFecha(docenteId, horario.getId(), fecha)
                .orElseThrow(() -> ex);
        }
    }

    /**
     * Carga manual por un administrador (RF-22 a RF-24).
     *
     * <p><b>La hora no se elige: la pone el sistema.</b> Es el momento en que el
     * administrador está cargando el registro, no un dato que se tipea. Dejarlo abierto
     * permitía escribir cualquier horario —incluso uno fuera de la franja de la clase— y
     * convertía un registro administrativo en algo que dice lo que quien lo carga quiera
     * que diga. Lo que se está asentando es "yo, tal administrador, a esta hora, declaro
     * que este docente estuvo o no estuvo en esta clase". Esa es la afirmación real, y
     * ésa es la que queda registrada.
     *
     * <p><b>El docente tiene que ser el de la comisión.</b> Antes se elegían el docente y
     * el horario por separado, así que nada impedía marcarle asistencia a un docente en la
     * clase de otro. La clase la dicta quien está asignado a esa comisión, y punto.
     *
     * <p>El motivo es obligatorio y sale de un catálogo: es lo que después permite contar
     * cuántas cargas manuales hubo por falla de cámara y cuántas por otra cosa.
     */
    @Transactional
    public Asistencia marcarManual(Long docenteId, Long horarioId, java.time.LocalDate fecha,
                                   EstadoAsistencia estado,
                                   Short motivoId, String detalleAdicional,
                                   Long usuarioActualId) {
        Long tenantId = TenantContext.getRequired();
        Docente docente = obtenerDocenteValidado(docenteId, tenantId);
        Horario horario = obtenerHorarioValidado(horarioId, tenantId);

        // La hora del asiento es AHORA: es cuando el administrador lo declara.
        LocalTime horaRegistrada = LocalTime.now();

        if (estado == null) {
            throw new IllegalArgumentException("Hay que indicar el estado de la asistencia.");
        }

        // El docente tiene que ser el asignado a la comision de ese horario.
        Docente asignado = horario.getComision().getDocenteAsignado();
        if (asignado == null) {
            throw new IllegalArgumentException(
                "La comisión '" + horario.getComision().getCodigo() + "' no tiene docente "
                + "asignado, así que no se le puede cargar asistencia a nadie. Asignale un "
                + "docente primero.");
        }
        if (!asignado.getId().equals(docenteId)) {
            throw new IllegalArgumentException(
                "Esa clase la dicta " + asignado.getNombreCompleto() + ", no "
                + docente.getNombreCompleto() + ". Elegí una clase de la comisión que tiene "
                + "asignado este docente.");
        }

        if (fecha == null || fecha.isAfter(java.time.LocalDate.now())) {
            throw new IllegalArgumentException("La fecha no puede ser futura.");
        }
        // La clase solo existe el día que está programado el horario, y acá la fecha la
        // elige el admin a mano: sin este chequeo entraría un lunes marcado un sábado.
        DiaSemana diaDelHorario = DiaSemana.fromNumero(horario.getDiaSemana());
        DiaSemana diaDeLaFecha = DiaSemana.deLaFecha(fecha);
        if (diaDelHorario != diaDeLaFecha) {
            throw new IllegalArgumentException(
                "El " + FORMATO_FECHA.format(fecha) + " cae " + diaDeLaFecha.getEtiqueta()
                + " y el horario elegido es de " + diaDelHorario.getEtiqueta()
                + ". Elegí una fecha que caiga un " + diaDelHorario.getEtiqueta() + ".");
        }
        if (asistenciaRepository.findByDocenteIdAndHorarioIdAndFecha(docenteId, horarioId, fecha)
                .isPresent()) {
            throw new IllegalArgumentException(
                "Ya existe una marca para ese docente, horario y fecha.");
        }

        MotivoCargaManual motivo = motivoCargaManualRepository.findById(motivoId)
            .orElseThrow(() -> new IllegalArgumentException("El motivo seleccionado no existe."));
        if (Boolean.FALSE.equals(motivo.getActivo())) {
            throw new IllegalArgumentException("El motivo elegido está inactivo.");
        }

        Usuario admin = usuarioRepository.findById(usuarioActualId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Usuario actual no encontrado: " + usuarioActualId));

        Asistencia asistencia = Asistencia.builder()
            .docente(docente)
            .comision(horario.getComision())
            .horario(horario)
            .fecha(fecha)
            .horaRegistrada(horaRegistrada.withSecond(0).withNano(0))
            .estado(estado)
            .metodo(MetodoAsistencia.MANUAL)
            .build();
        asistencia.setInstitucionId(tenantId);
        Asistencia guardada = asistenciaRepository.save(asistencia);

        AsistenciaManual detalle = AsistenciaManual.builder()
            .asistencia(guardada)
            .usuario(admin)
            .motivo(motivo)
            .detalleAdicional(trimToNull(detalleAdicional))
            .build();
        asistenciaManualRepository.save(detalle);

        log.info("Asistencia MANUAL marcada: id={}, docente={}, horario={}, fecha={}, estado={}, motivo={}",
                 guardada.getId(), docenteId, horarioId, fecha, estado, motivo.getCodigo());
        return guardada;
    }

    // Justifica una ausencia ya persistida como AUSENTE (RF-25, RF-26); las calculadas no aplican.
    @Transactional
    public JustificacionAusencia justificarAusencia(Long asistenciaId, String motivo,
                                                    String documentoUrl, Long usuarioActualId) {
        Long tenantId = TenantContext.getRequired();
        Asistencia asistencia = asistenciaRepository.findById(asistenciaId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Asistencia no encontrada: " + asistenciaId));
        if (!tenantId.equals(asistencia.getInstitucionId())) {
            log.warn("Cross-tenant blocked: tenant {} intento justificar asistencia id={}",
                     tenantId, asistenciaId);
            throw new EntityNotFoundException("Asistencia no encontrada");
        }
        if (asistencia.getEstado() != EstadoAsistencia.AUSENTE) {
            throw new IllegalArgumentException(
                "Solo se pueden justificar asistencias con estado AUSENTE.");
        }
        if (justificacionAusenciaRepository.findByAsistenciaId(asistenciaId).isPresent()) {
            throw new IllegalArgumentException("Esta ausencia ya está justificada.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("El motivo de la justificación es obligatorio.");
        }

        Usuario admin = usuarioRepository.findById(usuarioActualId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Usuario actual no encontrado: " + usuarioActualId));

        JustificacionAusencia justificacion = JustificacionAusencia.builder()
            .asistencia(asistencia)
            .usuario(admin)
            .motivo(motivo.trim())
            .documentoUrl(trimToNull(documentoUrl))
            .build();
        JustificacionAusencia guardada = justificacionAusenciaRepository.save(justificacion);
        log.info("Ausencia justificada: asistencia_id={}, usuario={}", asistenciaId, admin.getId());
        return guardada;
    }

    // Motivos activos para el selector del form de carga manual.
    @Transactional(readOnly = true)
    public List<MotivoCargaManual> motivosActivos() {
        return motivoCargaManualRepository.findByActivoTrueOrderByDescripcionAsc();
    }

    // Indica si la asistencia ya tiene justificación adjunta.
    @Transactional(readOnly = true)
    public boolean tieneJustificacion(Long asistenciaId) {
        return justificacionAusenciaRepository.findByAsistenciaId(asistenciaId).isPresent();
    }

    // Lista el día pedido sumando AUSENTE calculadas: horarios ya terminados y sin marca real.
    @Transactional(readOnly = true)
    public List<AsistenciaListItemDto> listarDelDia(LocalDate fecha) {
        Long tenantId = TenantContext.getRequired();
        byte diaSemana = (byte) fecha.getDayOfWeek().getValue();
        boolean esHoy = fecha.equals(LocalDate.now());
        LocalTime ahora = LocalTime.now();

        // 1) Asistencias realmente persistidas para esa fecha.
        List<AsistenciaListItemDto> resultado = new ArrayList<>();
        List<Asistencia> persistidas = asistenciaRepository.findDelDia(TenantContext.getRequired(), fecha);
        for (Asistencia a : persistidas) {
            resultado.add(AsistenciaListItemDto.from(a));
        }

        // Set de claves (docenteId, horarioId) ya cubiertas por marcas reales.
        Set<String> cubiertas = new HashSet<>();
        for (Asistencia a : persistidas) {
            cubiertas.add(a.getDocente().getId() + ":" + a.getHorario().getId());
        }

        // 2) Horarios del día sin marca → AUSENTE calculada (si ya terminaron).
        List<Horario> horariosDia =
            horarioRepository.findActivosDelDiaConDocente(diaSemana, fecha, tenantId);
        for (Horario h : horariosDia) {
            Long docenteId = h.getComision().getDocenteAsignado().getId();
            String key = docenteId + ":" + h.getId();
            if (cubiertas.contains(key)) continue;

            boolean horarioYaTermino = !esHoy || ahora.isAfter(h.getHoraFin());
            if (horarioYaTermino) {
                resultado.add(AsistenciaListItemDto.ausenteCalculada(h, fecha));
            }
        }

        // 3) Orden: primero por horaInicio, después por nombre de docente.
        resultado.sort(Comparator
            .comparing(AsistenciaListItemDto::getHoraInicio,
                       Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AsistenciaListItemDto::getDocenteNombre,
                           Comparator.nullsLast(Comparator.naturalOrder())));
        return resultado;
    }

    // ------------------------------------------------------------------------

    // Elige a qué clase corresponde la marca (RF-18): sin marcar antes, inicio más cercano, menor id.
    private Optional<Horario> elegirHorarioEnCurso(List<Horario> horariosHoy,
                                                   LocalTime ahora,
                                                   Long docenteId,
                                                   LocalDate fecha) {
        List<Horario> enVentana = horariosHoy.stream()
            .filter(h -> h.estaEnCurso(ahora))
            .toList();

        if (enVentana.isEmpty()) {
            return Optional.empty();
        }
        if (enVentana.size() == 1) {
            return Optional.of(enVentana.get(0));
        }

        // --- Caso ambiguo: hay más de un horario en ventana (RF-18) ---
        log.info("RF-18 ambigüedad: docente {} tiene {} horarios en ventana a las {} - aplicando desempate",
                 docenteId, enVentana.size(), ahora);

        // Criterio 1: preferir los que todavía no tienen marca.
        List<Horario> sinMarca = enVentana.stream()
            .filter(h -> asistenciaRepository
                .findByDocenteIdAndHorarioIdAndFecha(docenteId, h.getId(), fecha)
                .isEmpty())
            .toList();
        List<Horario> candidatos = sinMarca.isEmpty() ? enVentana : sinMarca;

        // Criterios 2 y 3: hora de inicio más cercana, y menor id ante empate.
        Optional<Horario> elegido = candidatos.stream()
            .min(Comparator
                .comparingLong((Horario h) -> minutosHasta(ahora, h.getHoraInicio()))
                .thenComparing(Horario::getId));

        elegido.ifPresent(h -> log.info(
            "RF-18 desempate: elegido horario {} (inicio {}) para docente {}",
            h.getId(), h.getHoraInicio(), docenteId));
        return elegido;
    }

    // Distancia absoluta en minutos entre dos horas del mismo día.
    private long minutosHasta(LocalTime desde, LocalTime hasta) {
        return Math.abs(java.time.Duration.between(desde, hasta).toMinutes());
    }

    /**
     * PRESENTE mientras la llegada entre en la tolerancia del horario, TARDE después.
     *
     * <p><b>Cambió en ADR-0018.</b> Antes clasificaba TARDE apenas pasaba {@code hora_inicio}
     * y la tolerancia solo servía para llegar antes; ahora perdona hacia los dos lados, que
     * es lo que dicen el RF-19, el glosario y el javadoc de {@link Horario}. Con tolerancia
     * 15, llegar 18:05 a una clase de las 18:00 pasa de TARDE a PRESENTE.
     *
     * <p>Las marcas anteriores al cambio conservan el estado con el que se guardaron: no se
     * recalcula el histórico.
     */
    private EstadoAsistencia calcularEstado(Horario h, LocalTime ahora) {
        return h.llegadaEnHora(ahora)
            ? EstadoAsistencia.PRESENTE
            : EstadoAsistencia.TARDE;
    }

    /**
     * Pasa la distancia LBPH (menor = mejor) a un score 0-1 (mayor = mejor) contra el umbral.
     *
     * <p>Es público porque {@code BloquePresenciaService} guarda la misma clase de score para
     * la entrada y la salida del bloque, y la conversión tiene que ser una sola: es una
     * decisión documentada (TD-004, ADR-0008), no una cuenta suelta que cada quien repite.
     */
    public BigDecimal distanciaToConfianza(double distancia) {
        if (umbralDistancia <= 0) return BigDecimal.ZERO;
        double score = Math.max(0.0, 1.0 - (distancia / umbralDistancia));
        score = Math.min(1.0, score);
        return BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
    }

    // Trae un horario asegurando que sea del tenant actual (se deduce por su materia padre).
    private Horario obtenerHorarioValidado(Long horarioId, Long tenantId) {
        Horario h = horarioRepository.findById(horarioId)
            .orElseThrow(() -> new IllegalArgumentException(
                "El horario seleccionado no existe."));
        if (h.getComision() == null || h.getComision().getMateria() == null
                || !tenantId.equals(h.getComision().getMateria().getInstitucionId())) {
            log.warn("Cross-tenant blocked: tenant {} intento usar horario id={}",
                     tenantId, horarioId);
            throw new IllegalArgumentException("El horario seleccionado no existe.");
        }
        return h;
    }

    // Normaliza texto opcional: deja null si viene vacío o solo con espacios.
    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // Trae un docente asegurando que sea del tenant actual; si no, responde "no encontrado".
    private Docente obtenerDocenteValidado(Long docenteId, Long tenantId) {
        Docente d = docenteRepository.findById(docenteId)
            .orElseThrow(() -> new EntityNotFoundException("Docente no encontrado: " + docenteId));
        if (!tenantId.equals(d.getInstitucionId())) {
            log.warn("Cross-tenant blocked: tenant {} intento marcar asistencia para docente id={} (tenant {})",
                     tenantId, docenteId, d.getInstitucionId());
            throw new EntityNotFoundException("Docente no encontrado");
        }
        return d;
    }

    // Reloj inyectable para futuros tests (Clock.systemDefaultZone() por default).
    @SuppressWarnings("unused")
    private Clock clock = Clock.systemDefaultZone();

    // Formato de fecha para los mensajes que ve el usuario (dd/MM/yyyy).
    private static final DateTimeFormatter FORMATO_FECHA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy");
}
