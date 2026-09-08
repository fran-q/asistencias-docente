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

    /**
     * Días sin uso tras los cuales la credencial del puesto deja de servir para el kiosco.
     *
     * <p>Solo aplica al funcionamiento sin sesión: con un administrador logueado el puesto es
     * un control adicional sobre una sesión que ya se validó, y ahí la credencial no es lo
     * único que autoriza.
     */
    @org.springframework.beans.factory.annotation.Value(
        "${app.biometria.puesto.dias-inactividad:30}")
    private long diasDeInactividad;

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

    /**
     * Resuelve el puesto —y con él la institución— a partir del token, sin sesión de por
     * medio (RF-84, RF-88).
     *
     * <p><b>Es el único punto del sistema donde la institución sale de algo que no es el
     * usuario autenticado.</b> Se apoya en que el token tiene un UNIQUE global: identifica un
     * puesto y una sola institución. Devuelve vacío ante cualquier duda —token que no existe,
     * puesto revocado, kiosco no habilitado, credencial vencida— y nunca una institución
     * "por defecto".
     *
     * <p><b>El vencimiento es por inactividad, no por fecha fija</b> (RF-88). En uso normal el
     * equipo marca todos los días y la credencial se renueva sola, así que no hay una mañana
     * en que la secretaría descubra que el sistema dejó de andar. Lo que caduca es el token de
     * una máquina que dejó de usarse: la que se robaron, la que se dio de baja.
     *
     * <p>Un puesto recién habilitado todavía no tiene {@code ultimoUsoEn}; ahí cuenta desde
     * cuándo se creó. Tratarlo como vencido dejaría el kiosco sin poder arrancar nunca.
     */
    @Transactional(readOnly = true)
    public Optional<PuestoCaptura> resolverKiosco(String tokenEnClaro) {
        if (tokenEnClaro == null || tokenEnClaro.isBlank()) {
            return Optional.empty();
        }
        return puestoRepository.paraKioscoPorToken(hashear(tokenEnClaro))
            .filter(this::credencialVigente);
    }

    // Si el puesto se uso dentro de la ventana de inactividad admitida.
    private boolean credencialVigente(PuestoCaptura puesto) {
        LocalDateTime referencia = puesto.getUltimoUsoEn() != null
            ? puesto.getUltimoUsoEn()
            : puesto.getCreadoEn();
        if (referencia == null) {
            return false;
        }
        boolean vigente = referencia.isAfter(LocalDateTime.now().minusDays(diasDeInactividad));
        if (!vigente) {
            log.warn("Credencial de kiosco vencida: puesto={}, institucion={}, ultimo uso={}",
                     puesto.getId(), puesto.getInstitucionId(), referencia);
        }
        return vigente;
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

    // ========================================================================
    //  Modo kiosco: operar sin sesion abierta (RF-85, ADR-0019)
    // ========================================================================

    /**
     * Habilita el equipo para tomar asistencia <b>sin ninguna sesión abierta</b> (RF-85).
     *
     * <p><b>Solo desde ese mismo equipo</b>, y ahí hay dos razones distintas.
     *
     * <p>La primera es de criterio: habilitar el kiosco es decidir que esa máquina, en el
     * lugar físico donde está, se puede dejar operando sola. Desde una lista de nombres eso
     * no se puede juzgar — "Secretaría PC-1" puede estar detrás de un mostrador o en un
     * pasillo abierto. Sentado ahí, sí.
     *
     * <p>La segunda es de credenciales, y es la que importa. El kiosco convierte la cookie
     * del puesto en la credencial completa: sola alcanza para registrar asistencia. Si esto
     * se pudiera habilitar a distancia, la contraseña institucional sola —filtrada,
     * reutilizada, adivinada— bastaría para convertir esa cookie en una credencial. Exigir
     * la cookie hace falta las dos cosas a la vez, que es la misma lógica con la que ya se
     * defiende {@link #revocar}.
     *
     * <p>Es idempotente: si ya estaba habilitado no reescribe la fecha. La fecha responde
     * "desde cuándo opera desatendido", y pisarla en cada visita a la pantalla la volvería
     * inútil justo para la pregunta que existe para responder.
     *
     * @param desdeEsePuesto si la petición trae la cookie de ese mismo puesto
     */
    @Transactional
    public void habilitarKiosco(Long puestoId, Long institucionId, Usuario quien,
                                boolean desdeEsePuesto) {
        PuestoCaptura puesto = puestoRepository.porIdEnInstitucion(puestoId, institucionId)
            .orElseThrow(() -> new IllegalArgumentException("El puesto no existe en esta institución."));

        if (!puesto.habilitado()) {
            throw new IllegalArgumentException(
                "Este equipo está revocado. Autorizá un equipo antes de habilitar el kiosco.");
        }
        if (!desdeEsePuesto) {
            log.warn("Habilitacion de kiosco rechazada: la peticion no viene del puesto {} "
                     + "(institucion {})", puestoId, institucionId);
            throw new IllegalArgumentException(
                "El modo kiosco se habilita desde esa misma máquina. Iniciá sesión con la "
                + "cuenta de la institución en el equipo autorizado y habilitalo desde ahí.");
        }
        if (Boolean.TRUE.equals(puesto.getKioscoHabilitado())) {
            return;                                        // ya estaba: no hay nada que hacer
        }

        puesto.setKioscoHabilitado(true);
        puesto.setKioscoHabilitadoEn(LocalDateTime.now());
        puesto.setKioscoHabilitadoPor(quien);
        puestoRepository.save(puesto);

        log.info("Modo kiosco HABILITADO: puesto={}, institucion={}, por usuario={}",
                 puestoId, institucionId, quien == null ? null : quien.getId());
    }

    /**
     * Deja de admitir el funcionamiento sin sesión, sin revocar el equipo (RF-85).
     *
     * <p><b>Funciona desde cualquier máquina</b>, al revés que habilitar. La asimetría es
     * deliberada: aflojar un control exige estar ahí, apretarlo de nuevo no. El caso urgente
     * —la máquina del kiosco se la llevaron, quedó sin llave, apareció una marca que nadie
     * explica— es justamente aquel en el que no se puede ir hasta esa máquina, y ese es el
     * momento en que apagarlo tiene que ser un clic.
     *
     * <p>El equipo sigue designado y sigue tomando asistencia con un administrador logueado.
     * Es lo que separa "que deje de funcionar solo" de "que deje de funcionar".
     *
     * <p>No borra {@code kioscoHabilitadoEn} ni quién lo habilitó: son el rastro de que el
     * equipo estuvo operando desatendido en un período, y esa pregunta se va a hacer después
     * de apagarlo, no antes.
     */
    @Transactional
    public void deshabilitarKiosco(Long puestoId, Long institucionId) {
        PuestoCaptura puesto = puestoRepository.porIdEnInstitucion(puestoId, institucionId)
            .orElseThrow(() -> new IllegalArgumentException("El puesto no existe en esta institución."));

        if (!Boolean.TRUE.equals(puesto.getKioscoHabilitado())) {
            return;                                        // ya estaba apagado
        }
        puesto.setKioscoHabilitado(false);
        puestoRepository.save(puesto);

        log.info("Modo kiosco DESHABILITADO: puesto={}, institucion={}", puestoId, institucionId);
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
