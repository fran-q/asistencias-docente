package edu.cent35.asistencias.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El alta de institución en espera vence cuando vence su código. El instante lo pasa el
 * servicio, que tiene el reloj; acá se prueba el borde.
 */
class AltaPendienteTest {

    private static final LocalDateTime VENCE = LocalDateTime.of(2026, 10, 14, 10, 15);

    private final AltaPendiente alta = new AltaPendiente(new AltaInstitucionFormDto(), "hash", VENCE);

    @Test
    @DisplayName("hasta el instante en que vence, el alta se puede confirmar")
    void vigenteHastaElBorde() {
        assertThat(alta.estaVencida(VENCE.minusSeconds(1))).isFalse();
        assertThat(alta.estaVencida(VENCE))
            .as("en el instante exacto todavía vale: vence lo que viene después")
            .isFalse();
    }

    @Test
    @DisplayName("pasado ese instante, ya no")
    void vencidaDespues() {
        assertThat(alta.estaVencida(VENCE.plusSeconds(1))).isTrue();
    }
}
