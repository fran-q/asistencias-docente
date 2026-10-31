package edu.cent35.asistencias.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los topes del freno por origen (TD-008): cuándo se bloquea, a quién, y que se libere solo.
 */
class FrenoDeOrigenServiceTest {

    private static final Instant INICIO = Instant.parse("2026-11-02T12:00:00Z");
    private static final String IP = "203.0.113.9";

    private FrenoDeOrigenService freno;

    @BeforeEach
    void preparar() {
        freno = new FrenoDeOrigenService();
        ReflectionTestUtils.setField(freno, "maxPorCuentaEIp", 3);
        ReflectionTestUtils.setField(freno, "maxPorCuenta", 5);
        ReflectionTestUtils.setField(freno, "maxPorIp", 7);
        ReflectionTestUtils.setField(freno, "ventanaMinutos", 15L);
        ReflectionTestUtils.setField(freno, "maxPublicosPorHora", 2);
        relojEn(0);
    }

    @Test
    @DisplayName("Una cuenta se bloquea desde esa dirección al llegar al tope, no antes")
    void bloqueaAlLlegarAlTope() {
        fallar("ana", IP, 2);
        assertThat(freno.loginBloqueado("ana", IP)).isFalse();

        fallar("ana", IP, 1);
        assertThat(freno.loginBloqueado("ana", IP)).isTrue();
    }

    @Test
    @DisplayName("El dueño de la cuenta sigue pudiendo entrar desde otra dirección")
    void otraDireccionNoQuedaBloqueada() {
        fallar("ana", IP, 3);

        assertThat(freno.loginBloqueado("ana", "198.51.100.1")).isFalse();
    }

    @Test
    @DisplayName("Un ataque repartido entre direcciones también frena")
    void ataqueRepartido() {
        for (int i = 0; i < 5; i++) {
            fallar("ana", "198.51.100." + i, 1);
        }

        assertThat(freno.loginBloqueado("ana", "198.51.100.200")).isTrue();
    }

    @Test
    @DisplayName("Probar muchas cuentas desde una dirección también frena")
    void recorrerCuentas() {
        for (int i = 0; i < 7; i++) {
            fallar("cuenta" + i, IP, 1);
        }

        assertThat(freno.loginBloqueado("otra", IP)).isTrue();
    }

    @Test
    @DisplayName("Da lo mismo con mayúsculas o espacios: es la misma cuenta")
    void noSeEsquivaEscribiendoDistinto() {
        fallar("ana", IP, 1);
        fallar(" ANA ", IP, 1);
        fallar("Ana", IP, 1);

        assertThat(freno.loginBloqueado("ana", IP)).isTrue();
    }

    @Test
    @DisplayName("Se libera solo al salir de la ventana")
    void seLiberaSolo() {
        fallar("ana", IP, 3);
        assertThat(freno.loginBloqueado("ana", IP)).isTrue();

        relojEn(15);
        assertThat(freno.loginBloqueado("ana", IP)).isFalse();
    }

    @Test
    @DisplayName("Entrar bien limpia el par cuenta y dirección, y no el total de la cuenta")
    void entrarBienLimpiaSoloLoSuyo() {
        fallar("ana", IP, 2);
        fallar("ana", "198.51.100.1", 2);

        freno.anotarLoginCorrecto("ana", IP);

        fallar("ana", IP, 2);
        assertThat(freno.loginBloqueado("ana", IP))
            .as("con el par limpio lleva 2 de 3, pero la cuenta ya suma 6 fallos en total")
            .isTrue();
    }

    @Test
    @DisplayName("Las pantallas públicas tienen su propio tope por hora")
    void pedidosPublicos() {
        assertThat(freno.permitirPedidoPublico(IP)).isTrue();
        assertThat(freno.permitirPedidoPublico(IP)).isTrue();
        assertThat(freno.permitirPedidoPublico(IP)).isFalse();
        assertThat(freno.permitirPedidoPublico("198.51.100.1")).isTrue();

        relojEn(60);
        assertThat(freno.permitirPedidoPublico(IP)).isTrue();
    }

    @Test
    @DisplayName("El barrido suelta lo vencido: el freno no puede agotar la memoria")
    void elBarridoSueltaLoVencido() {
        fallar("ana", IP, 1);
        freno.permitirPedidoPublico(IP);

        relojEn(61);
        freno.descartarVencidos();

        assertThat((Map<?, ?>) ReflectionTestUtils.getField(freno, "fallidos")).isEmpty();
        assertThat((Map<?, ?>) ReflectionTestUtils.getField(freno, "publicos")).isEmpty();
    }

    private void fallar(String usuario, String ip, int veces) {
        for (int i = 0; i < veces; i++) {
            freno.anotarLoginFallido(usuario, ip);
        }
    }

    private void relojEn(long minutos) {
        freno.setClock(Clock.fixed(INICIO.plusSeconds(minutos * 60), ZoneOffset.UTC));
    }
}
