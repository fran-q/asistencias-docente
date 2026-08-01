package edu.cent35.asistencias.service;

import edu.cent35.asistencias.dto.AltaInstitucionFormDto;
import edu.cent35.asistencias.model.Cuit;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.RolCodigo;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Da de alta una institución junto con su primera cuenta, en dos pasos: primero manda un código
 * al correo declarado y deja los datos en espera, y solo al validarlo crea la institución. Así
 * nada llega a existir con una dirección sin comprobar, y un alta abandonada no deja registros.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AltaInstitucionService {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final InstitucionRepository institucionRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificadorEmailService notificador;
    private final FrenoDeEnviosService freno;

    @Value("${app.verificacion.minutos-vigencia}")
    private int minutosVigencia;

    @Value("${app.verificacion.max-intentos}")
    private int maxIntentos;

    /** Por qué se rechazó un código. */
    public enum Rechazo { CORRECTO, INCORRECTO, VENCIDO, SIN_INTENTOS }

    /**
     * Paso 1: valida que los datos no choquen con nada ya registrado, manda el código al correo
     * declarado y devuelve el alta en espera para que quien llama la guarde en la sesión.
     *
     * <p>Las comprobaciones de unicidad se repiten en el paso 2. Acá sirven para no hacerle
     * completar un código a alguien cuyo nombre de institución ya estaba tomado.
     */
    public AltaPendiente iniciar(AltaInstitucionFormDto form) {
        verificarQueNoExista(form);

        String email = form.getEmail().trim();
        if (!freno.permitirEnvio(email)) {
            throw new IllegalStateException(
                "Esa dirección ya recibió varios códigos en la última hora. "
                + "Esperá un rato antes de volver a intentar.");
        }

        // Seis digitos con ceros a la izquierda, igual que el resto de los codigos del sistema.
        String codigoEnClaro = String.format("%06d", ALEATORIO.nextInt(1_000_000));

        // El usuario todavia no existe, asi que se arma uno de paso solo para el saludo del
        // correo. No se guarda: el notificador unicamente lee su nombre.
        Usuario destinatario = Usuario.builder()
            .nombre(form.getNombre().trim())
            .build();

        try {
            notificador.enviarCodigo(destinatario,
                edu.cent35.asistencias.model.PropositoCodigo.VERIFICACION_EMAIL,
                email, codigoEnClaro);
        } catch (RuntimeException ex) {
            // Si no salio, el cupo no se consumio: devolverlo evita castigar a quien no
            // recibio nada por una caida del servidor de correo.
            freno.devolverCupo(email);
            log.error("No se pudo enviar el codigo del alta de institucion", ex);
            throw new IllegalStateException(
                "No se pudo enviar el código al correo. Revisá la dirección o intentá más tarde.");
        }

        log.info("Alta de institucion iniciada: nombre='{}', a la espera del codigo",
                 form.getNombreInstitucion().trim());
        return new AltaPendiente(form, passwordEncoder.encode(codigoEnClaro),
                                 LocalDateTime.now().plusMinutes(minutosVigencia));
    }

    // Comprueba el codigo tipeado contra el que espera, sumando el intento si falla.
    public Rechazo comprobarCodigo(AltaPendiente pendiente, String codigoTipeado) {
        if (pendiente.estaVencida()) {
            return Rechazo.VENCIDO;
        }
        if (pendiente.getIntentos() >= maxIntentos) {
            return Rechazo.SIN_INTENTOS;
        }
        String tipeado = codigoTipeado == null ? "" : codigoTipeado.trim();
        if (!passwordEncoder.matches(tipeado, pendiente.getCodigoHash())) {
            int van = pendiente.sumarIntentoFallido();
            log.warn("Codigo de alta incorrecto: intento {} de {}", van, maxIntentos);
            return van >= maxIntentos ? Rechazo.SIN_INTENTOS : Rechazo.INCORRECTO;
        }
        return Rechazo.CORRECTO;
    }

    /**
     * Paso 2: crea la institución y su cuenta en una sola transacción, con el correo ya
     * comprobado. La cuenta nace verificada porque acaba de demostrar que controla esa casilla,
     * que es exactamente lo que la verificación pide.
     *
     * @return el usuario creado
     */
    @Transactional
    public Usuario confirmar(AltaPendiente pendiente) {
        AltaInstitucionFormDto form = pendiente.getDatos();

        // Se repite la comprobacion: entre el paso 1 y el 2 pasaron minutos, y en el medio
        // otra persona pudo haber registrado ese mismo nombre.
        verificarQueNoExista(form);

        Institucion institucion = institucionRepository.save(
            Institucion.builder()
                .nombre(form.getNombreInstitucion().trim())
                .cuit(Cuit.normalizar(form.getCuit()))
                .emailContacto(form.getEmail().trim())
                .activo(true)
                .build());

        Rol rol = rolRepository.findByCodigo(RolCodigo.INSTITUCION.name())
            .orElseThrow(() -> new IllegalStateException(
                "Falta el rol INSTITUCION: la base no esta inicializada correctamente."));

        Usuario usuario = Usuario.builder()
            .username(form.getUsername().trim())
            .email(form.getEmail().trim())
            .passwordHash(passwordEncoder.encode(form.getPassword()))
            .nombre(form.getNombre().trim())
            .apellido(form.getApellido().trim())
            .rol(rol)
            .activo(true)
            .emailVerificadoEn(LocalDateTime.now())
            .build();
        usuario.setInstitucionId(institucion.getId());

        Usuario guardado = usuarioRepository.save(usuario);
        log.info("Alta de institucion confirmada: institucion_id={}, nombre='{}', usuario_id={}",
                 institucion.getId(), institucion.getNombre(), guardado.getId());
        return guardado;
    }

    // El nombre y el CUIT son unicos en TODO el sistema, no por institucion: dos colegios
    // distintos no pueden llamarse igual ni compartir CUIT.
    private void verificarQueNoExista(AltaInstitucionFormDto form) {
        String nombre = form.getNombreInstitucion().trim();
        // Normalizado antes de comparar: si no, '30123456781' y '30-12345678-1'
        // pasarian como dos CUIT distintos siendo el mismo numero.
        String cuit = Cuit.normalizar(form.getCuit());

        if (institucionRepository.existsByNombre(nombre)) {
            throw new IllegalArgumentException("Ya hay una institución registrada con ese nombre.");
        }
        if (cuit != null && institucionRepository.existsByCuit(cuit)) {
            throw new IllegalArgumentException("Ya hay una institución registrada con ese CUIT.");
        }
    }

    // Deja null si el campo opcional vino vacio: asi el UNIQUE del CUIT no choca entre
    // dos instituciones que no lo declararon, porque en SQL un NULL no repite a otro.
    private String vacioANulo(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
