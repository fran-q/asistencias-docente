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
import java.util.Objects;

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
     * Helvetica 8 mas los 4pt de padding de cada lado. El reparto anterior le daba a la hora
     * y a lo dictado casi el doble del ancho de su contenido, y le quedaba corto a "Método":
     * "AUTOMATICO" mide 61pt y la columna tenia 57, asi que se partia en dos renglones en
     * casi todas las filas. Ajustado, entra el equipo y ademas sobra espacio para la materia
     * y el docente, que son los que de verdad necesitan crecer.
     */
    private static final float[] ANCHOS =
        {1.0f, 1.15f, 3.1f, 1.0f, 2.7f, 0.7f, 0.75f, 1.25f, 0.85f, 0.95f, 1.1f, 1.3f};
    private static final String[] CABECERAS = {
        "Fecha", "Horario", "Materia", "Comisión", "Docente",
        "Entra", "Sale", "Equipo", "Dictado", "Desvío", "Estado", "Método"
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
                         LocalDate desde, LocalDate hasta, String institucion,
                         ReporteAsistenciaService.TotalesDelReporte totales) {
        // Apaisado: con esta cantidad de columnas, en vertical el nombre de la materia se
        // parte en dos renglones.
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

                // Lo que suma el periodo. Es lo que se mira primero en un reporte impreso, y
                // hasta ahora habia que sacarlo sumando las filas a mano.
                doc.add(new Paragraph(" "));
                doc.add(new Paragraph(resumen(totales), FUENTE_SUBTITULO));
                doc.add(new Paragraph(
                    "Desvío: minutos de clase sin cubrir (-) y minutos de permanencia fuera de "
                    + "la franja de la clase (+).", FUENTE_SUBTITULO));
                // La columna del equipo solo se explica si alguna fila trae las dos puertas.
                // Con una sola cámara por institución el nombre suelto se entiende solo, y una
                // referencia de más en un PDF impreso es una línea que nadie lee.
                if (filas.stream().anyMatch(this::muestraLosDos)) {
                    doc.add(new Paragraph(
                        "Equipo: el nombre del equipo donde se registró la marca. Cuando la "
                        + "salida no se tomó en el mismo equipo que la entrada va «entrada › "
                        + "salida», y un guion del lado de la salida dice que la jornada se "
                        + "cerró sin pasar por una cámara.", FUENTE_SUBTITULO));
                }
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
            // Filas alternadas: con tantas columnas angostas es lo que evita saltar de
            // renglon al recorrerlas con la vista.
            Color fondo = gris ? new Color(0xF2, 0xF3, 0xF5) : Color.WHITE;
            gris = !gris;

            agregar(t, f.getFecha() == null ? "" : f.getFecha().format(FECHA), fondo);
            agregar(t, rango(f), fondo);
            agregar(t, texto(f.getMateriaCodigo()) + " " + texto(f.getMateriaNombre()), fondo);
            agregar(t, texto(f.getComisionCodigo()), fondo);
            agregar(t, texto(f.getDocenteApellido()) + ", " + texto(f.getDocenteNombre()), fondo);
            agregar(t, f.getHoraRegistrada() == null ? "—" : f.getHoraRegistrada().format(HORA), fondo);
            agregar(t, salida(f), fondo);
            agregar(t, equipos(f), fondo);
            agregar(t, dictado(f), fondo);
            agregar(t, desvio(f), fondo);
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

    /**
     * El desvío de la clase, en dos signos: lo que faltó cubrir y lo que sobró de permanencia.
     *
     * <p>"-12 +10" dice, sin leer un renglón, que faltaron doce minutos de clase y que el
     * docente estuvo diez en la institución fuera de esa franja. La referencia va al pie,
     * igual que la de la salida presumida.
     */
    private String desvio(AsistenciaReporteRowDto f) {
        if (f.getMinutosEfectivos() == null) {
            return "—";
        }
        int sinCubrir = f.getMinutosProgramados() - f.getMinutosEfectivos();
        int fuera = f.getMinutosFueraDeClase() == null ? 0 : f.getMinutosFueraDeClase();
        if (sinCubrir <= 0 && fuera <= 0) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        if (sinCubrir > 0) {
            sb.append("-").append(sinCubrir);
        }
        if (fuera > 0) {
            sb.append(sb.length() == 0 ? "" : " ").append("+").append(fuera);
        }
        return sb.toString();
    }

    /**
     * Por qué puerta entró y por cuál salió (RF-89, V031).
     *
     * <p>Un solo nombre cuando las dos marcas se tomaron en el mismo equipo, que es el caso
     * normal y el que no hay que leer. Los dos nombres cuando no coinciden: ahí está lo que
     * una inspección viene a buscar, y hasta que existió esta columna el PDF afirmaba, por
     * omisión, que el docente había salido por donde entró.
     */
    private String equipos(AsistenciaReporteRowDto f) {
        String entrada = nombre(f.getEquipoEntrada());
        if (!muestraLosDos(f)) {
            return entrada;
        }
        return entrada + " › " + nombre(f.getEquipoSalida());
    }

    /**
     * Si la fila tiene que mostrar los dos equipos.
     *
     * <p>Sin hora de salida no hay segundo equipo del que hablar —el guion de la columna
     * "Sale" ya lo dijo—, y con la misma puerta de los dos lados repetir el nombre solo gasta
     * ancho. El guion del lado de la salida, en cambio, sí es un dato: la jornada la cerró el
     * job por vencimiento o un admin desde la pantalla de pendientes, sin cámara de por medio.
     */
    private boolean muestraLosDos(AsistenciaReporteRowDto f) {
        if (f.getHoraSalida() == null) {
            return false;
        }
        if (f.getEquipoEntrada() == null && f.getEquipoSalida() == null) {
            return false;
        }
        return !Objects.equals(f.getEquipoEntrada(), f.getEquipoSalida());
    }

    // Guion cuando no hay equipo, igual que en el resto de la tabla.
    private String nombre(String equipo) {
        return equipo == null ? "—" : equipo;
    }

    // Lo que suma el periodo, en un renglon.
    private String resumen(ReporteAsistenciaService.TotalesDelReporte t) {
        if (t == null) {
            return "";
        }
        String base = "Programado " + t.programadoLegible()
            + "  ·  dictado " + t.netoLegible() + " (" + t.porcentajeDictado() + "%)"
            + "  ·  sin cubrir " + t.sinCubrirLegible()
            + "  ·  fuera de clase " + t.fueraDeClaseLegible();
        return t.clasesSinDato() == 0
            ? base
            : base + "  ·  " + t.clasesSinDato() + " sin dato de salida";
    }
}
