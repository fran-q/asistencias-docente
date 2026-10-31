package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.ModalidadInstalacion;
import edu.cent35.asistencias.model.biometria.PuestoCaptura;
import edu.cent35.asistencias.model.institucion.PropositoCodigo;
import edu.cent35.asistencias.model.institucion.Usuario;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.PuestoCapturaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cuándo autorizar un equipo pide el código al correo, y adónde va ese código (ADR-0015).
 *
 * <p>Lo que los IT no pueden ejercer: la instalación autónoma —el contexto de prueba corre con
 * correo— y el envío, que en ellos no tiene servidor del otro lado. Que el código se valide de
 * verdad, y que un intento fallido quede contado, lo prueba {@code PuestoCapturaIT} contra la
 * base.
 */
@ExtendWith(MockitoExtension.class)
class PuestoCapturaServiceTest {

    private static final Long INSTITUCION = 1L;

    @Mock private PuestoCapturaRepository puestoRepository;
    @Mock private InstitucionRepository institucionRepository;
    @Mock private CodigoVerificacionService codigoService;
    @Mock private CanalDeCodigos notificador;

    @Test
    @DisplayName("El primer equipo de la institucion no pide codigo")
    void elPrimeroNoPideCodigo() {
        when(puestoRepository.tuvoAlguno(INSTITUCION)).thenReturn(false);

        assertThat(servicio(false).pideCodigoParaAutorizar(INSTITUCION)).isFalse();
    }

    @Test
    @DisplayName("Con correo, los que siguen al primero piden codigo")
    void losSiguientesPidenCodigo() {
        when(puestoRepository.tuvoAlguno(INSTITUCION)).thenReturn(true);

        assertThat(servicio(false).pideCodigoParaAutorizar(INSTITUCION)).isTrue();
    }

    @Test
    @DisplayName("Sin internet no se pide codigo: no hay correo adonde mandarlo")
    void laInstalacionAutonomaNoPideCodigo() {
        // Ni siquiera pregunta si hubo equipos antes: sin correo, la respuesta es la misma.
        assertThat(servicio(true).pideCodigoParaAutorizar(INSTITUCION)).isFalse();
        verifyNoInteractions(puestoRepository);
    }

    @Test
    @DisplayName("Sin internet, el segundo equipo se autoriza sin tocar el codigo")
    void laInstalacionAutonomaAutorizaSinCodigo() {
        // La institución ya tuvo equipos: con correo, esto pediría código. lenient() porque el
        // servicio ni lo pregunta, y justamente eso es lo que se prueba.
        lenient().when(puestoRepository.tuvoAlguno(INSTITUCION)).thenReturn(true);
        when(puestoRepository.save(any(PuestoCaptura.class))).thenAnswer(i -> i.getArgument(0));

        PuestoCapturaService.PuestoDesignado alta =
            servicio(true).designar(INSTITUCION, "Entrada sur", usuario(), null);

        assertThat(alta.getTokenEnClaro()).isNotBlank();
        verify(codigoService, never()).validar(any(), any(), any());
    }

    @Test
    @DisplayName("El codigo va al correo que la cuenta tiene cargado, con su proposito")
    void elCodigoVaAlCorreoDeLaCuenta() {
        Usuario cuenta = usuario();
        when(codigoService.emitir(cuenta, PropositoCodigo.AUTORIZACION_PUESTO,
                                  "institucion@ejemplo.edu.ar", "10.0.0.5"))
            .thenReturn("481920");

        servicio(false).pedirCodigoParaAutorizar(cuenta, "10.0.0.5");

        verify(notificador).enviarCodigo(cuenta, PropositoCodigo.AUTORIZACION_PUESTO,
                                         "institucion@ejemplo.edu.ar", "481920");
    }

    // El servicio en la modalidad que pida el caso. ModalidadInstalacion es una clase sin
    // dependencias: se arma real en vez de mockearla.
    private PuestoCapturaService servicio(boolean autonoma) {
        return new PuestoCapturaService(puestoRepository, institucionRepository, codigoService,
                                        notificador, new ModalidadInstalacion(autonoma, true));
    }

    private Usuario usuario() {
        Usuario u = Usuario.builder()
            .id(9L).username("institucion").email("institucion@ejemplo.edu.ar").build();
        u.setInstitucionId(INSTITUCION);
        return u;
    }
}
