package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.dto.BloqueDeHorarios;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Agrupa las clases de un docente en bloques de presencia: las consecutivas separadas por
 * menos que el umbral de la institución van juntas, y una sola entrada y una sola salida las
 * cubren a todas (RF-75, RF-76, ADR-0017).
 * <p>
 * {@link #agrupar} es una función pura sobre intervalos —no toca la base ni la cámara— y por
 * eso se prueba sola. Los otros dos métodos solo le acercan los datos: qué clases tiene el
 * docente ese día y cuál es el umbral del tenant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResolutorDeBloquesService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final HorarioRepository horarioRepository;
    private final InstitucionRepository institucionRepository;

    /**
     * Agrupa los horarios en bloques según el umbral de separación, en minutos.
     *
     * <p><b>Es el corazón de la funcionalidad y no depende de nada más que de sus
     * parámetros.</b> Recibe el umbral en vez de ir a buscarlo para poder probar los casos
     * límite sin levantar Spring: el borde entre "un bloque" y "dos" es exactamente un
     * minuto y hay que poder pararse encima.
     *
     * <p>Reglas, todas derivadas de ADR-0017:
     * <ul>
     *   <li>El hueco se mide del fin acumulado del bloque al inicio de la clase siguiente.
     *       <b>Menor o igual</b> al umbral: misma franja. Mayor: se corta.</li>
     *   <li>El agrupamiento es transitivo y no tiene tope de duración. Lo que decide es la
     *       separación entre clases, no cuánto dura la cadena.</li>
     *   <li>La materia y la carrera no intervienen: lo que el bloque acredita es que la
     *       persona estuvo, no qué dictó en cada franja.</li>
     * </ul>
     *
     * <p>El fin del bloque se lleva con un máximo y no con el fin de la última clase
     * agregada. Dos comisiones del mismo docente pueden solaparse —{@code HorarioService}
     * valida superposiciones por comisión, no por docente— y entonces una clase que empieza
     * más tarde puede terminar antes: sin el máximo, el bloque se cerraría antes de tiempo
     * y la clase más larga quedaría partida.
     */
    public List<BloqueDeHorarios> agrupar(List<Horario> horarios, int umbralMin) {
        if (horarios == null || horarios.isEmpty()) {
            return List.of();
        }

        // El orden no viene garantizado por la query, y el algoritmo lo necesita. El id
        // desempata para que dos clases que arrancan a la misma hora caigan siempre igual.
        List<Horario> ordenados = horarios.stream()
            .filter(h -> h.getHoraInicio() != null && h.getHoraFin() != null)
            .sorted(Comparator.comparing(Horario::getHoraInicio)
                        .thenComparing(Horario::getId, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

        if (ordenados.isEmpty()) {
            return List.of();
        }

        List<BloqueDeHorarios> bloques = new ArrayList<>();
        List<Horario> enCurso = new ArrayList<>();
        LocalTime inicioBloque = null;
        LocalTime finBloque = null;

        for (Horario h : ordenados) {
            if (enCurso.isEmpty()) {
                enCurso.add(h);
                inicioBloque = h.getHoraInicio();
                finBloque = h.getHoraFin();
                continue;
            }

            // Negativo cuando las clases se solapan, y un hueco negativo siempre entra:
            // si se pisan, con más razón la persona estuvo ahí sin interrupción.
            long hueco = Duration.between(finBloque, h.getHoraInicio()).toMinutes();

            if (hueco <= umbralMin) {
                enCurso.add(h);
                if (h.getHoraFin().isAfter(finBloque)) {
                    finBloque = h.getHoraFin();
                }
            } else {
                bloques.add(new BloqueDeHorarios(List.copyOf(enCurso), inicioBloque, finBloque));
                enCurso = new ArrayList<>();
                enCurso.add(h);
                inicioBloque = h.getHoraInicio();
                finBloque = h.getHoraFin();
            }
        }
        bloques.add(new BloqueDeHorarios(List.copyOf(enCurso), inicioBloque, finBloque));

        return List.copyOf(bloques);
    }

    // Bloques del docente para el día de esa fecha, con el umbral de la institución actual.
    @Transactional(readOnly = true)
    public List<BloqueDeHorarios> bloquesDelDia(Long docenteId, LocalDate fecha) {
        Long tenantId = TenantContext.getRequired();
        byte diaSemana = (byte) fecha.getDayOfWeek().getValue();   // 1..7 ISO

        List<Horario> horariosDelDia =
            horarioRepository.findHoyParaDocente(docenteId, diaSemana, fecha, tenantId);

        return agrupar(horariosDelDia, umbralDelTenant(tenantId));
    }

    /**
     * El bloque al que corresponde una marca hecha en ese instante, si hay alguno.
     *
     * <p>Un bloque está en curso cuando alguna de sus clases lo está, y eso lo responde
     * {@link Horario#estaEnCurso} y no una copia de la ventana escrita acá: el pase y el
     * panel de inicio ya comparten ese método justamente para no desincronizarse, y una
     * tercera versión rompería lo mismo que ese diseño evita.
     */
    @Transactional(readOnly = true)
    public Optional<BloqueDeHorarios> bloqueEnCurso(Long docenteId, LocalDateTime instante) {
        LocalTime hora = instante.toLocalTime();

        List<BloqueDeHorarios> candidatos = bloquesDelDia(docenteId, instante.toLocalDate())
            .stream()
            .filter(b -> b.horarios().stream().anyMatch(h -> h.estaEnCurso(hora)))
            .toList();

        if (candidatos.isEmpty()) {
            return Optional.empty();
        }
        if (candidatos.size() == 1) {
            return Optional.of(candidatos.get(0));
        }

        // Dos bloques en ventana a la vez. Pasa cuando el umbral de separación es menor que
        // la tolerancia: los bloques quedan separados, pero la ventana del siguiente se abre
        // antes de que termine el anterior. El desempate es el mismo criterio del RF-18
        // —el inicio más cercano, y el menor id ante empate— para que la elección sea
        // reproducible y no dependa del orden en que vino la lista.
        log.info("RF-75 ambiguedad: docente {} tiene {} bloques en ventana a las {} - aplicando desempate",
                 docenteId, candidatos.size(), hora.format(HM));

        Optional<BloqueDeHorarios> elegido = candidatos.stream()
            .min(Comparator
                .comparingLong((BloqueDeHorarios b) ->
                    Math.abs(Duration.between(hora, b.horaInicio()).toMinutes()))
                .thenComparing(b -> b.primerHorario().getId()));

        elegido.ifPresent(b -> log.info(
            "RF-75 desempate: elegido bloque {}-{} ({} clase/s) para docente {}",
            b.horaInicio().format(HM), b.horaFin().format(HM), b.cantidadDeClases(), docenteId));
        return elegido;
    }

    // Umbral de separación de la institución actual (RF-76). Sin institución, el default.
    private int umbralDelTenant(Long tenantId) {
        return institucionRepository.findById(tenantId)
            .map(i -> i.getUmbralSeparacionMin() == null
                ? UMBRAL_POR_DEFECTO
                : i.getUmbralSeparacionMin().intValue())
            .orElse(UMBRAL_POR_DEFECTO);
    }

    /**
     * El mismo default que la columna {@code instituciones.umbral_separacion_min} de V019.
     *
     * <p>Solo se usa si la institución no aparece o el valor vino nulo, que con el
     * {@code NOT NULL} de la base no debería pasar. Está para que el agrupador no dependa de
     * que ese invariante se cumpla: sin esto, un null convierte una consulta de horarios en
     * una excepción.
     */
    private static final int UMBRAL_POR_DEFECTO = 60;
}
