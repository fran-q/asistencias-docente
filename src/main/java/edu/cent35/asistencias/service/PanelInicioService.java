package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.dto.ClasesDeAhoraDto;
import edu.cent35.asistencias.dto.PanelInicioDto;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.DiaSemana;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoAsistencia;
import edu.cent35.asistencias.model.EstadoCiclo;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.ModeloFacial;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.BloquePresenciaRepository;
import edu.cent35.asistencias.repository.CicloLectivoRepository;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.ConsentimientoBiometricoRepository;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.ModeloFacialRepository;
import edu.cent35.asistencias.repository.PuestoCapturaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Arma el panel de la pantalla de inicio (RF-60).
 *
 * <p>Todo sale de consultas que ya existian para otras pantallas; lo unico que se agrega es
 * cruzarlas. Se resuelve entero en una sola transaccion de lectura porque es lo primero que
 * ve el operador al entrar y no puede costar mas que la pantalla que va a abrir despues.
 *
 * <p>Tambien explica por que no se esta tomando asistencia, para el inicio y para el pase: un
 * ciclo sin activar, un dia sin clases, una comision sin docente. Antes las dos pantallas decian
 * "no hay clases" en todos los casos, y encontrar la causa exigia revisar el calendario entero.
 */
@Service
@RequiredArgsConstructor
public class PanelInicioService {

    private final HorarioRepository horarioRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final DocenteRepository docenteRepository;
    private final ConsentimientoBiometricoRepository consentimientoRepository;
    private final ModeloFacialRepository modeloFacialRepository;
    private final ComisionRepository comisionRepository;
    private final BloquePresenciaRepository bloquePresenciaRepository;
    private final CicloLectivoRepository cicloRepository;
    private final PuestoCapturaRepository puestoRepository;
    private final DiaNoLaborableService diaNoLaborableService;

    // Cuantas clases en curso se muestran como maximo, para que el panel no crezca sin limite.
    private static final int MAX_EN_CURSO = 6;

    // Cuantas clases por venir se anticipan. Mas de tres deja de ser "que sigue" y pasa a
    // ser la grilla del dia, que ya tiene su propia pantalla.
    private static final int MAX_PROXIMAS = 3;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Solo la cuenta de la institucion abre esta pantalla: para otro rol el aviso va sin enlace.
    private static final String CICLOS = "/ciclos";

    // Reloj inyectable: todo el panel se define contra "ahora", asi que sin poder fijarlo
    // los tests dependerian de la hora a la que se corren.
    private Clock clock = Clock.systemDefaultZone();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    // Arma los tres bloques de la pantalla de inicio en una sola transaccion de lectura.
    @Transactional(readOnly = true)
    public PanelInicioDto armar() {
        Long tenantId = TenantContext.getRequired();
        LocalDate hoy = LocalDate.now(clock);
        LocalTime ahora = LocalTime.now(clock);

        List<Horario> clasesDeHoy = horarioRepository.findActivosDelDiaConDocente(
            (byte) hoy.getDayOfWeek().getValue(), hoy, tenantId);
        List<Asistencia> marcasDeHoy = asistenciaRepository.findDelDia(TenantContext.getRequired(), hoy);
        List<PanelInicioDto.ClaseEnCurso> enCurso = clasesEnCurso(clasesDeHoy, marcasDeHoy, ahora);
        List<PanelInicioDto.ProximaClase> proximas = proximasClases(clasesDeHoy, ahora);
        List<CicloLectivo> ciclos = cicloRepository.listarDelTenant(tenantId);

        return new PanelInicioDto(
            enCurso,
            proximas,
            resumenDelDia(clasesDeHoy, marcasDeHoy, ahora),
            pendientes(tenantId, hoy, ciclos),
            enCurso.isEmpty() && proximas.isEmpty()
                ? motivoSinClases(tenantId, hoy, clasesDeHoy, ciclos)
                : null);
    }

