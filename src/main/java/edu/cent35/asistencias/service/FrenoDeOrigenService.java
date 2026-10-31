package edu.cent35.asistencias.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acota los intentos fallidos de login y los pedidos a las pantallas públicas, por cuenta y por
 * dirección de origen (TD-008). Es lo que le da sentido al largo mínimo de la contraseña: sin tope,
 * desde internet se puede probar una clave tras otra sin que nada lo frene.
 * <p>
 * Va en memoria, igual que {@link FrenoDeEnviosService}: es una defensa contra el abuso y no un
 * registro que deba sobrevivir a un reinicio. El sistema corre en una sola instancia, así que no
 * hace falta compartirlo con nadie.
 */
@Service
@Slf4j
public class FrenoDeOrigenService {

    /**
     * Fallos admitidos para una misma cuenta desde una misma dirección. Es el tope que frena el
     * caso común, y no le cierra la cuenta a quien la usa desde otro lado.
     */
    @Value("${app.seguridad.login.max-por-cuenta-e-ip}")
    private int maxPorCuentaEIp;

    /**
     * Fallos admitidos para una cuenta sumando todas las direcciones. Es más alto a propósito:
     * este es el único tope con el que un tercero puede dejar afuera al dueño de la cuenta, y para
     * llegar necesita varias direcciones. Se acepta a cambio de frenar un ataque repartido.
     */
    @Value("${app.seguridad.login.max-por-cuenta}")
    private int maxPorCuenta;

    /** Fallos admitidos desde una dirección, sea cual sea la cuenta que esté probando. */
    @Value("${app.seguridad.login.max-por-ip}")
    private int maxPorIp;

    @Value("${app.seguridad.login.ventana-minutos}")
    private long ventanaMinutos;

    /** Pedidos por hora a las pantallas públicas: alta de institución y recuperación. */
    @Value("${app.seguridad.publico.max-por-ip-por-hora}")
    private int maxPublicosPorHora;

    // Clave con su prefijo ("c:", "i:" o "ci:") -> instantes de los fallos dentro de la ventana.
    private final Map<String, Deque<LocalDateTime>> fallidos = new ConcurrentHashMap<>();

    // Dirección -> instantes de los pedidos públicos de la última hora.
    private final Map<String, Deque<LocalDateTime>> publicos = new ConcurrentHashMap<>();

    // El reloj de las dos ventanas.
    private Clock clock = Clock.systemDefaultZone();

    // Solo para tests: fija el reloj.
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * Si este intento de login se rechaza sin mirar la contraseña.
     *
     * <p>No depende de que la cuenta exista: responder distinto delataría cuáles existen.
     */
    public boolean loginBloqueado(String usuario, String ip) {
        LocalDateTime desde = LocalDateTime.now(clock).minusMinutes(ventanaMinutos);
        String cuenta = normalizar(usuario);
        boolean bloqueado = cuenta(fallidos, "ci:" + cuenta + "|" + ip, desde) >= maxPorCuentaEIp
            || cuenta(fallidos, "c:" + cuenta, desde) >= maxPorCuenta
            || cuenta(fallidos, "i:" + ip, desde) >= maxPorIp;
        if (bloqueado) {
            // Sin el usuario: a veces lo que se tipea en ese campo es la contraseña.
            log.warn("Login frenado: demasiados intentos fallidos desde {}", ip);
        }
        return bloqueado;
    }

    /** Anota un intento fallido contra los tres topes. */
    public void anotarLoginFallido(String usuario, String ip) {
        LocalDateTime ahora = LocalDateTime.now(clock);
        String cuenta = normalizar(usuario);
        anotar(fallidos, "ci:" + cuenta + "|" + ip, ahora);
        anotar(fallidos, "c:" + cuenta, ahora);
        anotar(fallidos, "i:" + ip, ahora);
    }

    /**
     * Quien entró bien desde esa dirección arranca de cero.
     *
     * <p>Solo se limpia el par cuenta y dirección. Si se limpiara el total de la cuenta, un
     * atacante repartido se beneficiaría de cada ingreso legítimo de la persona a la que ataca.
     */
    public void anotarLoginCorrecto(String usuario, String ip) {
        fallidos.remove("ci:" + normalizar(usuario) + "|" + ip);
    }

    /** Anota un pedido a una pantalla pública y responde si estaba permitido. */
    public boolean permitirPedidoPublico(String ip) {
        LocalDateTime ahora = LocalDateTime.now(clock);
        if (cuenta(publicos, ip, ahora.minusHours(1)) >= maxPublicosPorHora) {
            log.warn("Pedido publico frenado: demasiados pedidos desde {}", ip);
            return false;
        }
        anotar(publicos, ip, ahora);
        return true;
    }

    /**
     * Suelta lo que ya salió de la ventana.
     *
     * <p>Sin esto los mapas crecen con cada usuario inventado que alguien pruebe, y el freno
     * terminaría siendo él mismo la forma de agotar la memoria.
     */
    @Scheduled(fixedDelayString = "${app.seguridad.barrido-ms}")
    public void descartarVencidos() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        LocalDateTime limiteFallidos = ahora.minusMinutes(ventanaMinutos);
        fallidos.keySet().forEach(k -> {
            if (cuenta(fallidos, k, limiteFallidos) == 0) fallidos.remove(k);
        });
        LocalDateTime limitePublicos = ahora.minusHours(1);
        publicos.keySet().forEach(k -> {
            if (cuenta(publicos, k, limitePublicos) == 0) publicos.remove(k);
        });
    }

    // Cuántos quedan desde ese instante, descartando de paso los anteriores.
    private int cuenta(Map<String, Deque<LocalDateTime>> mapa, String clave, LocalDateTime desde) {
        Deque<LocalDateTime> recientes = mapa.get(clave);
        if (recientes == null) {
            return 0;
        }
        synchronized (recientes) {
            while (!recientes.isEmpty() && !recientes.peekFirst().isAfter(desde)) {
                recientes.pollFirst();
            }
            return recientes.size();
        }
    }

    // Suma un instante a la clave.
    private void anotar(Map<String, Deque<LocalDateTime>> mapa, String clave, LocalDateTime ahora) {
        Deque<LocalDateTime> recientes = mapa.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (recientes) {
            recientes.addLast(ahora);
        }
    }

    // Igual que el login: sin espacios a los costados y sin distinguir mayúsculas.
    private static String normalizar(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
    }
}
