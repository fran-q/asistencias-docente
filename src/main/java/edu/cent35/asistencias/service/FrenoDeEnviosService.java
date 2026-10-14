package edu.cent35.asistencias.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acota cuántos códigos se pueden pedir hacia una misma dirección de correo. Hace falta
 * porque el alta de institución es pública: sin este freno, cualquiera podría usar la
 * pantalla para mandarle mensajes repetidos a una casilla ajena.
 */
@Service
@Slf4j
public class FrenoDeEnviosService {

    @Value("${app.alta.max-envios-por-hora}")
    private int maxPorHora;

    // Correo destino -> instantes de los últimos envíos. En memoria a propósito: es una
    // defensa contra el abuso casual, no un registro que deba sobrevivir a un reinicio.
    private final Map<String, Deque<LocalDateTime>> envios = new ConcurrentHashMap<>();

    // El reloj de la ventana: se cuentan los envíos de la última hora.
    private Clock clock = Clock.systemDefaultZone();

    // Solo para tests: fija el reloj.
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * Anota un envío hacia esa dirección y responde si estaba permitido.
     *
     * @return true si se puede enviar; false si esa casilla ya recibió demasiados
     */
    public boolean permitirEnvio(String email) {
        String clave = email == null ? "" : email.trim().toLowerCase();
        LocalDateTime ahora = LocalDateTime.now(clock);

        Deque<LocalDateTime> recientes = envios.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (recientes) {
            // Se descartan los que ya salieron de la ventana de una hora.
            while (!recientes.isEmpty()
                   && Duration.between(recientes.peekFirst(), ahora).toMinutes() >= 60) {
                recientes.pollFirst();
            }
            if (recientes.size() >= maxPorHora) {
                log.warn("Envio frenado: la direccion indicada ya recibio {} codigos en la ultima hora",
                         recientes.size());
                return false;
            }
            recientes.addLast(ahora);
            return true;
        }
    }

    // Libera el cupo de una dirección. Se usa cuando el envío falló y no llegó nada.
    public void devolverCupo(String email) {
        String clave = email == null ? "" : email.trim().toLowerCase();
        Deque<LocalDateTime> recientes = envios.get(clave);
        if (recientes == null) return;
        synchronized (recientes) {
            recientes.pollLast();
        }
    }
}
