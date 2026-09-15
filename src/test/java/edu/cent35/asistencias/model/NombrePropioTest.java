package edu.cent35.asistencias.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre la mayúscula inicial de nombres y apellidos.
 *
 * <p>Lo que más importa no es el caso normal sino lo que la regla decide no tocar: una palabra
 * que ya mezcla mayúsculas y minúsculas la escribió alguien a propósito, y una corrección que la
 * pisara convertiría McDonald en Mcdonald cada vez que se guarda.
 */
class NombrePropioTest {

    private static String n(String texto) {
        return NombrePropio.normalizar(texto);
    }

    @Test
    @DisplayName("Lo tipeado en minúscula lleva mayúscula inicial en cada palabra")
    void minuscula() {
        assertThat(n("juan pablo")).isEqualTo("Juan Pablo");
        assertThat(n("ángel")).isEqualTo("Ángel");
        assertThat(n("ñañez")).isEqualTo("Ñañez");
    }

    @Test
    @DisplayName("Lo tipeado todo en mayúscula queda con la inicial y el resto en minúscula")
    void todoMayuscula() {
        // Es como viene el nombre en el DNI, y de ahí se copia.
        assertThat(n("GARCÍA")).isEqualTo("García");
        assertThat(n("MARÍA JOSÉ")).isEqualTo("María José");
        assertThat(n("ÑAÑEZ")).isEqualTo("Ñañez");
    }

    @Test
    @DisplayName("Las partículas van en minúscula, salvo cuando abren el campo")
    void particulas() {
        assertThat(n("de la fuente")).isEqualTo("De la Fuente");
        assertThat(n("DE LA FUENTE")).isEqualTo("De la Fuente");
        assertThat(n("del valle")).isEqualTo("Del Valle");
        assertThat(n("maría de los ángeles")).isEqualTo("María de los Ángeles");
        assertThat(n("GONZÁLEZ DE LA VEGA")).isEqualTo("González de la Vega");
        assertThat(n("ortega y gasset")).isEqualTo("Ortega y Gasset");
    }

    @Test
    @DisplayName("Una palabra que ya mezcla mayúsculas y minúsculas se respeta")
    void respetaLoElegido() {
        assertThat(n("McDonald")).isEqualTo("McDonald");
        assertThat(n("DiStefano")).isEqualTo("DiStefano");
        // Tampoco se bajan las partículas que alguien escribió con mayúscula.
        assertThat(n("María De Los Ángeles")).isEqualTo("María De Los Ángeles");
        // Y en el mismo campo se corrige lo demás.
        assertThat(n("ronald McDonald")).isEqualTo("Ronald McDonald");
    }

    @Test
    @DisplayName("Cada parte de un apellido con guion o apóstrofo lleva su mayúscula")
    void guionYApostrofo() {
        assertThat(n("pérez-gómez")).isEqualTo("Pérez-Gómez");
        // Se decide por tramo: la primera mitad ya estaba bien y la segunda no.
        assertThat(n("Pérez-gómez")).isEqualTo("Pérez-Gómez");
        assertThat(n("o'connor")).isEqualTo("O'Connor");
        // El apóstrofo tipográfico, el que ponen los teclados del celular.
        assertThat(n("O’CONNOR")).isEqualTo("O’Connor");
    }

    @Test
    @DisplayName("Sobran los espacios de afuera y los repetidos de adentro")
    void espacios() {
        assertThat(n("  juan   pablo  ")).isEqualTo("Juan Pablo");
        // El espacio duro que trae un texto copiado de otro lado.
        assertThat(n("juan pablo")).isEqualTo("Juan Pablo");
    }

    @Test
    @DisplayName("Una inicial con punto queda como inicial")
    void inicial() {
        assertThat(n("juan j.")).isEqualTo("Juan J.");
    }

    @Test
    @DisplayName("Aplicarla dos veces da lo mismo que una")
    void idempotente() {
        // Se aplica en cada guardado, también al editar otro dato: si no fuera estable, el
        // nombre cambiaría solo cada vez que alguien corrige un teléfono.
        for (String s : new String[] { "juan pablo", "GONZÁLEZ DE LA VEGA", "o'connor", "McDonald", "de la fuente" }) {
            assertThat(n(n(s))).as(s).isEqualTo(n(s));
        }
    }

    @Test
    @DisplayName("Nulo sigue nulo, y en blanco queda vacío")
    void vacios() {
        assertThat(n(null)).isNull();
        assertThat(n("   ")).isEmpty();
    }
}
