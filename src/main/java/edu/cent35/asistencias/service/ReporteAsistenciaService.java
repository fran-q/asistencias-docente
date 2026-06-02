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

    /**
     * Devuelve las filas del reporte aplicando los filtros recibidos. Las
     * referencias a {@code AsistenciaManual} y a la justificación se buscan
     * en bulk para no caer en N+1.
     */
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
            filtro.getDocenteId(), filtro.getMateriaId(),
            filtro.getEstado(), filtro.getMetodo());

        if (asistencias.isEmpty()) {
            return List.of();
        }

        // Bulk de manuales y justificaciones: una sola consulta cada uno
        // y mapeamos por asistencia_id para evitar N+1.
        List<Long> ids = asistencias.stream().map(Asistencia::getId).toList();
        Map<Long, AsistenciaManual> manualesPorId = asistenciaManualRepository.findAll().stream()
            .filter(m -> ids.contains(m.getAsistencia().getId()))
            .collect(Collectors.toMap(m -> m.getAsistencia().getId(), m -> m, (a, b) -> a));
        Map<Long, JustificacionAusencia> justificacionesPorId =
            justificacionAusenciaRepository.findAll().stream()
                .filter(j -> ids.contains(j.getAsistencia().getId()))
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
