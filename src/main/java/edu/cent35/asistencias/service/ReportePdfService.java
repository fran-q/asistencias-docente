package edu.cent35.asistencias.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
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

    // Anchos relativos de las columnas. La materia y el docente son los que se leen,
    // asi que se llevan el espacio; la fecha y la hora tienen largo fijo.
    private static final float[] ANCHOS =
        {1.1f, 1.6f, 2.4f, 0.9f, 2.0f, 0.8f, 0.8f, 1.2f, 1.0f, 1.0f};
    private static final String[] CABECERAS = {
        "Fecha", "Horario", "Materia", "Comisión", "Docente",
        "Entra", "Sale", "Dictado", "Estado", "Método"
    };

    private static final Font FUENTE_TITULO   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font FUENTE_SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
    private static final Font FUENTE_CABECERA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
    private static final Font FUENTE_CELDA    = FontFactory.getFont(FontFactory.HELVETICA, 8);
    private static final Color FONDO_CABECERA = new Color(0x33, 0x3A, 0x45);

    /**
     * Escribe el reporte al stream indicado. No lo cierra: de eso se encarga quien lo abrio,
     * que en el controlador es el propio response.
     */
    public void escribir(OutputStream out, List<AsistenciaReporteRowDto> filas,
                         LocalDate desde, LocalDate hasta, String institucion) {
        // Apaisado: son ocho columnas y en vertical el nombre de la materia se parte.
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 32, 28);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            doc.add(titulo(institucion));
            doc.add(subtitulo(filas.size(), desde, hasta));
            doc.add(new Paragraph(" "));

            if (filas.isEmpty()) {
                Paragraph vacio = new Paragraph(
                    "No hay asistencias registradas con los filtros elegidos.", FUENTE_SUBTITULO);
                vacio.setAlignment(Element.ALIGN_CENTER);
                doc.add(vacio);
            } else {
                doc.add(tabla(filas));
                // Un asterisco sin referencia es peor que no ponerlo: quien recibe el PDF
                // impreso no tiene a quien preguntarle que significa. Solo se aclara si hay
                // alguna, para no ensuciar los reportes donde todas las salidas se marcaron.
                boolean hayPresumidas = filas.stream()
                    .anyMatch(f -> f.getHoraSalida() != null && f.isSalidaPresumida());
                if (hayPresumidas) {
                    doc.add(new Paragraph(" "));
                    doc.add(new Paragraph(
                        "* Hora de salida completada por el sistema: nadie la registró. "
                        + "La asistencia es válida; el dato de salida está pendiente de "
                        + "confirmación.", FUENTE_SUBTITULO));
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

    // Nombre del archivo sugerido al navegador, con el rango adentro.
    public String nombreArchivo(LocalDate desde, LocalDate hasta) {
        return String.format("asistencias_%s_a_%s.pdf", desde, hasta);
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
    private Paragraph subtitulo(int cantidad, LocalDate desde, LocalDate hasta) {
        String texto = "Período " + desde.format(FECHA) + " a " + hasta.format(FECHA)
                     + "  ·  " + cantidad + (cantidad == 1 ? " registro" : " registros")
                     + "  ·  emitido el " + LocalDate.now().format(FECHA);
        return new Paragraph(texto, FUENTE_SUBTITULO);
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
            // Filas alternadas: con ocho columnas angostas es lo que evita saltar de renglon
            // al recorrerlas con la vista.
            Color fondo = gris ? new Color(0xF2, 0xF3, 0xF5) : Color.WHITE;
            gris = !gris;

            agregar(t, f.getFecha() == null ? "" : f.getFecha().format(FECHA), fondo);
            agregar(t, rango(f), fondo);
            agregar(t, texto(f.getMateriaCodigo()) + " " + texto(f.getMateriaNombre()), fondo);
            agregar(t, texto(f.getComisionCodigo()), fondo);
            agregar(t, texto(f.getDocenteApellido()) + ", " + texto(f.getDocenteNombre()), fondo);
            agregar(t, f.getHoraRegistrada() == null ? "—" : f.getHoraRegistrada().format(HORA), fondo);
            agregar(t, salida(f), fondo);
            agregar(t, dictado(f), fondo);
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
}
