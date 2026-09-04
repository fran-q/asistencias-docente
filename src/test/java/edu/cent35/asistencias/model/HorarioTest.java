package edu.cent35.asistencias.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre la ventana y la clasificación que dependen de la tolerancia (RF-19, RF-78, ADR-0018):
 * que perdone hacia los dos lados con el mismo número de minutos, que respete el tope de 30 y
 * que no dé la vuelta al reloj en los bordes del día.
 * <p>
 * Los casos límite son de a un minuto a propósito: es donde la definición de la tolerancia
 * como "anticipo" y como "margen bidireccional" dan resultados distintos, y el único test que
 * había —una llegada 30 minutos tarde— pasaba igual con las dos.
 */
class HorarioTest {

    @Nested
    @DisplayName("llegadaEnHora")
    class LlegadaEnHora {

        @Test
        @DisplayName("llegar antes del inicio siempre está en hora")
        void antesDelInicio() {
            Horario h = de(18, 0, 20, 0, (short) 15);
            assertThat(h.llegadaEnHora(LocalTime.of(17, 50))).isTrue();
            assertThat(h.llegadaEnHora(LocalTime.of(18, 0))).isTrue();
        }

        @Test
        @DisplayName("llegar dentro de la tolerancia posterior está en hora")
        void dentroDeLaToleranciaPosterior() {
            // El caso que cambia con ADR-0018: antes esto era TARDE.
            Horario h = de(18, 0, 20, 0, (short) 15);
            assertThat(h.llegadaEnHora(LocalTime.of(18, 5))).isTrue();
        }

        @Test
        @DisplayName("borde: el último minuto de la tolerancia todavía está en hora")
        void bordeExactoDeLaTolerancia() {
            Horario h = de(18, 0, 20, 0, (short) 15);
            assertThat(h.llegadaEnHora(LocalTime.of(18, 15))).isTrue();
            assertThat(h.llegadaEnHora(LocalTime.of(18, 16))).isFalse();
        }

        @Test
        @DisplayName("con tolerancia 0 cualquier minuto pasado el inicio es tarde")
        void toleranciaCero() {
            Horario h = de(18, 0, 20, 0, (short) 0);
            assertThat(h.llegadaEnHora(LocalTime.of(18, 0))).isTrue();
            assertThat(h.llegadaEnHora(LocalTime.of(18, 1))).isFalse();
        }
    }

    @Nested
    @DisplayName("salidaEnHora")
    class SalidaEnHora {

        @Test
        @DisplayName("irse dentro de la tolerancia previa al fin está en hora")
        void dentroDeLaToleranciaPrevia() {
            // Sin la tolerancia de este lado, irse 19:58 de una clase que termina 20:00
            // quedaría como retiro anticipado.
            Horario h = de(18, 0, 20, 0, (short) 15);
            assertThat(h.salidaEnHora(LocalTime.of(19, 58))).isTrue();
        }

        @Test
        @DisplayName("borde: el primer minuto de la tolerancia ya está en hora")
        void bordeExactoDeLaTolerancia() {
            Horario h = de(18, 0, 20, 0, (short) 15);
            assertThat(h.salidaEnHora(LocalTime.of(19, 45))).isTrue();
            assertThat(h.salidaEnHora(LocalTime.of(19, 44))).isFalse();
        }

        @Test
        @DisplayName("irse después del fin sigue estando en hora")
        void despuesDelFin() {
            Horario h = de(18, 0, 20, 0, (short) 15);
            assertThat(h.salidaEnHora(LocalTime.of(20, 10))).isTrue();
        }

        @Test
        @DisplayName("es el espejo exacto de la llegada: mismos minutos de cada lado")
        void esElEspejoDeLaLlegada() {
            Horario h = de(18, 0, 20, 0, (short) 15);
            // 15 minutos después del inicio y 15 antes del fin: los dos límites, simétricos.
            assertThat(h.llegadaEnHora(LocalTime.of(18, 15))).isTrue();
            assertThat(h.llegadaEnHora(LocalTime.of(18, 16))).isFalse();
            assertThat(h.salidaEnHora(LocalTime.of(19, 45))).isTrue();
            assertThat(h.salidaEnHora(LocalTime.of(19, 44))).isFalse();
        }
    }

    @Nested
    @DisplayName("toleranciaEfectiva y estaEnCurso")
    class VentanaDeAdmision {

        @Test
        @DisplayName("la tolerancia cargada se topea en 30 minutos")
        void topeDeTreintaMinutos() {
            // Las franjas cargadas antes de la regla del tope pueden tener valores mayores.
            Horario h = de(18, 0, 22, 0, (short) 120);
            assertThat(h.toleranciaEfectiva()).isEqualTo(30);
            assertThat(h.llegadaEnHora(LocalTime.of(18, 30))).isTrue();
            assertThat(h.llegadaEnHora(LocalTime.of(18, 31))).isFalse();
        }

        @Test
        @DisplayName("la ventana de admisión llega hasta el fin de la clase")
        void ventanaDeAdmision() {
            Horario h = de(18, 0, 20, 0, (short) 15);
            assertThat(h.estaEnCurso(LocalTime.of(17, 44))).isFalse();
            assertThat(h.estaEnCurso(LocalTime.of(17, 45))).isTrue();
            assertThat(h.estaEnCurso(LocalTime.of(20, 0))).isTrue();
            assertThat(h.estaEnCurso(LocalTime.of(20, 1))).isFalse();
        }

        @Test
        @DisplayName("una clase que arranca apenas pasada la medianoche no da la vuelta al reloj")
        void noDaLaVueltaAlRelojAlRestar() {
            // 00:10 menos 15 minutos son las 23:55 del día anterior. Sin el tope en el borde
            // del día, la ventana quedaría al revés y la clase no aceptaría ninguna marca.
            Horario h = de(0, 10, 2, 0, (short) 15);

            assertThat(h.estaEnCurso(LocalTime.of(0, 12))).isTrue();
            assertThat(h.estaEnCurso(LocalTime.of(0, 0))).isTrue();
            assertThat(h.estaEnCurso(LocalTime.of(23, 59))).isFalse();
        }

        @Test
        @DisplayName("una clase que termina justo antes de medianoche no da la vuelta al sumar")
        void noDaLaVueltaAlRelojAlSumar() {
            // 23:50 más 15 minutos son las 00:05 del día siguiente.
            Horario h = de(22, 0, 23, 50, (short) 15);

            assertThat(h.limiteDeSalida()).isEqualTo(LocalTime.MAX);
            assertThat(h.salidaEnHora(LocalTime.of(23, 55))).isTrue();
        }
    }

    // Horario de lunes con la franja y la tolerancia pedidas.
    private Horario de(int inicioHora, int inicioMin, int finHora, int finMin, short tolerancia) {
        return Horario.builder()
            .id(1L)
            .diaSemana((byte) 1)
            .horaInicio(LocalTime.of(inicioHora, inicioMin))
            .horaFin(LocalTime.of(finHora, finMin))
            .toleranciaMin(tolerancia)
            .activo(true)
            .build();
    }
}
