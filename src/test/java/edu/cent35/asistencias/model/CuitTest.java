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
        assertThat(Cuit.normalizar("30123456781")).isEqualTo("30-12345678-1");
    }

    @Test
    @DisplayName("Escrito con guiones se deja igual")
    void conGuionesQuedaIgual() {
        assertThat(Cuit.normalizar("30-12345678-1")).isEqualTo("30-12345678-1");
    }

    @Test
    @DisplayName("Las dos formas del MISMO número dan el mismo resultado")
    void lasDosFormasConvergen() {
        assertThat(Cuit.normalizar("30123456781"))
            .as("es lo que permite que el UNIQUE del CUIT detecte el duplicado")
            .isEqualTo(Cuit.normalizar("30-12345678-1"));
    }

    @Test
    @DisplayName("Los espacios y otros separadores no cambian el resultado")
    void toleraSeparadores() {
        assertThat(Cuit.normalizar(" 30 12345678 1 ")).isEqualTo("30-12345678-1");
        assertThat(Cuit.normalizar("30.12345678.1")).isEqualTo("30-12345678-1");
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

    // ========================================================================
    //  Digito verificador
    // ========================================================================

    @Test
    @DisplayName("Un CUIT real pasa la comprobación")
    void reconoceLosValidos() {
        assertThat(Cuit.esValido("30-12345678-1")).isTrue();
        assertThat(Cuit.esValido("20-12345678-6")).isTrue();
        assertThat(Cuit.esValido("27-12345678-0")).isTrue();
        // Tambien escrito de corrido.
        assertThat(Cuit.esValido("30123456781")).isTrue();
    }

    @Test
    @DisplayName("Un dígito verificador equivocado se detecta")
    void detectaElVerificadorMal() {
        // Mismo numero que el valido de arriba, con el ultimo digito cambiado.
        assertThat(Cuit.esValido("30-12345678-9"))
            .as("tiene la forma correcta pero ese CUIT no existe")
            .isFalse();
        assertThat(Cuit.esValido("20-12345678-5")).isFalse();
    }

    @Test
    @DisplayName("Un error de tipeo en el medio también rompe el cálculo")
    void detectaElTipeoEnElCuerpo() {
        // Para eso sirve el verificador: no solo cuida el ultimo digito, cierra
        // sobre los diez anteriores.
        assertThat(Cuit.esValido("30-12345679-1")).isFalse();
        assertThat(Cuit.esValido("31-12345678-1")).isFalse();
    }

    @Test
    @DisplayName("El campo vacío se da por válido: si es obligatorio lo dice otra anotación")
    void vacioEsValido() {
        assertThat(Cuit.esValido(null)).isTrue();
        assertThat(Cuit.esValido("")).isTrue();
        assertThat(Cuit.esValido("   ")).isTrue();
    }

    @Test
    @DisplayName("Un largo distinto de 11 no es válido")
    void largoInvalido() {
        assertThat(Cuit.esValido("3012345678")).isFalse();    // 10
        assertThat(Cuit.esValido("301234567811")).isFalse();  // 12
    }

    // ========================================================================

    @Test
    @DisplayName("Si no son 11 dígitos se devuelve tal cual, sin inventar nada")
    void loInvalidoNoSeMaquilla() {
        // El formulario ya lo rechazo antes de llegar aca. Completar o recortar seria
        // guardar un CUIT que nadie escribio.
        assertThat(Cuit.normalizar("301234567811")).isEqualTo("301234567811");
        assertThat(Cuit.normalizar("123")).isEqualTo("123");
    }
}
