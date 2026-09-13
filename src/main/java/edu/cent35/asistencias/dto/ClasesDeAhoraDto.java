package edu.cent35.asistencias.dto;

import java.util.List;

/**
 * Las clases de este momento, para el pase: las que tienen la ventana de marcado abierta y las
 * que siguen. Son los mismos dos bloques que el inicio ({@link PanelInicioDto}), sin el
 * resumen ni los pendientes, que el pase no muestra.
 */
public record ClasesDeAhoraDto(
    List<PanelInicioDto.ClaseEnCurso> enCurso,
    List<PanelInicioDto.ProximaClase> proximas
) {
    public boolean sinClasesAhora() {
        return enCurso.isEmpty();
    }

    public boolean nadaMasHoy() {
        return enCurso.isEmpty() && proximas.isEmpty();
    }
}
