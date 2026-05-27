package edu.cent35.asistencias.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del cifrado biométrico. Verifica el roundtrip cifrar→descifrar y
 * que el cifrado no devuelva los mismos bytes que la entrada.
 */
class CifradoBiometricoServiceTest {

    private static final String CLAVE = "test-clave-dev";
    private static final String SALT  = "5c0d1e2f3a4b5c6d";

    private final CifradoBiometricoService service = new CifradoBiometricoService(CLAVE, SALT);

    @Test
    @DisplayName("cifrar/descifrar: roundtrip recupera los bytes originales")
    void roundtrip() {
        byte[] original = "modelo LBPH ficticio: contenido aleatorio 12345 áéíóú ñ".getBytes();

        byte[] cifrado = service.cifrar(original);
        byte[] descifrado = service.descifrar(cifrado);

        assertThat(descifrado).isEqualTo(original);
    }

    @Test
    @DisplayName("cifrar: el resultado es distinto de la entrada")
    void cifradoEsDistintoDeOriginal() {
        byte[] original = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        byte[] cifrado = service.cifrar(original);
        assertThat(cifrado).isNotEqualTo(original);
        // El cifrado AES-GCM agrega IV + tag de autenticación → siempre más bytes.
        assertThat(cifrado.length).isGreaterThan(original.length);
    }

    @Test
    @DisplayName("cifrar: dos cifrados del mismo input dan resultados distintos (IV aleatorio)")
    void cifradoNoDeterminista() {
        byte[] original = "datos sensibles".getBytes();
        byte[] cifrado1 = service.cifrar(original);
        byte[] cifrado2 = service.cifrar(original);
        // AES-GCM con IV aleatorio garantiza salidas distintas en cada cifrado.
        assertThat(cifrado1).isNotEqualTo(cifrado2);
        // Ambos descifran al mismo plaintext.
        assertThat(service.descifrar(cifrado1)).isEqualTo(original);
        assertThat(service.descifrar(cifrado2)).isEqualTo(original);
    }

    @Test
    @DisplayName("cifrar: input vacío también funciona (roundtrip)")
    void inputVacio() {
        byte[] cifrado = service.cifrar(new byte[0]);
        byte[] descifrado = service.descifrar(cifrado);
        assertThat(descifrado).isEmpty();
    }
}
