package edu.cent35.asistencias.usuario.application;

import edu.cent35.asistencias.shared.multitenant.TenantContext;
import edu.cent35.asistencias.usuario.domain.Rol;
import edu.cent35.asistencias.usuario.domain.RolCodigo;
import edu.cent35.asistencias.usuario.domain.Usuario;
import edu.cent35.asistencias.usuario.infrastructure.RolRepository;
import edu.cent35.asistencias.usuario.infrastructure.UsuarioRepository;
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
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private UsuarioService service;

    private Rol rolInstitucion;
    private Rol rolAdmin;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_A);

        rolInstitucion = new Rol((short) 1, RolCodigo.INSTITUCION.name(), "Institucion");
        rolAdmin       = new Rol((short) 2, RolCodigo.ADMIN.name(),       "Admin");

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

        Usuario creado = service.crear("nuevo", "n@x.com", "pass1234", "Pepe", "Perez", RolCodigo.ADMIN);

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

        assertThatThrownBy(() -> service.crear("dup", "x@x.com", "pass1234", "P", "P", RolCodigo.ADMIN))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("username");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: falla si email ya existe en la institucion")
    void crear_emailDuplicado() {
        when(usuarioRepository.existsByUsernameAndInstitucionId(any(), any())).thenReturn(false);
        when(usuarioRepository.existsByEmailAndInstitucionId("dup@x.com", TENANT_A)).thenReturn(true);

        assertThatThrownBy(() -> service.crear("u", "dup@x.com", "pass1234", "P", "P", RolCodigo.ADMIN))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("email");
        verify(usuarioRepository, never()).save(any());
    }

    // ====================================================================
    //  ACTUALIZAR / autoproteccion
    // ====================================================================

    @Test
    @DisplayName("actualizar: rechaza desactivarse a si mismo")
    void actualizar_noAutoDesactivar() {
        Usuario yo = usuarioActivo(USUARIO_ACTUAL, RolCodigo.INSTITUCION);
        when(usuarioRepository.findById(USUARIO_ACTUAL)).thenReturn(Optional.of(yo));

        assertThatThrownBy(() -> service.actualizar(
            USUARIO_ACTUAL, "N", "A", "yo@x.com", RolCodigo.INSTITUCION,
            /*activo=*/ false, USUARIO_ACTUAL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("desactivarte");
    }

    @Test
    @DisplayName("actualizar: rechaza degradarse a si mismo de INSTITUCION a ADMIN")
    void actualizar_noAutoDegradar() {
        Usuario yo = usuarioActivo(USUARIO_ACTUAL, RolCodigo.INSTITUCION);
        when(usuarioRepository.findById(USUARIO_ACTUAL)).thenReturn(Optional.of(yo));

        assertThatThrownBy(() -> service.actualizar(
            USUARIO_ACTUAL, "N", "A", "yo@x.com", RolCodigo.ADMIN,
            true, USUARIO_ACTUAL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("degradarte");
    }

    @Test
    @DisplayName("actualizar: rechaza dejar a la institucion sin INSTITUCION activo")
    void actualizar_noQuedarSinInstitucion() {
        Usuario otroSuper = usuarioActivo(50L, RolCodigo.INSTITUCION);
        when(usuarioRepository.findById(50L)).thenReturn(Optional.of(otroSuper));
        when(usuarioRepository.countByInstitucionIdAndRolCodigoAndActivoTrue(
            TENANT_A, RolCodigo.INSTITUCION.name())).thenReturn(1L);  // este es el unico

        assertThatThrownBy(() -> service.actualizar(
            50L, "N", "A", "o@x.com", RolCodigo.INSTITUCION,
            /*activo=*/ false, USUARIO_ACTUAL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("al menos un");
    }

    @Test
    @DisplayName("actualizar: permite desactivar si hay otro INSTITUCION activo")
    void actualizar_okSiHayOtroSuper() {
        Usuario otroSuper = usuarioActivo(50L, RolCodigo.INSTITUCION);
        when(usuarioRepository.findById(50L)).thenReturn(Optional.of(otroSuper));
        when(usuarioRepository.countByInstitucionIdAndRolCodigoAndActivoTrue(
            TENANT_A, RolCodigo.INSTITUCION.name())).thenReturn(2L);
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Usuario actualizado = service.actualizar(
            50L, "N", "A", "o@x.com", RolCodigo.INSTITUCION,
            /*activo=*/ false, USUARIO_ACTUAL);

        assertThat(actualizado.getActivo()).isFalse();
        verify(usuarioRepository).save(any());
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
        verify(usuarioRepository).findByInstitucionIdOrderByActivoDescApellidoAscNombreAsc(TENANT_A);
    }

    // ====================================================================
    //  RESET PASSWORD
    // ====================================================================

    @Test
    @DisplayName("resetearPassword: hashea y guarda")
    void resetearPassword_ok() {
        Usuario u = usuarioActivo(50L, RolCodigo.ADMIN);
        when(usuarioRepository.findById(50L)).thenReturn(Optional.of(u));

        service.resetearPassword(50L, "nueva1234");

        assertThat(u.getPasswordHash()).isEqualTo("hash-fake");
        verify(passwordEncoder).encode("nueva1234");
        verify(usuarioRepository).save(u);
    }

    // ====================================================================
    //  Helpers
    // ====================================================================
    private Usuario usuarioActivo(Long id, RolCodigo rolCodigo) {
        Rol r = rolCodigo == RolCodigo.INSTITUCION ? rolInstitucion : rolAdmin;
        Usuario u = Usuario.builder()
            .id(id)
            .username("u" + id)
            .email("u" + id + "@x.com")
            .passwordHash("h")
            .nombre("N")
            .apellido("A")
            .activo(true)
            .rol(r)
            .build();
        u.setInstitucionId(TENANT_A);
        return u;
    }
}
