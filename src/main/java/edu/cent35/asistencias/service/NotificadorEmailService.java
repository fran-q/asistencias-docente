package edu.cent35.asistencias.service;

import edu.cent35.asistencias.model.PropositoCodigo;
import edu.cent35.asistencias.model.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Canal de correo: arma y envía el mensaje con el código de un solo uso.
 *
 * <p>Habla SMTP estándar a través de JavaMailSender, así que el servidor concreto es
 * configuración: en desarrollo apunta a un SMTP local de captura y en producción se cambia por
 * variables de entorno sin tocar el código.
 *
 * <p>Es el canal por defecto. El alternativo, {@link CanalConsola}, existe para poder probar
 * los flujos sin levantar un SMTP; se elige con {@code app.mail.canal}.
 */
@Service
@ConditionalOnProperty(name = "app.mail.canal", havingValue = "correo", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class NotificadorEmailService implements CanalDeCodigos {

    private final JavaMailSender mailSender;

    @Value("${app.mail.remitente}")
    private String remitente;

    @Value("${app.verificacion.minutos-vigencia}")
    private int minutosVigencia;

    /**
     * Envía el código al buzón indicado. Si el SMTP falla lanza la excepción hacia arriba: el
     * flujo tiene que enterarse, porque decirle a la persona que revise su correo cuando el
     * mensaje nunca salió la deja esperando algo que no va a llegar.
     */
    @Override
    public void enviarCodigo(Usuario usuario, PropositoCodigo proposito, String email, String codigo) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(email);
        mensaje.setSubject(asunto(proposito));
        mensaje.setText(cuerpo(usuario, proposito, codigo));

        mailSender.send(mensaje);
        log.info("Correo enviado: proposito={}, usuario={}", proposito, usuario.getId());
    }

    // Nombre corto del canal, para los logs y el aviso de arranque.
    @Override
    public String nombre() {
        return "correo";
    }

    private String asunto(PropositoCodigo proposito) {
        return switch (proposito) {
            case VERIFICACION_EMAIL -> "Confirmá tu correo - Asistencias";
            case RECUPERACION_PASSWORD -> "Código para recuperar tu contraseña - Asistencias";
        };
    }

    private String cuerpo(Usuario usuario, PropositoCodigo proposito, String codigo) {
        String motivo = switch (proposito) {
            case VERIFICACION_EMAIL ->
                "Pediste confirmar esta dirección de correo para tu cuenta.";
            case RECUPERACION_PASSWORD ->
                "Pediste recuperar la contraseña de tu cuenta.";
        };

        return """
            Hola %s,

            %s

            Tu código es: %s

            Vence en %d minutos y se puede usar una sola vez.

            Si no fuiste vos, ignorá este mensaje: sin el código no se puede hacer
            ningún cambio en la cuenta.

            --
            Asistencias - Gestión de asistencia con reconocimiento facial
            Este es un mensaje automático, no respondas a esta dirección.
            """.formatted(usuario.getNombre(), motivo, codigo, minutosVigencia);
    }
}