    /**
     * Las clases de este momento, para el pase.
     *
     * <p>Quien toma asistencia tenia que saber de memoria, o ir a buscarlo al inicio, que clase
     * correspondia y a quien estaba esperando (heuristica 6: reconocer antes que recordar). Es
     * el mismo calculo que el inicio y no uno parecido: si el pase mostrara como en curso algo
     * que el inicio no, las dos pantallas se contradirian sobre lo mismo.
     *
     * <p>Con las clases viaja lo que impide marcar: por que no hay ninguna, si no hay, y lo que
     * falta cargar para que el pase reconozca a alguien y le impute su clase.
     */
    @Transactional(readOnly = true)
    public ClasesDeAhoraDto clasesDeAhora() {
        Long tenantId = TenantContext.getRequired();
        LocalDate hoy = LocalDate.now(clock);
        LocalTime ahora = LocalTime.now(clock);
        List<Horario> clasesDeHoy = horarioRepository.findActivosDelDiaConDocente(
            (byte) hoy.getDayOfWeek().getValue(), hoy, tenantId);
        List<Asistencia> marcasDeHoy = asistenciaRepository.findDelDia(tenantId, hoy);
        List<PanelInicioDto.ClaseEnCurso> enCurso = clasesEnCurso(clasesDeHoy, marcasDeHoy, ahora);
        List<PanelInicioDto.ProximaClase> proximas = proximasClases(clasesDeHoy, ahora);

        // Los ciclos se piden solo si hace falta explicar un dia vacio: el pase refresca esta
        // tarjeta cada minuto, y con clases en curso no hay nada que explicar.
        PanelInicioDto.MotivoSinClases motivo = enCurso.isEmpty() && proximas.isEmpty()
            ? motivoSinClases(tenantId, hoy, clasesDeHoy, cicloRepository.listarDelTenant(tenantId))
            : null;

        return new ClasesDeAhoraDto(enCurso, proximas, motivo, pendientesDeCarga(tenantId, hoy));
    }

    // ------------------------------------------------------------------------
    //  Bloque 1: que esta corriendo ahora
    // ------------------------------------------------------------------------

    private List<PanelInicioDto.ClaseEnCurso> clasesEnCurso(List<Horario> clasesDeHoy,
                                                            List<Asistencia> marcasDeHoy,
                                                            LocalTime ahora) {
        // Indice por horario para no recorrer las marcas dentro del bucle de clases.
        Map<Long, Asistencia> porHorario = new HashMap<>();
        for (Asistencia a : marcasDeHoy) {
            porHorario.put(a.getHorario().getId(), a);
        }

        List<PanelInicioDto.ClaseEnCurso> filas = new ArrayList<>();
        for (Horario h : clasesDeHoy) {
            // La ventana la decide el propio horario, igual que en el pase: si aca se
            // usara otro criterio, la home mostraria como en curso algo que el pase
            // despues se niega a marcar.
            if (!h.estaEnCurso(ahora)) continue;

            Comision c = h.getComision();
            Asistencia marca = porHorario.get(h.getId());
            filas.add(new PanelInicioDto.ClaseEnCurso(
                h.getHoraInicio(),
                h.getHoraFin(),
                c.getCodigo(),
                c.getMateria().getNombre(),
                c.getDocenteAsignado().getNombreCompleto(),
                marca != null,
                marca == null ? null : marca.getEstado().name(),
                marca == null ? null : marca.getHoraRegistrada()));

            if (filas.size() == MAX_EN_CURSO) break;
        }
        return filas;
    }

