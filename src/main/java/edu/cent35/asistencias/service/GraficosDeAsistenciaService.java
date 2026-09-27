package edu.cent35.asistencias.service;

import edu.cent35.asistencias.dto.AsistenciaReporteRowDto;
import edu.cent35.asistencias.dto.GraficosDeAsistenciaDto;
import edu.cent35.asistencias.dto.GraficosDeAsistenciaDto.ColumnaSemanal;
import edu.cent35.asistencias.dto.GraficosDeAsistenciaDto.FilaDeRanking;
import edu.cent35.asistencias.dto.GraficosDeAsistenciaDto.RepartoDeEstados;
import edu.cent35.asistencias.model.EstadoAsistencia;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Resume las filas del reporte en las tres cosas que se pueden mirar de un vistazo (RF-33).
 *
 * <p><b>Por qué suma en Java y no con un GROUP BY.</b> Los minutos dictados son la
 * intersección entre la permanencia del docente y la franja de su clase, y esa regla vive en
 * {@link AsistenciaReporteRowDto}. Escribirla otra vez en SQL para poder sumarla sería tener
 * dos definiciones de lo mismo, y la segunda se olvida de actualizar. Las clases de un
 * período entran holgadas en memoria —es la misma consulta que ya alimenta la tabla— así que
 * se suma acá, contra la única definición que hay.
 *
 * <p><b>Una clase sin dato de salida no es una clase con cero minutos.</b> No entra en
 * ninguna suma y se cuenta aparte, igual que en el resto del reporte: meterla como cero diría
 * que esa clase no se dictó, cuando lo único que se sabe es que falta la marca.
 */
@Service
@Slf4j
public class GraficosDeAsistenciaService {

    // El lienzo del grafico semanal. Son coordenadas de un viewBox, no pixeles: el SVG se
    // estira al ancho que tenga la tarjeta.
    private static final float LIENZO_ANCHO = 720f;
    private static final float LIENZO_ALTO  = 200f;
    private static final float SEPARACION   = 6f;

    // Cuantas filas muestra cada ranking. Mas que esto deja de ser "donde intervenir" y pasa
    // a ser la tabla del reporte, que ya existe y ordena por lo que uno quiera.
    private static final int TOPE_RANKING = 8;

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    public GraficosDeAsistenciaDto resumir(List<AsistenciaReporteRowDto> filas) {
        int sinDato = (int) filas.stream().filter(f -> f.getMinutosEfectivos() == null).count();

        GraficosDeAsistenciaDto dto = GraficosDeAsistenciaDto.builder()
            .semanas(porSemana(filas))
            .estados(porEstado(filas))
            .docentes(ranking(filas, f -> f.getDocenteApellido() + ", " + f.getDocenteNombre()))
            .materias(ranking(filas, f -> f.getMateriaCodigo() + " " + f.getMateriaNombre()))
            .clases(filas.size())
            .clasesSinDato(sinDato)
            .build();

        log.debug("Gráficos armados sobre {} clases ({} sin dato de salida)",
                  filas.size(), sinDato);
        return dto;
    }

    // ------------------------------------------------------------------------

    /**
     * Agrupa por semana calendario, tomando el lunes como la etiqueta de cada una.
     *
     * <p>Las semanas sin ninguna clase del período no aparecen: un hueco en el eje diría que
     * esa semana se dictó cero, y lo que pasó es que no había clases —vacaciones, un feriado
     * largo, un filtro por materia que no cursa esa semana—.
     */
    private List<ColumnaSemanal> porSemana(List<AsistenciaReporteRowDto> filas) {
        Map<LocalDate, int[]> acumulado = new TreeMap<>();      // lunes -> [programados, dictados]
        for (AsistenciaReporteRowDto f : filas) {
            if (f.getFecha() == null) {
                continue;
            }
            LocalDate lunes = f.getFecha().with(DayOfWeek.MONDAY);
            int[] par = acumulado.computeIfAbsent(lunes, k -> new int[2]);
            if (f.getMinutosEfectivos() == null) {
                continue;                                       // la semana existe, la clase no suma
            }
            par[0] += f.getMinutosProgramados();
            par[1] += f.getMinutosEfectivos();
        }
        if (acumulado.isEmpty()) {
            return List.of();
        }

        // Todas las columnas miden lo mismo y se reparten el ancho: con pocas semanas quedan
        // anchas y con muchas, finitas, sin que ninguna se salga del lienzo.
        int cuantas = acumulado.size();
        float paso = LIENZO_ANCHO / cuantas;
        float ancho = Math.max(2f, paso - SEPARACION);

        List<ColumnaSemanal> columnas = new ArrayList<>(cuantas);
        int i = 0;
        for (Map.Entry<LocalDate, int[]> e : acumulado.entrySet()) {
            int programados = e.getValue()[0];
            int dictados = e.getValue()[1];
            Integer porcentaje = programados == 0
                ? null
                : (int) Math.round(dictados * 100.0 / programados);

            // Una semana sin dato se dibuja como una marca al ras, no como una columna cero:
            // cero es una afirmacion y esto es la ausencia de una.
            float alto = porcentaje == null ? 2f : LIENZO_ALTO * Math.min(100, porcentaje) / 100f;

            columnas.add(ColumnaSemanal.builder()
                .lunes(e.getKey())
                .etiqueta(e.getKey().format(DIA_MES))
                .porcentaje(porcentaje)
                .minutosProgramados(programados)
                .minutosDictados(dictados)
                .x(i * paso + (paso - ancho) / 2f)
                .y(LIENZO_ALTO - alto)
                .ancho(ancho)
                .alto(alto)
                .build());
            i++;
        }
        return columnas;
    }

