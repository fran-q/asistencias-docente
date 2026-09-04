package edu.cent35.asistencias.dto;

import edu.cent35.asistencias.model.Horario;

import java.time.LocalTime;
import java.util.List;

/**
 * Los horarios consecutivos de un docente que caen dentro del mismo bloque de presencia,
 * con la franja que abarcan de punta a punta (RF-75, ADR-0017).
 * <p>
 * <b>No confundir con {@code BloquePresencia}</b>, que es la entidad persistida. Esto es el
 * resultado de agrupar la grilla: dice qué clases irían juntas y en qué franja, y se puede
 * calcular sin que el docente haya aparecido nunca frente a la cámara. La entidad, en
 * cambio, registra que alguien efectivamente entró y salió.
 *
 * @param horarios   las clases del bloque, ordenadas por hora de inicio. Nunca vacía.
 * @param horaInicio inicio de la primera clase
 * @param horaFin    fin de la última clase. Es el <b>máximo</b> de los fines, no el fin del
 *                   último horario de la lista: con clases solapadas —dos comisiones a la
 *                   misma hora, que el sistema no prohíbe— la que empieza más tarde puede
 *                   terminar antes.
 */
public record BloqueDeHorarios(
    List<Horario> horarios,
    LocalTime horaInicio,
    LocalTime horaFin
) {

    // Primera clase del bloque: la que abre la franja.
    public Horario primerHorario() {
        return horarios.get(0);
    }

    /**
     * Última clase del bloque: la que cierra la franja, contra la que se mide si la salida
     * fue en hora o anticipada (RF-78).
     *
     * <p>Es la de mayor {@code horaFin} y no la última de la lista, por la misma razón por la
     * que {@link #horaFin} es un máximo.
     */
    public Horario ultimoHorario() {
        return horarios.stream()
            .max(java.util.Comparator.comparing(Horario::getHoraFin)
                .thenComparing(Horario::getId))
            .orElseThrow();
    }

    // Indica si la franja del bloque contiene ese momento, sin aplicar ninguna tolerancia.
    public boolean contiene(LocalTime hora) {
        return hora != null && !hora.isBefore(horaInicio) && !hora.isAfter(horaFin);
    }

    // Cuántas clases quedaron agrupadas. Un bloque de una sola clase es el caso normal.
    public int cantidadDeClases() {
        return horarios.size();
    }
}
