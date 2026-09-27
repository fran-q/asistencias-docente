package edu.cent35.asistencias.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.HeaderFooter;
import com.lowagie.text.PageSize;
import com.lowagie.text.Rectangle;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import edu.cent35.asistencias.dto.AsistenciaReporteRowDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Arma el reporte de asistencias en PDF (RF-61).
 *
 * <p>Convive con el CSV y no lo reemplaza, porque sirven a cosas distintas: el CSV se abre en
 * una planilla para seguir trabajando el dato, y el PDF se imprime o se adjunta tal cual. Por
 * eso el PDF trae menos columnas que el CSV y ninguna interna: lo que no se lee de un vistazo
 * en una hoja apaisada estorba mas de lo que aporta.
 */
@Service
@Slf4j
public class ReportePdfService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA  = DateTimeFormatter.ofPattern("HH:mm");

    /*
     * Anchos relativos de las columnas, medidos contra lo que de verdad entra en cada una a
     * Helvetica 8 mas los 4pt de padding de cada lado. Medirlos en vez de estimarlos es lo
     * que evita que una celda se parta en dos renglones y duplique el alto de toda la tabla:
     * "AUTOMATICO" mide 61pt y con 57 se partia en casi todas las filas.
     */
    private static final float[] ANCHOS =
        {1.0f, 1.15f, 3.1f, 1.0f, 2.7f, 0.7f, 0.75f, 0.85f, 1.25f, 1.1f, 1.3f};
    private static final String[] CABECERAS = {
        "Fecha", "Horario", "Materia", "Comisión", "Docente",
        "Entra", "Sale", "Dictado", "Equipo", "Estado", "Método"
    };

    private static final Font FUENTE_TITULO   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font FUENTE_SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
    private static final Font FUENTE_CABECERA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
    private static final Font FUENTE_CELDA    = FontFactory.getFont(FontFactory.HELVETICA, 8);
    private static final Color FONDO_CABECERA = new Color(0x33, 0x3A, 0x45);

    // El aviso de reporte cortado va en rojo y en negrita. No es decoracion: es lo unico
    // que distingue esta hoja de una completa, y se imprime igual que el resto en gris.
    private static final Font FUENTE_AVISO =
        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(0xA0, 0x1B, 0x1B));
    private static final Font FUENTE_PIE_AVISO =
        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, new Color(0xA0, 0x1B, 0x1B));

    // El bloque de referencias del pie. Un punto mas chico que el resumen: es material de
    // consulta, se lee una vez y despues estorba.
    private static final Font FUENTE_LEYENDA =
        FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
    private static final Font FUENTE_LEYENDA_COLUMNA =
        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.DARK_GRAY);

    /**
     * Escribe el reporte al stream indicado. No lo cierra: de eso se encarga quien lo abrio,
     * que en el controlador es el propio response.
     */
    public void escribir(OutputStream out, List<AsistenciaReporteRowDto> filas,
                         LocalDate desde, LocalDate hasta, String institucion,
                         long totalSinTope) {
        // El reporte trae hasta un tope de filas. Si lo paso, esta hoja no es el periodo:
        // es una parte, y los totales del pie tampoco son los del periodo.
        boolean cortado = totalSinTope > filas.size();
        // Apaisado: con esta cantidad de columnas, en vertical el nombre de la materia se
        // parte en dos renglones.
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 32, 28);
        try {
            PdfWriter.getInstance(doc, out);

            // En todas las paginas, no solo en la primera: un reporte de sesenta hojas se
            // lee por el medio, y el aviso de arriba no llega hasta ahi. Va DESPUES del
            // getInstance --que es cuando el writer se suscribe al documento-- y antes del
            // open: puesto antes, el pie se guarda en el Document y nadie lo dibuja.
            if (cortado) {
                HeaderFooter pie = new HeaderFooter(
                    new Phrase("Reporte incompleto — se listan " + filas.size()
                               + " de " + totalSinTope + " registros", FUENTE_PIE_AVISO),
                    false);
                pie.setAlignment(Element.ALIGN_CENTER);
                pie.setBorder(Rectangle.NO_BORDER);
                doc.setFooter(pie);
            }
            doc.open();

            doc.add(titulo(institucion));
            doc.add(subtitulo(filas.size(), desde, hasta, cortado, totalSinTope));
            if (cortado) {
                doc.add(aviso(filas.size(), totalSinTope));
            }
            doc.add(new Paragraph(" "));

            if (filas.isEmpty()) {
                Paragraph vacio = new Paragraph(
                    "No hay asistencias registradas con los filtros elegidos.", FUENTE_SUBTITULO);
                vacio.setAlignment(Element.ALIGN_CENTER);
                doc.add(vacio);
            } else {
                doc.add(tabla(filas));

                doc.add(new Paragraph(" "));
                doc.add(referencia("Dictado",
                    "minutos de la clase que el docente cubrió, sobre los que duraba. Cuenta "
                    + "sólo lo que se pisa con la franja de la clase: llegar antes o quedarse "
                    + "después no suma. Un guion es que falta la marca de salida y no se puede "
                    + "saber, que no es lo mismo que cero."));
                // El asterisco es el otro que no se entiende solo, con el agravante de que
                // ni siquiera es una palabra. Solo se explica si hay alguno en la hoja.
                if (filas.stream().anyMatch(
                        f -> f.getHoraSalida() != null && f.isSalidaPresumida())) {
                    doc.add(referencia("Sale",
                        "el asterisco marca la hora que completó el sistema porque nadie la "
                        + "registró. La asistencia es válida; el dato de salida está "
                        + "pendiente de confirmación."));
                }
            }
        } catch (Exception e) {
            // El stream ya puede llevar bytes escritos, asi que no hay forma de devolver una
            // pagina de error: se corta y se deja constancia con el detalle.
            throw new IllegalStateException("No se pudo generar el PDF del reporte.", e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
        log.info("Reporte PDF generado: {} filas, {} a {}", filas.size(), desde, hasta);
    }

    /**
     * Nombre del archivo sugerido al navegador, con el rango adentro.
     *
     * <p>Cuando el reporte vino cortado lo dice el nombre, porque es lo único del aviso que
     * sobrevive a que el archivo se guarde y se reenvíe: adentro está, pero para verlo hay
     * que abrirlo.
     */
    public String nombreArchivo(LocalDate desde, LocalDate hasta, boolean cortado) {
        return String.format("asistencias_%s_a_%s%s.pdf", desde, hasta,
                             cortado ? "_parcial" : "");
    }

    // ------------------------------------------------------------------------

    private Paragraph titulo(String institucion) {
        Paragraph p = new Paragraph(
            institucion == null || institucion.isBlank()
                ? "Reporte de asistencias"
                : "Reporte de asistencias — " + institucion,
            FUENTE_TITULO);
        p.setAlignment(Element.ALIGN_LEFT);
        return p;
    }

    // Deja escrito de que periodo es y cuantas filas trae: sin eso, dos PDF impresos con
    // filtros distintos son indistinguibles una vez que estan sobre el escritorio.
    private Paragraph subtitulo(int cantidad, LocalDate desde, LocalDate hasta,
                                boolean cortado, long totalSinTope) {
        String cuantos = cortado
            ? cantidad + " de " + totalSinTope + " registros"
            : cantidad + (cantidad == 1 ? " registro" : " registros");
        String texto = "Período " + desde.format(FECHA) + " a " + hasta.format(FECHA)
                     + "  ·  " + cuantos
                     + "  ·  emitido el " + LocalDate.now().format(FECHA);
        return new Paragraph(texto, FUENTE_SUBTITULO);
    }

    // El aviso de que esta hoja no es el periodo entero.
    private Paragraph aviso(int cuantas, long totalSinTope) {
        return new Paragraph(
            "REPORTE INCOMPLETO. El tope del reporte cortó la lista: se listan " + cuantas
            + " de " + totalSinTope + " registros. Acotá el rango de fechas o los filtros "
            + "para tenerlo entero.",
            FUENTE_AVISO);
    }

    private PdfPTable tabla(List<AsistenciaReporteRowDto> filas) throws Exception {
        PdfPTable t = new PdfPTable(ANCHOS.length);
        t.setWidthPercentage(100);
        t.setWidths(ANCHOS);
        // La cabecera se repite en cada pagina: un reporte de varias hojas sin encabezado
        // obliga a volver a la primera para saber que columna se esta mirando.
        t.setHeaderRows(1);

        for (String c : CABECERAS) {
            PdfPCell celda = new PdfPCell(new Phrase(c, FUENTE_CABECERA));
            celda.setBackgroundColor(FONDO_CABECERA);
            celda.setPadding(5f);
            celda.setBorderColor(FONDO_CABECERA);
            t.addCell(celda);
        }

        boolean gris = false;
        for (AsistenciaReporteRowDto f : filas) {
            // Filas alternadas: con tantas columnas angostas es lo que evita saltar de
            // renglon al recorrerlas con la vista.
            Color fondo = gris ? new Color(0xF2, 0xF3, 0xF5) : Color.WHITE;
            gris = !gris;

            agregar(t, f.getFecha() == null ? "" : f.getFecha().format(FECHA), fondo);
            agregar(t, rango(f), fondo);
            agregar(t, texto(f.getMateriaCodigo()) + " " + texto(f.getMateriaNombre()), fondo);
            agregar(t, texto(f.getComisionCodigo()), fondo);
            agregar(t, texto(f.getDocenteApellido()) + ", " + texto(f.getDocenteNombre()), fondo);
            agregar(t, entrada(f), fondo);
            agregar(t, salida(f), fondo);
            agregar(t, dictado(f), fondo);
            agregar(t, f.getEquipos(), fondo);
            agregar(t, estado(f), fondo);
            agregar(t, texto(f.getMetodo()), fondo);
        }
        return t;
    }

    private void agregar(PdfPTable t, String texto, Color fondo) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, FUENTE_CELDA));
        celda.setBackgroundColor(fondo);
        celda.setPadding(4f);
        celda.setBorderColor(new Color(0xDD, 0xDD, 0xDD));
        t.addCell(celda);
    }

    private String rango(AsistenciaReporteRowDto f) {
        if (f.getHoraInicio() == null || f.getHoraFin() == null) return "";
        return f.getHoraInicio().format(HORA) + "–" + f.getHoraFin().format(HORA);
    }

    // La justificacion viaja pegada al estado: una AUSENTE justificada y una que no lo esta
    // son dos cosas distintas, y en una columna aparte quedaria casi siempre vacia.
    private String estado(AsistenciaReporteRowDto f) {
        String base = texto(f.getEstado());
        return f.isJustificada() ? base + " (just.)" : base;
    }

    private String texto(String s) {
        return s == null ? "" : s;
    }

    /**
     * La hora de llegada, y guion cuando no hubo ninguna.
     *
     * <p>Una carga manual guarda el momento en que el administrador la asentó, y una ausencia
     * generada por el job, el fin de la clase. Impresas bajo "Entra" las dos dicen que alguien
     * llegó a esa hora, que es justo lo que no pasó. La columna "Método" y la de "Estado"
     * quedan diciendo por qué la fila no tiene llegada.
     */
    private String entrada(AsistenciaReporteRowDto f) {
        if (!f.isHoraDeLlegada() || f.getHoraRegistrada() == null) {
            return "—";
        }
        return f.getHoraRegistrada().format(HORA);
    }

    /**
     * La hora de salida, marcando cuando la presumió el sistema.
     *
     * <p>El asterisco no es decoración: en un PDF que se imprime y se archiva, una hora
     * observada y una completada por el sistema no pueden verse iguales. La referencia va al
     * pie del documento.
     */
    private String salida(AsistenciaReporteRowDto f) {
        if (f.getHoraSalida() == null) return "—";
        return f.getHoraSalida().format(HORA) + (f.isSalidaPresumida() ? " *" : "");
    }

    /**
     * Minutos dictados sobre programados.
     *
     * <p>Guion cuando no hay dato, que no es lo mismo que cero: cero dice que no dio la clase
     * y el guion, que de esa fila no se sabe.
     */
    private String dictado(AsistenciaReporteRowDto f) {
        if (f.getMinutosEfectivos() == null) return "—";
        return f.getMinutosEfectivos() + "/" + f.getMinutosProgramados();
    }

    /**
     * Un renglón del pie: el nombre de la columna en negrita y su explicación al lado.
     *
     * <p>Hubo un bloque de cuatro referencias, una por cada columna que se fue agregando.
     * Quedaron dos, y no por simetría: son las dos que no se entienden mirando la fila.
     * <b>"Dictado"</b> porque dos números separados por una barra pueden ser cualquier cosa,
     * y porque esconde dos decisiones que cambian lo que dicen —que sólo cuenta lo que se
     * pisa con la franja de la clase, y que un guion no es cero—. <b>El asterisco de "Sale"</b>
     * porque ni siquiera es una palabra: quien recibe la hoja impresa no tiene a quién
     * preguntarle qué significa. "Entra" y "Equipo" sí se leen solas y se fueron.
     *
     * <p>Lo de "Dictado" va en toda hoja; lo del asterisco, sólo si hay alguno.
     */
    private Paragraph referencia(String columna, String explicacion) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(columna + ": ", FUENTE_LEYENDA_COLUMNA));
        p.add(new Chunk(explicacion, FUENTE_LEYENDA));
        return p;
    }

}
