package edu.cent35.asistencias.model;

import java.time.LocalDate;

/**
 * Dia de la semana segun ISO 8601 (1=Lunes, 7=Domingo).
 * Coincide con el {@code TINYINT} en {@code horarios.dia_semana}.
 */
public enum DiaSemana {
    LUNES     ((byte) 1, "Lunes"),
    MARTES    ((byte) 2, "Martes"),
    MIERCOLES ((byte) 3, "Miércoles"),
    JUEVES    ((byte) 4, "Jueves"),
    VIERNES   ((byte) 5, "Viernes"),
    SABADO    ((byte) 6, "Sábado"),
    DOMINGO   ((byte) 7, "Domingo");

    private final byte numero;
    private final String etiqueta;

    DiaSemana(byte numero, String etiqueta) {
        this.numero = numero;
        this.etiqueta = etiqueta;
    }

    public byte getNumero()    { return numero; }
    public String getEtiqueta() { return etiqueta; }

    public static DiaSemana fromNumero(byte n) {
        for (DiaSemana d : values()) {
            if (d.numero == n) return d;
        }
        throw new IllegalArgumentException("Dia de semana invalido: " + n);
    }

    /**
     * Dia de la semana en que cae una fecha. {@link LocalDate} numera los
     * dias con el mismo criterio ISO 8601 que este enum (1=Lunes, 7=Domingo),
     * asi que el mapeo es directo.
     */
    public static DiaSemana deLaFecha(LocalDate fecha) {
        return fromNumero((byte) fecha.getDayOfWeek().getValue());
    }
}
