package edu.cent35.asistencias.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre la ventana que exige sostener la misma identidad antes de marcar. El tiempo se pasa
 * por parámetro en vez de leerse del reloj, así los casos de borde se prueban de forma
 * determinista y sin poner al test a esperar segundos reales.
 */
class VentanaConfirmacionServiceTest {

    private static final long VENTANA_MS = 3000;
    private static final long HUECO_MAX  = 2500;
    private static final Long ANA  = 1L;
    private static final Long LUIS = 2L;

    private VentanaConfirmacionService service;
    private ConfirmacionIdentidad racha;

    @BeforeEach
    void setUp() {
        service = new VentanaConfirmacionService();
        ReflectionTestUtils.setField(service, "ventanaMs", VENTANA_MS);
        ReflectionTestUtils.setField(service, "lecturasMinimas", 3);
        ReflectionTestUtils.setField(service, "huecoMaximoMs", HUECO_MAX);
        racha = new ConfirmacionIdentidad();
    }

    @Test
    @DisplayName("Una sola lectura no alcanza para marcar")
    void unaLecturaNoAlcanza() {
        assertThat(service.registrar(racha, ANA, 0).confirmado()).isFalse();
    }

    @Test
    @DisplayName("La misma persona sostenida los 3 segundos queda confirmada")
    void sostenerConfirma() {
        assertThat(service.registrar(racha, ANA, 0).confirmado()).isFalse();
        assertThat(service.registrar(racha, ANA, 1000).confirmado()).isFalse();
        assertThat(service.registrar(racha, ANA, 2000).confirmado())
            .as("a los 2 s todavia no: la ventana pide 3")
            .isFalse();
        assertThat(service.registrar(racha, ANA, 3000).confirmado()).isTrue();
    }

    @Test
    @DisplayName("Si aparece otra persona, la cuenta vuelve a cero")
    void otraPersonaReinicia() {
        service.registrar(racha, ANA, 0);
        service.registrar(racha, ANA, 1000);
        service.registrar(racha, ANA, 2000);

        // El parpadeo por cambio de luz: se cuela un frame de otra persona.
        assertThat(service.registrar(racha, LUIS, 2500).confirmado()).isFalse();
        assertThat(racha.getDocente()).isEqualTo(LUIS);

        // Ana vuelve, pero arranca de cero: no hereda los 2,5 s que ya llevaba.
        assertThat(service.registrar(racha, ANA, 3000).confirmado()).isFalse();
        assertThat(service.registrar(racha, ANA, 5000).confirmado()).isFalse();
        assertThat(service.registrar(racha, ANA, 6000).confirmado())
            .as("recien a los 3 s CONTADOS DESDE QUE VOLVIO")
            .isTrue();
    }

    @Test
    @DisplayName("Es exactamente el caso que motivó esto: oscilar entre dos parecidos nunca marca")
    void oscilarEntreDosNuncaConfirma() {
        Long[] alternando = {ANA, LUIS, ANA, LUIS, ANA, LUIS, ANA, LUIS};
        long t = 0;
        for (Long quien : alternando) {
            assertThat(service.registrar(racha, quien, t).confirmado())
                .as("con la identidad oscilando no se puede marcar a nadie")
                .isFalse();
            t += 1000;
        }
    }

    @Test
    @DisplayName("Un frame perdido no reinicia la cuenta")
    void toleraUnFramePerdido() {
        service.registrar(racha, ANA, 0);
        // Salto de 2 s: se perdio un frame, pero la persona sigue ahi.
        service.registrar(racha, ANA, 2000);
        assertThat(service.registrar(racha, ANA, 3000).confirmado())
            .as("exigir frames perfectos haria que nunca confirme con una camara real")
            .isTrue();
    }

    @Test
    @DisplayName("Si la persona se va y vuelve, empieza de nuevo")
    void irseYVolverReinicia() {
        service.registrar(racha, ANA, 0);
        service.registrar(racha, ANA, 1000);
        service.registrar(racha, ANA, 2000);

        // Hueco mayor al tolerado: se fue del cuadro.
        assertThat(service.registrar(racha, ANA, 2000 + HUECO_MAX + 1).confirmado())
            .as("volver despues de irse no puede aprovechar el tiempo de antes")
            .isFalse();
        assertThat(racha.getLecturas()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cortar la racha deja todo en cero, como despues de marcar")
    void cortarLimpia() {
        service.registrar(racha, ANA, 0);
        service.registrar(racha, ANA, 1000);

        service.cortar(racha);

        assertThat(racha.getDocente()).isNull();
        assertThat(racha.getLecturas()).isZero();
        assertThat(service.registrar(racha, ANA, 2000).confirmado()).isFalse();
    }

    @Test
    @DisplayName("El progreso informado nunca supera el objetivo")
    void progresoAcotado() {
        // Alguien que se queda parado mas tiempo del necesario: lecturas seguidas, sin
        // huecos, hasta pasarse holgadamente de los 3 s de la ventana.
        VentanaConfirmacionService.Estado e = null;
        for (long t = 0; t <= 6000; t += 1000) {
            e = service.registrar(racha, ANA, t);
        }

        assertThat(e.confirmado()).isTrue();
        assertThat(e.progreso())
            .as("si no se acotara, la barra de la pantalla se pasaria de largo")
            .isEqualTo(VENTANA_MS);
        assertThat(e.objetivo()).isEqualTo(VENTANA_MS);
    }
}
