package edu.cent35.asistencias.service;

import edu.cent35.asistencias.dto.IdentificacionResultadoDto;
import edu.cent35.asistencias.dto.PaseAsistenciaResultadoDto;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Materia;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Orquesta el flujo del pase: identifica al docente y, solo si esa identidad se sostiene
 * unos segundos, marca su asistencia. Es la fachada del endpoint
 * {@code POST /asistencia/pase/marcar}, que el navegador llama en bucle.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaseAsistenciaService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final IdentificacionFacialService identificacionService;
    private final AsistenciaService asistenciaService;
    private final VentanaConfirmacionService ventanaConfirmacion;

    // Pasa asistencia desde un frame: identifica, exige que la identidad se sostenga y marca.
    public PaseAsistenciaResultadoDto pasar(byte[] imagenBytes, ConfirmacionIdentidad confirmacion) {
        long inicioNs = System.nanoTime();
        IdentificacionResultadoDto id = identificacionService.identificar(imagenBytes);

        if (!id.rostroDetectado()) {
            // Sin nadie en el cuadro la racha no tiene sentido: quien vuelva empieza de cero.
            ventanaConfirmacion.cortar(confirmacion);
            return PaseAsistenciaResultadoDto.sinRostro();
        }
        if (!id.reconocido()) {
            ventanaConfirmacion.cortar(confirmacion);
            return PaseAsistenciaResultadoDto.noReconocido(
                id.distancia() == null ? 0.0 : id.distancia(),
                id.x(), id.y(), id.ancho(), id.alto());
        }

        // Antes de tocar el registro de asistencia, la identidad tiene que sostenerse. Un
        // reconocimiento suelto es demasiado fragil ante un cambio de luz, y una marca
        // equivocada queda asentada como si fuera un hecho.
        VentanaConfirmacionService.Estado confirmado =
            ventanaConfirmacion.registrar(confirmacion, id.docenteId(), System.currentTimeMillis());

        if (!confirmado.confirmado()) {
            return PaseAsistenciaResultadoDto.confirmando(
                id.distancia(), id.x(), id.y(), id.ancho(), id.alto(),
                confirmado.progreso(), confirmado.objetivo());
        }

        // Confirmada la identidad, la racha se corta: la proxima persona arranca limpia.
        ventanaConfirmacion.cortar(confirmacion);

        AsistenciaService.ResultadoMarca marca = asistenciaService.marcarAutomatica(
            id.docenteId(), id.modeloFacialId(), id.distancia());

        if (!marca.marcada()) {
            return PaseAsistenciaResultadoDto.reconocidoSinClase(
                id.docenteId(), id.docenteNombre(),
                id.distancia(),
                id.x(), id.y(), id.ancho(), id.alto(),
                marca.motivoNoMarca());
        }

        Asistencia a = marca.asistencia();
        String claseLabel = armarClaseLabel(a);
        long msTotal = (System.nanoTime() - inicioNs) / 1_000_000;
        log.info("RNF01 pase completo: docente={} estado={} yaEstaba={} msTotal={}",
                 id.docenteId(), a.getEstado(), marca.yaEstaba(), msTotal);
        return PaseAsistenciaResultadoDto.marcado(
            id.docenteId(), id.docenteNombre(), id.distancia(),
            id.x(), id.y(), id.ancho(), id.alto(),
            marca.yaEstaba(),
            a.getEstado().name(),
            claseLabel);
    }

    // Construye un label legible para la clase: "Comisión A - Matemática (18:00-20:00)".
    private String armarClaseLabel(Asistencia a) {
        Horario h = a.getHorario();
        Materia m = a.getComision().getMateria();
        return "Comisión " + a.getComision().getCodigo()
            + " - " + m.getNombre()
            + " (" + h.getHoraInicio().format(HM) + "-" + h.getHoraFin().format(HM) + ")";
    }
}
