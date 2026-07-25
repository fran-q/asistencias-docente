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

    // Numero ISO del dia (1=Lunes, 7=Domingo), que es lo que se guarda en la columna.
    public byte getNumero()    { return numero; }

    // Nombre para mostrar, con acento ("Miércoles", "Sábado").
    public String getEtiqueta() { return etiqueta; }

    // Convierte el numero guardado en la base al valor del enum.
    public static DiaSemana fromNumero(byte n) {
        for (DiaSemana d : values()) {
            if (d.numero == n) return d;
        }
        throw new IllegalArgumentException("Dia de semana invalido: " + n);
    }

    // Dia en que cae una fecha; LocalDate usa la misma numeracion ISO, asi que el mapeo es directo.
    public static DiaSemana deLaFecha(LocalDate fecha) {
        return fromNumero((byte) fecha.getDayOfWeek().getValue());
    }
}
