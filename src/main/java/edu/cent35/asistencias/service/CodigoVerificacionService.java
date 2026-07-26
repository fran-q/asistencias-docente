package edu.cent35.asistencias.service;

import edu.cent35.asistencias.model.CodigoVerificacion;
import edu.cent35.asistencias.model.PropositoCodigo;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.CodigoVerificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Emite y valida los códigos de un solo uso que sostienen la verificación de correo y la
 * recuperación de contraseña. Concentra acá las defensas —código guardado como hash, ventana
 * de vigencia corta, un único uso, tope de intentos y límite de reenvíos— para que los dos
 * flujos las compartan en lugar de reimplementarlas cada uno por su lado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CodigoVerificacionService {

    // Fuente criptografica: un Random comun es predecible y haria adivinable el codigo.
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final CodigoVerificacionRepository codigoRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.verificacion.minutos-vigencia}")
    private int minutosVigencia;

    @Value("${app.verificacion.max-intentos}")
    private int maxIntentos;

    @Value("${app.verificacion.max-por-hora}")
    private int maxPorHora;

    // Como resulto la validacion. Se distingue el motivo porque ayuda a la persona y no le
    // aporta nada a quien este probando codigos: ya sabe si el suyo fue rechazado.
    public enum Resultado {
        OK,
        INCORRECTO,
        VENCIDO,
        SIN_INTENTOS,
        INEXISTENTE
    }

    /**
     * Genera un código nuevo, lo guarda hasheado y lo devuelve en claro una única vez para que
     * quien llama lo envíe por correo. Invalida los pendientes del mismo propósito: pedir uno
     * nuevo tiene que dejar sin efecto al anterior.
     */
    @Transactional
    public String emitir(Usuario usuario, PropositoCodigo proposito, String email, String ip) {
        LocalDateTime ahora = LocalDateTime.now(clock);

        long pedidosRecientes = codigoRepository.contarDesde(usuario.getId(), proposito, ahora.minusHours(1));
        if (pedidosRecientes >= maxPorHora) {
            log.warn("Limite de reenvios alcanzado: usuario={}, proposito={}, pedidos={}",
                     usuario.getId(), proposito, pedidosRecientes);
            throw new IllegalStateException(
                "Se pidieron demasiados códigos en la última hora. Esperá un rato antes de volver a intentar.");
        }

        codigoRepository.invalidarPendientes(usuario.getId(), proposito, ahora);

        // Seis digitos con ceros a la izquierda: "000123" es tan valido como cualquier otro.
        String codigoEnClaro = String.format("%06d", ALEATORIO.nextInt(1_000_000));

        CodigoVerificacion codigo = CodigoVerificacion.builder()
            .usuario(usuario)
            .proposito(proposito)
            .email(email)
            .codigoHash(passwordEncoder.encode(codigoEnClaro))
            .expiraEn(ahora.plusMinutes(minutosVigencia))
            .creadoEn(ahora)
            .ipSolicitud(ip)
            .intentos((short) 0)
            .build();
        codigo.setInstitucionId(usuario.getInstitucionId());
        codigoRepository.save(codigo);

        log.info("Codigo emitido: usuario={}, proposito={}, vence={}",
                 usuario.getId(), proposito, codigo.getExpiraEn());
        return codigoEnClaro;
    }

    /**
     * Valida el código que tipeó la persona y, si es correcto, lo consume. Cada fallo suma un
     * intento y al llegar al tope el código se descarta, que es lo que vuelve inviable probar
     * el millón de combinaciones de un OTP de seis dígitos.
     */
    @Transactional
    public Resultado validar(Long usuarioId, PropositoCodigo proposito, String codigoIngresado) {
        LocalDateTime ahora = LocalDateTime.now(clock);

        Optional<CodigoVerificacion> quizas = codigoRepository.ultimoDe(usuarioId, proposito);
        if (quizas.isEmpty()) {
            return Resultado.INEXISTENTE;
        }
        CodigoVerificacion codigo = quizas.get();

        if (codigo.getUsadoEn() != null) {
            return Resultado.INEXISTENTE;
        }
        if (!ahora.isBefore(codigo.getExpiraEn())) {
            return Resultado.VENCIDO;
        }
        if (codigo.getIntentos() >= maxIntentos) {
            return Resultado.SIN_INTENTOS;
        }

        if (!passwordEncoder.matches(normalizar(codigoIngresado), codigo.getCodigoHash())) {
            codigo.setIntentos((short) (codigo.getIntentos() + 1));
            codigoRepository.save(codigo);
            log.warn("Codigo incorrecto: usuario={}, proposito={}, intento {}/{}",
                     usuarioId, proposito, codigo.getIntentos(), maxIntentos);
            return codigo.getIntentos() >= maxIntentos ? Resultado.SIN_INTENTOS : Resultado.INCORRECTO;
        }

        codigo.setUsadoEn(ahora);
        codigoRepository.save(codigo);
        log.info("Codigo validado: usuario={}, proposito={}", usuarioId, proposito);
        return Resultado.OK;
    }

    // A que direccion se mando el ultimo codigo pendiente; el flujo lo muestra enmascarado.
    @Transactional(readOnly = true)
    public Optional<String> emailDelUltimoCodigo(Long usuarioId, PropositoCodigo proposito) {
        return codigoRepository.ultimoDe(usuarioId, proposito).map(CodigoVerificacion::getEmail);
    }

    // Tolera que la persona pegue el codigo con espacios o guiones desde el correo.
    private String normalizar(String codigo) {
        return codigo == null ? "" : codigo.replaceAll("[^0-9]", "");
    }

    // Reloj inyectable para tests (Clock.systemDefaultZone() por default).
    private Clock clock = Clock.systemDefaultZone();

    // Solo para tests: permite fijar el reloj y probar el vencimiento sin esperar.
    void setClock(Clock clock) {
        this.clock = clock;
    }
}
