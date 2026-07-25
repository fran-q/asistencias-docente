package edu.cent35.asistencias.service;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.model.ConsentimientoBiometrico;
import edu.cent35.asistencias.model.EstadoConsentimiento;
import edu.cent35.asistencias.model.MetodoConsentimiento;
import edu.cent35.asistencias.repository.ConsentimientoBiometricoRepository;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Usuario;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre el consentimiento biométrico: que no se pueda otorgar dos veces sin revocar, que el
 * estado se deduzca del último registro y que cada operación quede auditada.
 */
@ExtendWith(MockitoExtension.class)
class ConsentimientoBiometricoServiceTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final Long DOCENTE_ID = 50L;
    private static final Long USUARIO_ACTUAL_ID = 100L;
    private static final String IP = "127.0.0.1";
    private static final String UA = "Mozilla/5.0 (Test)";

    @Mock private ConsentimientoBiometricoRepository consentimientoRepository;
    @Mock private DocenteRepository docenteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @InjectMocks private ConsentimientoBiometricoService service;

    @BeforeEach void setUp() { TenantContext.set(TENANT_A); }
    @AfterEach  void clear() { TenantContext.clear(); }

    // ========================================================================
    //  estadoActual
    // ========================================================================

    @Test
    @DisplayName("estadoActual: NUNCA_OTORGADO si no hay registros")
    void estadoActual_nuncaOtorgado() {
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docenteActivoA()));
        when(consentimientoRepository
            .findTopByDocenteIdOrderByFechaConsentimientoDescIdDesc(DOCENTE_ID))
            .thenReturn(Optional.empty());

        assertThat(service.estadoActual(DOCENTE_ID)).isEqualTo(EstadoConsentimiento.NUNCA_OTORGADO);
    }

    @Test
    @DisplayName("estadoActual: ACTIVO si el ultimo registro esta vigente")
    void estadoActual_activo() {
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docenteActivoA()));
        when(consentimientoRepository
            .findTopByDocenteIdOrderByFechaConsentimientoDescIdDesc(DOCENTE_ID))
            .thenReturn(Optional.of(consentimientoVigente()));

        assertThat(service.estadoActual(DOCENTE_ID)).isEqualTo(EstadoConsentimiento.ACTIVO);
    }

    @Test
    @DisplayName("estadoActual: REVOCADO si el ultimo registro fue revocado")
    void estadoActual_revocado() {
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docenteActivoA()));
        ConsentimientoBiometrico revocado = consentimientoVigente();
        revocado.setVigente(false);
        revocado.setFechaRevocacion(LocalDateTime.now());
        when(consentimientoRepository
            .findTopByDocenteIdOrderByFechaConsentimientoDescIdDesc(DOCENTE_ID))
            .thenReturn(Optional.of(revocado));

        assertThat(service.estadoActual(DOCENTE_ID)).isEqualTo(EstadoConsentimiento.REVOCADO);
    }

    @Test
    @DisplayName("estadoActual: cross-tenant -> EntityNotFound")
    void estadoActual_crossTenant() {
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docenteDeTenant(TENANT_B)));

        assertThatThrownBy(() -> service.estadoActual(DOCENTE_ID))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ========================================================================
    //  otorgar
    // ========================================================================

    @Test
    @DisplayName("otorgar: ok con docente activo del tenant y sin vigente previo")
    void otorgar_ok() {
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docenteActivoA()));
        when(consentimientoRepository.findByDocenteIdAndVigenteTrue(DOCENTE_ID)).thenReturn(Optional.empty());
        when(usuarioRepository.findById(USUARIO_ACTUAL_ID)).thenReturn(Optional.of(usuarioDeTenant(TENANT_A)));
        when(consentimientoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConsentimientoBiometrico c = service.otorgar(
            DOCENTE_ID, MetodoConsentimiento.ESCRITO, LocalDateTime.now(),
            IP, UA, null, USUARIO_ACTUAL_ID);

        assertThat(c.getVigente()).isTrue();
        assertThat(c.getMetodo()).isEqualTo(MetodoConsentimiento.ESCRITO);
        assertThat(c.getVersionTerminos()).isEqualTo(TextoConsentimiento.VERSION_ACTUAL);
        assertThat(c.getIpOtorgamiento()).isEqualTo(IP);
        assertThat(c.getUserAgentOtorgamiento()).isEqualTo(UA);
        assertThat(c.getFechaRevocacion()).isNull();
    }

    @Test
    @DisplayName("otorgar: bloquea si el docente esta inactivo")
    void otorgar_docenteInactivo() {
        Docente inactivo = docenteActivoA();
        inactivo.setActivo(false);
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(inactivo));

        assertThatThrownBy(() -> service.otorgar(
                DOCENTE_ID, MetodoConsentimiento.ESCRITO, LocalDateTime.now(),
                IP, UA, null, USUARIO_ACTUAL_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("inactivo");
        verify(consentimientoRepository, never()).save(any());
    }

    @Test
    @DisplayName("otorgar: bloquea si ya hay un consentimiento vigente")
    void otorgar_yaHayVigente() {
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docenteActivoA()));
        when(consentimientoRepository.findByDocenteIdAndVigenteTrue(DOCENTE_ID))
            .thenReturn(Optional.of(consentimientoVigente()));

        assertThatThrownBy(() -> service.otorgar(
                DOCENTE_ID, MetodoConsentimiento.ESCRITO, LocalDateTime.now(),
                IP, UA, null, USUARIO_ACTUAL_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("vigente");
        verify(consentimientoRepository, never()).save(any());
    }

    @Test
    @DisplayName("otorgar: rechaza fecha futura")
    void otorgar_fechaFutura() {
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docenteActivoA()));
        when(consentimientoRepository.findByDocenteIdAndVigenteTrue(DOCENTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.otorgar(
                DOCENTE_ID, MetodoConsentimiento.ESCRITO,
                LocalDateTime.now().plusDays(2),
                IP, UA, null, USUARIO_ACTUAL_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("futura");
    }

    @Test
    @DisplayName("otorgar: rechaza docente de otro tenant (camuflado)")
    void otorgar_crossTenant() {
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docenteDeTenant(TENANT_B)));

        assertThatThrownBy(() -> service.otorgar(
                DOCENTE_ID, MetodoConsentimiento.ESCRITO, LocalDateTime.now(),
                IP, UA, null, USUARIO_ACTUAL_ID))
            .isInstanceOf(EntityNotFoundException.class);
        verify(consentimientoRepository, never()).save(any());
    }

    @Test
    @DisplayName("otorgar: metodo null -> error")
    void otorgar_metodoNull() {
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docenteActivoA()));
        when(consentimientoRepository.findByDocenteIdAndVigenteTrue(DOCENTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.otorgar(
                DOCENTE_ID, null, LocalDateTime.now(),
                IP, UA, null, USUARIO_ACTUAL_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("método");
    }

    // ========================================================================
    //  revocar
    // ========================================================================

    @Test
    @DisplayName("revocar: ok con vigente existente -> marca vigente=false y completa audit")
    void revocar_ok() {
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docenteActivoA()));
        ConsentimientoBiometrico vigente = consentimientoVigente();
        when(consentimientoRepository.findByDocenteIdAndVigenteTrue(DOCENTE_ID))
            .thenReturn(Optional.of(vigente));
        when(usuarioRepository.findById(USUARIO_ACTUAL_ID))
            .thenReturn(Optional.of(usuarioDeTenant(TENANT_A)));
        when(consentimientoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConsentimientoBiometrico revocado = service.revocar(
            DOCENTE_ID, "El docente lo solicitó", IP, UA, USUARIO_ACTUAL_ID);

        assertThat(revocado.getVigente()).isFalse();
        assertThat(revocado.getFechaRevocacion()).isNotNull();
        assertThat(revocado.getMotivoRevocacion()).isEqualTo("El docente lo solicitó");
        assertThat(revocado.getIpRevocacion()).isEqualTo(IP);
        assertThat(revocado.getUserAgentRevocacion()).isEqualTo(UA);
        assertThat(revocado.getRevocadoPor()).isNotNull();
    }

    @Test
    @DisplayName("revocar: bloquea si no hay vigente")
    void revocar_sinVigente() {
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docenteActivoA()));
        when(consentimientoRepository.findByDocenteIdAndVigenteTrue(DOCENTE_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revocar(
                DOCENTE_ID, null, IP, UA, USUARIO_ACTUAL_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("vigente");
        verify(consentimientoRepository, never()).save(any());
    }

    @Test
    @DisplayName("revocar: cross-tenant -> EntityNotFound")
    void revocar_crossTenant() {
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docenteDeTenant(TENANT_B)));

        assertThatThrownBy(() -> service.revocar(
                DOCENTE_ID, null, IP, UA, USUARIO_ACTUAL_ID))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("revocar: motivo vacio o solo espacios queda como null")
    void revocar_motivoVacio() {
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docenteActivoA()));
        ConsentimientoBiometrico vigente = consentimientoVigente();
        when(consentimientoRepository.findByDocenteIdAndVigenteTrue(DOCENTE_ID))
            .thenReturn(Optional.of(vigente));
        when(usuarioRepository.findById(USUARIO_ACTUAL_ID))
            .thenReturn(Optional.of(usuarioDeTenant(TENANT_A)));
        when(consentimientoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConsentimientoBiometrico revocado = service.revocar(
            DOCENTE_ID, "   ", IP, UA, USUARIO_ACTUAL_ID);

        assertThat(revocado.getMotivoRevocacion()).isNull();
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    private Docente docenteActivoA() {
        return docenteDeTenant(TENANT_A);
    }

    // Docente del tenant indicado.
    private Docente docenteDeTenant(Long tenantId) {
        Docente d = Docente.builder()
            .id(DOCENTE_ID).dni("99888777").nombre("Juana").apellido("Pérez").activo(true)
            .build();
        d.setInstitucionId(tenantId);
        return d;
    }

    // Usuario administrador del tenant indicado.
    private Usuario usuarioDeTenant(Long tenantId) {
        Usuario u = Usuario.builder()
            .id(USUARIO_ACTUAL_ID).username("admin").email("a@x.com")
            .passwordHash("xxx").nombre("Admin").apellido("Test").activo(true)
            .build();
        u.setInstitucionId(tenantId);
        return u;
    }

    // Consentimiento sin revocar del docente.
    private ConsentimientoBiometrico consentimientoVigente() {
        return ConsentimientoBiometrico.builder()
            .id(1000L)
            .docente(docenteActivoA())
            .versionTerminos(TextoConsentimiento.VERSION_ACTUAL)
            .metodo(MetodoConsentimiento.ESCRITO)
            .fechaConsentimiento(LocalDateTime.now().minusDays(1))
            .vigente(true)
            .build();
    }
}
