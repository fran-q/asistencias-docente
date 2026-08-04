package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import lombok.Builder;
import lombok.Value;

import java.time.LocalTime;
import java.util.List;

/**
 * Datos pre-procesados para renderizar la grilla semanal de una carrera.
 * Cada {@link GrillaItem} ya tiene calculadas sus posiciones en el grid
 * CSS (columna por dia, filas por slots de 30 min desde {@code horaMin}).
 */
@Value
@Builder
public class GrillaSemanalDto {

    Long carreraId;
    String carreraCodigo;
    String carreraNombre;

    int totalHorarios;
    LocalTime horaMin;        // hora minima visible en la grilla (default 07:00)
    LocalTime horaMax;        // hora maxima visible en la grilla (default 23:00)
    int totalSlots;           // cantidad de slots de 30 min entre horaMin y horaMax

    // Etiquetas de hora a mostrar en la columna izquierda (07:00, 08:00, ...).
    List<HoraLabel> labels;

    // Items posicionados en el grid.
    List<GrillaItem> items;

    @Value
    @Builder
    public static class HoraLabel {
        String texto;       // "07:00"
        int gridRowStart;   // 1-based, row 1 es header
    }

    @Value
    @Builder
    public static class GrillaItem {
        Long horarioId;
        String comisionCodigo;
        String materiaCodigo;
        String materiaNombre;
        // Quien dicta. Se agrega para el cuadro flotante: saber que materia hay en un
        // bloque sin saber quien la da no alcanza para decidir nada.
        String docenteNombre;
        LocalTime horaInicio;
        LocalTime horaFin;
        short toleranciaMin;
        int diaNum;          // 1=Lunes ... 7=Domingo
        int gridColumn;      // 2-8 (col 1 = labels)
        int gridRowStart;
        int gridRowEnd;
    }
}
