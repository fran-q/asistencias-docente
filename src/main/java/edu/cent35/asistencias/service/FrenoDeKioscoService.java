package edu.cent35.asistencias.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acota cuántos cuadros por minuto acepta un equipo en modo kiosco, para que un endpoint que
 * corre reconocimiento facial sin autenticar no sea un vector de agotamiento de CPU
 * (ADR-0019).
 * <p>
 * Mismo mecanismo que {@link FrenoDeEnviosService} —ventana deslizante en memoria— y por la
 * misma razón: es una defensa contra el abuso, no un registro que deba sobrevivir a un
 * reinicio. La clave es el <b>puesto</b> y no la IP: la IP de una LAN institucional la
 * comparten todas las máquinas, y lo que hay que acotar es cuánto pide cada equipo.
 */
@Service
@Slf4j
public class FrenoDeKioscoService {

    /**
     * Cuadros por minuto admitidos por equipo.
     *
     * <p>El pase manda alrededor de uno por segundo, así que 120 deja el doble del uso normal
     * antes de frenar. Un límite ajustado al uso exacto cortaría la cámara en cualquier
     * hipo de red, que es peor que dejar pasar algo de margen.
     */
    @Value("${app.biometria.kiosco.max-por-minuto:120}")
    private int maxPorMinuto;

    // Puesto -> instantes de las ultimas peticiones.
    private final Map<Long, Deque<LocalDateTime>> peticiones = new ConcurrentHashMap<>();

    /**
     * Anota una petición de ese equipo y responde si estaba permitida.
     *
     * @return true si se puede procesar; false si ese equipo ya pidió demasiado
     */
    public boolean permitir(Long puestoId) {
        if (puestoId == null) {
            return false;
        }
        LocalDateTime ahora = LocalDateTime.now();
        Deque<LocalDateTime> recientes =
            peticiones.computeIfAbsent(puestoId, k -> new ArrayDeque<>());

        synchronized (recientes) {
            // Se descartan las que ya salieron de la ventana de un minuto.
            while (!recientes.isEmpty()
                   && Duration.between(recientes.peekFirst(), ahora).toSeconds() >= 60) {
                recientes.pollFirst();
            }
            if (recientes.size() >= maxPorMinuto) {
                log.warn("Kiosco frenado: el puesto {} lleva {} peticiones en el ultimo minuto",
                         puestoId, recientes.size());
                return false;
            }
            recientes.addLast(ahora);
            return true;
        }
    }

    /**
     * Suelta lo anotado de un equipo.
     *
     * <p>Se usa al revocar el puesto o al deshabilitarle el kiosco: dejar su historial
     * ocupando memoria no aporta nada, y si el mismo id volviera a habilitarse arrancaría con
     * el cupo de otro momento.
     */
    public void olvidar(Long puestoId) {
        if (puestoId != null) {
            peticiones.remove(puestoId);
        }
    }
}
