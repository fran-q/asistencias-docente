package edu.cent35.asistencias.dto;

import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoAsistencia;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.MetodoAsistencia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El dia que muestra el reporte (y que se exporta al CSV) tiene que salir de
 * la FECHA de la marca, no del dia programado del horario. Mientras coinciden
 * el bug es invisible; estos casos los hacen diverger a proposito.
 */
class AsistenciaReporteRowDtoTest {

    @Test
    @DisplayName("diaSemana sale de la fecha, no del dia del horario")
    void diaSemanaSaleDeLaFecha() {
        // Horario de lunes, pero la marca quedo cargada con fecha de un sabado.
        Asistencia a = asistenciaCon(LocalDate.of(2026, 7, 25), (byte) 1);

        AsistenciaReporteRowDto fila = AsistenciaReporteRowDto.from(a, null, null);

        assertThat(fila.getFecha()).isEqualTo(LocalDate.of(2026, 7, 25));
        assertThat(fila.getDiaSemana()).isEqualTo("Sábado");
    }

    @Test
    @DisplayName("diaSemana usa la etiqueta acentuada del enum")
    void diaSemanaConAcento() {
        // 2026-07-22 fue miercoles.
        Asistencia a = asistenciaCon(LocalDate.of(2026, 7, 22), (byte) 3);

        AsistenciaReporteRowDto fila = AsistenciaReporteRowDto.from(a, null, null);

        assertThat(fila.getDiaSemana()).isEqualTo("Miércoles");
    }

    // Arma una asistencia con la fecha y el día de horario que pida el caso.
    private Asistencia asistenciaCon(LocalDate fecha, byte diaSemanaHorario) {
        Materia materia = Materia.builder()
            .id(1L).codigo("BIO-201").nombre("Biología").build();
        Comision comision = Comision.builder()
            .id(2L).codigo("A").materia(materia).activo(true).build();
        Horario horario = Horario.builder()
            .id(3L).comision(comision)
            .diaSemana(diaSemanaHorario)
            .horaInicio(LocalTime.of(8, 0))
            .horaFin(LocalTime.of(10, 0))
            .toleranciaMin((short) 15)
                        .activo(true)
            .build();
        Docente docente = Docente.builder()
            .id(4L).dni("30123456").nombre("Juan").apellido("Pérez").activo(true).build();

        return Asistencia.builder()
            .id(5L).docente(docente).comision(comision).horario(horario)
            .fecha(fecha)
            .horaRegistrada(LocalTime.of(8, 5))
            .estado(EstadoAsistencia.TARDE)
            .metodo(MetodoAsistencia.MANUAL)
            .build();
    }
}
