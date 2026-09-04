package edu.cent35.asistencias.service;

import edu.cent35.asistencias.dto.ConfirmacionIdentidad;
import edu.cent35.asistencias.dto.IdentificacionResultadoDto;
import edu.cent35.asistencias.dto.PaseAsistenciaResultadoDto;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.BloquePresencia;
import edu.cent35.asistencias.model.EstadoSalida;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Materia;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Orquesta el flujo del pase: identifica al docente y, solo si esa identidad se sostiene unos
 * segundos, registra su presencia. Es la fachada del endpoint
 * {@code POST /asistencia/pase/marcar}, que el navegador llama en bucle.
 * <p>
 * <b>Una misma pasada por la cámara puede ser una entrada o una salida</b>, y esta clase no lo
 * decide: se lo pregunta a {@link BloquePresenciaService}, que lo deduce de si el docente tiene
 * un bloque abierto (ADR-0017). Acá solo se arma la respuesta que ve el operador.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaseAsistenciaService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final IdentificacionFacialService identificacionService;
    private final BloquePresenciaService bloquePresenciaService;
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
            // Cuando hay varias personas no viene recuadro: dibujarlo sobre una sola de
            // ellas daria a entender que el sistema eligio a esa, que es justo lo contrario
            // de lo que esta diciendo.
            if (id.x() == null) {
                return PaseAsistenciaResultadoDto.rechazadoSinRecuadro(id.mensaje());
            }
            return PaseAsistenciaResultadoDto.noReconocido(
                id.distancia() == null ? 0.0 : id.distancia(),
                id.mensaje(),
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

        // La misma pasada por la camara significa una cosa u otra segun el estado del docente:
        // sin bloque abierto es su entrada, con bloque abierto es su salida. Quien lo decide es
        // el servicio de bloques, no esta clase ni el operador (ADR-0017).
        BloquePresenciaService.ResultadoPresencia presencia = bloquePresenciaService.registrar(
            id.docenteId(), id.modeloFacialId(), id.distancia(), LocalDateTime.now());

        if (!presencia.registrada()) {
            return PaseAsistenciaResultadoDto.reconocidoSinClase(
                id.docenteId(), id.docenteNombre(),
                id.distancia(),
                id.x(), id.y(), id.ancho(), id.alto(),
                presencia.motivo());
        }

        long msTotal = (System.nanoTime() - inicioNs) / 1_000_000;
        log.info("RNF01 pase completo: docente={} tipo={} clases={} msTotal={}",
                 id.docenteId(), presencia.tipo(), presencia.clasesImputadas(), msTotal);

        return presencia.tipo() == BloquePresenciaService.TipoDeMarca.SALIDA
            ? armarSalida(id, presencia)
            : armarEntrada(id, presencia);
    }

    // Respuesta de una entrada: el estado con el que llegó y qué clase está dando.
    private PaseAsistenciaResultadoDto armarEntrada(IdentificacionResultadoDto id,
                                                    BloquePresenciaService.ResultadoPresencia p) {
        Asistencia a = p.asistencia();
        return PaseAsistenciaResultadoDto.entradaRegistrada(
            id.docenteId(), id.docenteNombre(), id.distancia(),
            id.x(), id.y(), id.ancho(), id.alto(),
            a == null ? "PRESENTE" : a.getEstado().name(),
            a == null ? null : armarClaseLabel(a));
    }

    // Respuesta de una salida: cuánto estuvo y cuántas clases le quedaron imputadas.
    private PaseAsistenciaResultadoDto armarSalida(IdentificacionResultadoDto id,
                                                   BloquePresenciaService.ResultadoPresencia p) {
        BloquePresencia b = p.bloque();
        int clases = p.clasesImputadas();
        String resumen = b.getHoraEntrada().format(HM) + " a " + b.getHoraSalida().format(HM)
            + " - " + clases + (clases == 1 ? " clase" : " clases");
        return PaseAsistenciaResultadoDto.salidaRegistrada(
            id.docenteId(), id.docenteNombre(), id.distancia(),
            id.x(), id.y(), id.ancho(), id.alto(),
            resumen,
            b.getEstadoSalida() == EstadoSalida.ANTICIPADA);
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
