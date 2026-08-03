package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.dto.AsistenciaReporteRowDto;
import edu.cent35.asistencias.dto.ReporteFiltroDto;
import edu.cent35.asistencias.model.EstadoAsistencia;
import edu.cent35.asistencias.model.MetodoAsistencia;
import edu.cent35.asistencias.service.DocenteService;
import edu.cent35.asistencias.service.MateriaService;
import edu.cent35.asistencias.service.CarreraService;
import edu.cent35.asistencias.service.MiInstitucionService;
import edu.cent35.asistencias.service.ReporteAsistenciaService;
import edu.cent35.asistencias.service.ReportePdfService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Reporte de asistencias filtrable por período, docente, materia, estado y método, para los
 * roles INSTITUCION y ADMIN. El mismo filtro alimenta la tabla en pantalla y las dos
 * descargas: CSV, en UTF-8 con BOM para que Excel lo abra sin romper los acentos, y PDF,
 * para imprimir o adjuntar. Son dos usos distintos y por eso conviven: el CSV se sigue
 * trabajando en una planilla, el PDF se lee tal cual.
 */
@Controller
@RequestMapping("/reportes")
@PreAuthorize("hasAnyRole('INSTITUCION', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class ReporteController {

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FMT_HORA  = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ReporteAsistenciaService reporteService;
    private final DocenteService docenteService;
    private final MateriaService materiaService;
    private final ReportePdfService reportePdfService;
    private final MiInstitucionService miInstitucionService;
    private final CarreraService carreraService;

    // Pantalla del reporte con filtros + tabla.
    @GetMapping
    public String pantalla(
            @RequestParam(name = "desde", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "docenteId", required = false) Long docenteId,
            @RequestParam(name = "materiaId", required = false) Long materiaId,
            @RequestParam(name = "carreraId", required = false) Long carreraId,
            @RequestParam(name = "estado",    required = false) EstadoAsistencia estado,
            @RequestParam(name = "metodo",    required = false) MetodoAsistencia metodo,
            Model model) {

        // Default: mes actual hasta hoy.
        LocalDate hoy = LocalDate.now();
        if (desde == null) desde = hoy.withDayOfMonth(1);
        if (hasta == null) hasta = hoy;

        ReporteFiltroDto filtro = ReporteFiltroDto.builder()
            .desde(desde).hasta(hasta)
            .docenteId(docenteId).materiaId(materiaId).carreraId(carreraId)
            .estado(estado).metodo(metodo)
            .build();

        try {
            List<AsistenciaReporteRowDto> filas = reporteService.reporte(filtro);
            model.addAttribute("filas", filas);
            // Cuantas habria sin el tope. Si son mas que las mostradas, la pantalla avisa:
            // un reporte cortado en silencio se lee como un reporte completo.
            long total = reporteService.contar(filtro);
            model.addAttribute("totalSinTope", total);
            model.addAttribute("truncado", total > filas.size());
            model.addAttribute("maxFilas", reporteService.getMaxFilas());
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("filas", List.of());
        }

        model.addAttribute("filtro", filtro);
        model.addAttribute("docentes", docenteService.listar());
        model.addAttribute("materias", materiaService.listar());
        model.addAttribute("carreras", carreraService.listar());
        model.addAttribute("estadosPosibles", EstadoAsistencia.values());
        model.addAttribute("metodosPosibles", MetodoAsistencia.values());
        return "reporte/asistencias";
    }

    // Descarga el reporte como CSV (UTF-8 con BOM para Excel).
    @GetMapping("/csv")
    public void descargarCsv(
            @ModelAttribute ReporteFiltroDto filtro,
            HttpServletResponse response) throws IOException {

        // Default: mes actual hasta hoy.
        LocalDate hoy = LocalDate.now();
        if (filtro.getDesde() == null) filtro.setDesde(hoy.withDayOfMonth(1));
        if (filtro.getHasta() == null) filtro.setHasta(hoy);

        List<AsistenciaReporteRowDto> filas = reporteService.reporte(filtro);

        String nombreArchivo = String.format("asistencias_%s_a_%s.csv",
            filtro.getDesde(), filtro.getHasta());
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
            "attachment; filename=\"" + nombreArchivo + "\"");

        try (OutputStream out = response.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            // BOM UTF-8 para que Excel reconozca el encoding.
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

            escribirEncabezado(writer);
            for (AsistenciaReporteRowDto f : filas) {
                escribirFila(writer, f);
            }
        }
        log.info("Reporte CSV exportado: {} filas, archivo={}", filas.size(), nombreArchivo);
    }

    // Descarga el mismo reporte como PDF, ya listo para imprimir.
    @GetMapping("/pdf")
    public void descargarPdf(
            @ModelAttribute ReporteFiltroDto filtro,
            HttpServletResponse response) throws IOException {

        // Mismo default que el CSV: mes actual hasta hoy.
        LocalDate hoy = LocalDate.now();
        if (filtro.getDesde() == null) filtro.setDesde(hoy.withDayOfMonth(1));
        if (filtro.getHasta() == null) filtro.setHasta(hoy);

        List<AsistenciaReporteRowDto> filas = reporteService.reporte(filtro);
        String institucion = miInstitucionService.getMiInstitucion().getNombre();
        String nombreArchivo = reportePdfService.nombreArchivo(filtro.getDesde(), filtro.getHasta());

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
            "attachment; filename=\"" + nombreArchivo + "\"");

        try (OutputStream out = response.getOutputStream()) {
            reportePdfService.escribir(out, filas, filtro.getDesde(), filtro.getHasta(), institucion);
        }
    }

    // ------------------------------------------------------------------------

    private void escribirEncabezado(PrintWriter w) {
        w.println(String.join(";",
            "id", "fecha", "dia_semana", "hora_inicio", "hora_fin",
            "carrera", "materia_codigo", "materia_nombre", "comision",
            "docente_dni", "docente_apellido", "docente_nombre",
            "hora_registrada", "estado", "metodo", "confianza",
            "motivo_carga_manual", "detalle_carga_manual", "usuario_registro",
            "justificada", "motivo_justificacion"));
    }

    // Vuelca una fila del reporte al CSV, en el mismo orden que la cabecera.
    private void escribirFila(PrintWriter w, AsistenciaReporteRowDto f) {
        w.println(String.join(";",
            csv(f.getAsistenciaId()),
            csv(f.getFecha()),
            csv(f.getDiaSemana()),
            csvTime(f.getHoraInicio()),
            csvTime(f.getHoraFin()),
            csv(f.getCarreraCodigo()),
            csv(f.getMateriaCodigo()),
            csv(f.getMateriaNombre()),
            csv(f.getComisionCodigo()),
            csv(f.getDocenteDni()),
            csv(f.getDocenteApellido()),
            csv(f.getDocenteNombre()),
            csvTime(f.getHoraRegistrada()),
            csv(f.getEstado()),
            csv(f.getMetodo()),
            csv(f.getConfianza()),
            csv(f.getMotivoManual()),
            csv(f.getDetalleManual()),
            csv(f.getUsuarioRegistrador()),
            csv(f.isJustificada() ? "SI" : "NO"),
            csv(f.getMotivoJustificacion())
        ));
    }

    // Escapa un campo CSV con separador ';' y comillas dobles (RFC 4180 con coma → ';').
    private static String csv(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        // Si tiene ; " o salto de línea, encerrar entre comillas y duplicar las " internas.
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // Formatea un decimal para el CSV; se deja el punto porque Excel lo interpreta bien y el
    // separador de columnas ya es punto y coma.
    private static String csv(BigDecimal v) {
        return v == null ? "" : v.toPlainString();
    }

    // Formatea una hora para el CSV, o vacío si no hay.
    private static String csvTime(LocalTime t) {
        return t == null ? "" : t.format(FMT_HORA);
    }

    // Formatea una fecha para el CSV, o vacío si no hay.
    private static String csv(LocalDate d) {
        return d == null ? "" : d.format(FMT_FECHA);
    }
}