    /**
     * Cuenta los tres estados y reparte el ancho de la barra apilada.
     *
     * <p>El último tramo se lleva lo que sobra en vez de calcularse aparte: tres porcentajes
     * redondeados no suman 100, y en una barra apilada esa diferencia se ve como una raya
     * blanca al final o como un tramo que se pasa.
     */
    private RepartoDeEstados porEstado(List<AsistenciaReporteRowDto> filas) {
        int presentes = contar(filas, EstadoAsistencia.PRESENTE);
        int tarde = contar(filas, EstadoAsistencia.TARDE);
        int ausentes = contar(filas, EstadoAsistencia.AUSENTE);
        int total = presentes + tarde + ausentes;

        int anchoPresentes = total == 0 ? 0 : (int) Math.round(presentes * 100.0 / total);
        int anchoTarde = total == 0 ? 0 : (int) Math.round(tarde * 100.0 / total);
        int anchoAusentes = total == 0 ? 0 : 100 - anchoPresentes - anchoTarde;

        return RepartoDeEstados.builder()
            .presentes(presentes).tarde(tarde).ausentes(ausentes)
            .anchoPresentes(anchoPresentes)
            .anchoTarde(anchoTarde)
            .anchoAusentes(Math.max(0, anchoAusentes))
            .build();
    }

    private int contar(List<AsistenciaReporteRowDto> filas, EstadoAsistencia estado) {
        return (int) filas.stream().filter(f -> estado.name().equals(f.getEstado())).count();
    }

    /**
     * Quiénes acumulan más ausencias, ordenados por cantidad.
     *
     * <p>Se muestran las ausencias <b>y</b> sobre cuántas clases: dos ausencias en tres clases
     * y dos en cuarenta son cosas distintas, y un ranking que solo muestre el primer número
     * pone arriba a quien más clases da.
     *
     * <p>El ancho de la barra va por el máximo de la lista, no por cien: con ausencias
     * chicas, barras contra el total serían todas invisibles y el gráfico no diría nada.
     */
    private List<FilaDeRanking> ranking(List<AsistenciaReporteRowDto> filas,
                                        java.util.function.Function<AsistenciaReporteRowDto,
                                                                    String> etiquetaDe) {
        Map<String, int[]> porClave = new LinkedHashMap<>();    // etiqueta -> [ausencias, clases]
        for (AsistenciaReporteRowDto f : filas) {
            int[] par = porClave.computeIfAbsent(etiquetaDe.apply(f), k -> new int[2]);
            par[1]++;
            if (EstadoAsistencia.AUSENTE.name().equals(f.getEstado())) {
                par[0]++;
            }
        }

        List<Map.Entry<String, int[]>> conAusencias = porClave.entrySet().stream()
            .filter(e -> e.getValue()[0] > 0)
            .sorted(Comparator
                .<Map.Entry<String, int[]>>comparingInt(e -> -e.getValue()[0])
                .thenComparing(Map.Entry::getKey))
            .limit(TOPE_RANKING)
            .toList();
        if (conAusencias.isEmpty()) {
            return List.of();
        }

        int maximo = conAusencias.get(0).getValue()[0];
        return conAusencias.stream()
            .map(e -> FilaDeRanking.builder()
                .etiqueta(e.getKey())
                .ausencias(e.getValue()[0])
                .clases(e.getValue()[1])
                .ancho((int) Math.round(e.getValue()[0] * 100.0 / maximo))
                .build())
            .toList();
    }
}
