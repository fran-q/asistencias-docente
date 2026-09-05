package edu.cent35.asistencias.service;
import edu.cent35.asistencias.repository.PersonaRepository;
import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.RolCodigo;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de UsuarioService.
 * <p>
 * Mockean repos y password encoder. La logica de tenant se ejercita
 * seteando manualmente {@link TenantContext} en cada test.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final Long USUARIO_ACTUAL = 100L;

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private RolRepository rolRepository;
    @Mock private PersonaRepository personaRepository;
    @Mock private PersonaService personaService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private UsuarioService service;

    private Rol rolInstitucion;
    private Rol rolAdmin;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_A);
        // La cuenta y la identidad se guardan por separado desde ADR-0016.
        lenient().when(personaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        rolInstitucion = Rol.builder().id((short) 1).codigo(RolCodigo.INSTITUCION.name()).descripcion("Institucion").build();
        rolAdmin       = Rol.builder().id((short) 2).codigo(RolCodigo.ADMIN.name()).descripcion("Admin").build();

        // lenient: algunos tests no usan password encoder ni repo de roles
        lenient().when(passwordEncoder.encode(any())).thenReturn("hash-fake");
        lenient().when(rolRepository.findByCodigo(RolCodigo.ADMIN.name())).thenReturn(Optional.of(rolAdmin));
        lenient().when(rolRepository.findByCodigo(RolCodigo.INSTITUCION.name())).thenReturn(Optional.of(rolInstitucion));
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    // ====================================================================
    //  CREAR
    // ====================================================================

    @Test
    @DisplayName("crear: persiste con datos validos y setea tenant del contexto")
    void crear_ok() {
        when(usuarioRepository.existsByUsernameAndInstitucionId("nuevo", TENANT_A)).thenReturn(false);
        when(usuarioRepository.existsByEmailAndInstitucionId("n@x.com", TENANT_A)).thenReturn(false);
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Usuario creado = service.crear("nuevo", "n@x.com", "pass1234", "Pepe", "Perez");

        assertThat(creado.getUsername()).isEqualTo("nuevo");
        assertThat(creado.getInstitucionId()).isEqualTo(TENANT_A);
        assertThat(creado.getPasswordHash()).isEqualTo("hash-fake");
        assertThat(creado.getRol().getCodigo()).isEqualTo(RolCodigo.ADMIN.name());
        verify(passwordEncoder).encode("pass1234");
    }

    @Test
    @DisplayName("crear: falla si username ya existe en la institucion")
    void crear_usernameDuplicado() {
        when(usuarioRepository.existsByUsernameAndInstitucionId("dup", TENANT_A)).thenReturn(true);

        assertThatThrownBy(() -> service.crear("dup", "x@x.com", "pass1234", "P", "P"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("username");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: falla si email ya existe en la institucion")
    void crear_emailDuplicado() {
        when(usuarioRepository.existsByUsernameAndInstitucionId(any(), any())).thenReturn(false);
        when(usuarioRepository.existsByEmailAndInstitucionId("dup@x.com", TENANT_A)).thenReturn(true);

        assertThatThrownBy(() -> service.crear("u", "dup@x.com", "pass1234", "P", "P"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("email");
        verify(usuarioRepository, never()).save(any());
    }

    // ====================================================================
    //  ACTUALIZAR / verificacion del correo (RF-57)
    // ====================================================================

    @Test
    @DisplayName("actualizar: cambiar el correo invalida la verificacion")
    void actualizar_cambiarCorreoInvalidaLaVerificacion() {
        Usuario u = usuarioActivo(10L, RolCodigo.ADMIN);
        u.setEmailVerificadoEn(java.time.LocalDateTime.now());
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(u));
        when(usuarioRepository.existsByEmailAndInstitucionId("otro@x.com", TENANT_A))
            .thenReturn(false);
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.actualizar(10L, "N", "A", "otro@x.com", true, USUARIO_ACTUAL);

        assertThat(u.getEmailVerificadoEn())
            .as("la direccion anterior estaba comprobada; esta no, asi que hay que "
                + "confirmarla antes de seguir operando")
            .isNull();
    }

    @Test
    @DisplayName("actualizar: dejar el mismo correo no obliga a verificar de nuevo")
    void actualizar_mismoCorreoConservaLaVerificacion() {
        Usuario u = usuarioActivo(10L, RolCodigo.ADMIN);
        java.time.LocalDateTime cuando = java.time.LocalDateTime.now().minusDays(3);
        u.setEmailVerificadoEn(cuando);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(u));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Se edita el nombre, no el correo.
        service.actualizar(10L, "Nombre nuevo", "A", u.getEmail(), true, USUARIO_ACTUAL);

        assertThat(u.getEmailVerificadoEn())
            .as("bloquear a alguien por editarle el nombre seria un castigo sin motivo")
            .isEqualTo(cuando);
    }

    // ====================================================================
    //  ACTUALIZAR / autoproteccion
    // ====================================================================

    @Test
    @DisplayName("actualizar: rechaza desactivarse a si mismo")
    void actualizar_noAutoDesactivar() {
        Usuario yo = usuarioActivo(USUARIO_ACTUAL, RolCodigo.ADMIN);
        when(usuarioRepository.findById(USUARIO_ACTUAL)).thenReturn(Optional.of(yo));

        assertThatThrownBy(() -> service.actualizar(
            USUARIO_ACTUAL, "N", "A", "yo@x.com", /*activo=*/ false, USUARIO_ACTUAL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("desactivarte");
    }

    @Test
    @DisplayName("actualizar: una cuenta de INSTITUCION no se puede dar de baja")
    void actualizar_institucionNoSeDaDeBaja() {
        // Es la unica cuenta que administra el establecimiento. Desactivarla por error
        // deja al colegio sin nadie que pueda entrar a repararlo.
        Usuario institucion = usuarioActivo(50L, RolCodigo.INSTITUCION);
        when(usuarioRepository.findById(50L)).thenReturn(Optional.of(institucion));

        assertThatThrownBy(() -> service.actualizar(
            50L, "Colegio", null, "col@x.com", /*activo=*/ false, USUARIO_ACTUAL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no se puede dar de baja");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("actualizar: una cuenta ADMIN si se puede dar de baja")
    void actualizar_adminSiSeDaDeBaja() {
        Usuario admin = usuarioActivo(50L, RolCodigo.ADMIN);
        when(usuarioRepository.findById(50L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Usuario actualizado = service.actualizar(
            50L, "N", "A", "o@x.com", /*activo=*/ false, USUARIO_ACTUAL);

        assertThat(actualizado.getActivo()).isFalse();
        verify(usuarioRepository).save(any());
    }

    @Test
    @DisplayName("actualizar: no toca el rol aunque la cuenta cambie de datos")
    void actualizar_noCambiaElRol() {
        // El rol no es un parametro del metodo: no hay forma de pedir el cambio ni
        // armando la peticion a mano. Este test lo fija para que no vuelva a agregarse.
        Usuario admin = usuarioActivo(50L, RolCodigo.ADMIN);
        when(usuarioRepository.findById(50L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Usuario actualizado = service.actualizar(
            50L, "Otro", "Nombre", "otro@x.com", true, USUARIO_ACTUAL);

        assertThat(actualizado.getRol().getCodigo()).isEqualTo(RolCodigo.ADMIN.name());
    }

    @Test
    @DisplayName("crear: siempre crea ADMIN, nunca INSTITUCION")
    void crear_siempreAdmin() {
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Usuario creado = service.crear("nuevo2", "n2@x.com", "pass1234", "Ana", "Diaz");

        assertThat(creado.getRol().getCodigo()).isEqualTo(RolCodigo.ADMIN.name());
    }

    @Test
    @DisplayName("crear: un apellido en blanco se guarda como NULL, no como cadena vacia")
    void crear_apellidoEnBlancoEsNull() {
        // NULL significa "no corresponde" --una institucion no es una persona--; la
        // cadena vacia diria "tiene apellido y es el vacio", que no quiere decir nada.
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Usuario creado = service.crear("sinape", "s@x.com", "pass1234", "Colegio", "   ");

        assertThat(creado.getPersona().getApellido()).isNull();
        assertThat(creado.getNombreParaMostrar()).isEqualTo("Colegio");
    }

    // ====================================================================
    //  AISLAMIENTO MULTI-TENANT
    // ====================================================================

    @Test
    @DisplayName("buscarPorId: tira EntityNotFound si el usuario es de otro tenant (camufla cross-tenant)")
    void buscarPorId_crossTenant() {
        Usuario ajeno = usuarioActivo(999L, RolCodigo.ADMIN);
        ajeno.setInstitucionId(TENANT_B);  // otro tenant
        when(usuarioRepository.findById(999L)).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.buscarPorId(999L))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("no encontrado");
    }

    @Test
    @DisplayName("buscarPorId: tira EntityNotFound si el id no existe")
    void buscarPorId_notFound() {
        when(usuarioRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(999L))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("listarMiInstitucion: delega al repo con el tenant del contexto")
    void listarMiInstitucion_filtraPorTenant() {
        service.listarMiInstitucion();
        verify(usuarioRepository).listarDelTenant(TENANT_A);
    }

    // ====================================================================
    //  Una contrasena nueva cada 24 horas
    // ====================================================================

    @Test
    @DisplayName("fijarPasswordPropia: rechaza si ya cambio hace menos de la ventana")
    void password_rechazaDentroDeLaVentana() {
        conVentanaDe(24);
        Usuario u = usuarioActivo(50L, RolCodigo.ADMIN);
        u.setPasswordCambiadaEn(LocalDateTime.now().minusHours(2));
        when(usuarioRepository.findById(50L)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.fijarPasswordPropia(50L, "NuevaClave1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("menos de 24 horas");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("fijarPasswordPropia: pasada la ventana deja cambiar y vuelve a sellar")
    void password_dejaCambiarPasadaLaVentana() {
        conVentanaDe(24);
        Usuario u = usuarioActivo(51L, RolCodigo.ADMIN);
        LocalDateTime hace25Horas = LocalDateTime.now().minusHours(25);
        u.setPasswordCambiadaEn(hace25Horas);
        when(usuarioRepository.findById(51L)).thenReturn(Optional.of(u));

        service.fijarPasswordPropia(51L, "NuevaClave1");

        verify(usuarioRepository).save(u);
        assertThat(u.getPasswordCambiadaEn())
            .as("sin volver a sellar, la ventana quedaria abierta para siempre")
            .isAfter(hace25Horas);
    }

    @Test
    @DisplayName("fijarPasswordPropia: el destrabe de un admin abre la ventana una vez")
    void password_elDestrabeAbreLaVentana() {
        conVentanaDe(24);
        Usuario u = usuarioActivo(52L, RolCodigo.ADMIN);
        u.setPasswordCambiadaEn(LocalDateTime.now().minusHours(1));
        u.setCambioPasswordHabilitadoEn(LocalDateTime.now());
        u.setCambioPasswordHabilitadoPor(USUARIO_ACTUAL);
        when(usuarioRepository.findById(52L)).thenReturn(Optional.of(u));

        service.fijarPasswordPropia(52L, "NuevaClave1");

        verify(usuarioRepository).save(u);
        assertThat(u.getCambioPasswordHabilitadoEn())
            .as("el destrabe se consume: si quedara puesto serviria para siempre")
            .isNull();
        assertThat(u.getCambioPasswordHabilitadoPor()).isNull();
    }

    @Test
    @DisplayName("fijarPasswordPropia: un destrabe anterior al ultimo cambio no sirve")
    void password_destrabeViejoNoSirve() {
        conVentanaDe(24);
        Usuario u = usuarioActivo(53L, RolCodigo.ADMIN);
        u.setCambioPasswordHabilitadoEn(LocalDateTime.now().minusHours(3));
        u.setPasswordCambiadaEn(LocalDateTime.now().minusHours(2));   // se uso despues
        when(usuarioRepository.findById(53L)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.fijarPasswordPropia(53L, "NuevaClave1"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("fijarPasswordPropia: una cuenta que nunca la cambio no esta trabada")
    void password_sinFechaNoTraba() {
        conVentanaDe(24);
        Usuario u = usuarioActivo(54L, RolCodigo.ADMIN);          // passwordCambiadaEn = null
        when(usuarioRepository.findById(54L)).thenReturn(Optional.of(u));

        service.fijarPasswordPropia(54L, "NuevaClave1");

        verify(usuarioRepository).save(u);
    }

    @Test
    @DisplayName("habilitarCambioDePassword: nadie se destraba a si mismo")
    void destrabe_noSePuedeUnoMismo() {
        // Si pudiera, el limite no existiria: alcanzaria con levantarlo antes de cada cambio.
        assertThatThrownBy(() -> service.habilitarCambioDePassword(USUARIO_ACTUAL, USUARIO_ACTUAL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("vos mismo");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("habilitarCambioDePassword: no alcanza a una cuenta de otra institucion")
    void destrabe_noCruzaTenant() {
        Usuario ajeno = usuarioActivo(55L, RolCodigo.ADMIN);
        ajeno.setInstitucionId(TENANT_B);
        when(usuarioRepository.findById(55L)).thenReturn(Optional.of(ajeno));

        // Se responde "no encontrado" y no "no autorizado": lo segundo revelaria que existe.
        assertThatThrownBy(() -> service.habilitarCambioDePassword(55L, USUARIO_ACTUAL))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("habilitarCambioDePassword: deja quien lo hizo, para poder rastrearlo")
    void destrabe_dejaConstancia() {
        Usuario u = usuarioActivo(56L, RolCodigo.ADMIN);
        when(usuarioRepository.findById(56L)).thenReturn(Optional.of(u));

        service.habilitarCambioDePassword(56L, USUARIO_ACTUAL);

        assertThat(u.getCambioPasswordHabilitadoEn()).isNotNull();
        assertThat(u.getCambioPasswordHabilitadoPor()).isEqualTo(USUARIO_ACTUAL);
        verify(usuarioRepository).save(u);
    }

    @Test
    @DisplayName("Con la ventana en cero el limite queda desactivado")
    void password_ventanaEnCeroNoLimita() {
        conVentanaDe(0);
        Usuario u = usuarioActivo(57L, RolCodigo.ADMIN);
        u.setPasswordCambiadaEn(LocalDateTime.now());
        when(usuarioRepository.findById(57L)).thenReturn(Optional.of(u));

        service.fijarPasswordPropia(57L, "NuevaClave1");

        verify(usuarioRepository).save(u);
    }

    // ====================================================================
    //  Helpers
    // ====================================================================

    // La ventana llega por @Value, que en un test unitario no se inyecta: sin esto queda en
    // cero y el limite no se ejercita, con lo que los casos de arriba pasarian sin probar nada.
    private void conVentanaDe(long horas) {
        org.springframework.test.util.ReflectionTestUtils.setField(
            service, "horasEntreCambios", horas);
    }

    private Usuario usuarioActivo(Long id, RolCodigo rolCodigo) {
        Rol r = rolCodigo == RolCodigo.INSTITUCION ? rolInstitucion : rolAdmin;
        Usuario u = Usuario.builder().persona(DatosDePrueba.persona("N", "A")).id(id).username("u" + id).email("u" + id + "@x.com").passwordHash("h").activo(true).rol(r).build();
        u.setInstitucionId(TENANT_A);
        return u;
    }
}
