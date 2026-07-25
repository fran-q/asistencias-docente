package edu.cent35.asistencias.config;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Optional;

/**
 * Guarda, por hilo, el id de la institución (tenant) activa durante el request. Lo setea
 * TenantInterceptor al empezar cada request y lo limpia al terminar; los jobs @Scheduled
 * corren en otro hilo, así que tienen que propagarlo a mano.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    // Establece el tenant del hilo actual; rechaza null.
    public static void set(Long institucionId) {
        if (institucionId == null) {
            throw new IllegalArgumentException("institucionId no puede ser null");
        }
        CURRENT_TENANT.set(institucionId);
    }

    // Devuelve el tenant del hilo, vacío si no hay ninguno seteado.
    public static Optional<Long> get() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    // Devuelve el tenant o falla: sin tenant se estarían tocando datos sin contexto autenticado.
    public static Long getRequired() {
        Long id = CURRENT_TENANT.get();
        if (id == null) {
            throw new IllegalStateException(
                "TenantContext vacio: se requiere un tenant activo en este punto. " +
                "Verificar que el request paso por TenantInterceptor o que se llamo " +
                "manualmente a TenantContext.set() antes (jobs, tests).");
        }
        return id;
    }

    // Limpia el tenant del hilo. Obligatorio al cerrar el request para no filtrarlo en el pool.
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
