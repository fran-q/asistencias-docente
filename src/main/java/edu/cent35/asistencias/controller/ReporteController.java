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
import jakarta.servlet.http.HttpServletRequest;
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

    /**
     * Los caracteres con los que una planilla arranca una fórmula.
     *
     * <p>El tabulador y el retorno de carro están en la lista aunque no sean operadores:
     * varias planillas los descartan al leer la celda y dejan de primero al que sí lo es.
     */
    private static final String ARRANCA_FORMULA = "=+-@\t\r";

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
            // Cuanto se dicto de lo programado en el periodo. Sin esto habia que bajar el CSV
            // y sumar a mano para contestar la pregunta mas frecuente del reporte.
            model.addAttribute("totales", reporteService.totales(filas));
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
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        // Default: mes actual hasta hoy.
        LocalDate hoy = LocalDate.now();
        if (filtro.getDesde() == null) filtro.setDesde(hoy.withDayOfMonth(1));
        if (filtro.getHasta() == null) filtro.setHasta(hoy);

        List<AsistenciaReporteRowDto> filas;
        try {
            filas = reporteService.reporte(filtro);
        } catch (IllegalArgumentException ex) {
            volverAlReporte(request, response, filtro, ex);
            return;
        }
        // Cuantas habria sin el tope, igual que en la pantalla. Un archivo cortado en
        // silencio se lee como el periodo entero, y este se abre en una planilla para
        // sacar cuentas: el que suma no tiene como saber que le faltan filas.
        long totalSinTope = reporteService.contar(filtro);
        boolean cortado = totalSinTope > filas.size();

        String nombreArchivo = String.format("asistencias_%s_a_%s%s.csv",
            filtro.getDesde(), filtro.getHasta(), cortado ? "_parcial" : "");
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
            // Al final y en una sola celda: arriba correria las columnas, y el que abre el
            // archivo para ver si esta completo va justo al final.
            if (cortado) {
                writer.println(csv(
                    "Reporte incompleto: se exportaron las primeras " + filas.size()
                    + " filas de " + totalSinTope + ". Acotá el rango de fechas o los "
                    + "filtros para bajarlo entero."));
            }
        }
        log.info("Reporte CSV exportado: {} filas de {}, archivo={}",
                 filas.size(), totalSinTope, nombreArchivo);
    }

    // Descarga el mismo reporte como PDF, ya listo para imprimir.
    @GetMapping("/pdf")
    public void descargarPdf(
            @ModelAttribute ReporteFiltroDto filtro,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        // Mismo default que el CSV: mes actual hasta hoy.
        LocalDate hoy = LocalDate.now();
        if (filtro.getDesde() == null) filtro.setDesde(hoy.withDayOfMonth(1));
        if (filtro.getHasta() == null) filtro.setHasta(hoy);

        List<AsistenciaReporteRowDto> filas;
        try {
            filas = reporteService.reporte(filtro);
        } catch (IllegalArgumentException ex) {
            volverAlReporte(request, response, filtro, ex);
            return;
        }
        long totalSinTope = reporteService.contar(filtro);
        String institucion = miInstitucionService.getMiInstitucion().getNombre();
        String nombreArchivo = reportePdfService.nombreArchivo(
            filtro.getDesde(), filtro.getHasta(), totalSinTope > filas.size());

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
            "attachment; filename=\"" + nombreArchivo + "\"");

        try (OutputStream out = response.getOutputStream()) {
            reportePdfService.escribir(out, filas, filtro.getDesde(), filtro.getHasta(),
                                       institucion, reporteService.totales(filas),
                                       totalSinTope);
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Cuando el filtro no da un reporte, vuelve a la pantalla con los mismos filtros.
     *
     * <p>Un rango al revés se escribe solo: la pantalla tiene los dos campos y nada impide
     * poner diciembre en "desde" y enero en "hasta". Ahí el error se muestra y se corrige;
     * pidiendo la descarga con esas mismas fechas —el botón está al lado, y la URL se guarda
     * en favoritos— la excepción salía sin manejar y terminaba en una pantalla de error.
     *
     * <p><b>No se le pasa el mensaje.</b> La pantalla arma el mismo reporte, se choca con la
     * misma validación del servicio y lo muestra ella: uno solo que lo diga y siempre el
     * mismo texto. Y la URL se arma con los valores <b>ya convertidos</b> —fechas, ids y
     * enums—, no con lo que vino escrito en la consulta: devolver texto de afuera dentro de
     * una cabecera {@code Location} es justo lo que no hay que hacer.
     */
    private void volverAlReporte(HttpServletRequest request, HttpServletResponse response,
                                 ReporteFiltroDto filtro, RuntimeException motivo)
            throws IOException {
        log.info("Descarga sin reporte posible ({}): desde={}, hasta={}",
                 motivo.getMessage(), filtro.getDesde(), filtro.getHasta());

        StringBuilder url = new StringBuilder(request.getContextPath())
            .append("/reportes?desde=").append(filtro.getDesde())
            .append("&hasta=").append(filtro.getHasta());
        parametro(url, "docenteId", filtro.getDocenteId());
        parametro(url, "materiaId", filtro.getMateriaId());
        parametro(url, "carreraId", filtro.getCarreraId());
        parametro(url, "estado",    filtro.getEstado());
        parametro(url, "metodo",    filtro.getMetodo());
        response.sendRedirect(url.toString());
    }

    private static void parametro(StringBuilder url, String nombre, Object valor) {
        if (valor != null) {
            url.append('&').append(nombre).append('=').append(valor);
        }
    }

    private void escribirEncabezado(PrintWriter w) {
        w.println(String.join(";",
            "id", "fecha", "dia_semana", "hora_inicio", "hora_fin",
            "carrera", "materia_codigo", "materia_nombre", "comision",
            "docente_dni", "docente_apellido", "docente_nombre",
            "hora_registrada", "hora_salida", "salida_presumida",
            "equipo_entrada", "equipo_salida",
            "minutos_programados", "minutos_efectivos",
            "minutos_tarde", "minutos_salida_anticipada", "minutos_fuera_de_clase",
            "llegada_en_margen", "salida_en_margen",
            "estado", "metodo",
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
            csvTime(f.getHoraSalida()),
            csv(f.getHoraSalida() == null ? "" : (f.isSalidaPresumida() ? "SI" : "NO")),
            // De que equipo salio cada marca (V031). En columnas separadas: en una planilla
            // se filtra por puerta, no se lee un texto con las dos adentro.
            csv(f.getEquipoEntrada()),
            csv(f.getEquipoSalida()),
            csv(f.getMinutosProgramados()),
            // Vacio, no cero: cero dice "no dio la clase" y vacio dice "no tenemos el dato".
            // En la planilla esa diferencia decide si el promedio de horas es una mentira.
            csv(f.getMinutosEfectivos()),
            // El desvio, desarmado en sus dos mitades. En columnas separadas y no en un texto
            // como "12 min tarde" porque el CSV se abre para sumar y filtrar, no para leer.
            csv(f.getMinutosTarde()),
            csv(f.getMinutosSalidaAnticipada()),
            csv(f.getMinutosFueraDeClase()),
            csv(f.getMinutosTarde() == null ? "" : (f.isLlegadaDentroDelMargen() ? "SI" : "NO")),
            csv(f.getMinutosSalidaAnticipada() == null
                ? "" : (f.isSalidaDentroDelMargen() ? "SI" : "NO")),
            csv(f.getEstado()),
            csv(f.getMetodo()),
            csv(f.getMotivoManual()),
            csv(f.getDetalleManual()),
            csv(f.getUsuarioRegistrador()),
            csv(f.isJustificada() ? "SI" : "NO"),
            csv(f.getMotivoJustificacion())
        ));
    }

    /**
     * Escapa un campo CSV con separador ';' y comillas dobles (RFC 4180 con coma → ';'), y
     * antes desarma lo que la planilla leería como fórmula.
     *
     * <p>Solo los textos: un número no puede arrancar una fórmula, y anteponerle una comilla
     * lo convertiría en texto, que en una planilla es dejar de poder sumarlo. Por eso las
     * columnas de minutos y la confianza salen intactas.
     */
    private static String csv(Object v) {
        if (v == null) return "";
        String s = v instanceof CharSequence ? sinFormula(v.toString()) : String.valueOf(v);
        // Si tiene ; " o salto de línea, encerrar entre comillas y duplicar las " internas.
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /**
     * Desactiva el texto que una planilla ejecutaría al abrir el archivo (CWE-1236).
     *
     * <p>Excel y LibreOffice no abren el CSV como texto: una celda que empieza con '=', '+',
     * '-' o '@' es una fórmula y se evalúa sola. Y buena parte de lo que va en este reporte
     * lo escribe gente —el nombre de una materia, el detalle de una carga manual, el nombre
     * que se le puso a una cámara—, así que alcanza con llamar a una materia
     * {@code =HYPERLINK("http://...")} para que eso corra en la máquina de quien descarga el
     * reporte, que no es la misma persona que lo escribió.
     *
     * <p>Se antepone una comilla simple, que es lo que la planilla entiende como "esto es
     * texto". <b>La comilla se ve en la celda</b>, y es a propósito: un dato con un carácter
     * de más se explica, uno que se ejecuta no. Solo se toca lo que empieza con alguno de
     * esos caracteres; el resto del reporte sale exactamente igual que antes.
     */
    private static String sinFormula(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return ARRANCA_FORMULA.indexOf(s.charAt(0)) >= 0 ? "'" + s : s;
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
