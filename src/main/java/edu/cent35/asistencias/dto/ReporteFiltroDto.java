package edu.cent35.asistencias.dto;

import edu.cent35.asistencias.model.EstadoAsistencia;
import edu.cent35.asistencias.model.MetodoAsistencia;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Filtros del reporte de asistencias.
 * Rango de fechas obligatorio; el resto opcional.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteFiltroDto {
    private LocalDate desde;
    private LocalDate hasta;
    private Long docenteId;
    private Long materiaId;

    // Filtra por carrera; complementa al de materia para poder mirar un plan completo (RF-29).
    private Long carreraId;
    private EstadoAsistencia estado;
    private MetodoAsistencia metodo;
}
