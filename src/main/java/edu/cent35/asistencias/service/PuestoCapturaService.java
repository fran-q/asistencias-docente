package edu.cent35.asistencias.service;

import edu.cent35.asistencias.model.PropositoCodigo;
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
    // Revocar a distancia exige un codigo de un solo uso. Se reusan las defensas que ya
    // tienen los otros dos flujos --vigencia, tope de intentos, un solo uso-- en vez de
    // inventar un segundo mecanismo que habria que endurecer por separado.
    private final CodigoVerificacionService codigoService;
    private final CanalDeCodigos notificador;

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
     * <p><b>Uno por institución.</b> Mientras haya un equipo habilitado no se puede autorizar
     * otro: para mudar la captura hay que revocar el actual primero, y eso se hace desde ese
     * mismo equipo. Las dos reglas juntas son las que sostienen que la captura biométrica
     * ocurre en una máquina conocida — con la contraseña institucional sola, desde afuera, no
     * alcanza para llevarse el puesto a otro lado.
     *
     * <p><b>El primero es un arranque.</b> Sin ningún puesto nadie puede tomar asistencia, y
     * hay que poder salir de esa situación desde cualquier máquina; para eso alcanza con la
     * cuenta institucional.
     *
     * <p>La restricción vive acá y no en la pantalla porque una vista que esconde el
     * formulario no frena un POST hecho a mano. Y también en la base, con el índice único que
     * agrega V022, porque un servicio no frena un INSERT.
     */
    @Transactional
    public PuestoDesignado designar(Long institucionId, String nombre, Usuario designadoPor) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException("El puesto necesita un nombre.");
        }
        if (contarHabilitados(institucionId) > 0) {
            log.warn("Designacion rechazada: la institucion {} ya tiene un puesto habilitado",
                     institucionId);
            throw new IllegalArgumentException(
                "Esta institución ya tiene un equipo autorizado y solo puede haber uno. "
                + "Revocá el actual desde esa misma máquina y después autorizá este.");
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
     * Revoca el puesto desde ese mismo equipo. Es baja lógica como en el resto del sistema,
     * pero acá tiene una consecuencia inmediata: la cookie que vive en ese equipo deja de
     * servir en la siguiente petición.
     *
     * <p><b>Por qué solo desde ahí.</b> Con un único puesto permitido, quien puede revocar
     * puede mudar la captura: revoca y designa el suyo. Si eso se pudiera hacer desde
     * cualquier lado, la contraseña institucional alcanzaría para llevarse la captura
     * biométrica a una máquina cualquiera, que es exactamente lo que ADR-0015 impide. Con el
     * equipo de por medio, hay que estar sentado ahí.
     *
     * <p>Cuando esa máquina se rompe o se formatea queda {@link #revocarConCodigo}, que exige
     * además el buzón de la institución.
     *
     * @param desdeEsePuesto si la petición trae la cookie de ese mismo puesto
     */
    @Transactional
    public void revocar(Long puestoId, Long institucionId, boolean desdeEsePuesto) {
        if (!desdeEsePuesto) {
            log.warn("Revocacion rechazada: la peticion no viene del puesto {} (institucion {})",
                     puestoId, institucionId);
            throw new IllegalArgumentException(
                "El equipo autorizado solo se revoca desde esa misma máquina. Si ya no la "
                + "tenés, pedí un código al correo de la institución para revocarlo desde acá.");
        }
        revocarSinControles(puestoId, institucionId);
    }

    /**
     * Revoca el puesto desde otra máquina, con un código de un solo uso al correo de la
     * institución. Es la salida para cuando el equipo autorizado ya no existe.
     *
     * <p>El código no es un trámite: es lo que convierte "sé la contraseña institucional" en
     * "sé la contraseña y además entro al buzón de la institución". Sin él, permitir la
     * revocación a distancia devolvería el agujero que {@link #revocar} cierra.
     */
    @Transactional
    public void revocarConCodigo(Long puestoId, Long institucionId, Usuario solicitante,
                                 String codigoIngresado) {
        CodigoVerificacionService.Resultado resultado = codigoService.validar(
            solicitante.getId(), PropositoCodigo.REVOCACION_PUESTO, codigoIngresado);

        if (resultado != CodigoVerificacionService.Resultado.OK) {
            throw new IllegalArgumentException(mensajeDelCodigo(resultado));
        }
        revocarSinControles(puestoId, institucionId);
        log.info("Puesto revocado a distancia con codigo: id={}, institucion={}, por usuario={}",
                 puestoId, institucionId, solicitante.getId());
    }

    /**
     * Emite y manda el código para revocar a distancia, al correo de quien lo pide.
     *
     * <p>Va al correo que la cuenta tiene cargado y no a uno que se escriba en la pantalla: si
     * el destino lo eligiera quien pide, el código no probaría nada.
     */
    @Transactional
    public void pedirCodigoDeRevocacion(Usuario solicitante, String ip) {
        String codigo = codigoService.emitir(
            solicitante, PropositoCodigo.REVOCACION_PUESTO, solicitante.getEmail(), ip);
        notificador.enviarCodigo(
            solicitante, PropositoCodigo.REVOCACION_PUESTO, solicitante.getEmail(), codigo);
        log.info("Codigo de revocacion de puesto emitido para el usuario {}", solicitante.getId());
    }

    // La baja en si, sin decidir quien tiene derecho a pedirla: eso ya lo resolvieron los dos
    // metodos de arriba, cada uno con su prueba.
    private void revocarSinControles(Long puestoId, Long institucionId) {
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

    // Traduce el resultado del codigo a algo que se pueda leer en la pantalla.
    private String mensajeDelCodigo(CodigoVerificacionService.Resultado resultado) {
        return switch (resultado) {
            case OK -> "";
            case INEXISTENTE -> "No hay ningún código pendiente. Pedí uno nuevo.";
            case VENCIDO -> "El código venció. Pedí uno nuevo.";
            case INCORRECTO -> "El código no es correcto.";
            case SIN_INTENTOS -> "Se agotaron los intentos. Pedí un código nuevo.";
        };
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
