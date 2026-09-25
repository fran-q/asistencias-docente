package edu.cent35.asistencias.dto;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.BloquePresencia;
import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoAsistencia;
import edu.cent35.asistencias.model.EstadoCierre;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.MetodoAsistencia;
import edu.cent35.asistencias.model.OrigenMarca;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre el desvío de cada clase: cuántos minutos se perdieron por llegar tarde, cuántos por
 * irse antes, y si cada uno entra en la tolerancia del horario.
 *
 * <p><b>El reparto tiene que cerrar:</b> dictados + tarde + salida anticipada = programados.
 * Sin esa cuenta, el reporte podría decir que faltaron veinte minutos y que se dictaron todos,
 * las dos cosas a la vez.
 *
 * <p>La tolerancia de estas clases es de 15 minutos, que es la que trae el horario por defecto.
 */
class DesvioDeClaseTest {

    private static final LocalTime INICIO = LocalTime.of(18, 0);
    private static final LocalTime FIN    = LocalTime.of(20, 0);

    @Test
    @DisplayName("un retraso dentro de la tolerancia se cuenta igual, pero no se marca")
    void tardeDentroDelMargen() {
        AsistenciaReporteRowDto f = fila(LocalTime.of(18, 7), FIN);

        assertThat(f.getMinutosTarde()).isEqualTo(7);
        assertThat(f.isLlegadaDentroDelMargen())
            .as("siete minutos entran en la tolerancia: se informa, no se marca")
            .isTrue();
        assertThat(f.getMinutosEfectivos()).isEqualTo(113);
    }

    @Test
    @DisplayName("pasada la tolerancia, el retraso queda marcado")
    void tardeFueraDelMargen() {
        AsistenciaReporteRowDto f = fila(LocalTime.of(18, 30), FIN);

        assertThat(f.getMinutosTarde()).isEqualTo(30);
        assertThat(f.isLlegadaDentroDelMargen()).isFalse();
    }

    @Test
    @DisplayName("irse un poco antes se cuenta y entra en la tolerancia")
    void salidaDentroDelMargen() {
        AsistenciaReporteRowDto f = fila(INICIO, LocalTime.of(19, 50));

        assertThat(f.getMinutosSalidaAnticipada()).isEqualTo(10);
        assertThat(f.isSalidaDentroDelMargen()).isTrue();
        assertThat(f.getMinutosEfectivos()).isEqualTo(110);
    }

    @Test
    @DisplayName("irse mucho antes queda marcado")
    void salidaFueraDelMargen() {
        AsistenciaReporteRowDto f = fila(INICIO, LocalTime.of(19, 0));

        assertThat(f.getMinutosSalidaAnticipada()).isEqualTo(60);
        assertThat(f.isSalidaDentroDelMargen()).isFalse();
    }

    @Test
    @DisplayName("el reparto cierra: dictados + tarde + salida anticipada = programados")
    void elRepartoCierra() {
        LocalTime[][] casos = {
            {LocalTime.of(18, 0),  LocalTime.of(20, 0)},    // clase entera
            {LocalTime.of(18, 20), LocalTime.of(19, 30)},   // tarde y se fue antes
            {LocalTime.of(17, 0),  LocalTime.of(21, 0)},    // jornada mas larga que la clase
            {LocalTime.of(8, 0),   LocalTime.of(10, 0)},    // jornada que no toca la clase
            {LocalTime.of(21, 0),  LocalTime.of(23, 0)},    // jornada posterior a la clase
        };

        for (LocalTime[] caso : casos) {
            AsistenciaReporteRowDto f = fila(caso[0], caso[1]);
            assertThat(f.getMinutosEfectivos() + f.getMinutosTarde() + f.getMinutosSalidaAnticipada())
                .as("jornada de %s a %s", caso[0], caso[1])
                .isEqualTo(f.getMinutosProgramados());
        }
    }

    @Test
    @DisplayName("sin jornada no se afirma ningun desvio")
    void sinJornada() {
        // Una carga manual guarda la hora en que se cargo, no la de llegada: decir "llego 40
        // minutos tarde" con ese dato seria inventarlo.
        Asistencia a = asistencia();
        a.setBloque(null);

        AsistenciaReporteRowDto f = AsistenciaReporteRowDto.from(a, null, null);

        assertThat(f.getMinutosTarde()).isNull();
        assertThat(f.getMinutosSalidaAnticipada()).isNull();
        assertThat(f.isLlegadaDentroDelMargen())
            .as("lo que la pantalla resalta es el desvio que se paso del margen, y aca no hay")
            .isTrue();
        assertThat(f.isSalidaDentroDelMargen()).isTrue();
    }

    @Test
    @DisplayName("con el docente todavia adentro hay retraso, pero no salida anticipada")
    void jornadaAbierta() {
        Asistencia a = asistencia();
        a.setBloque(BloquePresencia.builder()
            .id(1L).fecha(LocalDate.now()).horaEntrada(LocalTime.of(18, 20))
            .origenEntrada(OrigenMarca.AUTOMATICO).estadoCierre(EstadoCierre.ABIERTO)
            .build());

        AsistenciaReporteRowDto f = AsistenciaReporteRowDto.from(a, null, null);

        assertThat(f.getMinutosTarde())
            .as("a que hora llego ya se sabe, aunque todavia no se haya ido")
            .isEqualTo(20);
        assertThat(f.getMinutosSalidaAnticipada()).isNull();
    }

    // ------------------------------------------------------------------------

    private AsistenciaReporteRowDto fila(LocalTime entrada, LocalTime salida) {
        Asistencia a = asistencia();
        a.setBloque(BloquePresencia.builder()
            .id(1L).fecha(LocalDate.now())
            .horaEntrada(entrada).horaSalida(salida)
            .origenEntrada(OrigenMarca.AUTOMATICO).origenSalida(OrigenMarca.AUTOMATICO)
            .estadoCierre(EstadoCierre.CERRADO_POR_ROSTRO)
            .build());
        return AsistenciaReporteRowDto.from(a, null, null);
    }

    private Asistencia asistencia() {
        Carrera c = Carrera.builder().id(1L).codigo("CAR").nombre("Carrera").build();
        Materia m = Materia.builder().id(1L).codigo("MAT").nombre("Matemática").carrera(c).build();
        Comision com = Comision.builder().id(1L).codigo("A").materia(m).build();
        Horario h = Horario.builder().id(1L).comision(com)
            .diaSemana((byte) 1).horaInicio(INICIO).horaFin(FIN).toleranciaMin((short) 15)
            .build();
        Docente d = Docente.builder().id(1L)
            .persona(DatosDePrueba.personaConDni("12345678", "Juana", "Pérez")).build();

        return Asistencia.builder()
            .id(1L).docente(d).comision(com).horario(h)
            .fecha(LocalDate.now()).horaRegistrada(INICIO)
            .estado(EstadoAsistencia.PRESENTE).metodo(MetodoAsistencia.AUTOMATICO)
            .build();
    }
}
