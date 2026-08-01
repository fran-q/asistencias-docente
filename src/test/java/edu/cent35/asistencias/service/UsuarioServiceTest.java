package edu.cent35.asistencias.service;
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
        when(rolRepository.findByCodigo("ADMIN")).thenReturn(Optional.of(rolAdmin));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.actualizar(10L, "N", "A", "otro@x.com", RolCodigo.ADMIN, true, USUARIO_ACTUAL);

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
        when(rolRepository.findByCodigo("ADMIN")).thenReturn(Optional.of(rolAdmin));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Se edita el nombre, no el correo.
        service.actualizar(10L, "Nombre nuevo", "A", u.getEmail(),
                           RolCodigo.ADMIN, true, USUARIO_ACTUAL);

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
