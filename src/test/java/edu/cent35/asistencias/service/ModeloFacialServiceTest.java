package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoConsentimiento;
import edu.cent35.asistencias.model.ModeloFacial;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.ModeloFacialRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModeloFacialServiceTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final Long DOCENTE_ID = 50L;
    private static final Long USUARIO_ACTUAL_ID = 100L;

    @Mock private ModeloFacialRepository modeloFacialRepository;
    @Mock private DocenteRepository docenteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ConsentimientoBiometricoService consentimientoService;
    @Mock private DeteccionRostroService deteccionRostroService;
    @Mock private MotorLbphService motorLbph;
    @Mock private CifradoBiometricoService cifradoService;
    @Mock private IdentificacionFacialService identificacionFacialService;

    @InjectMocks private ModeloFacialService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_A);
        ReflectionTestUtils.setField(service, "minimoCapturasValidas", 5);
        ReflectionTestUtils.setField(service, "tamanoRostro", 200);
        ReflectionTestUtils.setField(service, "duracionGrabacionSeg", 30);
        ReflectionTestUtils.setField(service, "intervaloCapturaMs", 1500);
    }

    @AfterEach
    void clear() { TenantContext.clear(); }

    // ========================================================================
    //  registrar
    // ========================================================================

    @Test
    @DisplayName("registrar: ok con consentimiento activo y 10 capturas válidas")
    void registrar_ok() {
        Docente docente = docenteActivoA();
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docente));
        when(consentimientoService.estadoActual(DOCENTE_ID)).thenReturn(EstadoConsentimiento.ACTIVO);
        when(deteccionRostroService.extraerRostroNormalizado(any(), anyInt()))
            .thenReturn(rostroExtraidoValido());
        when(motorLbph.entrenar(anyList())).thenReturn(new byte[]{1, 2, 3});
        when(cifradoService.cifrar(any())).thenReturn(new byte[]{9, 8, 7});
        when(modeloFacialRepository.findByDocenteIdAndActivoTrue(DOCENTE_ID))
            .thenReturn(Optional.empty());
        when(usuarioRepository.findById(USUARIO_ACTUAL_ID)).thenReturn(Optional.of(usuarioA()));
        when(modeloFacialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ModeloFacial guardado = service.registrar(DOCENTE_ID,
            capturasDe(10), USUARIO_ACTUAL_ID);

        assertThat(guardado.getActivo()).isTrue();
        assertThat(guardado.getAlgoritmo()).isEqualTo("LBPH");
        assertThat(guardado.getDocente()).isSameAs(docente);
        assertThat(guardado.getEmbeddingCifrado()).isEqualTo(new byte[]{9, 8, 7});
        assertThat(guardado.getDimensiones()).isEqualTo((short) 200);
    }

    @Test
    @DisplayName("registrar: rechaza docente inactivo")
    void registrar_docenteInactivo() {
        Docente inactivo = docenteActivoA();
        inactivo.setActivo(false);
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(inactivo));

        assertThatThrownBy(() -> service.registrar(DOCENTE_ID, capturasDe(10), USUARIO_ACTUAL_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("inactivo");
        verify(modeloFacialRepository, never()).save(any());
    }

    @Test
    @DisplayName("registrar: rechaza si no hay consentimiento ACTIVO")
    void registrar_sinConsentimiento() {
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docenteActivoA()));
        when(consentimientoService.estadoActual(DOCENTE_ID))
            .thenReturn(EstadoConsentimiento.NUNCA_OTORGADO);

        assertThatThrownBy(() -> service.registrar(DOCENTE_ID, capturasDe(10), USUARIO_ACTUAL_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("consentimiento");
        verify(modeloFacialRepository, never()).save(any());
    }

    @Test
    @DisplayName("registrar: rechaza si la grabación llega vacía")
    void registrar_capturasVacias() {
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docenteActivoA()));
        when(consentimientoService.estadoActual(DOCENTE_ID)).thenReturn(EstadoConsentimiento.ACTIVO);

        assertThatThrownBy(() -> service.registrar(DOCENTE_ID, List.of(), USUARIO_ACTUAL_ID))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("registrar: rechaza si quedan menos rostros válidos que el mínimo")
    void registrar_pocasCapturasValidas() {
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docenteActivoA()));
        when(consentimientoService.estadoActual(DOCENTE_ID)).thenReturn(EstadoConsentimiento.ACTIVO);
        // todas las capturas se descartan (no detectó rostro en ninguna)
        when(deteccionRostroService.extraerRostroNormalizado(any(), anyInt())).thenReturn(null);

        assertThatThrownBy(() -> service.registrar(DOCENTE_ID, capturasDe(10), USUARIO_ACTUAL_ID))
            .isInstanceOf(IllegalArgumentException.class);
        verify(modeloFacialRepository, never()).save(any());
    }

    @Test
    @DisplayName("registrar: re-registro (RF-09) da de baja el modelo anterior")
    void registrar_reRegistroDaBajaAnterior() {
        Docente docente = docenteActivoA();
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docente));
        when(consentimientoService.estadoActual(DOCENTE_ID)).thenReturn(EstadoConsentimiento.ACTIVO);
        when(deteccionRostroService.extraerRostroNormalizado(any(), anyInt()))
            .thenReturn(rostroExtraidoValido());
        when(motorLbph.entrenar(anyList())).thenReturn(new byte[]{1, 2, 3});
        when(cifradoService.cifrar(any())).thenReturn(new byte[]{9, 8, 7});

        ModeloFacial anterior = ModeloFacial.builder()
            .id(900L).docente(docente).activo(true).build();
        when(modeloFacialRepository.findByDocenteIdAndActivoTrue(DOCENTE_ID))
            .thenReturn(Optional.of(anterior));
        when(usuarioRepository.findById(USUARIO_ACTUAL_ID)).thenReturn(Optional.of(usuarioA()));
        when(modeloFacialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.registrar(DOCENTE_ID, capturasDe(10), USUARIO_ACTUAL_ID);

        // El anterior se marcó inactivo + fecha de baja antes de guardar el nuevo.
        assertThat(anterior.getActivo()).isFalse();
        assertThat(anterior.getFechaBaja()).isNotNull();
    }

    @Test
    @DisplayName("registrar: docente de otro tenant -> EntityNotFound (no revela existencia)")
    void registrar_crossTenant() {
        Docente ajeno = docenteActivoA();
        ajeno.setInstitucionId(TENANT_B);
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.registrar(DOCENTE_ID, capturasDe(10), USUARIO_ACTUAL_ID))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ========================================================================
    //  suprimirDatosBiometricos (ARCO - RNF-14)
    // ========================================================================

    @Test
    @DisplayName("suprimir: borra FISICAMENTE todos los modelos y evicta el cache")
    void suprimir_borradoFisico() {
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docenteActivoA()));
        ModeloFacial activo = ModeloFacial.builder().id(10L).activo(true).build();
        ModeloFacial historico = ModeloFacial.builder().id(9L).activo(false).build();
        when(modeloFacialRepository.findByDocenteIdOrderByFechaRegistroDescIdDesc(DOCENTE_ID))
            .thenReturn(List.of(activo, historico));

        int suprimidos = service.suprimirDatosBiometricos(DOCENTE_ID, USUARIO_ACTUAL_ID);

        assertThat(suprimidos).isEqualTo(2);
        // Borrado fisico: deleteAll, NO baja logica (save nunca se llama).
        verify(modeloFacialRepository).deleteAll(List.of(activo, historico));
        verify(modeloFacialRepository, never()).save(any());
        // Defensa en profundidad: el cache en memoria tambien se evicta.
        verify(identificacionFacialService).evictarModelo(10L);
        verify(identificacionFacialService).evictarModelo(9L);
    }

    @Test
    @DisplayName("suprimir: sin modelos -> error claro, no borra nada")
    void suprimir_sinModelos() {
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docenteActivoA()));
        when(modeloFacialRepository.findByDocenteIdOrderByFechaRegistroDescIdDesc(DOCENTE_ID))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.suprimirDatosBiometricos(DOCENTE_ID, USUARIO_ACTUAL_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("suprimir");
        verify(modeloFacialRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("suprimir: docente de otro tenant -> EntityNotFound (no revela existencia)")
    void suprimir_crossTenant() {
        Docente ajeno = docenteActivoA();
        ajeno.setInstitucionId(TENANT_B);
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.suprimirDatosBiometricos(DOCENTE_ID, USUARIO_ACTUAL_ID))
            .isInstanceOf(EntityNotFoundException.class);
        verify(modeloFacialRepository, never()).deleteAll(anyList());
    }

    // ========================================================================
    //  estado / tieneModeloActivo
    // ========================================================================

    @Test
    @DisplayName("tieneModeloActivo: true si hay activo, false si no")
    void tieneModeloActivo() {
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(docenteActivoA()));
        when(modeloFacialRepository.findByDocenteIdAndActivoTrue(DOCENTE_ID))
            .thenReturn(Optional.of(ModeloFacial.builder().id(1L).activo(true).build()));
        assertThat(service.tieneModeloActivo(DOCENTE_ID)).isTrue();

        when(modeloFacialRepository.findByDocenteIdAndActivoTrue(DOCENTE_ID))
            .thenReturn(Optional.empty());
        assertThat(service.tieneModeloActivo(DOCENTE_ID)).isFalse();
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    private Docente docenteActivoA() {
        Docente d = Docente.builder()
            .id(DOCENTE_ID).dni("12345678").nombre("Juana").apellido("Pérez").activo(true)
            .build();
        d.setInstitucionId(TENANT_A);
        return d;
    }

    private Usuario usuarioA() {
        Usuario u = Usuario.builder()
            .id(USUARIO_ACTUAL_ID).username("admin").email("a@x.com")
            .passwordHash("xxx").nombre("Admin").apellido("Test").activo(true)
            .build();
        u.setInstitucionId(TENANT_A);
        return u;
    }

    private List<byte[]> capturasDe(int n) {
        byte[][] arr = new byte[n][];
        for (int i = 0; i < n; i++) arr[i] = new byte[]{ (byte) i };
        return List.of(arr);
    }

    // Stub de RostroExtraido con un Mat vacío. El servicio no inspecciona la imagen.
    private DeteccionRostroService.RostroExtraido rostroExtraidoValido() {
        return new DeteccionRostroService.RostroExtraido(new Mat(), 0, 0, 200, 200);
    }
}
