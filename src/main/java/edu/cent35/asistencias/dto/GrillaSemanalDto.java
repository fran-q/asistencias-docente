package edu.cent35.asistencias.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalTime;
import java.util.List;

/**
 * Datos pre-procesados para renderizar la grilla semanal de una carrera.
 * Cada {@link GrillaItem} ya tiene calculadas sus posiciones en el grid
 * CSS (columna por día, filas por slots de 30 min desde {@code horaMin}).
 */
@Value
@Builder
public class GrillaSemanalDto {

    Long carreraId;
    String carreraCodigo;
    String carreraNombre;

    /** Cuántos años dura la carrera: acota las opciones del filtro. */
    Short duracionAnios;

    /** Año por el que se filtró, o null si se muestran todos. */
    Short anioFiltrado;
    // El ciclo que se está viendo. Null cuando la institución todavía no abrió ninguno: la
    // grilla sale vacía y la pantalla lo explica, en vez de mostrar una cuadrícula muda.
    Long cicloId;
    Short cicloAnio;

    int totalHorarios;
    LocalTime horaMin;        // hora mínima visible en la grilla (default 07:00)
    LocalTime horaMax;        // hora máxima visible en la grilla (default 23:00)
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
