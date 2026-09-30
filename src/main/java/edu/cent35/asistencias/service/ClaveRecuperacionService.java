package edu.cent35.asistencias.service;

import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.RolCodigo;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * La clave de recuperación de una instalación autónoma: lo que destraba la cuenta de la
 * institución cuando no hay correo por el cual mandar un código (ADR-0022, decisión 5).
 *
 * <p><b>Qué problema es este.</b> TD-009. La cuenta INSTITUCION es la única que administra
 * usuarios, ciclos y puestos, y en la máquina de la institución no hay SMTP: olvidar esa
 * contraseña dejaba la instalación sin acceso y sin camino de vuelta.
 *
 * <p><b>Se muestra una sola vez.</b> De la clave se guarda el hash, igual que de una contraseña
 * o del token del puesto (ADR-0015), así que el sistema no puede volver a mostrarla. Si se
 * pierde, se genera otra desde adentro —y la anterior deja de servir en el mismo acto—, que es
 * lo que evita que perder el papel sea perder la instalación.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClaveRecuperacionService {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    /**
     * Sin las letras y números que se confunden al copiar a mano de un papel: no van la O ni el
     * 0, la I, la L ni el 1. Esta clave se imprime, se guarda en un cajón y se vuelve a tipear
     * meses después, probablemente por alguien que no la anotó.
     */
    private static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private static final int GRUPOS = 4;
    private static final int LARGO_GRUPO = 5;

    private final InstitucionRepository institucionRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioService usuarioService;

    /** Cómo terminó un intento de restablecer con la clave. */
    public enum Resultado { OK, CLAVE_INCORRECTA, SIN_CLAVE, SIN_CUENTA }

    /**
     * Genera una clave nueva para la institución, guarda su hash y devuelve la clave en claro.
     *
     * <p><b>Es la única vez que se puede leer.</b> Quien llama tiene que mostrarla; después no
     * queda en ningún lado. Generar una nueva invalida la anterior, y eso es deliberado: dos
     * claves vivas serían dos papeles dando vueltas y ninguna forma de saber cuál se revocó.
     */
    @Transactional
    public String generar(Long institucionId) {
        Institucion institucion = institucionRepository.findById(institucionId)
            .orElseThrow(() -> new IllegalArgumentException(
                "No existe la institución " + institucionId));

        // Se hashea la forma normalizada --sin guiones y en mayuscula-- y se devuelve la forma
        // legible. Si se hasheara con los guiones, la clave escrita de cualquier otra manera no
        // coincidiria nunca, y "como la haya copiado la persona" dejaria de ser cierto.
        String crudo = sortearClave();
        institucion.setClaveRecuperacionHash(passwordEncoder.encode(crudo));
        institucion.setClaveRecuperacionCreadaEn(LocalDateTime.now());
        institucionRepository.save(institucion);
        String enClaro = conGuiones(crudo);

        // La clave NO va al log, por lo mismo que no va una contrasena: el log se lee, se copia
        // y se manda por ahi. Se anota que se genero, que es lo que sirve para responder
        // "cuando se cambio esto".
        log.info("Clave de recuperacion generada: institucion_id={}", institucionId);
        return enClaro;
    }

    /**
     * Restablece la contraseña de una cuenta INSTITUCION contra la clave de recuperación.
     *
     * <p><b>Saltea el límite de un cambio cada 24 horas</b> (V021) a propósito. Ese límite
     * supone que hay otro camino —otro administrador, o la institución— que puede destrabar un
     * cambio; acá la cuenta que se está recuperando <i>es</i> ese camino. Aplicarlo convertiría
     * "me olvidé la contraseña" en "la institución no puede trabajar hasta mañana", que es
     * exactamente el problema que esta clave viene a resolver.
     *
     * <p><b>Sin tope de intentos, y es una decisión.</b> La clave tiene veinte caracteres sobre
     * un alfabeto de treinta y uno: adivinarla probando contra el formulario no es un ataque
     * viable. Un tope acá solo agregaría una forma de dejar afuera a quien la tiene bien y se
     * equivocó tipeando, que es el momento de más apuro del sistema.
     */
    @Transactional
    public Resultado restablecer(String username, String claveTipeada, String passwordNueva) {
        Optional<Usuario> cuenta = buscarCuentaInstitucional(username);
        if (cuenta.isEmpty()) {
            return Resultado.SIN_CUENTA;
        }
        Usuario usuario = cuenta.get();

        Institucion institucion = institucionRepository.findById(usuario.getInstitucionId())
            .orElseThrow(() -> new IllegalStateException(
                "La cuenta " + usuario.getId() + " apunta a una institución que no existe"));

        if (institucion.getClaveRecuperacionHash() == null) {
            return Resultado.SIN_CLAVE;
        }
        String tipeada = normalizar(claveTipeada);
        if (tipeada.isEmpty()
            || !passwordEncoder.matches(tipeada, institucion.getClaveRecuperacionHash())) {
            log.warn("Clave de recuperacion incorrecta: institucion_id={}", institucion.getId());
            return Resultado.CLAVE_INCORRECTA;
        }

        usuario.setPasswordHash(passwordEncoder.encode(passwordNueva));
        usuarioService.sellarCambioDePassword(usuario);
        usuarioRepository.save(usuario);
        log.info("Contrasena restablecida con la clave de recuperacion: usuario_id={}",
                 usuario.getId());
        return Resultado.OK;
    }

    /**
     * La cuenta institucional que se llama así, si existe, está activa y es de rol INSTITUCION.
     *
     * <p>Sin pasar por el tenant: esto corre sin nadie autenticado, así que no hay institución
     * publicada en el contexto todavía. Es el mismo caso que el login.
     */
    private Optional<Usuario> buscarCuentaInstitucional(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        List<Usuario> candidatos = usuarioRepository.findByUsername(username.trim());
        return candidatos.stream()
            .filter(u -> Boolean.TRUE.equals(u.getActivo()))
            .filter(u -> u.getRol() != null
                      && RolCodigo.INSTITUCION.name().equals(u.getRol().getCodigo()))
            .findFirst();
    }

    // Se acepta como venga escrita: con espacios, con guiones, en minuscula. Lo que se compara
    // es lo que se genero, y nadie tiene por que acordarse de si los grupos iban separados.
    private String normalizar(String clave) {
        if (clave == null) return "";
        return clave.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    // Los 20 caracteres, sin separadores: es la forma que se hashea y contra la que se compara.
    private String sortearClave() {
        StringBuilder sb = new StringBuilder(GRUPOS * LARGO_GRUPO);
        for (int i = 0; i < GRUPOS * LARGO_GRUPO; i++) {
            sb.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    // La misma clave en cuatro grupos de cinco, que es como se muestra y como se copia a un
    // papel: veinte caracteres de corrido se pierden de vista a la mitad.
    private String conGuiones(String crudo) {
        StringBuilder sb = new StringBuilder(crudo.length() + GRUPOS - 1);
        for (int g = 0; g < GRUPOS; g++) {
            if (g > 0) sb.append('-');
            sb.append(crudo, g * LARGO_GRUPO, (g + 1) * LARGO_GRUPO);
        }
        return sb.toString();
    }
}