    /**
     * Las clases que todavia no abrieron su ventana, de la mas proxima en adelante.
     *
     * <p>Se calculan siempre, pero la pantalla solo las muestra cuando no hay ninguna en
     * curso: el bloque pasaba la mayor parte del dia diciendo "no hay nada" y ocupando un
     * tercio del ancho igual.
     */
    private List<PanelInicioDto.ProximaClase> proximasClases(List<Horario> clasesDeHoy,
                                                             LocalTime ahora) {
        return clasesDeHoy.stream()
            .filter(h -> !h.estaEnCurso(ahora))
            .filter(h -> h.getHoraInicio().isAfter(ahora))
            .sorted(Comparator.comparing(Horario::getHoraInicio))
            .limit(MAX_PROXIMAS)
            .map(h -> {
                Comision c = h.getComision();
                return new PanelInicioDto.ProximaClase(
                    h.getHoraInicio(), h.getHoraFin(),
                    c.getCodigo(), c.getMateria().getNombre(),
                    c.getDocenteAsignado().getNombreCompleto());
            })
            .toList();
    }

    /**
     * Por qué no hay nada en curso ni por venir hoy, de lo que explica más a lo que explica
     * menos.
     *
     * <p>El orden importa: un feriado explica el día entero aunque el calendario esté mal
     * cargado, y un ciclo sin activar explica por qué no aparece ninguna clase aunque haya
     * horarios de sobra. Recién con el calendario en orden tiene sentido mirar las clases.
     */
    private PanelInicioDto.MotivoSinClases motivoSinClases(Long tenantId, LocalDate hoy,
                                                           List<Horario> clasesDeHoy,
                                                           List<CicloLectivo> ciclos) {
        Optional<String> feriado = diaNoLaborableService.motivoSinClases(tenantId, hoy);
        if (feriado.isPresent()) {
            return PanelInicioDto.MotivoSinClases.informativo(
                "Hoy no hay clases: " + feriado.get() + ".");
        }

        Optional<ProblemaDelCiclo> delCiclo = problemaDelCiclo(ciclos, hoy);
        if (delCiclo.isPresent()) {
            ProblemaDelCiclo p = delCiclo.get();
            String texto = p.titulo() + ". " + p.detalle();
            return p.requiereAtencion()
                ? new PanelInicioDto.MotivoSinClases(texto, true, "Ir a Ciclos lectivos", CICLOS, true)
                : PanelInicioDto.MotivoSinClases.informativo(texto);
        }

        if (!clasesDeHoy.isEmpty()) {
            // Habia clases, y ninguna esta en curso ni por venir: terminaron todas.
            return PanelInicioDto.MotivoSinClases.informativo("Las clases de hoy ya terminaron.");
        }

        // La grilla semanal muestra estas clases, y el pase no: sin docente no hay a quien
        // imputarselas. Es la otra causa que no se ve desde los horarios.
        long sinDocente = horarioRepository.contarDelDiaSinDocente(
            (byte) hoy.getDayOfWeek().getValue(), hoy, tenantId);
        if (sinDocente > 0) {
            return new PanelInicioDto.MotivoSinClases(
                (sinDocente == 1 ? "Hoy hay 1 clase" : "Hoy hay " + sinDocente + " clases")
                + " en comisiones sin docente asignado, y una clase sin docente no se le puede "
                + "marcar a nadie.",
                true, "Asignar docentes en Comisiones", "/comisiones", false);
        }

        // Dentro del ciclo pero entre periodos, como el receso entre cuatrimestres: no es un
        // error, pero sin decirlo parece que se perdieron los horarios.
        Optional<CicloLectivo> activo = cicloActivo(ciclos);
        if (activo.isPresent() && activo.get().getPeriodos().stream()
                .noneMatch(p -> !hoy.isBefore(p.getFechaInicio()) && !hoy.isAfter(p.getFechaFin()))) {
            return PanelInicioDto.MotivoSinClases.informativo(
                "Hoy no cae dentro de ningún período del ciclo " + activo.get().getAnio() + ".");
        }

        // "Los lunes", pero "los sábados": solo los dos del fin de semana cambian en plural.
        String dia = DiaSemana.deLaFecha(hoy).getEtiqueta().toLowerCase();
        return PanelInicioDto.MotivoSinClases.informativo(
            "Los " + (dia.endsWith("s") ? dia : dia + "s") + " no hay clases cargadas.");
    }

