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

    /**
     * Las clases del período, sin el tope de la pantalla y sin el detalle que no se usa.
     *
     * <p>Es lo que alimenta los gráficos, y por dos motivos no puede ser {@link #reporte}:
     *
     * <ul>
     *   <li><b>El tope no va.</b> Corta a {@code maxFilas} para que el navegador no se caiga
     *       dibujando la tabla; un gráfico armado sobre un período cortado mostraría una
     *       curva falsa, que es peor que no mostrarla.</li>
     *   <li><b>El detalle tampoco.</b> El motivo de una carga manual y el de una
     *       justificación son dos consultas más por reporte, y ningún gráfico los mira.</li>
     * </ul>
     *
     * <p>La consulta es la misma —con su filtro de institución— así que lo que se ve acá es
     * exactamente lo que se ve en la tabla, sin el corte.
     */
    @Transactional(readOnly = true)
    public List<AsistenciaReporteRowDto> filasParaGraficos(ReporteFiltroDto filtro) {
        LocalDate desde = desdeEfectivo(filtro);
        LocalDate hasta = hastaEfectivo(filtro);
        validarRango(desde, hasta);

        List<Asistencia> asistencias = asistenciaRepository.findParaReporte(
            TenantContext.getRequired(),
            desde, hasta,
            filtro.getDocenteId(), filtro.getMateriaId(), filtro.getCarreraId(),
            filtro.getEstado(), filtro.getMetodo());

        log.info("Gráficos: {} clases, desde={}, hasta={}", asistencias.size(), desde, hasta);
        return asistencias.stream()
            .map(a -> AsistenciaReporteRowDto.from(a, null, null))
            .toList();
    }

    // El rango que se usa si el filtro no lo trae: el mes actual hasta hoy.
    private LocalDate desdeEfectivo(ReporteFiltroDto filtro) {
        return filtro.getDesde() != null
            ? filtro.getDesde() : LocalDate.now().withDayOfMonth(1);
    }

    private LocalDate hastaEfectivo(ReporteFiltroDto filtro) {
        return filtro.getHasta() != null ? filtro.getHasta() : LocalDate.now();
    }

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException(
                "La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
    }

    // Arma las filas del reporte; los detalles manuales y de justificación se traen en bulk (evita N+1).
    @Transactional(readOnly = true)
    public List<AsistenciaReporteRowDto> reporte(ReporteFiltroDto filtro) {
        LocalDate desde = desdeEfectivo(filtro);
        LocalDate hasta = hastaEfectivo(filtro);
        validarRango(desde, hasta);

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
    //  Permanencia fuera de la franja de clase
    // ========================================================================

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
