package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.dto.AsistenciaReporteRowDto;
import edu.cent35.asistencias.dto.ReporteFiltroDto;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.BloquePresencia;
import edu.cent35.asistencias.model.AsistenciaManual;
import edu.cent35.asistencias.model.JustificacionAusencia;
import edu.cent35.asistencias.repository.AsistenciaManualRepository;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.JustificacionAusenciaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Genera el reporte de asistencias filtrado (Sprint 6 Fase A).
 * Junta el detalle de carga manual y de justificación con cada fila para
 * exportar a CSV con toda la información de un saque.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReporteAsistenciaService {

    private final AsistenciaRepository asistenciaRepository;
    private final AsistenciaManualRepository asistenciaManualRepository;
    private final JustificacionAusenciaRepository justificacionAusenciaRepository;

    // Cuantas filas como maximo devuelve un reporte.
    @Value("${app.reportes.max-filas}")
    private int maxFilas;

    // Cuantas filas devolveria el reporte sin el tope; la pantalla lo usa para avisar.
    @Transactional(readOnly = true)
    public long contar(ReporteFiltroDto filtro) {
        LocalDate hoy = LocalDate.now();
        LocalDate desde = filtro.getDesde() != null ? filtro.getDesde() : hoy.withDayOfMonth(1);
        LocalDate hasta = filtro.getHasta() != null ? filtro.getHasta() : hoy;
        if (desde.isAfter(hasta)) return 0;
        return asistenciaRepository.contarParaReporte(
            TenantContext.getRequired(),
            desde, hasta, filtro.getDocenteId(), filtro.getMateriaId(),
            filtro.getCarreraId(), filtro.getEstado(), filtro.getMetodo());
    }

    // Tope configurado, para que la pantalla pueda decir cuantas filas entran.
    public int getMaxFilas() {
        return maxFilas;
    }

    // Arma las filas del reporte; los detalles manuales y de justificación se traen en bulk (evita N+1).
    @Transactional(readOnly = true)
    public List<AsistenciaReporteRowDto> reporte(ReporteFiltroDto filtro) {
        // Default: rango del mes actual si no se especifica.
        LocalDate hoy = LocalDate.now();
        LocalDate desde = filtro.getDesde() != null
            ? filtro.getDesde()
            : hoy.withDayOfMonth(1);
        LocalDate hasta = filtro.getHasta() != null
            ? filtro.getHasta()
            : hoy;
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException(
                "La fecha 'desde' no puede ser posterior a 'hasta'.");
        }

        Long tenantId = TenantContext.getRequired();
        List<Asistencia> asistencias = asistenciaRepository.findParaReporte(
            tenantId,
            desde, hasta,
            filtro.getDocenteId(), filtro.getMateriaId(), filtro.getCarreraId(),
            filtro.getEstado(), filtro.getMetodo());

        if (asistencias.isEmpty()) {
            return List.of();
        }

        // Tope duro. Un rango de un año sin filtros trae todo a memoria y de ahi al HTML,
        // que es donde el navegador se cae primero. Se corta y se avisa en vez de tardar
        // dos minutos y morir sin explicacion: el que pidio el reporte puede acotar el
        // rango, pero solo si sabe que le falta algo.
        boolean truncado = asistencias.size() > maxFilas;
        if (truncado) {
            log.warn("Reporte truncado: {} filas encontradas, se devuelven {}.",
                     asistencias.size(), maxFilas);
            asistencias = asistencias.subList(0, maxFilas);
        }

        // Manuales y justificaciones de ESTAS asistencias, en una consulta cada uno. Antes
        // se hacia findAll() y se filtraba en Java: traia las dos tablas enteras a memoria
        // para quedarse con un punado, y el filtro era un List.contains dentro de un
        // stream, o sea cuadratico sobre la tabla completa.
        List<Long> ids = asistencias.stream().map(Asistencia::getId).toList();
        Map<Long, AsistenciaManual> manualesPorId =
            asistenciaManualRepository.findByAsistenciaIdIn(ids).stream()
                .collect(Collectors.toMap(m -> m.getAsistencia().getId(), m -> m, (a, b) -> a));
        Map<Long, JustificacionAusencia> justificacionesPorId =
            justificacionAusenciaRepository.findByAsistenciaIdIn(ids).stream()
                .collect(Collectors.toMap(j -> j.getAsistencia().getId(), j -> j, (a, b) -> a));

        // Los minutos que cada jornada pasó fuera de la franja de sus clases. Se calculan acá
        // y no en la fila porque dependen de la jornada entera, no de una asistencia sola.
        Map<Long, Integer> fueraDeClase = minutosFueraDeClasePorAsistencia(asistencias, tenantId);

        List<AsistenciaReporteRowDto> filas = new ArrayList<>(asistencias.size());
        for (Asistencia a : asistencias) {
            AsistenciaManual manual = manualesPorId.get(a.getId());
            JustificacionAusencia just = justificacionesPorId.get(a.getId());
            String motivoJust = just != null ? just.getMotivo() : null;
            filas.add(AsistenciaReporteRowDto.from(a, manual, motivoJust,
                                                   fueraDeClase.get(a.getId())));
        }
        log.info("Reporte generado: {} filas, desde={}, hasta={}",
                 filas.size(), desde, hasta);
        return filas;
    }

    // ========================================================================
    //  Tiempo neto y desvios
    // ========================================================================

    /**
     * Lo que suma el período: cuántas horas de clase estaban programadas, cuántas se dictaron
     * de verdad, cuántas quedaron sin cubrir y cuánta permanencia hubo fuera de las franjas.
     *
     * <p>Las filas <b>sin dato de salida</b> no entran en ninguna de esas sumas y se cuentan
     * aparte: meterlas como cero diría que esas clases no se dictaron, que no es lo que se
     * sabe. Por eso el total lleva siempre cuántas son.
     */
    public record TotalesDelReporte(int clases, int clasesConDato, int minutosProgramados,
                                    int minutosNetos, int minutosSinCubrir,
                                    int minutosFueraDeClase) {

        public int clasesSinDato() {
            return clases - clasesConDato;
        }

        /** Lo dictado sobre lo programado. Cero si no hay ninguna clase con dato. */
        public int porcentajeDictado() {
            return minutosProgramados == 0 ? 0
                : (int) Math.round(minutosNetos * 100.0 / minutosProgramados);
        }

        public String netoLegible()         { return enHoras(minutosNetos); }
        public String programadoLegible()   { return enHoras(minutosProgramados); }
        public String sinCubrirLegible()    { return enHoras(minutosSinCubrir); }
        public String fueraDeClaseLegible() { return enHoras(minutosFueraDeClase); }

        // "4 h 30 min" se lee de un vistazo; "270 min" hay que dividirlo mentalmente.
        private static String enHoras(int minutos) {
            int horas = minutos / 60;
            int resto = minutos % 60;
            if (horas == 0) {
                return resto + " min";
            }
            return resto == 0 ? horas + " h" : horas + " h " + resto + " min";
        }
    }

    /**
     * Suma las filas del reporte.
     *
     * <p>Es la pregunta que el reporte no contestaba —cuántas horas netas se dictaron en el
     * período— y que obligaba a bajar el CSV y sumar a mano.
     */
    public TotalesDelReporte totales(List<AsistenciaReporteRowDto> filas) {
        int conDato = 0;
        int programados = 0;
        int netos = 0;
        int fuera = 0;

        for (AsistenciaReporteRowDto f : filas) {
            if (f.getMinutosFueraDeClase() != null) {
                fuera += f.getMinutosFueraDeClase();
            }
            if (f.getMinutosEfectivos() == null) {
                continue;                       // sin dato de salida no suma ni resta
            }
            conDato++;
            programados += f.getMinutosProgramados();
            netos += f.getMinutosEfectivos();
        }
        return new TotalesDelReporte(filas.size(), conDato, programados, netos,
                                     programados - netos, fuera);
    }

    /**
     * Cuántos minutos pasó cada jornada fuera de la franja de sus clases, atribuidos a la
     * asistencia de su primera y de su última clase.
     *
     * <p>Es lo que evita el doble conteo: el rato entre dos clases seguidas de la misma jornada
     * no es tiempo de más, es la segunda clase.
     */
    private Map<Long, Integer> minutosFueraDeClasePorAsistencia(List<Asistencia> asistencias,
                                                                Long tenantId) {
        List<Long> bloqueIds = asistencias.stream()
            .map(Asistencia::getBloque)
            .filter(Objects::nonNull)
            .map(BloquePresencia::getId)
            .distinct()
            .toList();
        if (bloqueIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, AsistenciaRepository.BordesDeLaJornada> bordes =
            asistenciaRepository.bordesDeLasJornadas(tenantId, bloqueIds).stream()
                .collect(Collectors.toMap(AsistenciaRepository.BordesDeLaJornada::getBloqueId,
                                          b -> b, (unaYaCargada, otra) -> unaYaCargada));

        Map<Long, Integer> porAsistencia = new HashMap<>();
        for (Asistencia a : asistencias) {
            BloquePresencia b = a.getBloque();
            if (b == null || b.getHoraSalida() == null) {
                continue;                       // mismo criterio que los minutos efectivos
            }
            AsistenciaRepository.BordesDeLaJornada borde = bordes.get(b.getId());
            if (borde == null) {
                continue;
            }

            int minutos = 0;
            if (a.getHorario().getHoraInicio().equals(borde.getPrimeraClase())
                && b.getHoraEntrada().isBefore(borde.getPrimeraClase())) {
                minutos += minutosEntre(b.getHoraEntrada(), borde.getPrimeraClase());
            }
            if (a.getHorario().getHoraFin().equals(borde.getUltimaClase())
                && b.getHoraSalida().isAfter(borde.getUltimaClase())) {
                minutos += minutosEntre(borde.getUltimaClase(), b.getHoraSalida());
            }
            porAsistencia.put(a.getId(), minutos);
        }
        return porAsistencia;
    }

    private static int minutosEntre(LocalTime desde, LocalTime hasta) {
        return (int) Duration.between(desde, hasta).toMinutes();
    }
}