    /**
     * Lo que le pasa al calendario, si le pasa algo.
     *
     * <p>Sin un ciclo activo que incluya el día de hoy, ni el pase ni el inicio ven ninguna
     * clase. Es la causa más común de "no me toma la asistencia" y la más difícil de encontrar:
     * la grilla semanal y los horarios no miran el estado del ciclo, así que desde ahí todo
     * parece cargado.
     *
     * @param requiereAtencion si hay algo que hacer --va a "Requiere atención"-- o es solo cómo
     *                         viene el calendario, como un ciclo que todavía no empezó
     */
    private record ProblemaDelCiclo(String titulo, String detalle, boolean requiereAtencion) {}

    private Optional<ProblemaDelCiclo> problemaDelCiclo(List<CicloLectivo> ciclos, LocalDate hoy) {
        Optional<CicloLectivo> activo = cicloActivo(ciclos);
        if (activo.isPresent()) {
            CicloLectivo c = activo.get();
            // No se cierra solo a proposito --cerrar es una decision--, pero terminado y activo
            // no ve ninguna clase, y el año siguiente tampoco hasta que se active.
            if (hoy.isAfter(c.getFechaFin())) {
                return Optional.of(new ProblemaDelCiclo(
                    "El ciclo lectivo " + c.getAnio() + " terminó el "
                        + c.getFechaFin().format(FECHA) + " y sigue activo",
                    "Mientras tanto el pase no ve ninguna clase. Cerralo y activá el del año "
                        + "siguiente.",
                    true));
            }
            if (hoy.isBefore(c.getFechaInicio())) {
                return Optional.of(new ProblemaDelCiclo(
                    "El ciclo lectivo " + c.getAnio() + " empieza el "
                        + c.getFechaInicio().format(FECHA),
                    "Hasta ese día no hay clases que marcar.",
                    false));
            }
            return Optional.empty();
        }

        // Sin ciclo activo. El que casi siempre falta activar es el de este año.
        Optional<CicloLectivo> enPreparacion = ciclos.stream()
            .filter(c -> c.getEstado() == EstadoCiclo.PREPARACION)
            .filter(c -> c.getAnio() != null && c.getAnio().intValue() == hoy.getYear())
            .findFirst();
        if (enPreparacion.isPresent()) {
            return Optional.of(new ProblemaDelCiclo(
                "El ciclo lectivo " + enPreparacion.get().getAnio() + " está en preparación",
                "Hasta que lo actives, el pase no ve ninguna clase y no se toma asistencia.",
                true));
        }
        return Optional.of(new ProblemaDelCiclo(
            "No hay ningún ciclo lectivo activo",
            "Sin un ciclo activo el pase no ve ninguna clase. Creá el del año y activalo.",
            true));
    }

    private static Optional<CicloLectivo> cicloActivo(List<CicloLectivo> ciclos) {
        return ciclos.stream().filter(c -> c.getEstado() == EstadoCiclo.ACTIVO).findFirst();
    }

    // ------------------------------------------------------------------------
    //  Bloque 2: el dia en numeros
    // ------------------------------------------------------------------------

