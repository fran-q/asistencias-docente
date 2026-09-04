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
 * Cubre los minutos dictados que muestra el reporte (RF-27 a RF-29): la intersección entre la
 * permanencia del docente y la franja de la clase.
 * <p>
 * El caso que justifica el cálculo es el del docente con varias clases seguidas: su bloque
 * cubre toda la jornada, y si cada clase contara la permanencia completa en vez de su
 * intersección, el reporte diría que dictó el triple de lo que dictó.
 */
class HorasEfectivasTest {

    @Test
    @DisplayName("una clase cubierta de punta a punta suma sus minutos completos")
    void claseCubiertaEntera() {
        AsistenciaReporteRowDto f = fila(
            LocalTime.of(18, 0), LocalTime.of(20, 0),   // clase
            LocalTime.of(18, 0), LocalTime.of(20, 0),   // bloque
            EstadoAsistencia.PRESENTE);

        assertThat(f.getMinutosProgramados()).isEqualTo(120);
        assertThat(f.getMinutosEfectivos()).isEqualTo(120);
    }

    @Test
    @DisplayName("llegar tarde descuenta desde la entrada")
    void llegoTarde() {
        AsistenciaReporteRowDto f = fila(
            LocalTime.of(18, 0), LocalTime.of(20, 0),
            LocalTime.of(18, 30), LocalTime.of(20, 0),
            EstadoAsistencia.TARDE);

        assertThat(f.getMinutosEfectivos()).isEqualTo(90);
    }

    @Test
    @DisplayName("irse antes descuenta hasta la salida")
    void seFueAntes() {
        AsistenciaReporteRowDto f = fila(
            LocalTime.of(18, 0), LocalTime.of(20, 0),
            LocalTime.of(18, 0), LocalTime.of(19, 0),
            EstadoAsistencia.PRESENTE);

        assertThat(f.getMinutosEfectivos()).isEqualTo(60);
    }

    @Test
    @DisplayName("estar más tiempo que la clase NO suma más minutos que la clase")
    void permanenciaMasLargaQueLaClase() {
        // Es el caso que importa. Un docente con tres clases seguidas tiene UN bloque que
        // cubre las seis horas; si cada clase contara la permanencia entera en vez de su
        // intersección, el reporte diría que dictó dieciocho horas.
        AsistenciaReporteRowDto f = fila(
            LocalTime.of(18, 0), LocalTime.of(20, 0),   // clase de 2 h
            LocalTime.of(8, 0), LocalTime.of(23, 0),    // jornada de 15 h
            EstadoAsistencia.PRESENTE);

        assertThat(f.getMinutosEfectivos()).isEqualTo(120);
        assertThat(f.getMinutosEfectivos()).isLessThanOrEqualTo(f.getMinutosProgramados());
    }

    @Test
    @DisplayName("una clase que el bloque no llega a tocar da cero, no negativo")
    void bloqueQueNoSolapa() {
        AsistenciaReporteRowDto f = fila(
            LocalTime.of(18, 0), LocalTime.of(20, 0),
            LocalTime.of(8, 0), LocalTime.of(10, 0),
            EstadoAsistencia.PRESENTE);

        assertThat(f.getMinutosEfectivos()).isZero();
    }

    @Test
    @DisplayName("una ausencia da cero, no vacío: se sabe que no dio la clase")
    void ausenteDaCero() {
        Asistencia a = asistencia(LocalTime.of(18, 0), LocalTime.of(20, 0),
                                  EstadoAsistencia.AUSENTE);
        a.setBloque(null);

        assertThat(AsistenciaReporteRowDto.from(a, null, null).getMinutosEfectivos()).isZero();
    }

    @Test
    @DisplayName("sin bloque el dato queda vacío, que no es lo mismo que cero")
    void sinBloqueEsVacio() {
        // Marcas anteriores a la marca de salida y cargas manuales. Cero diría que no dio la
        // clase; vacío dice que de esa fila no tenemos el dato, y en una planilla esa
        // diferencia decide si el promedio de horas es una mentira.
        Asistencia a = asistencia(LocalTime.of(18, 0), LocalTime.of(20, 0),
                                  EstadoAsistencia.PRESENTE);
        a.setBloque(null);

        assertThat(AsistenciaReporteRowDto.from(a, null, null).getMinutosEfectivos()).isNull();
    }

    @Test
    @DisplayName("con el docente todavía adentro el dato queda vacío")
    void bloqueAbiertoEsVacio() {
        Asistencia a = asistencia(LocalTime.of(18, 0), LocalTime.of(20, 0),
                                  EstadoAsistencia.PRESENTE);
        BloquePresencia abierto = BloquePresencia.builder()
            .id(1L).fecha(LocalDate.now()).horaEntrada(LocalTime.of(18, 0))
            .origenEntrada(OrigenMarca.AUTOMATICO).estadoCierre(EstadoCierre.ABIERTO)
            .build();
        a.setBloque(abierto);

        assertThat(AsistenciaReporteRowDto.from(a, null, null).getMinutosEfectivos()).isNull();
    }

    // ------------------------------------------------------------------------

    private AsistenciaReporteRowDto fila(LocalTime claseDesde, LocalTime claseHasta,
                                         LocalTime entrada, LocalTime salida,
                                         EstadoAsistencia estado) {
        Asistencia a = asistencia(claseDesde, claseHasta, estado);
        a.setBloque(BloquePresencia.builder()
            .id(1L).fecha(LocalDate.now())
            .horaEntrada(entrada).horaSalida(salida)
            .origenEntrada(OrigenMarca.AUTOMATICO).origenSalida(OrigenMarca.AUTOMATICO)
            .estadoCierre(EstadoCierre.CERRADO_POR_ROSTRO)
            .build());
        return AsistenciaReporteRowDto.from(a, null, null);
    }

    private Asistencia asistencia(LocalTime desde, LocalTime hasta, EstadoAsistencia estado) {
        Carrera c = Carrera.builder().id(1L).codigo("CAR").nombre("Carrera").build();
        Materia m = Materia.builder().id(1L).codigo("MAT").nombre("Matemática").carrera(c).build();
        Comision com = Comision.builder().id(1L).codigo("A").materia(m).build();
        Horario h = Horario.builder().id(1L).comision(com)
            .diaSemana((byte) 1).horaInicio(desde).horaFin(hasta).toleranciaMin((short) 15)
            .build();
        Docente d = Docente.builder().id(1L)
            .persona(DatosDePrueba.personaConDni("12345678", "Juana", "Pérez")).build();

        return Asistencia.builder()
            .id(1L).docente(d).comision(com).horario(h)
            .fecha(LocalDate.now()).horaRegistrada(LocalTime.of(18, 0))
            .estado(estado).metodo(MetodoAsistencia.AUTOMATICO)
            .build();
    }
}
