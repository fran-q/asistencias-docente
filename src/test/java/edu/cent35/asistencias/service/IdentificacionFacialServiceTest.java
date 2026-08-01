package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.dto.IdentificacionResultadoDto;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.ModeloFacial;
import edu.cent35.asistencias.repository.ModeloFacialRepository;
import org.bytedeco.opencv.opencv_core.Mat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Tests del flujo de identificación. La identificación contra LBPH real
 * requiere imágenes y modelos válidos — eso se valida manualmente.
 * Estos tests cubren la lógica del servicio en sus ramas principales:
 * sin rostro, sin modelos, sin docente válido.
 */
@ExtendWith(MockitoExtension.class)
class IdentificacionFacialServiceTest {

    private static final Long TENANT_A = 1L;

    @Mock private ModeloFacialRepository modeloFacialRepository;
    @Mock private DeteccionRostroService deteccionRostroService;
    @Mock private MotorLbphService motorLbph;
    @Mock private CifradoBiometricoService cifradoService;

    @InjectMocks private IdentificacionFacialService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_A);
        ReflectionTestUtils.setField(service, "tamanoRostro", 200);
        ReflectionTestUtils.setField(service, "umbralConfianza", 65.0);
        ReflectionTestUtils.setField(service, "margenMinimo", 12.0);
    }

    @AfterEach
    void clear() { TenantContext.clear(); }

    @Test
    @DisplayName("identificar: sin rostro detectado -> sinRostro")
    void identificar_sinRostro() {
        when(deteccionRostroService.extraerRostroNormalizado(any(), anyInt())).thenReturn(null);

        IdentificacionResultadoDto r = service.identificar(new byte[]{1, 2, 3});

        assertThat(r.rostroDetectado()).isFalse();
        assertThat(r.reconocido()).isFalse();
        assertThat(r.docenteNombre()).isNull();
    }

    @Test
    @DisplayName("identificar: hay rostro pero ningún docente con modelo -> noHayModelos")
    void identificar_sinModelos() {
        when(deteccionRostroService.extraerRostroNormalizado(any(), anyInt()))
            .thenReturn(rostroExtraidoValido());
        when(modeloFacialRepository.findActivosDelTenant(TENANT_A)).thenReturn(List.of());

        IdentificacionResultadoDto r = service.identificar(new byte[]{1, 2, 3});

        assertThat(r.rostroDetectado()).isTrue();
        assertThat(r.reconocido()).isFalse();
        assertThat(r.docenteNombre()).isNull();
        assertThat(r.x()).isEqualTo(10);
        assertThat(r.y()).isEqualTo(20);
        assertThat(r.ancho()).isEqualTo(100);
        assertThat(r.alto()).isEqualTo(100);
    }

    @Test
    @DisplayName("identificar: devuelve bbox del rostro detectado siempre que haya rostro")
    void identificar_incluyeBbox() {
        when(deteccionRostroService.extraerRostroNormalizado(any(), anyInt()))
            .thenReturn(rostroExtraidoValido());
        when(modeloFacialRepository.findActivosDelTenant(TENANT_A)).thenReturn(List.of());

        IdentificacionResultadoDto r = service.identificar(new byte[]{1, 2, 3});

        assertThat(r.x()).isNotNull();
        assertThat(r.y()).isNotNull();
        assertThat(r.ancho()).isNotNull();
        assertThat(r.alto()).isNotNull();
    }

    @Test
    @DisplayName("identificar: sinRostro tiene todas las coords null")
    void identificar_sinRostro_sinCoords() {
        when(deteccionRostroService.extraerRostroNormalizado(any(), anyInt())).thenReturn(null);

        IdentificacionResultadoDto r = service.identificar(new byte[]{1, 2, 3});

        assertThat(r.x()).isNull();
        assertThat(r.y()).isNull();
        assertThat(r.ancho()).isNull();
        assertThat(r.alto()).isNull();
    }

    // ========================================================================
    //  La regla que decide si se acepta (falso positivo de 2026-08-01)
    //
    //  Las distancias de estos casos salen del log de una sesion real, cuando el
    //  sistema le adjudico el mismo docente a dos personas distintas.
    // ========================================================================

    @Test
    @DisplayName("Un rostro claramente parecido y sin competencia se acepta")
    void decidir_aceptaLoQueEsClaro() {
        // Caso real: mejor 48,0 contra segundo 62,2. Margen 14,2.
        assertThat(service.decidir(48.0, 62.2))
            .isEqualTo(IdentificacionFacialService.Veredicto.ACEPTADO);
    }

    @Test
    @DisplayName("Un rostro que no esta registrado se rechaza por distancia")
    void decidir_rechazaAlDesconocido() {
        // Con el umbral viejo de 100 esto entraba: es el agujero que se cerro.
        assertThat(service.decidir(84.8, 88.6))
            .isEqualTo(IdentificacionFacialService.Veredicto.NO_REGISTRADO);
        assertThat(service.decidir(108.8, null))
            .isEqualTo(IdentificacionFacialService.Veredicto.NO_REGISTRADO);
    }

    @Test
    @DisplayName("Dos modelos empatados se rechazan aunque entren en el umbral")
    void decidir_rechazaElEmpate() {
        // Caso real: 55,0 contra 56,0. Ambos comodos bajo el umbral, pero separados
        // por un punto: cual gana lo decide una sombra, no la cara.
        assertThat(service.decidir(55.0, 56.0))
            .as("un empate no es una identificacion")
            .isEqualTo(IdentificacionFacialService.Veredicto.AMBIGUO);

        // Y el peor caso medido: margen de 0,3.
        assertThat(service.decidir(52.3, 52.6))
            .isEqualTo(IdentificacionFacialService.Veredicto.AMBIGUO);
    }

    @Test
    @DisplayName("El margen se exige justo, no de mas ni de menos")
    void decidir_bordeDelMargen() {
        // Margen exacto de 12: alcanza.
        assertThat(service.decidir(50.0, 62.0))
            .isEqualTo(IdentificacionFacialService.Veredicto.ACEPTADO);
        // Un decimo menos: no.
        assertThat(service.decidir(50.0, 61.9))
            .isEqualTo(IdentificacionFacialService.Veredicto.AMBIGUO);
    }

    @Test
    @DisplayName("La distancia manda sobre el margen: lejos se rechaza aunque no haya competencia")
    void decidir_laDistanciaMandaPrimero() {
        // Nadie cerca en el segundo puesto, pero el mejor esta lejisimos igual.
        assertThat(service.decidir(90.0, 200.0))
            .as("sin esto, alguien sin registrar entra solo por no parecerse a nadie mas")
            .isEqualTo(IdentificacionFacialService.Veredicto.NO_REGISTRADO);
    }

    @Test
    @DisplayName("Con un solo modelo cargado no hay margen que exigir")
    void decidir_unSoloModelo() {
        assertThat(service.decidir(48.0, null))
            .isEqualTo(IdentificacionFacialService.Veredicto.ACEPTADO);
        // Pero el umbral sigue aplicando.
        assertThat(service.decidir(70.0, null))
            .isEqualTo(IdentificacionFacialService.Veredicto.NO_REGISTRADO);
    }

    // ------------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------------

    // La calidad va en null: al identificar en el pase no se exige, porque un docente
    // apurado en la puerta del aula no puede repetir la pose. La calidad se exige al
    // REGISTRAR, que es cuando se construye el modelo contra el que se compara.
    private DeteccionRostroService.RostroExtraido rostroExtraidoValido() {
        return new DeteccionRostroService.RostroExtraido(new Mat(), 10, 20, 100, 100, null);
    }

    // Modelo facial activo del docente indicado.
    @SuppressWarnings("unused")
    private ModeloFacial modeloDe(Docente d) {
        return ModeloFacial.builder()
            .id(1L).docente(d).activo(true)
            .algoritmo("LBPH").versionAlgoritmo("4.10.0").dimensiones((short) 200)
            .embeddingCifrado(new byte[]{1, 2, 3, 4})
            .build();
    }
}
