package edu.cent35.asistencias.service;
import edu.cent35.asistencias.dto.InstantaneaIdentidad;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.RolCodigo;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Operaciones sobre los usuarios (admins) de la institucion del tenant
 * actual. Cubre RF-06.
 * <p>
 * <b>Aislamiento multi-tenant</b>: combina dos defensas:
 * <ol>
 *   <li>El filtro de Hibernate {@code "tenant"} (activado por
 *       {@code TenantFilterAspect}) hace que las queries automaticas
 *       de Spring Data filtren por institucion.</li>
 *   <li>{@link #ensureMismoTenant(Usuario)} valida explicitamente que
 *       cada usuario accedido pertenezca al tenant actual antes de
 *       devolver/modificar - protege casos donde el filtro no aplica
 *       (ej: {@code findById}).</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PersonaRepository personaRepository;
    private final PersonaService personaService;
    private final PasswordEncoder passwordEncoder;

    // Lista los usuarios de la institucion actual (activos + inactivos, ordenados).
    @Transactional(readOnly = true)
    public List<Usuario> listarMiInstitucion() {
        Long tenantId = TenantContext.getRequired();
        return usuarioRepository.listarDelTenant(tenantId);
    }

    @Transactional(readOnly = true)
    // Busca por id validando que el usuario sea de la misma institución.
    public Usuario buscarPorId(Long id) {
        Usuario u = usuarioRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + id));
        ensureMismoTenant(u);
        return u;
    }

    /**
     * Crea una cuenta de administrador en la institución actual.
     *
     * <p><b>El rol no se elige: siempre es ADMIN.</b> La cuenta de rol INSTITUCION se crea una
     * sola vez, en el alta pública de la institución, y es la que representa al establecimiento.
     * Dejar que una institución fabrique otras cuentas de su mismo rango no aporta nada y sí
     * multiplica quién puede administrarlo todo. Que el rol no sea un parámetro es lo que hace
     * imposible pedirlo desde afuera: no alcanza con sacar el desplegable de la pantalla,
     * porque un formulario se puede armar a mano.
     *
     * <p>La contraseña se hashea con BCrypt antes de guardarse.
     */
    @Transactional
    public Usuario crear(String username, String email, String passwordPlano,
                         String nombre, String apellido) {

        Long tenantId = TenantContext.getRequired();

        if (usuarioRepository.existsByUsernameAndInstitucionId(username, tenantId)) {
            throw new IllegalArgumentException("El username '" + username + "' ya existe en esta institucion");
        }
        if (usuarioRepository.existsByEmailAndInstitucionId(email, tenantId)) {
            throw new IllegalArgumentException("El email '" + email + "' ya existe en esta institucion");
        }

        Rol rol = rolRepository.findByCodigo(RolCodigo.ADMIN.name())
            .orElseThrow(() -> new IllegalStateException("Rol no encontrado: " + RolCodigo.ADMIN));

        // La identidad va aparte de la cuenta (ADR-0016). Se crea una persona nueva y no se
        // intenta reutilizar una existente: acá no se pide el DNI, así que cruzar por nombre
        // sería adivinar. Unificar dos personas que son la misma es una acción deliberada.
        Persona persona = Persona.builder()
            .nombre(nombre.trim())
            .apellido(normalizarApellido(apellido))
            .email(email.trim())
            .build();
        persona.setInstitucionId(tenantId);
        persona = personaRepository.save(persona);

        Usuario nuevo = Usuario.builder()
            .username(username.trim())
            .email(email.trim())
            .passwordHash(passwordEncoder.encode(passwordPlano))
            .persona(persona)
            .rol(rol)
            .activo(true)
            .build();
        nuevo.setInstitucionId(tenantId);

        Usuario saved = usuarioRepository.save(nuevo);
        log.info("Usuario creado: id={}, username={}, rol=ADMIN, institucion_id={}",
                 saved.getId(), saved.getUsername(), tenantId);
        return saved;
    }

    // Un apellido en blanco se guarda como NULL: "no corresponde" y "cadena vacia" no son
    // lo mismo, y dejar '' obligaria a cada consulta a distinguirlos. Ver migracion V014.
    private static String normalizarApellido(String apellido) {
        return (apellido == null || apellido.isBlank()) ? null : apellido.trim();
    }

    /**
     * Edita nombre, apellido, correo y estado. El username, la contraseña y <b>el rol</b> no
     * se tocan acá.
     *
     * <p><b>Por qué el rol es inmutable.</b> El rol no es un atributo de la cuenta, es lo que
     * la cuenta <i>es</i>. Cambiarlo reescribe hacia atrás el sentido de todo lo que esa cuenta
     * hizo: un consentimiento registrado por un administrador, leído después de convertirlo en
     * institución, parece haber sido registrado por la institución. Para que alguien pase a
     * tener otro rol se le da de baja la cuenta y se crea una nueva, y así el historial queda
     * partido en dos donde efectivamente cambió quién era.
     *
     * <p><b>Por qué una cuenta INSTITUCION no se puede desactivar.</b> Es la cuenta que
     * representa al establecimiento y la única que administra usuarios, carreras y los datos de
     * la institución. Desactivarla por error deja al colegio entero sin nadie que pueda entrar
     * a repararlo: no hay a quién pedírselo desde adentro. La baja de una institución es una
     * operación de otro nivel y no puede estar a un click de distancia en la misma grilla que
     * las cuentas operativas.
     */
    @Transactional
    public Usuario actualizar(Long id, String nombre, String apellido, String email,
                              boolean activo, Long usuarioActualId) {
        return actualizar(id, nombre, apellido, email, activo, usuarioActualId, false);
    }

    @Transactional
    // Igual que el anterior, con la confirmacion ya dada.
    public Usuario actualizar(Long id, String nombre, String apellido, String email,
                              boolean activo, Long usuarioActualId, boolean confirmado) {

        Usuario u = buscarPorId(id);

        boolean esCuentaDeInstitucion = RolCodigo.INSTITUCION.name().equals(u.getRol().getCodigo());

        // Si se cambia email, validar unicidad por institucion
        String emailNuevo = email.trim();
        boolean cambioElCorreo = !emailNuevo.equalsIgnoreCase(u.getEmail());
        if (cambioElCorreo
                && usuarioRepository.existsByEmailAndInstitucionId(emailNuevo, u.getInstitucionId())) {
            throw new IllegalArgumentException("El email '" + emailNuevo + "' ya existe en esta institucion");
        }

        // La cuenta de la institucion no se da de baja desde la administracion de usuarios.
        // Se comprueba en el servidor y no solo escondiendo la casilla: la casilla se puede
        // mandar igual armando el formulario a mano.
        if (esCuentaDeInstitucion && !activo) {
            throw new IllegalArgumentException(
                "La cuenta de la institución no se puede dar de baja: es la única que administra "
                + "usuarios y datos del establecimiento, y sin ella nadie podría volver a entrar "
                + "a repararlo.");
        }

        if (id.equals(usuarioActualId) && !activo) {
            throw new IllegalArgumentException(
                "No podés desactivarte a vos mismo. Pedíselo a otra cuenta con permisos.");
        }

        // La confirmacion va DESPUES de validar: preguntar "seguro que querés cambiarlo en todos
        // sus roles" para despues rechazar el formulario por un correo repetido seria hacer
        // decidir sobre algo que no se iba a guardar igual.
        //
        // Si esta persona ademas da clases, el cambio de nombre se ve en la ficha del docente y
        // en los listados de asistencia.
        if (!confirmado && personaService.edicionRequiereConfirmacion(u.getPersona())) {
            String propuesto = (apellido == null || apellido.isBlank())
                ? nombre.trim() : apellido.trim() + ", " + nombre.trim();
            throw new ConfirmacionRequeridaException(
                personaService.impactoDeEdicion(u.getPersona(), propuesto));
        }

        // El nombre se edita del lado de la persona; el correo de acceso, del lado de la cuenta.
        //
        // La cuenta institucional no tiene persona (V018), y entonces no hay nombre que editar:
        // el que muestra es el de la institución, y ese se cambia desde "Mi institución", que es
        // donde corresponde. Lo que sí se puede editar acá es su correo y su estado.
        Persona persona = u.getPersona();
        if (persona != null) {
            InstantaneaIdentidad antes = InstantaneaIdentidad.de(persona);
            persona.setNombre(nombre.trim());
            persona.setApellido(normalizarApellido(apellido));
            personaRepository.save(persona);
            personaService.registrarCambios(persona, antes, usuarioActualId, "USUARIO");
        }

        u.setEmail(emailNuevo);
        u.setActivo(activo);
        // Solo al desactivar: al reactivar se limpia, porque arrastrar quien la dio de baja
        // describiria una baja que ya no esta vigente.
        u.setDadoDeBajaPor(activo ? null : usuarioActualId);

        // Cambiar la direccion invalida la verificacion: la anterior fue confirmada, esta no.
        // Sin esto la cuenta seguiria figurando verificada con un correo que nadie probo, y
        // la recuperacion de contrasena pasaria a apuntar a ese buzon sin ninguna garantia.
        if (cambioElCorreo && u.getEmailVerificadoEn() != null) {
            u.setEmailVerificadoEn(null);
            log.info("Verificacion invalidada: el usuario {} cambio su correo y debe confirmarlo",
                     u.getId());
        }

        Usuario saved = usuarioRepository.save(u);
        log.info("Usuario actualizado: id={}, username={}, activo={}",
                 saved.getId(), saved.getUsername(), activo);
        return saved;
    }


    // Verifica que el usuario pertenezca al tenant actual; defensa en profundidad.
    private void ensureMismoTenant(Usuario u) {
        Long tenantId = TenantContext.getRequired();
        if (!tenantId.equals(u.getInstitucionId())) {
            log.warn("Intento de acceso cross-tenant: tenantActual={}, usuarioInstitucion={}",
                     tenantId, u.getInstitucionId());
            // Camuflamos como "no encontrado" para no filtrar la existencia entre tenants
            throw new EntityNotFoundException("Usuario no encontrado");
        }
    }

    /**
     * Fija la contraseña de la propia cuenta.
     *
     * <p><b>No pide la contraseña actual, y es a propósito.</b> Quien llega hasta acá ya
     * acreditó el control del correo de la cuenta con un código de un solo uso, que es una
     * prueba más fuerte: la contraseña actual la puede tener quien miró por encima del hombro
     * o quien encontró una sesión abierta, y el código exige además entrar al buzón.
     *
     * <p>Pedir las dos cosas sonaría más seguro pero no agrega nada frente a un atacante que
     * ya tiene el correo, y en cambio deja afuera al caso más común y más legítimo: alguien
     * que quiere cambiar la contraseña justamente porque no está seguro de cuál es.
     *
     * <p>La comprobación del código NO ocurre acá: este método confía en que quien lo llama ya
     * la hizo. Es el motivo por el que no es público en ningún controlador sin ese paso previo.
     */
    @Transactional
    public void fijarPasswordPropia(Long usuarioId, String nueva) {
        Usuario u = buscarPorId(usuarioId);
        if (passwordEncoder.matches(nueva, u.getPasswordHash())) {
            throw new IllegalArgumentException(
                "La contraseña nueva tiene que ser distinta de la que ya tenías.");
        }
        u.setPasswordHash(passwordEncoder.encode(nueva));
        usuarioRepository.save(u);
        log.info("Password cambiada por el propio usuario: id={}", usuarioId);
    }
}
