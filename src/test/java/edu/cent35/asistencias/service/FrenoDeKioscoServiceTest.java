package edu.cent35.asistencias.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre el tope de peticiones por equipo en modo kiosco (ADR-0019): que corte al llegar al
 * límite y que el cupo de un equipo no afecte al de otro.
 * <p>
 * Existe porque el endpoint del kiosco corre reconocimiento facial <b>sin autenticar</b>, así
 * que es lo único que separa un uso normal de un agotamiento de CPU.
 */
class FrenoDeKioscoServiceTest {

    private static final Long PUESTO_A = 1L;
    private static final Long PUESTO_B = 2L;

    private FrenoDeKioscoService service;

    @BeforeEach
    void setUp() {
        service = new FrenoDeKioscoService();
        ReflectionTestUtils.setField(service, "maxPorMinuto", 3);
    }

    @Test
    @DisplayName("deja pasar hasta el limite y corta despues")
    void cortaAlLlegarAlLimite() {
        assertThat(service.permitir(PUESTO_A)).isTrue();
        assertThat(service.permitir(PUESTO_A)).isTrue();
        assertThat(service.permitir(PUESTO_A)).isTrue();
        assertThat(service.permitir(PUESTO_A))
            .as("la cuarta en el mismo minuto ya pasa el tope de 3")
            .isFalse();
    }

    @Test
    @DisplayName("el cupo es por equipo: uno frenado no frena al otro")
    void elCupoEsPorEquipo() {
        // Si el tope fuera global, la secretaría de una institución dejaría sin marcar a la
        // de otra sin que nadie entienda por qué.
        service.permitir(PUESTO_A);
        service.permitir(PUESTO_A);
        service.permitir(PUESTO_A);
        assertThat(service.permitir(PUESTO_A)).isFalse();

        assertThat(service.permitir(PUESTO_B)).isTrue();
    }

    @Test
    @DisplayName("sin equipo identificado no se procesa nada")
    void sinEquipoNoPasa() {
        // Llegar acá sin puesto significa que la credencial no resolvió: no hay a quién
        // cobrarle el cupo, y procesar igual sería justamente lo que el freno evita.
        assertThat(service.permitir(null)).isFalse();
    }

    @Test
    @DisplayName("olvidar libera el cupo del equipo")
    void olvidarLiberaElCupo() {
        // Se usa al revocar el puesto o quitarle el kiosco: si ese id volviera a habilitarse,
        // arrancaría con el cupo consumido en otro momento.
        service.permitir(PUESTO_A);
        service.permitir(PUESTO_A);
        service.permitir(PUESTO_A);
        assertThat(service.permitir(PUESTO_A)).isFalse();

        service.olvidar(PUESTO_A);

        assertThat(service.permitir(PUESTO_A)).isTrue();
    }

    @Test
    @DisplayName("olvidar un equipo que nunca pidio nada no rompe")
    void olvidarLoQueNoExisteNoRompe() {
        service.olvidar(PUESTO_B);
        service.olvidar(null);

        assertThat(service.permitir(PUESTO_B)).isTrue();
    }
}
