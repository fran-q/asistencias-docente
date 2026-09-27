package edu.cent35.asistencias.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

/**
 * Lo que dibuja la pantalla de gráficos del reporte (RF-33).
 *
 * <p><b>Por qué viene con la geometría resuelta.</b> El SVG lo escribe la plantilla, y una
 * plantilla que además calcula coordenadas es una plantilla que no se puede probar: las
 * expresiones de Thymeleaf fallan recién al renderizar y un error de cuenta no falla nunca,
 * solo dibuja mal. Acá las columnas ya vienen con su x, su alto y su etiqueta, así que el
 * cálculo se prueba como cualquier otro método y la plantilla queda sin decisiones.
 */
@Value
@Builder
public class GraficosDeAsistenciaDto {

    /** Cuánto se dictó de lo programado, semana a semana. */
    List<ColumnaSemanal> semanas;

    /** Cómo se reparten las clases del período entre los tres estados. */
    RepartoDeEstados estados;

    /** Quiénes concentran las ausencias. Vacío si no hubo ninguna. */
    List<FilaDeRanking> docentes;
    List<FilaDeRanking> materias;

    /** Cuántas clases entraron en los gráficos y cuántas no tenían dato de salida. */
    int clases;
    int clasesSinDato;

    /** Si no hay nada que dibujar, la pantalla lo dice en palabras en vez de un eje vacío. */
    public boolean isVacio() {
        return clases == 0;
    }

    /**
     * Una columna del gráfico semanal, ya ubicada dentro del lienzo.
     *
     * <p>{@code y} y {@code alto} salen del porcentaje dictado; {@code etiqueta} es el lunes
     * de esa semana. Una semana <b>sin dato</b> —ninguna clase con marca de salida— no es
     * cero: se dibuja hueca y el porcentaje va en null, porque una columna al ras diría que
     * no se dictó nada.
     */
    @Value
    @Builder
    public static class ColumnaSemanal {
        LocalDate lunes;
        String etiqueta;
        Integer porcentaje;       // null = esa semana no tiene ninguna clase con dato
        int minutosProgramados;
        int minutosDictados;
        float x;
        float y;
        float ancho;
        float alto;

        public boolean isSinDato() {
            return porcentaje == null;
        }
    }

    /**
     * El reparto de estados del período.
     *
     * <p>Los anchos se calculan acá y no en la plantilla para que sumen 100 exacto: repartir
     * tres porcentajes redondeados deja una raya blanca o un desborde de un píxel, que en una
     * barra apilada se ve.
     */
    @Value
    @Builder
    public static class RepartoDeEstados {
        int presentes;
        int tarde;
        int ausentes;
        int anchoPresentes;
        int anchoTarde;
        int anchoAusentes;

        public int total() {
            return presentes + tarde + ausentes;
        }
    }

    /** Una fila del ranking: cuántas ausencias sobre cuántas clases, y el ancho de su barra. */
    @Value
    @Builder
    public static class FilaDeRanking {
        String etiqueta;
        int ausencias;
        int clases;
        int ancho;
    }
}
