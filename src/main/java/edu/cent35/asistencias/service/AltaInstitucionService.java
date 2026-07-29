package edu.cent35.asistencias.service;

import edu.cent35.asistencias.dto.AltaInstitucionFormDto;
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

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Da de alta una institución junto con su primera cuenta. Es la única operación del sistema
 * que corre sin TenantContext, porque se ejecuta antes de que el tenant exista: por eso está
 * protegida por una clave de configuración y no por un rol, y por eso valida el aislamiento
 * a mano en vez de apoyarse en el filtro de Hibernate.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AltaInstitucionService {

    private final InstitucionRepository institucionRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.instalacion.clave}")
    private String claveConfigurada;

    /**
     * Crea la institución y su cuenta inicial en una sola transacción: una institución sin
     * cuenta con la cual entrar sería inservible, y quedaría ocupando el nombre.
     *
     * @return el usuario creado, ya verificado
     */
    @Transactional
    public Usuario darDeAlta(AltaInstitucionFormDto form) {
        verificarClave(form.getClaveInstalacion());

        String nombreInst = form.getNombreInstitucion().trim();
        String cuit = vacioANulo(form.getCuit());

        // El nombre y el CUIT son unicos en TODO el sistema, no por institucion: dos colegios
        // distintos no pueden llamarse igual ni compartir CUIT.
        if (institucionRepository.existsByNombre(nombreInst)) {
            throw new IllegalArgumentException(
                "Ya hay una institución registrada con ese nombre.");
        }
        if (cuit != null && institucionRepository.existsByCuit(cuit)) {
            throw new IllegalArgumentException(
                "Ya hay una institución registrada con ese CUIT.");
        }

        Institucion institucion = institucionRepository.save(
            Institucion.builder()
                .nombre(nombreInst)
                .cuit(cuit)
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
            // Nace verificada a proposito: quien la crea ya demostro tener la clave de
            // instalacion, que es una prueba mas fuerte que un codigo por correo. Si tuviera
            // que verificarse, una institucion sin servidor de correo quedaria sin acceso a
            // su propia cuenta de gestion, que es exactamente el problema que se evita.
            .emailVerificadoEn(LocalDateTime.now())
            .build();
        usuario.setInstitucionId(institucion.getId());

        Usuario guardado = usuarioRepository.save(usuario);
        log.info("Alta de institucion: institucion_id={}, nombre='{}', usuario_id={}, username={}",
                 institucion.getId(), nombreInst, guardado.getId(), guardado.getUsername());
        return guardado;
    }

    // Compara en tiempo constante para no filtrar por cuanto tarda cuantos caracteres acerto.
    private void verificarClave(String ingresada) {
        if (claveConfigurada == null || claveConfigurada.isBlank()) {
            log.error("Intento de alta de institucion con app.instalacion.clave sin configurar");
            throw new IllegalStateException(
                "El alta de instituciones está deshabilitada: falta configurar la clave de instalación.");
        }
        byte[] a = claveConfigurada.getBytes(StandardCharsets.UTF_8);
        byte[] b = ingresada == null ? new byte[0] : ingresada.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(a, b)) {
            log.warn("Alta de institucion rechazada: clave de instalacion incorrecta");
            throw new IllegalArgumentException("La clave de instalación no es correcta.");
        }
    }

    private String vacioANulo(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
