package edu.cent35.asistencias.dto;

import java.util.List;

/**
 * Las clases de este momento, para el pase: las que tienen la ventana de marcado abierta y las
 * que siguen. Son los mismos dos bloques que el inicio ({@link PanelInicioDto}), sin el
 * resumen del día.
 *
 * @param motivoSinClases por qué no hay nada en curso ni por venir; null si hay algo
 * @param avisos          lo que falta cargar para que el pase reconozca y marque: docentes sin
 *                        consentimiento o sin rostro, comisiones sin docente o sin horarios. Sin
 *                        el calendario, que ya lo explica el motivo, sin las salidas pendientes,
 *                        que no traban el pase, y sin el equipo autorizado: si se está viendo el
 *                        pase, el equipo lo está
 */
public record ClasesDeAhoraDto(
    List<PanelInicioDto.ClaseEnCurso> enCurso,
    List<PanelInicioDto.ProximaClase> proximas,
    PanelInicioDto.MotivoSinClases motivoSinClases,
    List<PanelInicioDto.Pendiente> avisos
) {
    public boolean sinClasesAhora() {
        return enCurso.isEmpty();
    }

    public boolean hayAvisos() {
        return avisos != null && !avisos.isEmpty();
    }

    public boolean nadaMasHoy() {
        return enCurso.isEmpty() && proximas.isEmpty();
    }
}
