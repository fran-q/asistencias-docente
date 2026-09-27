package edu.cent35.asistencias.service;

import edu.cent35.asistencias.dto.ConfirmacionIdentidad;
import edu.cent35.asistencias.dto.IdentificacionResultadoDto;
import edu.cent35.asistencias.dto.KioscoResultadoDto;
import edu.cent35.asistencias.dto.MarcaSinCamaraResultadoDto;
import edu.cent35.asistencias.dto.PaseAsistenciaResultadoDto;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.BloquePresencia;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoSalida;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.PuestoCaptura;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
    private final DocenteService docenteService;

    /**
     * De acá sale el instante de cada marca.
     *
     * <p>Es un campo y no una llamada suelta a {@code now()} para que un test pueda fijarlo:
     * las clases se guardan como {@link java.time.LocalTime} y un test que arme la suya
     * alrededor de la hora de la máquina se rompe cerca de medianoche, cuando restarle media
     * hora al reloj cae en el día anterior. Con el reloj fijo, la misma prueba vale a
     * cualquier hora. Mismo patrón que {@code CicloLectivoService} y
     * {@code CodigoVerificacionService}.
     */
    private Clock clock = Clock.systemDefaultZone();

    // Pasa asistencia desde un frame: identifica, exige que la identidad se sostenga y marca.
    /**
     * En qué quedó una pasada por la cámara, sin decidir todavía cómo se muestra.
     *
     * <p>Existe porque el pase y el kiosco comparten el pipeline entero y se separan recién en
     * la respuesta: uno nombra al docente y el otro no (RF-87). Con dos copias de la
     * orquestación, cualquier cambio en la ventana de confirmación o en el orden de las
     * guardas habría que hacerlo dos veces, y la segunda se olvida.
     *
     * @param identificacion lo que devolvió el reconocimiento
     * @param confirmacion   el avance de la ventana; null si no se llegó a evaluar
     * @param presencia      lo que se registró; null si no se llegó a intentar
     */
    private record Paso(IdentificacionResultadoDto identificacion,
                        VentanaConfirmacionService.Estado confirmacion,
                        BloquePresenciaService.ResultadoPresencia presencia) {

        boolean sinRostro()   { return !identificacion.rostroDetectado(); }
        boolean noReconocido() { return !identificacion.reconocido(); }
        boolean esperando()   { return confirmacion != null && !confirmacion.confirmado(); }
        boolean registrada()  { return presencia != null && presencia.registrada(); }
    }

    // Corre el pipeline completo: identifica, exige que la identidad se sostenga y registra.
    private Paso procesar(byte[] imagenBytes, ConfirmacionIdentidad confirmacion,
                          PuestoCaptura puesto) {
        IdentificacionResultadoDto id = identificacionService.identificar(imagenBytes);

        if (!id.rostroDetectado() || !id.reconocido()) {
            // Sin nadie en el cuadro, o con alguien que no se pudo identificar, la racha no
            // tiene sentido: quien vuelva empieza de cero.
            ventanaConfirmacion.cortar(confirmacion);
            return new Paso(id, null, null);
        }

        // Antes de tocar el registro de asistencia, la identidad tiene que sostenerse. Un
        // reconocimiento suelto es demasiado fragil ante un cambio de luz, y una marca
        // equivocada queda asentada como si fuera un hecho.
        VentanaConfirmacionService.Estado confirmado =
            ventanaConfirmacion.registrar(confirmacion, id.docenteId(), System.currentTimeMillis());

        if (!confirmado.confirmado()) {
            return new Paso(id, confirmado, null);
        }

        // Confirmada la identidad, la racha se corta: la proxima persona arranca limpia.
        ventanaConfirmacion.cortar(confirmacion);

        // La misma pasada por la camara significa una cosa u otra segun el estado del docente:
        // sin bloque abierto es su entrada, con bloque abierto es su salida. Quien lo decide es
        // el servicio de bloques, no esta clase ni el operador (ADR-0017).
        BloquePresenciaService.ResultadoPresencia presencia = bloquePresenciaService.registrar(
            id.docenteId(), id.modeloFacialId(), id.distancia(), LocalDateTime.now(clock),
            puesto);

        return new Paso(id, confirmado, presencia);
    }

    // Pase con un administrador presente: la respuesta nombra al docente y la clase.
    public PaseAsistenciaResultadoDto pasar(byte[] imagenBytes, ConfirmacionIdentidad confirmacion,
                                            PuestoCaptura puesto) {
        long inicioNs = System.nanoTime();
        Paso paso = procesar(imagenBytes, confirmacion, puesto);
        IdentificacionResultadoDto id = paso.identificacion();

        if (paso.sinRostro()) {
            return PaseAsistenciaResultadoDto.sinRostro();
        }
        if (paso.noReconocido()) {
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
        if (paso.esperando()) {
            return PaseAsistenciaResultadoDto.confirmando(
                id.distancia(), id.x(), id.y(), id.ancho(), id.alto(),
                paso.confirmacion().progreso(), paso.confirmacion().objetivo());
        }
        if (!paso.registrada()) {
            return PaseAsistenciaResultadoDto.reconocidoSinClase(
                id.docenteId(), id.docenteNombre(),
                id.distancia(),
                id.x(), id.y(), id.ancho(), id.alto(),
                paso.presencia().motivo());
        }

        long msTotal = (System.nanoTime() - inicioNs) / 1_000_000;
        log.info("RNF01 pase completo: docente={} tipo={} clases={} msTotal={}",
                 id.docenteId(), paso.presencia().tipo(), paso.presencia().clasesImputadas(),
                 msTotal);

        return paso.presencia().tipo() == BloquePresenciaService.TipoDeMarca.SALIDA
            ? armarSalida(id, paso.presencia())
            : armarEntrada(id, paso.presencia());
    }

    /**
     * Pase en una pantalla desatendida: la respuesta lleva <b>apellido</b> y nada más (RF-87).
     *
     * <p>El nombre completo no se recorta en el navegador: no llega. Lo que no se muestra
     * tampoco se envía, porque una vez que el dato salió del servidor ya está fuera de
     * control — basta abrir las herramientas del navegador para verlo.
     */
    public KioscoResultadoDto pasarEnKiosco(byte[] imagenBytes, ConfirmacionIdentidad confirmacion,
                                            PuestoCaptura puesto) {
        long inicioNs = System.nanoTime();
        Paso paso = procesar(imagenBytes, confirmacion, puesto);
        IdentificacionResultadoDto id = paso.identificacion();

        if (paso.sinRostro()) {
            return KioscoResultadoDto.sinRostro();
        }
        if (paso.noReconocido()) {
            // El motivo explica que paso, pero sin nombrar a nadie: decir a quien se parecio
            // seria peor que no decir nada en una pantalla que mira cualquiera.
            return KioscoResultadoDto.noReconocido(
                id.mensaje(), id.x(), id.y(), id.ancho(), id.alto());
        }
        if (paso.esperando()) {
            return KioscoResultadoDto.confirmando(
                id.x(), id.y(), id.ancho(), id.alto(),
                paso.confirmacion().progreso(), paso.confirmacion().objetivo());
        }
        if (!paso.registrada()) {
            return KioscoResultadoDto.rechazada(
                id.docenteApellido(), paso.presencia().motivo(),
                id.x(), id.y(), id.ancho(), id.alto());
        }

        long msTotal = (System.nanoTime() - inicioNs) / 1_000_000;
        log.info("RNF01 kiosco completo: docente={} tipo={} clases={} puesto={} msTotal={}",
                 id.docenteId(), paso.presencia().tipo(), paso.presencia().clasesImputadas(),
                 puesto == null ? null : puesto.getId(), msTotal);

        boolean esSalida = paso.presencia().tipo() == BloquePresenciaService.TipoDeMarca.SALIDA;
        BloquePresencia b = paso.presencia().bloque();
        String detalle = esSalida
            ? b.getHoraEntrada().format(HM) + " a " + b.getHoraSalida().format(HM)
            : b.getHoraEntrada().format(HM);

        return KioscoResultadoDto.registrada(
            id.docenteApellido(), esSalida ? "SALIDA" : "ENTRADA", detalle,
            id.x(), id.y(), id.ancho(), id.alto(),
            (esSalida ? "Salida registrada: " : "Entrada registrada: ") + detalle);
    }

    /**
     * Registra la marca de un docente <b>sin pasar por la cámara</b>, a pedido del admin que
     * está atendiendo el pase (V029).
     *
     * <p>Es la salida para cuando el reconocimiento no sirve y el docente está ahí parado:
     * rostro sin registrar, cámara que falla, luz que no da, o consentimiento revocado. Antes
     * había que ir a la carga manual, que vive en otra pantalla —o sea soltando la cámara— y
     * que no abre bloque: quedaba la asistencia de esa clase, sin hora de salida ni permanencia.
     *
     * <p><b>Acá no se elige entre entrada y salida.</b> Lo decide el estado del docente, igual
     * que en una pasada por la cámara (ADR-0017). Lo único que se agrega es por qué hubo que
     * hacerlo a mano, y queda con el nombre de quien lo hizo.
     *
     * <p>Va transaccional entero, y no solo la escritura: con {@code open-in-view} apagado, el
     * nombre del docente y la clase imputada se leen recién al armar la respuesta, y fuera de
     * la transacción esas lecturas fallan.
     */
    @Transactional
    public MarcaSinCamaraResultadoDto marcarSinCamara(Long docenteId, Short motivoId,
                                                      String detalle, Long usuarioActualId,
                                                      PuestoCaptura puesto) {
        // Antes de tocar nada: si el docente no es de esta institución, esto tira y no sigue.
        Docente docente = docenteService.buscarPorId(docenteId);
        String nombre = docente.getNombreCompleto();

        BloquePresenciaService.ResultadoPresencia p = bloquePresenciaService.registrarSinCamara(
            docenteId, motivoId, detalle, usuarioActualId, LocalDateTime.now(clock), puesto);

        if (!p.registrada()) {
            return MarcaSinCamaraResultadoDto.rechazada(nombre, p.motivo());
        }

        log.info("Marca sin camara: docente={} tipo={} clases={} admin={} puesto={}",
                 docenteId, p.tipo(), p.clasesImputadas(), usuarioActualId,
                 puesto == null ? null : puesto.getId());

        if (p.tipo() == BloquePresenciaService.TipoDeMarca.SALIDA) {
            return MarcaSinCamaraResultadoDto.salida(
                nombre, resumenDeSalida(p.bloque(), p.clasesImputadas()));
        }

        // Sin asistencia imputada el bloque abrió en la ventana previa al inicio de la clase:
        // hay entrada, todavía no hay clase que marcar. Se informa la hora y nada más, porque
        // decir un estado de asistencia ahí estaría inventando una clase que no empezó.
        Asistencia a = p.asistencia();
        return MarcaSinCamaraResultadoDto.entrada(nombre, a == null
            ? p.bloque().getHoraEntrada().format(HM)
            : a.getEstado().name() + " en " + armarClaseLabel(a));
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
        String resumen = resumenDeSalida(b, p.clasesImputadas());
        return PaseAsistenciaResultadoDto.salidaRegistrada(
            id.docenteId(), id.docenteNombre(), id.distancia(),
            id.x(), id.y(), id.ancho(), id.alto(),
            resumen,
            b.getEstadoSalida() == EstadoSalida.ANTICIPADA);
    }

    // Cuanto estuvo y cuantas clases le quedaron imputadas: "18:00 a 20:00 - 2 clases".
    // Lo comparten la salida por rostro y la cargada a mano, que informan lo mismo.
    private static String resumenDeSalida(BloquePresencia b, int clases) {
        return b.getHoraEntrada().format(HM) + " a " + b.getHoraSalida().format(HM)
            + " - " + clases + (clases == 1 ? " clase" : " clases");
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
