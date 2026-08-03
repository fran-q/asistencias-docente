package edu.cent35.asistencias.service;

import edu.cent35.asistencias.dto.AsistenciaReporteRowDto;
import edu.cent35.asistencias.dto.ReporteFiltroDto;
import edu.cent35.asistencias.model.Asistencia;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

        List<Asistencia> asistencias = asistenciaRepository.findParaReporte(
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

        List<AsistenciaReporteRowDto> filas = new ArrayList<>(asistencias.size());
        for (Asistencia a : asistencias) {
            AsistenciaManual manual = manualesPorId.get(a.getId());
            JustificacionAusencia just = justificacionesPorId.get(a.getId());
            String motivoJust = just != null ? just.getMotivo() : null;
            filas.add(AsistenciaReporteRowDto.from(a, manual, motivoJust));
        }
        log.info("Reporte generado: {} filas, desde={}, hasta={}",
                 filas.size(), desde, hasta);
        return filas;
    }
}
