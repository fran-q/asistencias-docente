package edu.cent35.asistencias.service;

import edu.cent35.asistencias.model.PropositoCodigo;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Orquesta los dos flujos que usan un código de un solo uso: confirmar el correo de la cuenta
 * y recuperar la contraseña sin depender del superadmin. Se apoya en CodigoVerificacionService
 * para las defensas del código y en NotificadorEmailService para el envío.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificacionCuentaService {

    private final UsuarioRepository usuarioRepository;
    private final CodigoVerificacionService codigoService;
    private final NotificadorEmailService notificador;
    private final PasswordEncoder passwordEncoder;

    // ========================================================================
    //  Verificacion del correo (con sesion iniciada)
    // ========================================================================

    // Manda un codigo al correo que la cuenta tiene cargado hoy.
    @Transactional
    public void enviarCodigoDeVerificacion(Long usuarioId, String ip) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + usuarioId));

        if (usuario.getEmailVerificadoEn() != null) {
            throw new IllegalArgumentException("Este correo ya está verificado.");
        }

        String codigo = codigoService.emitir(
            usuario, PropositoCodigo.VERIFICACION_EMAIL, usuario.getEmail(), ip);
        notificador.enviarCodigo(usuario, PropositoCodigo.VERIFICACION_EMAIL, usuario.getEmail(), codigo);
    }

    // Confirma el correo si el codigo es correcto; solo entonces queda marcado como verificado.
    @Transactional
    public CodigoVerificacionService.Resultado confirmarEmail(Long usuarioId, String codigoIngresado) {
        CodigoVerificacionService.Resultado resultado =
            codigoService.validar(usuarioId, PropositoCodigo.VERIFICACION_EMAIL, codigoIngresado);

        if (resultado == CodigoVerificacionService.Resultado.OK) {
            Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + usuarioId));
            usuario.setEmailVerificadoEn(LocalDateTime.now());
            usuarioRepository.save(usuario);
            log.info("Correo verificado: usuario={}", usuarioId);
        }
        return resultado;
    }

    // ========================================================================
    //  Recuperacion de contrasena (sin sesion)
    // ========================================================================

    /**
     * Arranca la recuperación a partir de un usuario o un correo. Devuelve el id solo para que
     * el flujo lo guarde en la sesión; quien llama <b>no</b> debe cambiar la respuesta según
     * venga vacío o no, porque eso permitiría averiguar qué cuentas existen probando direcciones.
     */
    @Transactional
    public Optional<Long> iniciarRecuperacion(String usuarioOEmail, String ip) {
        Optional<Usuario> quizas = buscarPorUsuarioOEmail(usuarioOEmail);
        if (quizas.isEmpty()) {
            log.info("Recuperacion pedida para un identificador inexistente; se responde igual que si existiera");
            return Optional.empty();
        }

        Usuario usuario = quizas.get();
        if (Boolean.FALSE.equals(usuario.getActivo())) {
            log.warn("Recuperacion pedida para la cuenta inactiva {}", usuario.getId());
            return Optional.empty();
        }

        try {
            String codigo = codigoService.emitir(
                usuario, PropositoCodigo.RECUPERACION_PASSWORD, usuario.getEmail(), ip);
            notificador.enviarCodigo(
                usuario, PropositoCodigo.RECUPERACION_PASSWORD, usuario.getEmail(), codigo);
        } catch (RuntimeException ex) {
            // Ni el limite de reenvios ni una caida del SMTP deben delatar que la cuenta existe.
            log.warn("No se pudo emitir el codigo de recuperacion para {}: {}",
                     usuario.getId(), ex.toString());
        }
        return Optional.of(usuario.getId());
    }

    // Fija la contrasena nueva si el codigo es correcto. El codigo queda consumido en el intento.
    @Transactional
    public CodigoVerificacionService.Resultado completarRecuperacion(Long usuarioId,
                                                                    String codigoIngresado,
                                                                    String passwordNueva) {
        CodigoVerificacionService.Resultado resultado =
            codigoService.validar(usuarioId, PropositoCodigo.RECUPERACION_PASSWORD, codigoIngresado);

        if (resultado == CodigoVerificacionService.Resultado.OK) {
            Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + usuarioId));
            usuario.setPasswordHash(passwordEncoder.encode(passwordNueva));
            usuarioRepository.save(usuario);
            log.info("Contrasena recuperada por el propio usuario: {}", usuarioId);
        }
        return resultado;
    }

    // Correo enmascarado del codigo pendiente, para poder decir a donde se envio sin exponerlo.
    @Transactional(readOnly = true)
    public String emailEnmascaradoDeRecuperacion(Long usuarioId) {
        return codigoService.emailDelUltimoCodigo(usuarioId, PropositoCodigo.RECUPERACION_PASSWORD)
            .map(VerificacionCuentaService::enmascarar)
            .orElse("");
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    // Acepta indistintamente el nombre de usuario o el correo; si hay ambiguedad entre
    // instituciones no elige ninguno, igual que hace el login.
    private Optional<Usuario> buscarPorUsuarioOEmail(String entrada) {
        if (entrada == null || entrada.isBlank()) {
            return Optional.empty();
        }
        String limpio = entrada.trim();

        List<Usuario> porUsername = usuarioRepository.findByUsername(limpio);
        if (porUsername.size() == 1) {
            return Optional.of(porUsername.get(0));
        }
        if (porUsername.size() > 1) {
            log.warn("Identificador '{}' ambiguo entre instituciones; no se emite codigo", limpio);
            return Optional.empty();
        }

        List<Usuario> porEmail = usuarioRepository.findByEmailIgnoreCase(limpio);
        return porEmail.size() == 1 ? Optional.of(porEmail.get(0)) : Optional.empty();
    }

    // Deja visibles el primer caracter y el dominio: "a****@cent35.edu.ar".
    static String enmascarar(String email) {
        int arroba = email.indexOf('@');
        if (arroba <= 0) {
            return "****";
        }
        return email.charAt(0) + "****" + email.substring(arroba);
    }
}