    private PanelInicioDto.ResumenDelDia resumenDelDia(List<Horario> clasesDeHoy,
                                                       List<Asistencia> marcasDeHoy,
                                                       LocalTime ahora) {
        long presentes = 0;
        long tarde = 0;
        long ausentes = 0;
        Set<Long> horariosConFila = new HashSet<>();
        Set<Long> docentesQueMarcaron = new HashSet<>();

        for (Asistencia a : marcasDeHoy) {
            horariosConFila.add(a.getHorario().getId());
            switch (a.getEstado()) {
                case PRESENTE -> presentes++;
                case TARDE    -> tarde++;
                // Las ausencias TAMBIEN se persisten: las escribe el generador al cierre del
                // dia. Antes caian en este default y no se contaban en ningun lado, asi que
                // el tablero decia "0 ausentes" con dos filas AUSENTE en la base, y el
                // listado de asistencias --que si las muestra-- lo desmentia.
                case AUSENTE  -> ausentes++;
            }
            // Solo cuenta como "marco" quien efectivamente se presento. Una ausencia
            // generada por el job es una fila de asistencia, no una marca: contarla daba
            // "1 de 1 ya marcaron (100%)" para un docente que justamente no vino.
            if (a.getEstado() == EstadoAsistencia.PRESENTE || a.getEstado() == EstadoAsistencia.TARDE) {
                docentesQueMarcaron.add(a.getDocente().getId());
            }
        }

        long pendientesDeMarcar = 0;
        Set<Long> docentesConClase = new HashSet<>();

        for (Horario h : clasesDeHoy) {
            docentesConClase.add(h.getComision().getDocenteAsignado().getId());
            if (horariosConFila.contains(h.getId())) continue;

            // Una clase sin marca que todavia no termino no es una ausencia, es una clase
            // que falta. Contarlas juntas dejaria el tablero en rojo todas las mananas.
            if (ahora.isAfter(h.getHoraFin())) {
                ausentes++;
            } else {
                pendientesDeMarcar++;
            }
        }

        return new PanelInicioDto.ResumenDelDia(
            presentes, tarde, ausentes, pendientesDeMarcar,
            docentesQueMarcaron.size(), docentesConClase.size());
    }

    // ------------------------------------------------------------------------
    //  Bloque 3: lo que impide que el sistema funcione
    // ------------------------------------------------------------------------

    /**
     * Cosas cargadas a medias que hoy solo se descubren entrando ficha por ficha, y que
     * explican la mayoria de los "no me anda".
     *
     * <p>Primero lo que deja sin asistencia a la institucion entera --el calendario y el
     * equipo autorizado--, despues lo que hay que resolver hoy y al final lo que le falta a
     * cada docente o comision.
     */
    private List<PanelInicioDto.Pendiente> pendientes(Long tenantId, LocalDate hoy,
                                                      List<CicloLectivo> ciclos) {
        List<PanelInicioDto.Pendiente> lista = new ArrayList<>();

        problemaDelCiclo(ciclos, hoy)
            .filter(ProblemaDelCiclo::requiereAtencion)
            .ifPresent(p -> lista.add(new PanelInicioDto.Pendiente(
                null, p.titulo(), p.detalle(), CICLOS, true, List.of())));

        // Sin un equipo autorizado no hay pase ni registro de rostro posible (ADR-0015). Lleva
        // al pase porque es ahi, desde la computadora que se va a usar, donde se autoriza.
        if (puestoRepository.contarHabilitados(tenantId) == 0) {
            lista.add(new PanelInicioDto.Pendiente(null,
                "Ningún equipo está autorizado para tomar asistencia",
                "El pase y el registro del rostro solo andan en un equipo autorizado. Abrí el "
                + "pase desde la computadora que se va a usar y autorizala ahí.",
                "/asistencia/pase", true, List.of()));
        }

        // Va antes que las cargas incompletas porque es lo unico que hay que resolver HOY: es
        // una jornada ya ocurrida cuyo registro dice una hora que nadie observo. La salida es
        // obligatoria (RF-79), asi que su falta no se descarta en silencio.
        long salidasPendientes = bloquePresenciaRepository.countPendientesDeCierre(tenantId);
        if (salidasPendientes > 0) {
            lista.add(new PanelInicioDto.Pendiente(salidasPendientes,
                salidasPendientes == 1 ? "salida sin registrar" : "salidas sin registrar",
                "El sistema completó la hora para no dejar al docente sin asistencia, pero "
                + "nadie la observó. Confirmala o corregila.",
                "/asistencias/bloques/pendientes"));
        }

        lista.addAll(pendientesDeCarga(tenantId, hoy));
        return lista;
    }

