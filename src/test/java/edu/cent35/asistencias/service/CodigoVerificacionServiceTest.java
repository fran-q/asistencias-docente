package edu.cent35.asistencias.service;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.model.CodigoVerificacion;
import edu.cent35.asistencias.model.PropositoCodigo;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.CodigoVerificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre las defensas del código de un solo uso, que son lo que hace inviable adivinarlo: que se
 * guarde hasheado y no en claro, que venza, que se consuma en el primer uso, que los intentos
 * fallidos lo agoten y que no se puedan pedir códigos sin límite.
 */
@ExtendWith(MockitoExtension.class)
class CodigoVerificacionServiceTest {

    private static final Long USUARIO_ID = 7L;
    private static final Long TENANT = 1L;
    private static final ZoneId ZONA = ZoneId.systemDefault();

    @Mock private CodigoVerificacionRepository codigoRepository;

    // Encoder real: lo que se prueba es justamente que el codigo no quede recuperable.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private CodigoVerificacionService service;

    @BeforeEach
    void setUp() {
        service = new CodigoVerificacionService(codigoRepository, passwordEncoder);
        ReflectionTestUtils.setField(service, "minutosVigencia", 15);
        ReflectionTestUtils.setField(service, "maxIntentos", 5);
        ReflectionTestUtils.setField(service, "maxPorHora", 5);
    }

    // ========================================================================
    //  Emision
    // ========================================================================

    @Test
    @DisplayName("El codigo emitido tiene 6 digitos y en la base solo queda su hash")
    void elCodigoSeGuardaHasheado() {
        when(codigoRepository.contarDesde(eq(USUARIO_ID), any(), any())).thenReturn(0L);

        String codigo = service.emitir(usuario(), PropositoCodigo.VERIFICACION_EMAIL, "a@b.com", "127.0.0.1");

        assertThat(codigo).hasSize(6).containsOnlyDigits();

        ArgumentCaptor<CodigoVerificacion> capturado = ArgumentCaptor.forClass(CodigoVerificacion.class);
        verify(codigoRepository).save(capturado.capture());
        CodigoVerificacion guardado = capturado.getValue();

        assertThat(guardado.getCodigoHash())
            .as("el codigo en claro no puede quedar en la base")
            .isNotEqualTo(codigo);
        assertThat(passwordEncoder.matches(codigo, guardado.getCodigoHash())).isTrue();
        assertThat(guardado.getInstitucionId()).isEqualTo(TENANT);
    }

    @Test
    @DisplayName("Emitir uno nuevo invalida los pendientes del mismo proposito")
    void emitirInvalidaLosAnteriores() {
        when(codigoRepository.contarDesde(eq(USUARIO_ID), any(), any())).thenReturn(0L);

        service.emitir(usuario(), PropositoCodigo.RECUPERACION_PASSWORD, "a@b.com", null);

        verify(codigoRepository).invalidarPendientes(eq(USUARIO_ID),
            eq(PropositoCodigo.RECUPERACION_PASSWORD), any());
    }

