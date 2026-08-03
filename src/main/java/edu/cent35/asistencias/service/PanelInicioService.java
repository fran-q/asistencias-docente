package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.dto.PanelInicioDto;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.ModeloFacial;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.ConsentimientoBiometricoRepository;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.ModeloFacialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Arma el panel de la pantalla de inicio (RF-60).
 *
 * <p>Todo sale de consultas que ya existian para otras pantallas; lo unico que se agrega es
 * cruzarlas. Se resuelve entero en una sola transaccion de lectura porque es lo primero que
 * ve el operador al entrar y no puede costar mas que la pantalla que va a abrir despues.
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

    // Cuantas clases en curso se muestran como maximo, para que el panel no crezca sin limite.
    private static final int MAX_EN_CURSO = 6;

    // Cuantas clases por venir se anticipan. Mas de tres deja de ser "que sigue" y pasa a
    // ser la grilla del dia, que ya tiene su propia pantalla.
    private static final int MAX_PROXIMAS = 3;

    // Reloj inyectable: todo el panel se define contra "ahora", asi que sin poder fijarlo
    // los tests dependerian de la hora a la que se corren.
    private Clock clock = Clock.systemDefaultZone();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PanelInicioDto armar() {
        Long tenantId = TenantContext.getRequired();
        LocalDate hoy = LocalDate.now(clock);
        LocalTime ahora = LocalTime.now(clock);

        List<Horario> clasesDeHoy = horarioRepository.findActivosDelDiaConDocente(
            (byte) hoy.getDayOfWeek().getValue(), tenantId);
        List<Asistencia> marcasDeHoy = asistenciaRepository.findDelDia(hoy);

        return new PanelInicioDto(
            clasesEnCurso(clasesDeHoy, marcasDeHoy, ahora),
            proximasClases(clasesDeHoy, ahora),
            resumenDelDia(clasesDeHoy, marcasDeHoy, ahora),
            pendientes(tenantId));
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

    // ------------------------------------------------------------------------
    //  Bloque 2: el dia en numeros
    // ------------------------------------------------------------------------

    private PanelInicioDto.ResumenDelDia resumenDelDia(List<Horario> clasesDeHoy,
                                                       List<Asistencia> marcasDeHoy,
                                                       LocalTime ahora) {
        long presentes = 0;
        long tarde = 0;
        Set<Long> horariosMarcados = new HashSet<>();
        Set<Long> docentesQueMarcaron = new HashSet<>();

        for (Asistencia a : marcasDeHoy) {
            horariosMarcados.add(a.getHorario().getId());
            docentesQueMarcaron.add(a.getDocente().getId());
            switch (a.getEstado()) {
                case PRESENTE -> presentes++;
                case TARDE    -> tarde++;
                default       -> { }
            }
        }

        long ausentes = 0;
        long pendientesDeMarcar = 0;
        Set<Long> docentesConClase = new HashSet<>();

        for (Horario h : clasesDeHoy) {
            docentesConClase.add(h.getComision().getDocenteAsignado().getId());
            if (horariosMarcados.contains(h.getId())) continue;

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
     * explican la mayoria de los "no me anda": un docente sin consentimiento no puede tener
     * rostro registrado, uno sin modelo nunca va a marcar solo, y una comision sin docente o
     * sin horarios no genera asistencia aunque todo lo demas este bien.
     */
    private List<PanelInicioDto.Pendiente> pendientes(Long tenantId) {
        List<PanelInicioDto.Pendiente> lista = new ArrayList<>();

        List<Docente> activos = docenteRepository.findByActivoTrueOrderByApellidoAscNombreAsc();

        Set<Long> conConsentimiento = new HashSet<>();
        consentimientoRepository.findUltimoEstadoPorDocenteEnTenant(tenantId).forEach(v -> {
            if (Boolean.TRUE.equals(v.getVigente())) conConsentimiento.add(v.getDocenteId());
        });

        Set<Long> conModelo = new HashSet<>();
        for (ModeloFacial m : modeloFacialRepository.findActivosDelTenant(tenantId)) {
            conModelo.add(m.getDocente().getId());
        }

        long sinConsentimiento = activos.stream()
            .filter(d -> !conConsentimiento.contains(d.getId()))
            .count();
        if (sinConsentimiento > 0) {
            lista.add(new PanelInicioDto.Pendiente(sinConsentimiento,
                "docentes sin consentimiento vigente",
                "Sin el consentimiento firmado no se les puede registrar el rostro.",
                "/docentes"));
        }

        // Solo cuenta a los que ya tienen consentimiento: a los otros les falta el paso previo
        // y aparecerian en las dos filas diciendo lo mismo dos veces.
        long sinModelo = activos.stream()
            .filter(d -> conConsentimiento.contains(d.getId()))
            .filter(d -> !conModelo.contains(d.getId()))
            .count();
        if (sinModelo > 0) {
            lista.add(new PanelInicioDto.Pendiente(sinModelo,
                "docentes sin rostro registrado",
                "Tienen el consentimiento, pero hasta registrarles el rostro no pueden "
                + "marcar por cámara.",
                "/docentes"));
        }

        List<Comision> comisiones = comisionRepository.findActivasDelTenant(tenantId);

        long sinDocente = comisiones.stream()
            .filter(c -> c.getDocenteAsignado() == null)
            .count();
        if (sinDocente > 0) {
            lista.add(new PanelInicioDto.Pendiente(sinDocente,
                "comisiones sin docente asignado",
                "Sus clases no se le pueden imputar a nadie.",
                "/comisiones"));
        }

        long sinHorarios = comisiones.stream()
            .filter(c -> horarioRepository.countByComisionIdAndActivoTrue(c.getId()) == 0)
            .count();
        if (sinHorarios > 0) {
            lista.add(new PanelInicioDto.Pendiente(sinHorarios,
                "comisiones sin horarios cargados",
                "Sin franja horaria nunca hay una clase en curso contra la cual marcar.",
                "/horarios"));
        }

        return lista;
    }
}