    /**
     * Lo que le falta a cada docente o comisión para que el pase reconozca a alguien y le impute
     * su clase: un docente sin consentimiento no puede tener rostro registrado, uno sin modelo
     * nunca va a marcar solo, y una comision sin docente o sin horarios no genera asistencia
     * aunque todo lo demas este bien.
     *
     * <p>Cada uno nombra sus casos: el listado de docentes no dice a quién le falta el rostro,
     * y con solo la cantidad había que abrir las fichas una por una.
     */
    private List<PanelInicioDto.Pendiente> pendientesDeCarga(Long tenantId, LocalDate hoy) {
        List<PanelInicioDto.Pendiente> lista = new ArrayList<>();

        List<Docente> activos = docenteRepository.listarVigentesDelTenant(tenantId);

        Set<Long> conConsentimiento = new HashSet<>();
        consentimientoRepository.findUltimoEstadoPorDocenteEnTenant(tenantId).forEach(v -> {
            if (Boolean.TRUE.equals(v.getVigente())) conConsentimiento.add(v.getDocenteId());
        });

        Set<Long> conModelo = new HashSet<>();
        for (ModeloFacial m : modeloFacialRepository.findActivosDelTenant(tenantId)) {
            conModelo.add(m.getDocente().getId());
        }

        List<Docente> sinConsentimiento = activos.stream()
            .filter(d -> !conConsentimiento.contains(d.getId()))
            .toList();
        if (!sinConsentimiento.isEmpty()) {
            lista.add(new PanelInicioDto.Pendiente((long) sinConsentimiento.size(),
                "docentes sin consentimiento vigente",
                "Sin el consentimiento firmado no se les puede registrar el rostro.",
                aDocentes(sinConsentimiento), false, nombres(sinConsentimiento)));
        }

        // Solo cuenta a los que ya tienen consentimiento: a los otros les falta el paso previo
        // y aparecerian en las dos filas diciendo lo mismo dos veces.
        List<Docente> sinModelo = activos.stream()
            .filter(d -> conConsentimiento.contains(d.getId()))
            .filter(d -> !conModelo.contains(d.getId()))
            .toList();
        if (!sinModelo.isEmpty()) {
            lista.add(new PanelInicioDto.Pendiente((long) sinModelo.size(),
                "docentes sin rostro registrado",
                "Tienen el consentimiento, pero hasta registrarles el rostro no pueden "
                + "marcar por cámara.",
                aDocentes(sinModelo), false, nombres(sinModelo)));
        }

        // Las del ciclo que corre hoy: contar las de un ano cerrado como "sin docente"
        // pondria en el panel un pendiente que ya no existe.
        List<Comision> comisiones = comisionRepository.findActivasEnFecha(hoy, tenantId);

        List<String> sinDocente = comisiones.stream()
            .filter(c -> c.getDocenteAsignado() == null)
            .map(Comision::getCodigo)
            .toList();
        if (!sinDocente.isEmpty()) {
            lista.add(new PanelInicioDto.Pendiente((long) sinDocente.size(),
                "comisiones sin docente asignado",
                "Sus clases no se le pueden imputar a nadie.",
                "/comisiones", false, sinDocente));
        }

        List<String> sinHorarios = comisiones.stream()
            .filter(c -> horarioRepository.countByComisionIdAndActivoTrue(c.getId()) == 0)
            .map(Comision::getCodigo)
            .toList();
        if (!sinHorarios.isEmpty()) {
            lista.add(new PanelInicioDto.Pendiente((long) sinHorarios.size(),
                "comisiones sin horarios cargados",
                "Sin franja horaria nunca hay una clase en curso contra la cual marcar.",
                "/horarios", false, sinHorarios));
        }

        return lista;
    }

    // Con un solo docente se va derecho a su ficha, que es donde se otorga el consentimiento y
    // se registra el rostro; con varios, al listado.
    private static String aDocentes(List<Docente> docentes) {
        return docentes.size() == 1
            ? "/docentes/" + docentes.get(0).getId() + "/ficha"
            : "/docentes";
    }

    private static List<String> nombres(List<Docente> docentes) {
        return docentes.stream().map(Docente::getNombreCompleto).toList();
    }
}
