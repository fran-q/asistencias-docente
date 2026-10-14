package edu.cent35.asistencias.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La ventana que abre el código del correo actual: hasta cuándo deja confirmar el nuevo, y
 * cuándo hay que volver a empezar. El instante lo pasa quien pregunta, que tiene el reloj.
 */
class CambioDeCorreoTest {

    private static final LocalDateTime LAS_DIEZ = LocalDateTime.of(2026, 10, 14, 10, 0);

    private final CambioDeCorreo cambio = new CambioDeCorreo(7L, "nuevo@instituto.test");

    @Test
    @DisplayName("sin el código del correo actual no hay autorización, y tampoco nada vencido")
    void sinConfirmarElActual() {
        assertThat(cambio.estaAutorizado(LAS_DIEZ)).isFalse();
        assertThat(cambio.autorizacionVencida(LAS_DIEZ))
            .as("no venció algo que nunca se abrió: la pantalla no tiene que avisar nada")
            .isFalse();
    }

    @Test
    @DisplayName("la ventana vale hasta el instante en que cierra, sin incluirlo")
    void laVentanaCierraEnElBorde() {
        cambio.autorizarHasta(LAS_DIEZ.plusMinutes(10));

        assertThat(cambio.estaAutorizado(LAS_DIEZ.plusMinutes(9).plusSeconds(59))).isTrue();
        assertThat(cambio.estaAutorizado(LAS_DIEZ.plusMinutes(10))).isFalse();
        assertThat(cambio.autorizacionVencida(LAS_DIEZ.plusMinutes(10)))
            .as("se había abierto y ya cerró: hay que pedir un código nuevo")
            .isTrue();
    }

    @Test
    @DisplayName("desautorizar vuelve al primer paso sin dejar un aviso de vencimiento")
    void desautorizar() {
        cambio.autorizarHasta(LAS_DIEZ.plusMinutes(10));
        cambio.desautorizar();

        assertThat(cambio.estaAutorizado(LAS_DIEZ)).isFalse();
        assertThat(cambio.autorizacionVencida(LAS_DIEZ.plusHours(1))).isFalse();
    }
}
