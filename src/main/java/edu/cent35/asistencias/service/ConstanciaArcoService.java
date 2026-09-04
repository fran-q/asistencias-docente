package edu.cent35.asistencias.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import edu.cent35.asistencias.model.ConsentimientoBiometrico;
import edu.cent35.asistencias.model.Docente;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Genera la constancia en PDF de una gestión ARCO (RNF-14).
 *
 * <p>La Ley 25.326 le da al titular del dato el derecho a ejercer acceso, rectificación,
 * cancelación y oposición. Ejercerlos y no poder demostrar que se ejercieron es casi lo
 * mismo que no poder ejercerlos: si el docente pide que se borre su rostro, lo que necesita
 * llevarse es un papel que diga qué se borró, cuándo y quién lo hizo.
 *
 * <p>La constancia es el único artefacto del sistema pensado para salir de él. Por eso dice
 * el nombre de la institución, la fecha y hora, y quién ejecutó la operación.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConstanciaArcoService {

    private static final DateTimeFormatter FECHA_HORA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Font TITULO    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
    private static final Font ETIQUETA  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font CUERPO    = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font PIE       = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);

    /**
     * Vuelca la constancia al stream indicado.
     *
     * @param institucion  responsable de la base de datos, que es quien responde por ella
     * @param operador     quién ejecutó la gestión
     * @param historial    consentimientos del docente, del más nuevo al más viejo
     * @param tieneModelo  si al momento de emitirla queda algún modelo facial vivo
     */
    public void escribir(OutputStream out, Docente docente, String institucion,
                         String operador, List<ConsentimientoBiometrico> historial,
                         boolean tieneModelo) {
        Document doc = new Document(PageSize.A4, 42, 42, 40, 40);
        PdfWriter.getInstance(doc, out);
        doc.open();

        doc.add(parrafo("Constancia de tratamiento de datos biométricos", TITULO, 4));
        doc.add(parrafo(institucion + " — Ley 25.326 y Resolución AAIP 255/2022", SUBTITULO, 18));

        doc.add(parrafo("Titular del dato", ETIQUETA, 2));
        doc.add(parrafo(docente.getNombreCompleto() + " — DNI " + docente.getPersona().getDni()
                        + (docente.getLegajo() != null ? " — Legajo " + docente.getLegajo() : ""),
                        CUERPO, 14));

        doc.add(parrafo("Datos biométricos en el sistema", ETIQUETA, 2));
        doc.add(parrafo(tieneModelo
            ? "Existe un modelo facial registrado. El sistema NO almacena fotografías: guarda "
              + "un modelo matemático cifrado, del que no se puede reconstruir la imagen."
            : "No hay ningún modelo facial registrado para esta persona.", CUERPO, 14));

        doc.add(parrafo("Historial de consentimiento", ETIQUETA, 2));
        if (historial.isEmpty()) {
            doc.add(parrafo("No se registran consentimientos.", CUERPO, 14));
        } else {
            for (ConsentimientoBiometrico c : historial) {
                String linea = "• " + FECHA.format(c.getFechaConsentimiento()) + " — "
                    + (Boolean.TRUE.equals(c.getVigente()) ? "otorgado (vigente)" : "otorgado")
                    + (c.getFechaRevocacion() != null
                        ? ", revocado el " + FECHA.format(c.getFechaRevocacion()) : "");
                doc.add(parrafo(linea, CUERPO, 2));
            }
            doc.add(parrafo(" ", CUERPO, 10));
        }

        doc.add(parrafo("Derechos del titular", ETIQUETA, 2));
        doc.add(parrafo(
            "Acceso: a conocer qué datos suyos trata la institución. "
            + "Rectificación: a corregirlos si son inexactos. "
            + "Cancelación: a que se supriman, lo que en este sistema es un borrado físico y "
            + "definitivo del modelo. "
            + "Oposición: a revocar el consentimiento, tras lo cual deja de usarse el "
            + "reconocimiento facial para registrar su asistencia.", CUERPO, 14));

        doc.add(parrafo("Emitida el " + FECHA_HORA.format(LocalDateTime.now())
                        + " por " + operador + ".", PIE, 0));

        doc.close();
        log.info("Constancia ARCO emitida para docente id={} por {}", docente.getId(), operador);
    }

    // Nombre de archivo con el DNI, que es como la institución archiva estos papeles.
    public String nombreArchivo(Docente docente) {
        return "constancia_arco_" + docente.getPersona().getDni() + ".pdf";
    }

    private Paragraph parrafo(String texto, Font fuente, float espacioDespues) {
        Paragraph p = new Paragraph(texto, fuente);
        p.setAlignment(Element.ALIGN_LEFT);
        p.setSpacingAfter(espacioDespues);
        return p;
    }
}
