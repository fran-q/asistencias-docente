package edu.cent35.asistencias.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre la normalización del CUIT, que es lo que hace que su restricción de unicidad sirva
 * de algo: sin llevarlo a una sola forma, el mismo número escrito de dos maneras entraría
 * dos veces sin que la base lo note.
 */
class CuitTest {

    @Test
    @DisplayName("Escrito de corrido queda con guiones")
    void deCorridoSeNormaliza() {
        assertThat(Cuit.normalizar("30123456789")).isEqualTo("30-12345678-9");
    }

    @Test
    @DisplayName("Escrito con guiones se deja igual")
    void conGuionesQuedaIgual() {
        assertThat(Cuit.normalizar("30-12345678-9")).isEqualTo("30-12345678-9");
    }

    @Test
    @DisplayName("Las dos formas del MISMO número dan el mismo resultado")
    void lasDosFormasConvergen() {
        assertThat(Cuit.normalizar("30123456789"))
            .as("es lo que permite que el UNIQUE del CUIT detecte el duplicado")
            .isEqualTo(Cuit.normalizar("30-12345678-9"));
    }

    @Test
    @DisplayName("Los espacios y otros separadores no cambian el resultado")
    void toleraSeparadores() {
        assertThat(Cuit.normalizar(" 30 12345678 9 ")).isEqualTo("30-12345678-9");
        assertThat(Cuit.normalizar("30.12345678.9")).isEqualTo("30-12345678-9");
    }

    @Test
    @DisplayName("Vacío o solo espacios queda en null, no en cadena vacía")
    void vacioEsNulo() {
        // Importa: el CUIT es opcional y unico. Guardar "" en vez de null haria que dos
        // instituciones sin CUIT chocaran entre si, porque en SQL un NULL no repite a otro
        // pero dos cadenas vacias si.
        assertThat(Cuit.normalizar("")).isNull();
        assertThat(Cuit.normalizar("   ")).isNull();
        assertThat(Cuit.normalizar(null)).isNull();
    }

    @Test
    @DisplayName("Si no son 11 dígitos se devuelve tal cual, sin inventar nada")
    void loInvalidoNoSeMaquilla() {
        // El formulario ya lo rechazo antes de llegar aca. Completar o recortar seria
        // guardar un CUIT que nadie escribio.
        assertThat(Cuit.normalizar("301234567899")).isEqualTo("301234567899");
        assertThat(Cuit.normalizar("123")).isEqualTo("123");
    }
}
