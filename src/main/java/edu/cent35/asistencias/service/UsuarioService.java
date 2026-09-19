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
import edu.cent35.asistencias.validacion.UsuarioValidoValidator;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

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

    // Horas entre una contrasena nueva y la siguiente. En cero, sin limite.
    @Value("${app.password.horas-entre-cambios}")
    private long horasEntreCambios;

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
            throw new IllegalArgumentException("Ya hay un usuario '" + username + "' en esta institución");
        }
        if (usuarioRepository.existsByEmailAndInstitucionId(email, tenantId)) {
            throw new IllegalArgumentException("El correo '" + email + "' ya está en uso en esta institución");
        }

        Rol rol = rolRepository.findByCodigo(RolCodigo.ADMIN.name())
            .orElseThrow(() -> new IllegalStateException("Rol no encontrado: " + RolCodigo.ADMIN));

        // La identidad va aparte de la cuenta (ADR-0016). Se crea una persona nueva y no se
        // intenta reutilizar una existente: acá no se pide el DNI, así que cruzar por nombre
        // sería adivinar. Unificar dos personas que son la misma es una acción deliberada.
        Persona persona = Persona.builder()
            .nombre(NombrePropio.normalizar(nombre))
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
    // El que viene, con mayuscula inicial como el nombre (NombrePropio).
    private static String normalizarApellido(String apellido) {
        return (apellido == null || apellido.isBlank()) ? null : NombrePropio.normalizar(apellido);
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
                              Long usuarioActualId) {
        return actualizar(id, nombre, apellido, email, usuarioActualId, false);
    }

    /**
     * Igual que el anterior, con la confirmación ya dada.
     *
     * <p>No toca el estado de la cuenta. Darla de baja dejó de ser una casilla de este
     * formulario --se guardaba junto con el nombre y el correo, sin preguntar nada-- y pasó a
     * ser su propia acción: {@link #darDeBaja} y {@link #reactivar}.
     */
    @Transactional
    public Usuario actualizar(Long id, String nombre, String apellido, String email,
                              Long usuarioActualId, boolean confirmado) {

        Usuario u = buscarPorId(id);

        // Si se cambia email, validar unicidad por institucion
        String emailNuevo = email.trim();
        boolean cambioElCorreo = !emailNuevo.equalsIgnoreCase(u.getEmail());
        if (cambioElCorreo
                && usuarioRepository.existsByEmailAndInstitucionId(emailNuevo, u.getInstitucionId())) {
            throw new IllegalArgumentException("El correo '" + emailNuevo + "' ya está en uso en esta institución");
        }

        // Lo que se va a guardar, que es tambien lo que se muestra si hay que confirmar.
        String nombreNuevo   = NombrePropio.normalizar(nombre);
        String apellidoNuevo = normalizarApellido(apellido);

        // La confirmacion va DESPUES de validar: preguntar "seguro que querés cambiarlo en todos
        // sus roles" para despues rechazar el formulario por un correo repetido seria hacer
        // decidir sobre algo que no se iba a guardar igual.
        //
        // Si esta persona ademas da clases, el cambio de nombre se ve en la ficha del docente y
        // en los listados de asistencia.
        if (!confirmado && personaService.edicionRequiereConfirmacion(u.getPersona())) {
            String propuesto = apellidoNuevo == null ? nombreNuevo : apellidoNuevo + ", " + nombreNuevo;
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
            persona.setNombre(nombreNuevo);
            persona.setApellido(apellidoNuevo);
            personaRepository.save(persona);
            personaService.registrarCambios(persona, antes, usuarioActualId, "USUARIO");
        }

        u.setEmail(emailNuevo);

        // Cambiar la direccion invalida la verificacion: la anterior fue confirmada, esta no.
        // Sin esto la cuenta seguiria figurando verificada con un correo que nadie probo, y
        // la recuperacion de contrasena pasaria a apuntar a ese buzon sin ninguna garantia.
        if (cambioElCorreo && u.getEmailVerificadoEn() != null) {
            u.setEmailVerificadoEn(null);
            log.info("Verificacion invalidada: el usuario {} cambio su correo y debe confirmarlo",
                     u.getId());
        }

        Usuario saved = usuarioRepository.save(u);
        log.info("Usuario actualizado: id={}, username={}", saved.getId(), saved.getUsername());
        return saved;
    }

    /**
     * Da de baja una cuenta: deja de poder iniciar sesión.
     *
     * <p>Es su propia operación y no una casilla del formulario de datos. Como casilla se
     * guardaba junto con el nombre y el correo, sin preguntar nada, y desactivar a alguien
     * quedaba a un clic distraído de distancia; ahora la pantalla pide confirmarlo, igual que
     * la baja de un docente.
     *
     * <p>Las dos guardas se comprueban acá y no solo escondiendo el botón: un POST armado a
     * mano llega igual.
     */
    @Transactional
    public Usuario darDeBaja(Long id, Long usuarioActualId) {
        Usuario u = buscarPorId(id);

        if (RolCodigo.INSTITUCION.name().equals(u.getRol().getCodigo())) {
            throw new IllegalArgumentException(
                "La cuenta de la institución no se puede dar de baja: es la única que administra "
                + "usuarios y datos del establecimiento, y sin ella nadie podría volver a entrar "
                + "a repararlo.");
        }
        if (id.equals(usuarioActualId)) {
            throw new IllegalArgumentException(
                "No podés desactivarte a vos mismo. Pedíselo a otra cuenta con permisos.");
        }

        u.setActivo(false);
        u.setDadoDeBajaPor(usuarioActualId);
        Usuario guardado = usuarioRepository.save(u);
        log.info("Usuario dado de baja: id={}, username={}, por={}",
                 guardado.getId(), guardado.getUsername(), usuarioActualId);
        return guardado;
    }

    // Vuelve a habilitar una cuenta dada de baja. Se limpia quien la dio de baja: arrastrarlo
    // describiria una baja que ya no esta vigente.
    @Transactional
    public Usuario reactivar(Long id) {
        Usuario u = buscarPorId(id);
        u.setActivo(true);
        u.setDadoDeBajaPor(null);
        Usuario guardado = usuarioRepository.save(u);
        log.info("Usuario reactivado: id={}, username={}", guardado.getId(), guardado.getUsername());
        return guardado;
    }


    /**
     * Edita los datos de la propia cuenta desde Mi cuenta: el usuario y, si la cuenta es de una
     * persona, su nombre. El correo no pasa por acá: cambia recién con un código al correo
     * actual y otro al nuevo ({@code VerificacionCuentaService}).
     *
     * <p><b>El usuario se puede cambiar, pero solo su titular.</b> Es lo que esa persona teclea
     * cada vez que entra, y si quedó mal escrito tiene que poder corregirlo. La institución, en
     * cambio, no puede cambiarle el usuario a otra cuenta: le cambiaría a alguien cómo entra sin
     * que se entere. Lo ya registrado no se pierde, porque el historial apunta a la cuenta y no
     * al texto del usuario; lo único que conserva el nombre viejo es un PDF ya descargado.
     *
     * <p><b>Único entre todas las instituciones, no solo en esta.</b> El login busca el usuario
     * en todas ({@code CargadorDeUsuarios}): si otra institución ya lo usa, el ingreso con
     * usuario queda ambiguo para las dos cuentas, y la otra persona deja de poder entrar con el
     * suyo sin haber tocado nada.
     *
     * <p><b>El nombre pide confirmación solo si cambia</b> y esa persona además da clases, igual
     * que en la edición de usuarios. Si lo único que cambió es el usuario no se pregunta nada:
     * preguntar por lo que no cambió entrena a aceptar sin leer.
     */
    @Transactional
    public Usuario actualizarPropia(Long usuarioId, String username, String nombre,
                                    String apellido, boolean confirmado) {

        Usuario u = buscarPorId(usuarioId);

        String usuarioNuevo = username == null ? "" : username.trim();
        boolean cambiaElUsuario = !usuarioNuevo.equals(u.getUsername());
        if (cambiaElUsuario) {
            String problema = usuarioNuevo.isEmpty()
                ? "El usuario es obligatorio." : UsuarioValidoValidator.problema(usuarioNuevo);
            if (problema != null) {
                throw new IllegalArgumentException(problema);
            }
            if (usuarioRepository.contarUsernameEnOtrasCuentas(usuarioNuevo, u.getId()) > 0) {
                throw new IllegalArgumentException("Ese usuario ya lo usa otra cuenta. Elegí otro.");
            }
        }

        // La cuenta institucional no tiene persona (V018): su nombre es el del establecimiento y
        // se cambia desde Mi institución, que es donde se cambian los datos del establecimiento.
        Persona persona = u.getPersona();
        if (persona != null) {
            String nombreNuevo   = NombrePropio.normalizar(nombre);
            String apellidoNuevo = normalizarApellido(apellido);
            if (nombreNuevo == null || nombreNuevo.isEmpty()) {
                throw new IllegalArgumentException("El nombre es obligatorio.");
            }
            boolean cambiaElNombre = !nombreNuevo.equals(persona.getNombre())
                || !Objects.equals(apellidoNuevo, persona.getApellido());

            if (cambiaElNombre) {
                if (!confirmado && personaService.edicionRequiereConfirmacion(persona)) {
                    String propuesto = apellidoNuevo == null
                        ? nombreNuevo : apellidoNuevo + ", " + nombreNuevo;
                    throw new ConfirmacionRequeridaException(
                        personaService.impactoDeEdicion(persona, propuesto));
                }
                InstantaneaIdentidad antes = InstantaneaIdentidad.de(persona);
                persona.setNombre(nombreNuevo);
                persona.setApellido(apellidoNuevo);
                personaRepository.save(persona);
                // Origen USUARIO: el cambio entra por la cuenta. Que lo hizo la propia persona lo
                // dice usuario_id, que es el de esa misma cuenta.
                personaService.registrarCambios(persona, antes, usuarioId, "USUARIO");
            }
        }

        if (cambiaElUsuario) {
            log.info("Usuario cambiado por su titular: id={}, antes='{}', ahora='{}'",
                     u.getId(), u.getUsername(), usuarioNuevo);
            u.setUsername(usuarioNuevo);
        }
        return usuarioRepository.save(u);
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

        String impedimento = motivoQueImpideCambiarPassword(u);
        if (impedimento != null) {
            throw new IllegalStateException(impedimento);
        }
        if (passwordEncoder.matches(nueva, u.getPasswordHash())) {
            throw new IllegalArgumentException(
                "La contraseña nueva tiene que ser distinta de la que ya tenías.");
        }
        u.setPasswordHash(passwordEncoder.encode(nueva));
        sellarCambioDePassword(u);
        usuarioRepository.save(u);
        log.info("Password cambiada por el propio usuario: id={}", usuarioId);
    }

    // ========================================================================
    //  Limite de un cambio de contrasena cada 24 horas
    // ========================================================================

    /**
     * Por qué esta cuenta no puede fijar una contraseña nueva ahora, o {@code null} si puede.
     *
     * <p>Devuelve el texto y no un booleano porque el mensaje es la mitad de la función: sin
     * él la persona ve un rechazo y no sabe si esperar, si insistir o si pedir ayuda. Mismo
     * criterio que {@code DocenteService.motivoQueImpideLaBaja}.
     *
     * <p><b>Lo aplican dos caminos.</b> El cambio voluntario desde Mi cuenta y la recuperación
     * pública, que no se conocen entre sí. La regla en sí vive en {@code Usuario} —donde
     * ninguno de los dos puede tenerla a medias— y acá se le pone la ventana configurada y las
     * palabras.
     */
    // Sin @Transactional a proposito: no toca la base, calcula sobre la entidad que ya
    // trajo quien llama. Anotarlo abriria una transaccion por fila del listado.
    public String motivoQueImpideCambiarPassword(Usuario u) {
        if (horasEntreCambios <= 0) {
            return null;                              // limite desactivado por configuracion
        }
        if (u.puedeCambiarPassword(LocalDateTime.now(), Duration.ofHours(horasEntreCambios))) {
            return null;
        }
        return "Ya cambiaste tu contraseña hace menos de " + horasEntreCambios + " horas. "
             + "Esperá a que pase ese plazo, o pedile a otro administrador o a la institución "
             + "que te habilite un cambio nuevo.";
    }

    /**
     * Deja la cuenta marcada como recién cambiada y consume el destrabe si lo había.
     *
     * <p>No guarda: lo hace quien llama, dentro de su propia transacción. Así el sello y el
     * hash nuevo entran o no entran juntos —una contraseña cambiada sin sellar dejaría la
     * ventana abierta, y un sello sin contraseña nueva la cerraría por nada—.
     */
    public void sellarCambioDePassword(Usuario u) {
        u.setPasswordCambiadaEn(LocalDateTime.now());
        u.setCambioPasswordHabilitadoEn(null);
        u.setCambioPasswordHabilitadoPor(null);
    }

    /**
     * Levanta el bloqueo de una cuenta para que pueda volver a cambiar la contraseña.
     *
     * <p><b>No fija ni ve la contraseña.</b> Solo habilita un intento: la persona vuelve a
     * recuperarla por correo como siempre. Es lo que mantiene la propiedad de que la
     * contraseña la conoce únicamente su titular — un administrador que la tipeara podría
     * después entrar con ella.
     *
     * <p><b>Nadie se destraba a sí mismo.</b> Si pudiera, el límite no existiría: alcanzaría
     * con levantarlo antes de cada cambio. Por eso la cuenta que habilita tiene que ser otra,
     * que es además lo que el pedido dice —"a través de otro administrador o la institución"—.
     */
    @Transactional
    public void habilitarCambioDePassword(Long objetivoId, Long quienHabilitaId) {
        if (objetivoId != null && objetivoId.equals(quienHabilitaId)) {
            throw new IllegalArgumentException(
                "No podés habilitarte el cambio a vos mismo. Tiene que hacerlo otro "
                + "administrador o la institución.");
        }
        // buscarPorId valida el tenant y responde "no encontrado" si es de otra institucion.
        Usuario objetivo = buscarPorId(objetivoId);

        objetivo.setCambioPasswordHabilitadoEn(LocalDateTime.now());
        objetivo.setCambioPasswordHabilitadoPor(quienHabilitaId);
        usuarioRepository.save(objetivo);

        log.info("Cambio de contrasena habilitado: usuario={}, por usuario={}",
                 objetivoId, quienHabilitaId);
    }
}
