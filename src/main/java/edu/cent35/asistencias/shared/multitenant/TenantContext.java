package edu.cent35.asistencias.shared.multitenant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Optional;

/**
 * Holder por hilo del identificador de la institucion (tenant)
 * activa para el request actual.
 * <p>
 * Se setea al inicio de cada request HTTP por {@link TenantInterceptor}
 * (que se construye en Fase C, una vez que tengamos autenticacion real
 * con un usuario que pertenece a una institucion).
 * <p>
 * El aspecto {@link TenantFilterAspect} consulta este contexto para
 * activar el filtro de Hibernate ({@code "tenant"}) en cada metodo
 * transaccional, lo que hace que las queries SQL incluyan automaticamente
 * {@code WHERE institucion_id = :institucionId}.
 * <p>
 * <b>Aislamiento por hilo</b>: cada request HTTP corre en su propio hilo
 * de Tomcat, por lo que los valores no se mezclan entre requests. El
 * {@link ThreadLocal#remove()} en {@link #clear()} es <b>obligatorio</b>
 * al finalizar el request para evitar fugas en pools de hilos.
 * <p>
 * <b>Tareas asincronas o jobs scheduled</b>: si en el futuro se ejecutan
 * tareas en hilos distintos al del request HTTP (por ejemplo,
 * {@code @Scheduled} o {@code @Async}), hay que propagar el contexto
 * manualmente con {@link #set(Long)} al inicio y {@link #clear()} al final.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    /**
     * Establece el tenant activo para el hilo actual.
     *
     * @param institucionId id de la institucion. No puede ser null.
     * @throws IllegalArgumentException si {@code institucionId} es null.
     */
    public static void set(Long institucionId) {
        if (institucionId == null) {
            throw new IllegalArgumentException("institucionId no puede ser null");
        }
        CURRENT_TENANT.set(institucionId);
    }

    /**
     * Devuelve el tenant del hilo actual, si existe.
     *
     * @return Optional con el id, vacio si no hay tenant seteado.
     */
    public static Optional<Long> get() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    /**
     * Devuelve el tenant del hilo actual o lanza si no existe.
     * <p>
     * Util en services que requieren si o si un tenant: si no hay,
     * estamos accediendo a datos sensibles desde un contexto sin
     * autenticar - error de programacion.
     *
     * @return id de la institucion activa.
     * @throws IllegalStateException si no hay tenant seteado.
     */
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

    /**
     * Limpia el tenant del hilo actual. <b>Llamar siempre al finalizar
     * el request</b> (lo hace {@link TenantInterceptor#afterCompletion}).
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