    @Test
    @DisplayName("Pasado el limite por hora se deja de emitir")
    void limiteDeReenvios() {
        when(codigoRepository.contarDesde(eq(USUARIO_ID), any(), any())).thenReturn(5L);

        assertThatThrownBy(() ->
            service.emitir(usuario(), PropositoCodigo.VERIFICACION_EMAIL, "a@b.com", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("demasiados códigos");

        verify(codigoRepository, never()).save(any());
    }

    // ========================================================================
    //  Validacion
    // ========================================================================

    @Test
    @DisplayName("El codigo correcto valida y queda consumido")
    void codigoCorrectoSeConsume() {
        CodigoVerificacion vigente = vigenteCon("123456");
        when(codigoRepository.ultimoDe(USUARIO_ID, PropositoCodigo.VERIFICACION_EMAIL))
            .thenReturn(Optional.of(vigente));

        CodigoVerificacionService.Resultado r =
            service.validar(USUARIO_ID, PropositoCodigo.VERIFICACION_EMAIL, "123456");

        assertThat(r).isEqualTo(CodigoVerificacionService.Resultado.OK);
        assertThat(vigente.getUsadoEn()).isNotNull();
    }

    @Test
    @DisplayName("Se tolera que el codigo venga con espacios o guiones")
    void seNormalizaLoQueSePega() {
        when(codigoRepository.ultimoDe(USUARIO_ID, PropositoCodigo.VERIFICACION_EMAIL))
            .thenReturn(Optional.of(vigenteCon("123456")));

        assertThat(service.validar(USUARIO_ID, PropositoCodigo.VERIFICACION_EMAIL, " 123-456 "))
            .isEqualTo(CodigoVerificacionService.Resultado.OK);
    }

    @Test
    @DisplayName("Un codigo equivocado suma intento y no consume el codigo")
    void codigoIncorrectoSumaIntento() {
        CodigoVerificacion vigente = vigenteCon("123456");
        when(codigoRepository.ultimoDe(USUARIO_ID, PropositoCodigo.VERIFICACION_EMAIL))
            .thenReturn(Optional.of(vigente));

        CodigoVerificacionService.Resultado r =
            service.validar(USUARIO_ID, PropositoCodigo.VERIFICACION_EMAIL, "999999");

        assertThat(r).isEqualTo(CodigoVerificacionService.Resultado.INCORRECTO);
        assertThat(vigente.getIntentos()).isEqualTo((short) 1);
        assertThat(vigente.getUsadoEn()).isNull();
    }

    @Test
    @DisplayName("Al agotar los intentos el codigo deja de servir aunque se acierte")
    void seAgotanLosIntentos() {
        CodigoVerificacion quemado = vigenteCon("123456");
        quemado.setIntentos((short) 5);
        when(codigoRepository.ultimoDe(USUARIO_ID, PropositoCodigo.VERIFICACION_EMAIL))
            .thenReturn(Optional.of(quemado));

        assertThat(service.validar(USUARIO_ID, PropositoCodigo.VERIFICACION_EMAIL, "123456"))
            .isEqualTo(CodigoVerificacionService.Resultado.SIN_INTENTOS);
        assertThat(quemado.getUsadoEn()).isNull();
    }

    @Test
    @DisplayName("Un codigo vencido no vale ni siendo el correcto")
    void codigoVencido() {
        CodigoVerificacion viejo = vigenteCon("123456");
        viejo.setExpiraEn(LocalDateTime.now().minusMinutes(1));
        when(codigoRepository.ultimoDe(USUARIO_ID, PropositoCodigo.RECUPERACION_PASSWORD))
            .thenReturn(Optional.of(viejo));

        assertThat(service.validar(USUARIO_ID, PropositoCodigo.RECUPERACION_PASSWORD, "123456"))
            .isEqualTo(CodigoVerificacionService.Resultado.VENCIDO);
    }

    @Test
    @DisplayName("Un codigo ya usado no se puede reutilizar")
    void codigoYaUsado() {
        CodigoVerificacion usado = vigenteCon("123456");
        usado.setUsadoEn(LocalDateTime.now().minusMinutes(1));
        when(codigoRepository.ultimoDe(USUARIO_ID, PropositoCodigo.VERIFICACION_EMAIL))
            .thenReturn(Optional.of(usado));

        assertThat(service.validar(USUARIO_ID, PropositoCodigo.VERIFICACION_EMAIL, "123456"))
            .isEqualTo(CodigoVerificacionService.Resultado.INEXISTENTE);
    }

    @Test
    @DisplayName("Sin codigo pendiente no se valida nada")
    void sinCodigoPendiente() {
        when(codigoRepository.ultimoDe(USUARIO_ID, PropositoCodigo.VERIFICACION_EMAIL))
            .thenReturn(Optional.empty());

        assertThat(service.validar(USUARIO_ID, PropositoCodigo.VERIFICACION_EMAIL, "123456"))
            .isEqualTo(CodigoVerificacionService.Resultado.INEXISTENTE);
    }

    @Test
    @DisplayName("El codigo deja de valer apenas pasa su ventana de vigencia")
    void venceAlPasarLaVentana() {
        LocalDateTime emision = LocalDateTime.of(2026, 7, 26, 10, 0);
        CodigoVerificacion codigo = vigenteCon("123456");
        codigo.setExpiraEn(emision.plusMinutes(15));
        when(codigoRepository.ultimoDe(USUARIO_ID, PropositoCodigo.VERIFICACION_EMAIL))
            .thenReturn(Optional.of(codigo));

        // Un minuto antes del vencimiento todavia sirve.
        service.setClock(relojEn(emision.plusMinutes(14)));
        assertThat(service.validar(USUARIO_ID, PropositoCodigo.VERIFICACION_EMAIL, "123456"))
            .isEqualTo(CodigoVerificacionService.Resultado.OK);

        // Pasada la ventana, ya no.
        codigo.setUsadoEn(null);
        service.setClock(relojEn(emision.plusMinutes(16)));
        assertThat(service.validar(USUARIO_ID, PropositoCodigo.VERIFICACION_EMAIL, "123456"))
            .isEqualTo(CodigoVerificacionService.Resultado.VENCIDO);
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    // Usuario minimo del tenant A.
    private Usuario usuario() {
        Usuario u = Usuario.builder().persona(DatosDePrueba.persona("Test")).id(USUARIO_ID).username("test").build();
        u.setInstitucionId(TENANT);
        return u;
    }

    // Codigo disponible cuyo hash corresponde al valor pasado.
    private CodigoVerificacion vigenteCon(String codigoEnClaro) {
        return CodigoVerificacion.builder()
            .id(1L)
            .usuario(usuario())
            .proposito(PropositoCodigo.VERIFICACION_EMAIL)
            .email("a@b.com")
            .codigoHash(passwordEncoder.encode(codigoEnClaro))
            .expiraEn(LocalDateTime.now().plusMinutes(15))
            .intentos((short) 0)
            .build();
    }

    // Reloj fijo en el instante pedido, para probar el vencimiento sin esperar.
    private Clock relojEn(LocalDateTime momento) {
        return Clock.fixed(momento.atZone(ZONA).toInstant(), ZONA);
    }
}
