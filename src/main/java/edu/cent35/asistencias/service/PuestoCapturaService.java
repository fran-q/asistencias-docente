package edu.cent35.asistencias.service;

import edu.cent35.asistencias.model.PuestoCaptura;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.PuestoCapturaRepository;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Alta, baja y verificación de los equipos autorizados a capturar datos biométricos
 * (ADR-0015). Es lo que decide si una petición al pase o al registro del rostro viene de un
 * puesto habilitado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PuestoCapturaService {

    // Fuente criptografica: un Random comun es predecible, y este token es la unica prueba
    // de que un equipo esta autorizado.
    private static final SecureRandom ALEATORIO = new SecureRandom();

    // 32 bytes = 256 bits. En Base64 sin relleno son 43 caracteres, comodos para una cookie.
    private static final int BYTES_TOKEN = 32;

    private final PuestoCapturaRepository puestoRepository;

    /** Un puesto recién designado, con su token en claro. Es la única vez que el token existe fuera del navegador. */
    @Value
    public static class PuestoDesignado {
        PuestoCaptura puesto;
        String tokenEnClaro;
    }

    /**
     * Registra el equipo desde el que se está llamando y devuelve el token que hay que dejarle
     * en la cookie. El token en claro no se guarda en ningún lado: se devuelve una vez y
     * después solo existe en ese navegador.
     */
    /**
     * Designa el equipo desde el que llega la petición.
     *
     * <p><b>El primer puesto es un arranque; los demás no.</b> Mientras la institución no
     * tenga ninguno, nadie puede tomar asistencia y hay que poder salir de esa situación desde
     * cualquier máquina — para eso alcanza con la cuenta institucional. Pero una vez que hay
     * un puesto habilitado, autorizar otro <b>solo se puede desde uno ya autorizado</b>.
     *
     * <p>Sin esa condición el control no controlaba nada: cualquiera con la cuenta
     * institucional podía convertir su propia máquina en puesto desde donde estuviera, que es
     * exactamente lo que ADR-0015 quiere impedir. La restricción vive acá y no en la pantalla
     * porque una vista que esconde el formulario no frena un POST hecho a mano.
     *
     * @param vieneDePuestoAutorizado si la petición trae la cookie de un puesto habilitado
     */
    @Transactional
    public PuestoDesignado designar(Long institucionId, String nombre, Usuario designadoPor,
                                    boolean vieneDePuestoAutorizado) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException("El puesto necesita un nombre.");
        }
        if (!vieneDePuestoAutorizado && contarHabilitados(institucionId) > 0) {
            log.warn("Designacion rechazada: la institucion {} ya tiene puesto(s) habilitado(s) "
                     + "y la peticion no viene de uno", institucionId);
            throw new IllegalArgumentException(
                "Esta institución ya tiene un equipo autorizado, así que un equipo nuevo solo "
                + "se puede autorizar desde uno que ya lo esté. Entrá desde el equipo "
                + "autorizado y agregalo desde la pantalla de puestos.");
        }
        if (puestoRepository.existeNombre(institucionId, limpio, null)) {
            throw new IllegalArgumentException(
                "Ya hay un puesto con ese nombre en esta institución. Elegí otro para poder "
                + "distinguirlos cuando haya que revocar uno.");
        }

        byte[] crudo = new byte[BYTES_TOKEN];
        ALEATORIO.nextBytes(crudo);
        String tokenEnClaro = Base64.getUrlEncoder().withoutPadding().encodeToString(crudo);

        PuestoCaptura puesto = PuestoCaptura.builder()
            .nombre(limpio)
            .tokenHash(hashear(tokenEnClaro))
            .activo(true)
            .designadoPor(designadoPor)
            .build();
        puesto.setInstitucionId(institucionId);

        PuestoCaptura guardado = puestoRepository.save(puesto);
        log.info("Puesto de captura designado: id={}, nombre='{}', institucion={}",
                 guardado.getId(), limpio, institucionId);

        return new PuestoDesignado(guardado, tokenEnClaro);
    }

    /**
     * Si el token corresponde a un puesto habilitado de esa institución.
     *
     * <p>Corre en cada petición de las pantallas de captura —incluido el endpoint que recibe
     * un cuadro por segundo mientras la cámara está encendida—, así que tiene que ser barato.
     */
    @Transactional(readOnly = true)
    public Optional<PuestoCaptura> verificar(String tokenEnClaro, Long institucionId) {
        if (tokenEnClaro == null || tokenEnClaro.isBlank() || institucionId == null) {
            return Optional.empty();
        }
        return puestoRepository.habilitadoPorToken(hashear(tokenEnClaro), institucionId);
    }

    /** Deja constancia de que el puesto se usó. Va aparte de {@link #verificar} para no escribir en una consulta de solo lectura. */
    @Transactional
    public void registrarUso(Long puestoId, Long institucionId) {
        puestoRepository.registrarUso(puestoId, institucionId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<PuestoCaptura> listar(Long institucionId) {
        return puestoRepository.deInstitucion(institucionId);
    }

    /** Cuántos equipos pueden capturar hoy. Cero significa que la institución no puede tomar asistencia. */
    @Transactional(readOnly = true)
    public long contarHabilitados(Long institucionId) {
        return puestoRepository.contarHabilitados(institucionId);
    }

    /**
     * Revoca un puesto. Es baja lógica como en el resto del sistema, pero acá tiene una
     * consecuencia inmediata: la cookie que vive en ese equipo deja de servir en la siguiente
     * petición, sin necesidad de tocar la máquina.
     */
    @Transactional
    public void revocar(Long puestoId, Long institucionId) {
        PuestoCaptura puesto = puestoRepository.porIdEnInstitucion(puestoId, institucionId)
            .orElseThrow(() -> new IllegalArgumentException("El puesto no existe en esta institución."));

        if (!puesto.habilitado()) {
            return;                                  // ya estaba revocado: no hay nada que hacer
        }
        puesto.setActivo(false);
        puesto.setFechaBaja(LocalDate.now());
        puestoRepository.save(puesto);

        log.info("Puesto de captura revocado: id={}, institucion={}", puestoId, institucionId);
    }

    /**
     * Hash del token.
     *
     * <p><b>SHA-256 y no BCrypt, que es lo que usa el resto del proyecto para contraseñas y
     * códigos.</b> BCrypt existe para encarecer la fuerza bruta sobre secretos de poca
     * entropía: una contraseña elegida por una persona, o un código de seis dígitos con un
     * millón de combinaciones. Este token tiene 256 bits al azar, así que no hay diccionario
     * ni espacio que recorrer, y lo que BCrypt aportaría es solo demora.
     *
     * <p>Hay además una razón funcional que lo vuelve obligatorio: BCrypt lleva sal por hash,
     * de modo que el mismo token produce valores distintos y no se puede buscar por él.
     * Verificar exigiría traer todos los puestos y probar {@code matches()} uno por uno, en
     * un endpoint que corre una vez por segundo. SHA-256 es determinístico, así que la
     * búsqueda entra por el índice único de la columna.
     */
    private String hashear(String token) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM; si falta, algo mucho peor esta roto.
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
