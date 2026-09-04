package edu.cent35.asistencias.service;

import edu.cent35.asistencias.dto.ConfirmacionIdentidad;
import edu.cent35.asistencias.dto.IdentificacionResultadoDto;
import edu.cent35.asistencias.dto.PaseAsistenciaResultadoDto;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.BloquePresencia;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.EstadoAsistencia;
import edu.cent35.asistencias.model.EstadoSalida;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Materia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre la respuesta del pase una vez confirmada la identidad (RF-17, RF-20): que una entrada y
 * una salida no se vean iguales, y que un rechazo no se confunda con una marca.
 * <p>
 * La identificación en sí no se prueba acá —eso es {@code IdentificacionFacialServiceTest}—:
 * esta clase solo decide qué mostrarle al operador con lo que le devuelven los otros dos
 * servicios.
 */
@ExtendWith(MockitoExtension.class)
class PaseAsistenciaServiceTest {

    private static final Long DOCENTE_ID = 50L;
    private static final String NOMBRE = "Pérez, Juana";

    @Mock private IdentificacionFacialService identificacionService;
    @Mock private BloquePresenciaService bloquePresenciaService;
    @Mock private VentanaConfirmacionService ventanaConfirmacion;

    @InjectMocks private PaseAsistenciaService service;

    @Test
    @DisplayName("sin rostro en el cuadro se corta la racha y no se toca el registro")
    void sinRostro() {
        when(identificacionService.identificar(any())).thenReturn(IdentificacionResultadoDto.sinRostro());

        PaseAsistenciaResultadoDto r = service.pasar(new byte[]{1}, new ConfirmacionIdentidad());

        assertThat(r.rostroDetectado()).isFalse();
        assertThat(r.tipoDeMarca()).isNull();
        verify(ventanaConfirmacion).cortar(any());
        verify(bloquePresenciaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("mientras la identidad no se sostiene no se registra nada")
    void confirmando() {
        identificado();
        when(ventanaConfirmacion.registrar(any(), any(), anyLongArg()))
            .thenReturn(new VentanaConfirmacionService.Estado(false, 1200L, 3000L));

        PaseAsistenciaResultadoDto r = service.pasar(new byte[]{1}, new ConfirmacionIdentidad());

        assertThat(r.confirmando()).isTrue();
        assertThat(r.tipoDeMarca()).isNull();
        // El nombre no viaja hasta que la identidad está confirmada: mostrarlo antes es lo que
        // hace que alguien vea el nombre equivocado durante un parpadeo.
        assertThat(r.docenteNombre()).isNull();
        verify(bloquePresenciaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("una entrada se anuncia como entrada, con el estado y la clase")
    void entrada() {
        identificado();
        confirmado();
        Asistencia a = asistencia(EstadoAsistencia.PRESENTE);
        when(bloquePresenciaService.registrar(any(), any(), any(), any()))
            .thenReturn(BloquePresenciaService.ResultadoPresencia.entrada(bloque(), 1, a));

        PaseAsistenciaResultadoDto r = service.pasar(new byte[]{1}, new ConfirmacionIdentidad());

        assertThat(r.tipoDeMarca()).isEqualTo("ENTRADA");
        assertThat(r.asistenciaMarcada()).isTrue();
        assertThat(r.estadoAsistencia()).isEqualTo("PRESENTE");
        assertThat(r.mensaje()).startsWith("Entrada registrada:");
        assertThat(r.claseLabel()).contains("Matemática");
        // Un bloque abierto no se vuelve a abrir: la segunda pasada es la salida, no un
        // duplicado, así que "ya estaba" nunca aplica a una entrada.
        assertThat(r.yaEstaba()).isFalse();
    }

    @Test
    @DisplayName("una salida se anuncia como salida, con la permanencia y las clases")
    void salida() {
        identificado();
        confirmado();
        when(bloquePresenciaService.registrar(any(), any(), any(), any()))
            .thenReturn(BloquePresenciaService.ResultadoPresencia.salida(
                bloqueCerrado(LocalTime.of(18, 2), LocalTime.of(22, 5), EstadoSalida.EN_HORA), 2));

        PaseAsistenciaResultadoDto r = service.pasar(new byte[]{1}, new ConfirmacionIdentidad());

        assertThat(r.tipoDeMarca()).isEqualTo("SALIDA");
        assertThat(r.mensaje()).isEqualTo("Salida registrada: 18:02 a 22:05 - 2 clases");
        // El estado describe cómo llegó, y al irse eso ya está decidido.
        assertThat(r.estadoAsistencia()).isNull();
    }

    @Test
    @DisplayName("una salida anticipada lo dice en el mensaje")
    void salidaAnticipada() {
        identificado();
        confirmado();
        when(bloquePresenciaService.registrar(any(), any(), any(), any()))
            .thenReturn(BloquePresenciaService.ResultadoPresencia.salida(
                bloqueCerrado(LocalTime.of(18, 0), LocalTime.of(19, 0), EstadoSalida.ANTICIPADA), 1));

        PaseAsistenciaResultadoDto r = service.pasar(new byte[]{1}, new ConfirmacionIdentidad());

        assertThat(r.mensaje()).contains("anticipada");
        assertThat(r.mensaje()).contains("1 clase");
    }

    @Test
    @DisplayName("un rechazo se muestra con su motivo y sin marca")
    void rechazada() {
        identificado();
        confirmado();
        when(bloquePresenciaService.registrar(any(), any(), any(), any()))
            .thenReturn(BloquePresenciaService.ResultadoPresencia.rechazada(
                "Todavía no pasaron 10 minutos desde que Pérez, Juana registró su entrada."));

        PaseAsistenciaResultadoDto r = service.pasar(new byte[]{1}, new ConfirmacionIdentidad());

        assertThat(r.asistenciaMarcada()).isFalse();
        assertThat(r.tipoDeMarca()).isNull();
        assertThat(r.mensaje()).contains("10 minutos");
        // Se reconoció a la persona: el recuadro se dibuja igual, lo que no hay es marca.
        assertThat(r.reconocido()).isTrue();
        assertThat(r.docenteNombre()).isEqualTo(NOMBRE);
    }

    // ------------------------------------------------------------------------

    private void identificado() {
        when(identificacionService.identificar(any())).thenReturn(
            IdentificacionResultadoDto.match(DOCENTE_ID, NOMBRE, 9L, 42.0, 10, 20, 100, 100));
    }

    private void confirmado() {
        when(ventanaConfirmacion.registrar(any(), any(), anyLongArg()))
            .thenReturn(new VentanaConfirmacionService.Estado(true, 3000L, 3000L));
    }

    // El instante de la ventana es un long primitivo: hace falta el matcher del tipo exacto.
    private static long anyLongArg() {
        return org.mockito.ArgumentMatchers.anyLong();
    }

    private BloquePresencia bloque() {
        return BloquePresencia.builder()
            .id(300L).horaEntrada(LocalTime.of(18, 2)).build();
    }

    private BloquePresencia bloqueCerrado(LocalTime entrada, LocalTime salida, EstadoSalida estado) {
        return BloquePresencia.builder()
            .id(300L).horaEntrada(entrada).horaSalida(salida).estadoSalida(estado).build();
    }

    private Asistencia asistencia(EstadoAsistencia estado) {
        Materia m = Materia.builder().id(80L).codigo("MAT").nombre("Matemática").build();
        Comision c = Comision.builder().id(70L).codigo("A").materia(m).build();
        Horario h = Horario.builder().id(60L)
            .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(20, 0)).build();
        return Asistencia.builder()
            .id(500L).comision(c).horario(h).estado(estado).build();
    }
}
