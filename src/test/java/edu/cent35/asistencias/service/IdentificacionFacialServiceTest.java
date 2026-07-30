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
        ReflectionTestUtils.setField(service, "umbralConfianza", 100.0);
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
