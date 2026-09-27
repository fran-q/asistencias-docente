package edu.cent35.asistencias.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import edu.cent35.asistencias.dto.AsistenciaReporteRowDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La columna del equipo en el PDF del reporte (RF-89, V031).
 *
 * <p><b>Por qué se lee el PDF y no el método.</b> Lo que hay que sostener no es que una
 * función devuelva un string: es que ese texto termine impreso en la hoja que alguien archiva.
 * Entre las dos cosas está el armado de la tabla, y una columna que se arma pero no se agrega
 * pasaría un test de unidad sin que el PDF cambie. Por eso el test genera el documento y le
 * extrae el texto, que además prueba algo que no se ve de otra forma: que el separador «›»
 * sobreviva a la codificación de la fuente, cosa que una flecha Unicode no hace.
 */
class ReportePdfServiceTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 6, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 6, 30);

    private final ReportePdfService service = new ReportePdfService();

    @Test
    @DisplayName("Entrar por una puerta y salir por otra queda escrito con las dos")
    void lasDosPuertas() {
        String texto = pdfDe(fila("Entrada norte", "Entrada sur", LocalTime.of(20, 10)));

        assertThat(texto)
            .as("la columna existe")
            .contains("Equipo")
            .as("las dos puertas, en el orden en que pasó: entrada y después salida")
            .contains("Entrada norte › Entrada sur");
    }

    @Test
    @DisplayName("Con la misma puerta de los dos lados el nombre va una sola vez")
    void mismaPuerta() {
        String texto = pdfDe(fila("Entrada norte", "Entrada norte", LocalTime.of(20, 10)));

        assertThat(texto)
            .as("repetir el nombre solo gasta ancho: no hay nada que comparar")
            .contains("Entrada norte")
            .doesNotContain("›");
    }

    @Test
    @DisplayName("Una salida sin camara se distingue de una salida por la misma puerta")
    void salidaSinEquipo() {
        String texto = pdfDe(fila("Entrada norte", null, LocalTime.of(20, 10)));

        assertThat(texto)
            .as("el guion del lado de la salida dice que se cerró sin pasar por una cámara, "
                + "que no es lo mismo que haber salido por donde entró")
            .contains("Entrada norte › —");
    }

    @Test
    @DisplayName("Sin hora de salida no se inventa un segundo equipo")
    void todaviaAdentro() {
        String texto = pdfDe(fila("Entrada norte", null, null));

        assertThat(texto)
            .as("el docente sigue adentro: el guion de la columna 'Sale' ya lo dijo")
            .contains("Entrada norte")
            .doesNotContain("›");
    }

    @Test
    @DisplayName("Una fila sin ningun equipo muestra guion, no un renglon vacio")
    void sinEquipos() {
        String texto = pdfDe(fila(null, null, LocalTime.of(20, 10)));

        assertThat(texto).contains("Equipo").doesNotContain("›");
    }

    @Test
    @DisplayName("En una hoja sin asteriscos, al pie va solo lo de Dictado")
    void alPieVaLoDeDictado() {
        String texto = pdfDe(fila("Entrada norte", "Entrada sur", LocalTime.of(20, 10)));

        assertThat(texto)
            .as("dos números separados por una barra no se adivinan")
            .contains("Dictado: minutos de la clase que el docente cubrió")
            .as("y las dos decisiones que cambian lo que esa cifra dice")
            .contains("Cuenta sólo lo que se pisa con la franja de la clase")
            .contains("no es lo mismo que cero");

        assertThat(texto)
            .as("sin ninguna salida presumida, esa referencia no tiene a qué apuntar")
            .doesNotContain("Sale:")
            .as("y el resto se entiende mirando la fila: explicar lo que ya se entiende "
                + "llena de texto una hoja que se imprime")
            .doesNotContain("Cómo leer esta tabla")
            .doesNotContain("Entra:")
            .doesNotContain("Equipo:");
    }

    @Test
    @DisplayName("Con una salida que completo el sistema, se suma la linea del asterisco")
    void elAsteriscoSeExplicaCuandoLoHay() {
        // La fila trae ademas dos puertas distintas y ninguna llegada observada: sirve para
        // ver que lo que vuelve es la linea del asterisco y no el bloque entero.
        String texto = pdfDe(conSalidaPresumida());

        assertThat(texto)
            .as("un asterisco sin referencia, en una hoja que se archiva, no se lo puede "
                + "preguntar a nadie")
            .contains("Sale: el asterisco marca la hora que completó el sistema")
            .as("lo de Dictado sigue en su lugar")
            .contains("Dictado: minutos de la clase");

        assertThat(texto)
            .as("vuelve esa línea sola, no el bloque que se sacó")
            .doesNotContain("Cómo leer esta tabla")
            .doesNotContain("Entra:")
            .doesNotContain("Equipo:");
    }

    @Test
    @DisplayName("Cada dato entra entero en su celda, sin partirse en dos renglones")
    void cadaDatoEntraEnteroEnSuCelda() {
        // Sin normalizar: un dato que no entra en su columna aparece cortado —"AUTOMATI CO"—
        // y deja de encontrarse entero. Es la unica forma de sostener los anchos, que no son
        // una cuestion de gusto: una celda que se parte duplica el alto de todas las filas.
        String crudo = crudoDe(fila("Entrada norte", "Entrada norte", LocalTime.of(20, 10)));

        assertThat(crudo)
            .contains("15/06/2026")
            .contains("18:00–20:00")
            .contains("Entrada norte")
            .contains("120/120")
            .contains("PRESENTE")
            .as("el metodo es el mas largo de los valores fijos y el que primero se parte")
            .contains("AUTOMATICO");
    }

    @Test
    @DisplayName("Un reporte cortado lo dice arriba, al pie de cada hoja y en el recuento")
    void elReporteCortadoLoDice() {
        // Una fila devuelta de 5384 que habia: el tope se comio el resto.
        String texto = pdfDe(fila("Entrada norte", "Entrada norte", LocalTime.of(20, 10)), 5384);

        assertThat(texto)
            .as("el aviso arriba de todo, donde se mira primero")
            .contains("REPORTE INCOMPLETO")
            .contains("se listan 1 de 5384 registros")
            .as("el recuento del encabezado deja de decir que son todos")
            .contains("1 de 5384 registros")
            .as("y al pie de la hoja, porque un reporte largo se lee por el medio")
            .contains("Reporte incompleto — se listan 1 de 5384 registros");
    }

    @Test
    @DisplayName("Un reporte entero no lleva ningun aviso")
    void elReporteEnteroNoAvisa() {
        String texto = pdfDe(fila("Entrada norte", "Entrada norte", LocalTime.of(20, 10)));

        assertThat(texto)
            .doesNotContain("INCOMPLETO")
            .doesNotContain("Reporte incompleto")
            .as("el recuento sigue siendo el de siempre")
            .contains("1 registro");
    }

    @Test
    @DisplayName("El nombre del archivo avisa cuando el reporte vino cortado")
    void elNombreDelArchivoAvisa() {
        assertThat(service.nombreArchivo(DESDE, HASTA, false))
            .isEqualTo("asistencias_2026-06-01_a_2026-06-30.pdf");
        assertThat(service.nombreArchivo(DESDE, HASTA, true))
            .as("es lo unico del aviso que sobrevive a guardar el archivo y reenviarlo")
            .isEqualTo("asistencias_2026-06-01_a_2026-06-30_parcial.pdf");
    }

    // ------------------------------------------------------------------------

    private String pdfDe(AsistenciaReporteRowDto fila) {
        return pdfDe(fila, 1);
    }

    private String pdfDe(AsistenciaReporteRowDto fila, long totalSinTope) {
        // Los saltos se normalizan: una celda angosta parte su contenido en dos renglones y
        // eso es correcto en la hoja, pero no tiene que romper una asercion de contenido.
        return crudoDe(fila, totalSinTope).replaceAll("\\s+", " ");
    }

    private String crudoDe(AsistenciaReporteRowDto fila) {
        return crudoDe(fila, 1);
    }

    // El mismo PDF, diciendo cuantas filas habria sin el tope: igual a la que se pasa
    // significa que el reporte esta entero.
    private String crudoDe(AsistenciaReporteRowDto fila, long totalSinTope) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.escribir(out, List.of(fila), DESDE, HASTA, "Instituto de prueba",
                         totalSinTope);
        try {
            PdfReader reader = new PdfReader(out.toByteArray());
            try {
                return new PdfTextExtractor(reader).getTextFromPage(1);
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer el PDF generado.", e);
        }
    }

    // Una jornada que cerro el job, entrando por una puerta y saliendo por otra: es la
    // fila que antes disparaba tres referencias distintas al pie.
    private AsistenciaReporteRowDto conSalidaPresumida() {
        return AsistenciaReporteRowDto.builder()
            .asistenciaId(3L)
            .fecha(LocalDate.of(2026, 6, 15))
            .diaSemana("Lunes")
            .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(20, 0))
            .materiaCodigo("PRG1").materiaNombre("Programación I")
            .comisionCodigo("A")
            .docenteApellido("Pérez").docenteNombre("Ana")
            .horaRegistrada(LocalTime.of(17, 55))
            .horaSalida(LocalTime.of(20, 0)).salidaPresumida(true)
            .equipoEntrada("Entrada norte").equipoSalida("Entrada sur")
            .estado("PRESENTE").metodo("AUTOMATICO")
            .minutosProgramados(120).minutosEfectivos(120)
            .llegadaDentroDelMargen(true).salidaDentroDelMargen(true)
            .build();
    }

    // Una clase de 18 a 20, dictada entera, con los equipos que el caso necesita.
    private AsistenciaReporteRowDto fila(String equipoEntrada, String equipoSalida,
                                         LocalTime horaSalida) {
        return AsistenciaReporteRowDto.builder()
            .asistenciaId(1L)
            .fecha(LocalDate.of(2026, 6, 15))
            .diaSemana("Lunes")
            .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(20, 0))
            .materiaCodigo("PRG1").materiaNombre("Programación I")
            .comisionCodigo("A")
            .docenteApellido("Pérez").docenteNombre("Ana")
            .horaRegistrada(LocalTime.of(17, 55))
            .horaSalida(horaSalida)
            .equipoEntrada(equipoEntrada).equipoSalida(equipoSalida)
            .estado("PRESENTE").metodo("AUTOMATICO")
            .minutosProgramados(120)
            .minutosEfectivos(horaSalida == null ? null : 120)
            .llegadaDentroDelMargen(true).salidaDentroDelMargen(true)
            .build();
    }
}
