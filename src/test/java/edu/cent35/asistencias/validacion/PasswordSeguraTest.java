package edu.cent35.asistencias.validacion;

import edu.cent35.asistencias.dto.*;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La política de contraseñas, y que los cuatro formularios apliquen exactamente la misma.
 *
 * <p>Este test existe por un error concreto: la regla estaba escrita cuatro veces con tres
 * valores distintos, así que crear una cuenta pedía 6 caracteres y cambiarle la contraseña a
 * esa misma cuenta pedía 10. Nadie lo nota leyendo el código —cada archivo por separado se ve
 * razonable—, se nota usando la aplicación.
 *
 * <p>Comprobar los cuatro DTO contra los mismos casos es lo que impide que vuelvan a
 * separarse: si alguien afloja la regla en uno, este test lo dice.
 */
class PasswordSeguraTest {

    private static Validator validator;

    @BeforeAll
    static void iniciar() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    // ========================================================================
    //  La regla
    // ========================================================================

    @ParameterizedTest
    @ValueSource(strings = {
        "Visum2026",        // el caso tipico
        "Abc123",           // justo el minimo: 6
        "Aa1bbbbbbbbbbbbbbbbb",   // justo el maximo: 20 caracteres
        "aB3xyz",
    })
    @DisplayName("acepta las que cumplen largo y las tres clases de carácter")
    void aceptaLasValidas(String password) {
        assertThat(violacionesDe(password))
            .as("'%s' cumple la regla y tendría que pasar", password)
            .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Ab1",                      // corta
        "Aa1234567890123456789",    // 21: se pasa por uno
        "visum2026",                // sin mayuscula
        "VISUM2026",                // sin minuscula
        "VisumVisum",               // sin numero
        "12345678",                 // solo numeros
    })
    @DisplayName("rechaza las que no cumplen")
    void rechazaLasInvalidas(String password) {
        assertThat(violacionesDe(password))
            .as("'%s' no cumple la regla y tendría que ser rechazada", password)
            .isNotEmpty();
    }

    @Test
    @DisplayName("el mensaje dice QUÉ falta, no un 'no cumple los requisitos'")
    void elMensajeExplicaQueFalta() {
        // Quien crea una cuenta no tiene por que adivinar cual de las cinco condiciones fallo.
        assertThat(mensajeDe("visum2026")).contains("una mayúscula");
        assertThat(mensajeDe("VisumVisum")).contains("un número");
        assertThat(mensajeDe("VISUM2026")).contains("una minúscula");
        assertThat(mensajeDe("Ab1")).contains("muy corta");
        assertThat(mensajeDe("Aa1234567890123456789")).contains("muy larga");

        // Y cuando falta mas de una cosa, las nombra todas.
        assertThat(mensajeDe("abc")).contains("muy corta").contains("una mayúscula").contains("un número");
    }

    @Test
    @DisplayName("el campo vacío lo reporta @NotBlank, no esta validación")
    void vacioNoDuplicaElError() {
        // Si las dos se quejaran, la persona veria dos errores por el mismo hueco.
        assertThat(violacionesDe("")).isEmpty();
        assertThat(violacionesDe(null)).isEmpty();
    }

    // ========================================================================
    //  Los cuatro formularios aplican la MISMA regla
    // ========================================================================

    @Test
    @DisplayName("alta de institución, alta de usuario, recuperación y cambio: la misma regla")
    void losCuatroFormulariosCoinciden() {
        String valida   = "Visum2026";
        String invalida = "visum";      // corta, sin mayuscula y sin numero

        // Alta de institucion
        assertThat(erroresDeCampo(altaCon(valida), "password")).isEmpty();
        assertThat(erroresDeCampo(altaCon(invalida), "password")).isNotEmpty();

        // Alta de usuario
        assertThat(erroresDeCampo(usuarioCon(valida), "password")).isEmpty();
        assertThat(erroresDeCampo(usuarioCon(invalida), "password")).isNotEmpty();

        // Cambio desde Mi cuenta: es el que pedia 10 caracteres y ahora pide lo mismo
        // que los demas. Con 9 tiene que pasar.
        CambioPasswordDto cambio = new CambioPasswordDto();
        cambio.setNuevaPassword(valida);
        cambio.setConfirmacion(valida);
        assertThat(erroresDeCampo(cambio, "nuevaPassword"))
            .as("antes exigia 10 caracteres y rechazaba una de 9 que los demas aceptaban")
            .isEmpty();

        // Recuperacion desde afuera
        RecuperacionCompletarFormDto rec = new RecuperacionCompletarFormDto();
        rec.setNuevaPassword(valida);
        rec.setConfirmacion(valida);
        assertThat(erroresDeCampo(rec, "nuevaPassword")).isEmpty();

        rec.setNuevaPassword(invalida);
        assertThat(erroresDeCampo(rec, "nuevaPassword")).isNotEmpty();
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    private Set<ConstraintViolation<CambioPasswordDto>> violacionesDe(String password) {
        CambioPasswordDto dto = new CambioPasswordDto();
        dto.setNuevaPassword(password);
        dto.setConfirmacion(password);
        return validator.validateProperty(dto, "nuevaPassword").stream()
            .filter(v -> !v.getMessage().contains("obligatoria"))
            .collect(java.util.stream.Collectors.toSet());
    }

    private String mensajeDe(String password) {
        return violacionesDe(password).stream()
            .map(ConstraintViolation::getMessage)
            .findFirst()
            .orElse("");
    }

    private <T> Set<ConstraintViolation<T>> erroresDeCampo(T dto, String campo) {
        return validator.validateProperty(dto, campo);
    }

    private AltaInstitucionFormDto altaCon(String password) {
        AltaInstitucionFormDto f = new AltaInstitucionFormDto();
        f.setPassword(password);
        f.setConfirmacion(password);
        return f;
    }

    private UsuarioCreateFormDto usuarioCon(String password) {
        UsuarioCreateFormDto f = new UsuarioCreateFormDto();
        f.setPassword(password);
        f.setConfirmacion(password);
        return f;
    }
}
