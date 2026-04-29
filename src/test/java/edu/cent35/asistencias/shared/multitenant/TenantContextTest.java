package edu.cent35.asistencias.shared.multitenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitarios de TenantContext.
 * <p>
 * No requieren contexto de Spring (es una clase con ThreadLocal estatico),
 * por eso no usan @SpringBootTest - corren mas rapido.
 */
class TenantContextTest {

    @AfterEach
    void cleanup() {
        // Garantiza aislamiento entre tests
        TenantContext.clear();
    }

    @Test
    @DisplayName("set seguido de get devuelve el valor")
    void testSetGet() {
        TenantContext.set(42L);
        assertThat(TenantContext.get()).hasValue(42L);
    }

    @Test
    @DisplayName("get sin haber seteado devuelve Optional vacio")
    void testGetEmpty() {
        assertThat(TenantContext.get()).isEmpty();
    }

    @Test
    @DisplayName("clear remueve el valor")
    void testClear() {
        TenantContext.set(42L);
        TenantContext.clear();
        assertThat(TenantContext.get()).isEmpty();
    }

    @Test
    @DisplayName("getRequired devuelve el valor cuando esta seteado")
    void testGetRequired() {
        TenantContext.set(7L);
        assertThat(TenantContext.getRequired()).isEqualTo(7L);
    }

    @Test
    @DisplayName("getRequired lanza IllegalStateException si no hay tenant")
    void testGetRequiredEmpty() {
        assertThatThrownBy(() -> TenantContext.getRequired())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TenantContext vacio");
    }

    @Test
    @DisplayName("set con null lanza IllegalArgumentException")
    void testSetNull() {
        assertThatThrownBy(() -> TenantContext.set(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no puede ser null");
    }

    @Test
    @DisplayName("el valor es aislado entre hilos (ThreadLocal)")
    void testThreadIsolation() throws InterruptedException {
        TenantContext.set(1L);

        AtomicReference<Optional<Long>> otherThreadValue = new AtomicReference<>();
        Thread otherThread = new Thread(() -> otherThreadValue.set(TenantContext.get()));
        otherThread.start();
        otherThread.join();

        // El otro hilo no ve nuestro tenant
        assertThat(otherThreadValue.get()).isEmpty();
        // El hilo actual sigue teniendo su valor intacto
        assertThat(TenantContext.get()).hasValue(1L);
    }

    @Test
    @DisplayName("multiples sets en el mismo hilo sobrescriben el valor")
    void testOverwrite() {
        TenantContext.set(1L);
        TenantContext.set(2L);
        TenantContext.set(3L);
        assertThat(TenantContext.get()).hasValue(3L);
    }
}
