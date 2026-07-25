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
 * Orquesta el flujo de pase de asistencia: identifica al docente y, si lo
 * reconoce, intenta marcar asistencia para la clase en curso.
 * <p>
 * Es la fachada que usa el endpoint {@code POST /asistencia/pase/marcar}
 * desde el loop continuo del navegador.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaseAsistenciaService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final IdentificacionFacialService identificacionService;
    private final AsistenciaService asistenciaService;

    // Pasa asistencia desde un frame: identifica y marca, midiendo el tiempo total (RNF-01).
    public PaseAsistenciaResultadoDto pasar(byte[] imagenBytes) {
        long inicioNs = System.nanoTime();
        IdentificacionResultadoDto id = identificacionService.identificar(imagenBytes);

        if (!id.rostroDetectado()) {
            return PaseAsistenciaResultadoDto.sinRostro();
        }
        if (!id.reconocido()) {
            return PaseAsistenciaResultadoDto.noReconocido(
                id.distancia() == null ? 0.0 : id.distancia(),
                id.x(), id.y(), id.ancho(), id.alto());
        }

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
