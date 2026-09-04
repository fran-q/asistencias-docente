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
    // La interfaz y no la clase: cual sale por correo y cual por consola lo decide
    // la propiedad app.mail.canal, y este servicio no tiene por que enterarse.
    private final CanalDeCodigos notificador;
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
    //  Cambio de la propia contrasena, ya con sesion iniciada
    // ========================================================================

    /**
     * Manda un código de un solo uso al correo de la cuenta para autorizar el cambio de su
     * contraseña.
     *
     * <p><b>Por qué hace falta si la persona ya inició sesión.</b> Una sesión abierta prueba
     * que alguien entró con esa contraseña en algún momento, no que quien está frente a la
     * pantalla ahora sea el dueño de la cuenta. Una sesión olvidada abierta alcanza para
     * quedarse con la cuenta para siempre: basta con cambiarle la contraseña. El código exige
     * además acceso al correo, que es lo único que el sistema sabe que le pertenece.
     *
     * <p>Usa el mismo propósito que la recuperación pública porque es el mismo acto —acreditar
     * el control del correo para fijar una contraseña nueva—; lo único que cambia es desde
     * dónde se pide.
     */
    @Transactional
    public void enviarCodigoParaCambiarPassword(Long usuarioId, String ip) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + usuarioId));

        String codigo = codigoService.emitir(
            usuario, PropositoCodigo.RECUPERACION_PASSWORD, usuario.getEmail(), ip);
        notificador.enviarCodigo(
            usuario, PropositoCodigo.RECUPERACION_PASSWORD, usuario.getEmail(), codigo);
        log.info("Codigo de cambio de contrasena emitido para el usuario {}", usuarioId);
    }

    /**
     * Comprueba el código del cambio de contraseña. No cambia nada por sí solo: el código se
     * consume acá y la contraseña se fija en el paso siguiente.
     */
    @Transactional
    public CodigoVerificacionService.Resultado validarCodigoParaCambiarPassword(
            Long usuarioId, String codigoIngresado) {
        return codigoService.validar(
            usuarioId, PropositoCodigo.RECUPERACION_PASSWORD, codigoIngresado);
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
        Busqueda busqueda = buscarPorUsuarioOEmail(usuarioOEmail);
        if (!busqueda.hayCuenta()) {
            log.info("Recuperacion sin codigo emitido ({}); se responde igual que si existiera",
                     busqueda.motivo());
            notificador.noSeEmitio(usuarioOEmail, busqueda.motivo());
            return Optional.empty();
        }

        Usuario usuario = busqueda.usuario();
        if (Boolean.FALSE.equals(usuario.getActivo())) {
            log.warn("Recuperacion pedida para la cuenta inactiva {}", usuario.getId());
            notificador.noSeEmitio(usuarioOEmail, "la cuenta existe pero esta dada de baja");
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
            notificador.noSeEmitio(usuarioOEmail,
                "la cuenta existe, pero la emision fallo: " + ex.getMessage());
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

    // ========================================================================
    //  helpers
    // ========================================================================

    /**
     * Lo que dio buscar la cuenta a recuperar: o hay una, o hay un motivo por el cual no.
     *
     * <p>Antes esto era un {@code Optional} vacío y los cuatro motivos posibles —no existe, hay
     * varias, está de baja, falló la emisión— quedaban indistinguibles. Se los separa porque el
     * motivo tiene que llegar al log y al canal de consola: a la respuesta HTTP no llega nunca,
     * y de eso se ocupa quien llama.
     */
    private record Busqueda(Usuario usuario, String motivo) {

        static Busqueda encontrada(Usuario usuario) {
            return new Busqueda(usuario, null);
        }

        static Busqueda ninguna(String motivo) {
            return new Busqueda(null, motivo);
        }

        boolean hayCuenta() {
            return usuario != null;
        }
    }

    // Acepta indistintamente el nombre de usuario o el correo; si hay ambiguedad entre
    // instituciones no elige ninguno, igual que hace el login.
    private Busqueda buscarPorUsuarioOEmail(String entrada) {
        if (entrada == null || entrada.isBlank()) {
            return Busqueda.ninguna("no se ingreso ningun usuario ni correo");
        }
        String limpio = entrada.trim();

        List<Usuario> porUsername = usuarioRepository.findByUsername(limpio);
        if (porUsername.size() == 1) {
            return Busqueda.encontrada(porUsername.get(0));
        }
        if (porUsername.size() > 1) {
            log.warn("Identificador '{}' ambiguo entre instituciones; no se emite codigo", limpio);
            return Busqueda.ninguna(
                "ese usuario existe en " + porUsername.size() + " instituciones distintas; "
                + "hay que entrar el correo para saber de cual se trata");
        }

        List<Usuario> porEmail = usuarioRepository.findByEmailIgnoreCase(limpio);
        if (porEmail.size() == 1) {
            return Busqueda.encontrada(porEmail.get(0));
        }
        if (porEmail.size() > 1) {
            log.warn("Correo '{}' ambiguo entre instituciones; no se emite codigo", limpio);
            return Busqueda.ninguna(
                "ese correo esta cargado en " + porEmail.size() + " cuentas de distintas "
                + "instituciones; no hay forma de elegir una");
        }
        return Busqueda.ninguna("no existe ninguna cuenta con ese usuario ni con ese correo");
    }
}
