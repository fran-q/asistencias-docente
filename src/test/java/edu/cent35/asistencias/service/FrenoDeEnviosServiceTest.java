package edu.cent35.asistencias.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El tope de códigos por casilla en una hora: corta al llegar, y la ventana se corre sola.
 *
 * <p>La ventana se prueba con el reloj fijo y movido a mano: sin eso, ver que una casilla
 * recupera el cupo exigía esperar una hora de verdad, y por eso no había test.
 */
class FrenoDeEnviosServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Ushuaia");
    private static final Instant LAS_DIEZ = Instant.parse("2026-10-14T13:00:00Z");
    private static final String CASILLA = "secretaria@instituto.test";

    private FrenoDeEnviosService service;

    @BeforeEach
    void setUp() {
        service = new FrenoDeEnviosService();
        ReflectionTestUtils.setField(service, "maxPorHora", 2);
        service.setClock(Clock.fixed(LAS_DIEZ, ZONA));
    }

    private void aLos(Duration despues) {
        service.setClock(Clock.fixed(LAS_DIEZ.plus(despues), ZONA));
    }

    @Test
    @DisplayName("deja pasar hasta el tope de la hora y corta después")
    void cortaAlLlegarAlTope() {
        assertThat(service.permitirEnvio(CASILLA)).isTrue();
        assertThat(service.permitirEnvio(CASILLA)).isTrue();
        assertThat(service.permitirEnvio(CASILLA))
            .as("el tercero en la misma hora pasa el tope de 2")
            .isFalse();
    }

    @Test
    @DisplayName("a los 59 minutos la casilla sigue frenada; a los 60 vuelve a tener cupo")
    void laVentanaEsDeUnaHora() {
        service.permitirEnvio(CASILLA);
        service.permitirEnvio(CASILLA);

        aLos(Duration.ofMinutes(59));
        assertThat(service.permitirEnvio(CASILLA))
            .as("los dos envíos de las 10:00 siguen dentro de la hora")
            .isFalse();

        aLos(Duration.ofMinutes(60));
        assertThat(service.permitirEnvio(CASILLA))
            .as("a las 11:00 los de las 10:00 ya salieron de la ventana")
            .isTrue();
    }

    @Test
    @DisplayName("la casilla se compara sin mayúsculas ni espacios")
    void laCasillaSeNormaliza() {
        // Si no, alcanzaría con variar las mayúsculas para mandarle el doble a la misma persona.
        service.permitirEnvio("Secretaria@Instituto.TEST");
        service.permitirEnvio("  secretaria@instituto.test ");

        assertThat(service.permitirEnvio(CASILLA)).isFalse();
    }

    @Test
    @DisplayName("devolver el cupo de un envío que falló deja mandar otro")
    void devolverElCupo() {
        // Un correo que no salió no puede contar contra el tope: la persona se quedaría sin
        // código y además sin poder pedir otro.
        service.permitirEnvio(CASILLA);
        service.permitirEnvio(CASILLA);

        service.devolverCupo(CASILLA);

        assertThat(service.permitirEnvio(CASILLA)).isTrue();
    }
}
