package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.dto.BloqueDeHorarios;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.BloquePresencia;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoCierre;
import edu.cent35.asistencias.model.EstadoConsentimiento;
import edu.cent35.asistencias.model.EstadoSalida;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.ModeloFacial;
import edu.cent35.asistencias.model.OrigenMarca;
import edu.cent35.asistencias.model.MotivoCargaManual;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.MotivoCargaManualRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.repository.BloquePresenciaRepository;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.ModeloFacialRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Abre y cierra los bloques de presencia: decide si una pasada por la cámara es la entrada o
 * la salida del docente, y le imputa las clases que efectivamente cubrió (RF-74 a RF-82).
 * <p>
 * <b>El sentido de la marca no se elige, se deduce del estado.</b> Con un bloque abierto y la
 * permanencia mínima cumplida, la marca es salida; en cualquier otro caso es entrada. No hay
 * selector en la pantalla porque el docente es sujeto pasivo por diseño, y un modo mal
 * seleccionado produce un dato incorrecto que parece correcto (ADR-0017, decisión 6).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BloquePresenciaService {

    private final BloquePresenciaRepository bloqueRepository;
    private final HorarioRepository horarioRepository;
    private final DocenteRepository docenteRepository;
    private final ModeloFacialRepository modeloFacialRepository;
    private final ResolutorDeBloquesService resolutor;
    private final AsistenciaService asistenciaService;
    private final ConsentimientoBiometricoService consentimientoService;
    private final AsistenciaRepository asistenciaRepository;
    private final UsuarioRepository usuarioRepository;
    private final MotivoCargaManualRepository motivoCargaManualRepository;

    // Minutos que tienen que pasar desde la entrada para aceptar una salida (RF-77).
    @Value("${app.asistencia.permanencia-minima-min}")
    private int permanenciaMinimaMin;

    // Qué hizo el sistema con la pasada por la cámara.
    public enum TipoDeMarca { ENTRADA, SALIDA, RECHAZADA }

    /**
     * Resultado de registrar una presencia.
     *
     * @param tipo             si abrió, cerró, o no hizo nada
     * @param bloque           el bloque abierto o cerrado; null si se rechazó
     * @param clasesImputadas  cuántas clases del bloque quedaron con asistencia registrada.
     *                         Incluye las que ya la tenían: la imputación es idempotente y
     *                         devuelve la marca existente en vez de duplicarla
     * @param asistencia       la clase que se marcó al entrar, para poder nombrarla en pantalla.
     *                         Null al salir: ahí puede haber varias y ninguna es "la" clase
     * @param motivo           por qué se rechazó, en texto para el operador; null si no se rechazó
     */
    public record ResultadoPresencia(
        TipoDeMarca tipo,
        BloquePresencia bloque,
        int clasesImputadas,
        Asistencia asistencia,
        String motivo
    ) {
        public static ResultadoPresencia entrada(BloquePresencia b, int imputadas, Asistencia a) {
            return new ResultadoPresencia(TipoDeMarca.ENTRADA, b, imputadas, a, null);
        }
        public static ResultadoPresencia salida(BloquePresencia b, int imputadas) {
            return new ResultadoPresencia(TipoDeMarca.SALIDA, b, imputadas, null, null);
        }
        public static ResultadoPresencia rechazada(String motivo) {
            return new ResultadoPresencia(TipoDeMarca.RECHAZADA, null, 0, null, motivo);
        }
        public boolean registrada() {
            return tipo != TipoDeMarca.RECHAZADA;
        }
    }

    /**
     * Registra una pasada por la cámara como entrada o como salida, según corresponda.
     *
     * <p>La identidad ya viene confirmada por el pipeline de identificación: acá no se decide
     * quién es la persona, solo qué significa que esté parada frente a la cámara ahora.
     */
    @Transactional
    public ResultadoPresencia registrar(Long docenteId, Long modeloFacialId,
                                        Double distanciaLbph, LocalDateTime instante) {
        Long tenantId = TenantContext.getRequired();
        Docente docente = obtenerDocenteValidado(docenteId, tenantId);

        // Regla dura: sin consentimiento vigente no se usa un rostro, ni para entrar ni para
        // salir (RF-82, RNF-13, Ley 25.326). Va antes que cualquier otra cosa porque no es una
        // validación de negocio que se pueda reordenar por conveniencia.
        if (consentimientoService.estadoActual(docenteId) != EstadoConsentimiento.ACTIVO) {
            log.warn("Presencia rechazada por consentimiento no vigente: docente={}", docenteId);
            return ResultadoPresencia.rechazada(
                "El consentimiento biométrico de " + docente.getNombreCompleto()
                + " no está vigente, así que su rostro no se puede usar. "
                + "Registrá la asistencia por carga manual.");
        }

        Optional<BloquePresencia> abierto =
            bloqueRepository.findByDocenteIdAndEstadoCierre(docenteId, EstadoCierre.ABIERTO);

        return abierto.isPresent()
            ? cerrar(abierto.get(), docente, modeloFacialId, distanciaLbph, instante)
            : abrir(docente, tenantId, modeloFacialId, distanciaLbph, instante);
    }

    // ------------------------------------------------------------------------
    //  Apertura
    // ------------------------------------------------------------------------

    /**
     * Abre un bloque e imputa únicamente la clase en curso (RF-81).
     *
     * <p>Las clases siguientes del bloque <b>no</b> se marcan acá. Hacerlo asentaría como
     * hecho consumado una clase que todavía no empezó, y si el docente se retira antes queda
     * registrado que dictó algo que no dictó. Se imputan al cerrar, según lo que haya cubierto.
     */
    private ResultadoPresencia abrir(Docente docente, Long tenantId, Long modeloFacialId,
                                     Double distanciaLbph, LocalDateTime instante) {
        Optional<BloqueDeHorarios> enCurso = resolutor.bloqueEnCurso(docente.getId(), instante);
        if (enCurso.isEmpty()) {
            log.info("Entrada rechazada: docente {} no tiene clase en ventana a las {}",
                     docente.getId(), instante.toLocalTime());
            return ResultadoPresencia.rechazada(
                "No hay clase en este momento para " + docente.getNombreCompleto() + ".");
        }

        LocalTime horaEntrada = instante.toLocalTime().withNano(0);
        ModeloFacial modelo = (modeloFacialId == null) ? null
            : modeloFacialRepository.findById(modeloFacialId).orElse(null);

        BloquePresencia bloque = BloquePresencia.builder()
            .docente(docente)
            .fecha(instante.toLocalDate())
            .horaEntrada(horaEntrada)
            .origenEntrada(OrigenMarca.AUTOMATICO)
            .modeloFacialEntrada(modelo)
            .confianzaEntrada(distanciaLbph == null ? null
                : asistenciaService.distanciaToConfianza(distanciaLbph))
            .estadoCierre(EstadoCierre.ABIERTO)
            .build();
        bloque.setInstitucionId(tenantId);

        BloquePresencia guardado;
        try {
            guardado = bloqueRepository.saveAndFlush(bloque);
        } catch (DataIntegrityViolationException ex) {
            // El loop del navegador mandó dos frames casi juntos y el UNIQUE de la base
            // resolvió la carrera. Devolvemos el bloque que quedó, sin volver a imputar.
            log.info("Apertura descartada por bloque concurrente: docente={}", docente.getId());
            return bloqueRepository
                .findByDocenteIdAndEstadoCierre(docente.getId(), EstadoCierre.ABIERTO)
                .map(b -> ResultadoPresencia.entrada(b, 0, null))
                .orElseThrow(() -> ex);
        }

        // De todas las clases del bloque, solo la que está corriendo ahora.
        Optional<Horario> claseActual = enCurso.get().horarios().stream()
            .filter(h -> h.estaEnCurso(horaEntrada))
            .min(Comparator.comparing(Horario::getHoraInicio).thenComparing(Horario::getId));

        int imputadas = 0;
        Asistencia marcada = null;
        if (claseActual.isPresent()) {
            marcada = asistenciaService.imputarDelBloque(guardado, claseActual.get(), horaEntrada);
            imputadas = 1;
        }

        log.info("Bloque abierto: id={}, docente={}, fecha={}, entrada={}, clases del bloque={}",
                 guardado.getId(), docente.getId(), guardado.getFecha(), horaEntrada,
                 enCurso.get().cantidadDeClases());
        return ResultadoPresencia.entrada(guardado, imputadas, marcada);
    }

    // ------------------------------------------------------------------------
    //  Cierre
    // ------------------------------------------------------------------------

    // Cierra el bloque por reconocimiento e imputa lo que el docente haya alcanzado a cubrir.
    private ResultadoPresencia cerrar(BloquePresencia bloque, Docente docente,
                                      Long modeloFacialId, Double distanciaLbph,
                                      LocalDateTime instante) {
        LocalTime horaSalida = instante.toLocalTime().withNano(0);

        long minutosAdentro = minutosDesdeLaEntrada(bloque, instante);
        if (minutosAdentro < permanenciaMinimaMin) {
            log.info("Salida rechazada por permanencia mínima: docente={}, lleva {} min de {}",
                     docente.getId(), minutosAdentro, permanenciaMinimaMin);
            return ResultadoPresencia.rechazada(
                "Todavía no pasaron " + permanenciaMinimaMin + " minutos desde que "
                + docente.getNombreCompleto() + " registró su entrada.");
        }

        ModeloFacial modelo = (modeloFacialId == null) ? null
            : modeloFacialRepository.findById(modeloFacialId).orElse(null);

        List<Horario> cubiertas = clasesCubiertas(
            docente.getId(), bloque.getFecha(), bloque.getHoraEntrada(), horaSalida);

        bloque.setHoraSalida(horaSalida);
        bloque.setOrigenSalida(OrigenMarca.AUTOMATICO);
        bloque.setModeloFacialSalida(modelo);
        bloque.setConfianzaSalida(distanciaLbph == null ? null
            : asistenciaService.distanciaToConfianza(distanciaLbph));
        bloque.setEstadoCierre(EstadoCierre.CERRADO_POR_ROSTRO);
        bloque.setEstadoSalida(clasificarSalida(cubiertas, horaSalida));

        BloquePresencia cerrado = bloqueRepository.saveAndFlush(bloque);

        int imputadas = 0;
        for (Horario h : cubiertas) {
            // La hora de llegada de cada clase: la entrada real en la que estaba corriendo, y
            // su propia hora de inicio en las que empezaron con el docente ya adentro.
            LocalTime llegada = bloque.getHoraEntrada().isAfter(h.getHoraInicio())
                ? bloque.getHoraEntrada()
                : h.getHoraInicio();
            asistenciaService.imputarDelBloque(cerrado, h, llegada);
            imputadas++;
        }

        log.info("Bloque cerrado por rostro: id={}, docente={}, {} a {}, salida={}, clases={}",
                 cerrado.getId(), docente.getId(), cerrado.getHoraEntrada(), horaSalida,
                 cerrado.getEstadoSalida(), imputadas);
        return ResultadoPresencia.salida(cerrado, imputadas);
    }

    // ------------------------------------------------------------------------
    //  Cierre manual (RF-83)
    // ------------------------------------------------------------------------

    /**
     * Resultado de cerrar o corregir un bloque a mano.
     *
     * @param bloque        el bloque ya cerrado
     * @param imputadas     clases que quedaron con asistencia por este cierre
     * @param fueraDeRango  asistencias del bloque que la hora nueva deja afuera. <b>No se
     *                      borran</b>: quitar una marca de asistencia es otro acto
     *                      administrativo y tiene su propio flujo. Se devuelven para poder
     *                      avisarle al admin qué quedó marcado por un rango que ya no existe
     */
    public record ResultadoCierreManual(
        BloquePresencia bloque,
        int imputadas,
        List<Asistencia> fueraDeRango
    ) {}

    /**
     * Cierra un bloque a mano, o corrige la hora de salida de uno ya cerrado (RF-83).
     *
     * <p>Es el camino cuando el reconocimiento falla al salir —que con LBPH pasa, basta un
     * cambio de iluminación respecto del momento de la entrada—, cuando el consentimiento
     * dejó de estar vigente y el rostro ya no se puede usar (RF-82), o cuando el sistema
     * presumió una hora que no fue la real.
     *
     * <p><b>Corregir un cierre por reconocimiento borra su evidencia biométrica.</b> El
     * modelo y la confianza de salida se limpian, porque el CHECK
     * {@code ck_bloques_salida_modelo} solo los admite en un cierre AUTOMATICO y porque la
     * afirmación cambió de dueño: ahora la hora la sostiene una persona, no una medición.
     * Es deliberado y tiene su costo — esa evidencia no se recupera.
     *
     * <p><b>No se guarda el valor anterior.</b> Alcanza para saber quién afirma que el
     * docente se fue a tal hora, no para reconstruir qué decía el registro antes. Guardar el
     * historial completo sería la auditoría que se descartó en V009.
     */
    @Transactional
    public ResultadoCierreManual cerrarManualmente(Long bloqueId, LocalTime horaSalida,
                                                   Short motivoId, String detalle,
                                                   Long usuarioActualId) {
        Long tenantId = TenantContext.getRequired();
        BloquePresencia bloque = bloqueRepository.findById(bloqueId)
            .orElseThrow(() -> new EntityNotFoundException("Bloque no encontrado: " + bloqueId));
        if (!tenantId.equals(bloque.getInstitucionId())) {
            log.warn("Cross-tenant blocked: tenant {} intento cerrar bloque id={} (tenant {})",
                     tenantId, bloqueId, bloque.getInstitucionId());
            throw new EntityNotFoundException("Bloque no encontrado");
        }

        if (horaSalida == null) {
            throw new IllegalArgumentException("Hay que indicar la hora de salida.");
        }
        if (!horaSalida.isAfter(bloque.getHoraEntrada())) {
            throw new IllegalArgumentException(
                "La hora de salida tiene que ser posterior a la de entrada ("
                + bloque.getHoraEntrada() + ").");
        }
        // Una salida futura acreditaria una permanencia que todavia no ocurrio. Solo aplica
        // al bloque de hoy: los de dias anteriores ya terminaron enteros.
        if (bloque.getFecha().equals(LocalDate.now()) && horaSalida.isAfter(LocalTime.now())) {
            throw new IllegalArgumentException("La hora de salida no puede ser futura.");
        }

        MotivoCargaManual motivo = motivoCargaManualRepository.findById(motivoId)
            .orElseThrow(() -> new IllegalArgumentException("El motivo seleccionado no existe."));
        if (Boolean.FALSE.equals(motivo.getActivo())) {
            throw new IllegalArgumentException("El motivo elegido está inactivo.");
        }
        String detalleLimpio = trimToNull(detalle);
        if ("OTRO".equals(motivo.getCodigo()) && detalleLimpio == null) {
            throw new IllegalArgumentException(
                "Elegiste el motivo Otro: contá en el detalle qué pasó, si no el registro "
                + "no dice nada.");
        }

        Usuario admin = usuarioRepository.findById(usuarioActualId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Usuario actual no encontrado: " + usuarioActualId));

        List<Horario> cubiertas = clasesCubiertas(
            bloque.getDocente().getId(), bloque.getFecha(), bloque.getHoraEntrada(), horaSalida);

        bloque.setHoraSalida(horaSalida);
        bloque.setOrigenSalida(OrigenMarca.MANUAL);
        bloque.setEstadoCierre(EstadoCierre.CERRADO_POR_ADMIN);
        bloque.setEstadoSalida(clasificarSalida(cubiertas, horaSalida));
        bloque.setCerradoPor(admin);
        bloque.setMotivoCierre(motivo);
        bloque.setDetalleCierre(detalleLimpio);
        // Lo exige ck_bloques_salida_modelo, y ademas la hora ya no la sostiene una medicion.
        bloque.setModeloFacialSalida(null);
        bloque.setConfianzaSalida(null);

        BloquePresencia cerrado = bloqueRepository.saveAndFlush(bloque);

        int imputadas = 0;
        for (Horario h : cubiertas) {
            LocalTime llegada = cerrado.getHoraEntrada().isAfter(h.getHoraInicio())
                ? cerrado.getHoraEntrada()
                : h.getHoraInicio();
            asistenciaService.imputarDelBloque(cerrado, h, llegada);
            imputadas++;
        }

        // Si la correccion acorto el rango, hay clases marcadas por un rango que ya no existe.
        Set<Long> idsCubiertos = cubiertas.stream().map(Horario::getId).collect(Collectors.toSet());
        List<Asistencia> fueraDeRango = asistenciaRepository
            .findByBloqueIdOrderByHoraRegistradaAsc(cerrado.getId()).stream()
            .filter(a -> !idsCubiertos.contains(a.getHorario().getId()))
            .toList();

        log.info("Bloque cerrado a mano: id={}, docente={}, salida={}, motivo={}, admin={}, "
                 + "clases={}, fuera de rango={}",
                 cerrado.getId(), cerrado.getDocente().getId(), horaSalida, motivo.getCodigo(),
                 admin.getId(), imputadas, fueraDeRango.size());
        return new ResultadoCierreManual(cerrado, imputadas, fueraDeRango);
    }

    // Normaliza texto opcional: deja null si viene vacio o solo con espacios.
    private static String trimToNull(String texto) {
        if (texto == null) return null;
        String t = texto.trim();
        return t.isEmpty() ? null : t;
    }

    // Bloques sin salida registrada, del mas viejo al mas nuevo (RF-79).
    @Transactional(readOnly = true)
    public List<BloquePresencia> pendientesDeCierre() {
        return bloqueRepository.findPendientesDeCierre(TenantContext.getRequired());
    }

    // ------------------------------------------------------------------------
    //  Cierre automatico
    // ------------------------------------------------------------------------

    /**
     * Cierra los bloques que quedaron abiertos porque nadie registró la salida, e imputa las
     * clases que el docente cubrió (RF-79, RF-80).
     *
     * <p><b>La asistencia no puede quedar rehén del dato de salida.</b> Si la imputación
     * ocurriera solo al cerrar por rostro, un bloque sin salida dejaría al docente AUSENTE en
     * clases que efectivamente dio, y el sistema estaría castigando a la persona por una falla
     * del procedimiento administrativo. Así que se imputa igual, tomando el fin de la última
     * clase del bloque como límite.
     *
     * <p><b>Lo que queda pendiente es la hora de salida, no la asistencia.</b> El bloque se
     * marca {@link EstadoCierre#SIN_CIERRE} con {@link OrigenMarca#PRESUNTO}, y de ahí sale la
     * lista de pendientes del panel de inicio. El sistema completa la hora para poder operar,
     * pero nunca la hace pasar por observada: distinguir lo que se midió de lo que se supuso
     * es lo que hace defendible el registro.
     *
     * <p>Corre dentro del {@code TenantContext} que le setea el job. Devuelve cuántos cerró.
     */
    @Transactional
    public int cerrarBloquesVencidos(LocalDate hoy, LocalTime ahora) {
        Long tenantId = TenantContext.getRequired();
        List<BloquePresencia> abiertos = bloqueRepository.findAbiertosHasta(tenantId, hoy);

        int cerrados = 0;
        for (BloquePresencia bloque : abiertos) {
            LocalTime salidaPresunta = finPresuntoDe(bloque);

            // Un bloque de hoy se cierra recién cuando terminó la última clase; los de días
            // anteriores se cierran siempre. Un bloque abierto de ayer no se cierra solo, y
            // mientras siga abierto el docente no puede volver a entrar: el UNIQUE de un solo
            // bloque abierto por docente se lo impide.
            if (bloque.getFecha().equals(hoy) && !ahora.isAfter(salidaPresunta)) {
                continue;
            }

            List<Horario> cubiertas = clasesCubiertas(
                bloque.getDocente().getId(), bloque.getFecha(),
                bloque.getHoraEntrada(), salidaPresunta);

            bloque.setHoraSalida(salidaPresunta);
            bloque.setOrigenSalida(OrigenMarca.PRESUNTO);
            bloque.setEstadoSalida(EstadoSalida.SIN_MARCA);
            bloque.setEstadoCierre(EstadoCierre.SIN_CIERRE);
            // Sin modelo ni confianza: no hubo reconocimiento. Lo exige ck_bloques_salida_modelo.
            BloquePresencia cerrado = bloqueRepository.saveAndFlush(bloque);

            for (Horario h : cubiertas) {
                LocalTime llegada = cerrado.getHoraEntrada().isAfter(h.getHoraInicio())
                    ? cerrado.getHoraEntrada()
                    : h.getHoraInicio();
                asistenciaService.imputarDelBloque(cerrado, h, llegada);
            }

            cerrados++;
            log.info("Bloque cerrado sin marca de salida: id={}, docente={}, fecha={}, "
                     + "entrada={}, salida presunta={}, clases imputadas={}",
                     cerrado.getId(), cerrado.getDocente().getId(), cerrado.getFecha(),
                     cerrado.getHoraEntrada(), salidaPresunta, cubiertas.size());
        }
        return cerrados;
    }

    /**
     * Hasta qué hora se presume que el docente estuvo: el fin de la franja que estaba
     * cursando cuando entró.
     *
     * <p>Si esa franja ya no existe —alguien cambió la grilla entre la entrada y ahora— no hay
     * de dónde deducir la hora, y devolver algo anterior a la entrada rompería el CHECK
     * {@code ck_bloques_salida_posterior}. En ese caso se cierra con un minuto de permanencia:
     * es evidentemente un valor de relleno, deja el bloque como pendiente para que alguien lo
     * corrija, y sobre todo <b>desbloquea al docente</b>, que con su bloque abierto no podría
     * volver a marcar nunca.
     */
    private LocalTime finPresuntoDe(BloquePresencia bloque) {
        LocalTime entrada = bloque.getHoraEntrada();
        LocalTime fin = resolutor.bloquesDelDia(bloque.getDocente().getId(), bloque.getFecha())
            .stream()
            .filter(b -> !b.horaFin().isBefore(entrada))
            .findFirst()
            .map(BloqueDeHorarios::horaFin)
            .orElse(null);

        if (fin == null || !fin.isAfter(entrada)) {
            log.warn("Bloque {} sin franja determinable (docente={}, fecha={}, entrada={}): "
                     + "se cierra con un minuto para no dejarlo abierto",
                     bloque.getId(), bloque.getDocente().getId(), bloque.getFecha(), entrada);
            return entrada.plusMinutes(1);
        }
        return fin;
    }

    // ------------------------------------------------------------------------

    /**
     * Las clases del docente que quedaron cubiertas por el lapso entre entrada y salida
     * (RF-81).
     *
     * <p>El criterio es el solapamiento y no la agrupación: una clase se imputa si el docente
     * estuvo presente durante alguna parte de ella. No se vuelve a preguntar cómo agrupa la
     * grilla, porque el bloque ya está registrado y su franja es la que vale — si entre la
     * entrada y la salida alguien cambió un horario o el umbral de la institución, gana lo que
     * quedó asentado.
     */
    List<Horario> clasesCubiertas(Long docenteId, LocalDate fecha,
                                  LocalTime entrada, LocalTime salida) {
        Long tenantId = TenantContext.getRequired();
        byte diaSemana = (byte) fecha.getDayOfWeek().getValue();

        return horarioRepository.findHoyParaDocente(docenteId, diaSemana, fecha, tenantId).stream()
            .filter(h -> h.getHoraInicio() != null && h.getHoraFin() != null)
            .filter(h -> entrada.isBefore(h.getHoraFin()) && salida.isAfter(h.getHoraInicio()))
            .sorted(Comparator.comparing(Horario::getHoraInicio).thenComparing(Horario::getId))
            .toList();
    }

    /**
     * Si la salida fue en hora o anticipada, medida contra la última clase cubierta (RF-78).
     *
     * <p>Sin ninguna clase cubierta la salida es anticipada por definición: el docente se fue
     * antes de que empezara la primera. Es un caso muy de borde —la permanencia mínima ya
     * obliga a quedarse diez minutos— pero dejarlo como "en hora" afirmaría que cumplió una
     * jornada en la que no llegó a estar en ninguna clase.
     */
    private EstadoSalida clasificarSalida(List<Horario> cubiertas, LocalTime horaSalida) {
        if (cubiertas.isEmpty()) {
            return EstadoSalida.ANTICIPADA;
        }
        Horario ultima = cubiertas.stream()
            .max(Comparator.comparing(Horario::getHoraFin).thenComparing(Horario::getId))
            .orElseThrow();
        return ultima.salidaEnHora(horaSalida) ? EstadoSalida.EN_HORA : EstadoSalida.ANTICIPADA;
    }

    /**
     * Cuántos minutos lleva el docente adentro.
     *
     * <p>Un bloque de una fecha anterior se da por cumplido sin hacer la cuenta: las horas son
     * {@link LocalTime} y restarlas entre días distintos da cualquier cosa. Ese bloque además
     * ya es un pendiente que el job va a cerrar.
     */
    private long minutosDesdeLaEntrada(BloquePresencia bloque, LocalDateTime instante) {
        if (!bloque.getFecha().equals(instante.toLocalDate())) {
            return Long.MAX_VALUE;
        }
        return Duration.between(bloque.getHoraEntrada(), instante.toLocalTime()).toMinutes();
    }

    // Carga el docente validando tenant; si es de otro responde "no existe" para no revelarlo.
    private Docente obtenerDocenteValidado(Long docenteId, Long tenantId) {
        Docente d = docenteRepository.findById(docenteId)
            .orElseThrow(() -> new EntityNotFoundException("Docente no encontrado: " + docenteId));
        if (!tenantId.equals(d.getInstitucionId())) {
            log.warn("Cross-tenant blocked: tenant {} intento operar sobre docente id={} (tenant {})",
                     tenantId, docenteId, d.getInstitucionId());
            throw new EntityNotFoundException("Docente no encontrado");
        }
        return d;
    }
}
