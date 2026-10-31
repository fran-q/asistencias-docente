package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.dto.biometria.IdentificacionResultadoDto;
import edu.cent35.asistencias.dto.biometria.ModeloActivo;
import edu.cent35.asistencias.model.biometria.ModeloFacial;
import edu.cent35.asistencias.model.persona.Docente;
import edu.cent35.asistencias.repository.DocenteRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    private static final Long TENANT_B = 2L;
    // Para los casos que miran el cache: nadie queda reconocido y no hay a quién ir a buscar.
    private static final double LEJOS_DEL_UMBRAL = 100.0;

    @Mock private ModeloFacialRepository modeloFacialRepository;
    @Mock private DocenteRepository docenteRepository;
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
        when(deteccionRostroService.extraerRostroNormalizado(any(), anyInt()))
            .thenReturn(sinRostro());

        IdentificacionResultadoDto r = service.identificar(new byte[]{1, 2, 3});

        assertThat(r.rostroDetectado()).isFalse();
        assertThat(r.reconocido()).isFalse();
        assertThat(r.docenteNombre()).isNull();
    }

    @Test
    @DisplayName("identificar: hay rostro pero ningún docente con modelo -> noHayModelos")
    void identificar_sinModelos() {
        when(deteccionRostroService.extraerRostroNormalizado(any(), anyInt()))
            .thenReturn(unRostro());
        when(modeloFacialRepository.activosDelTenant(TENANT_A)).thenReturn(List.of());

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
            .thenReturn(unRostro());
        when(modeloFacialRepository.activosDelTenant(TENANT_A)).thenReturn(List.of());

        IdentificacionResultadoDto r = service.identificar(new byte[]{1, 2, 3});

        assertThat(r.x()).isNotNull();
        assertThat(r.y()).isNotNull();
        assertThat(r.ancho()).isNotNull();
        assertThat(r.alto()).isNotNull();
    }

    @Test
    @DisplayName("identificar: sinRostro tiene todas las coords null")
    void identificar_sinRostro_sinCoords() {
        when(deteccionRostroService.extraerRostroNormalizado(any(), anyInt()))
            .thenReturn(sinRostro());

        IdentificacionResultadoDto r = service.identificar(new byte[]{1, 2, 3});

        assertThat(r.x()).isNull();
        assertThat(r.y()).isNull();
        assertThat(r.ancho()).isNull();
        assertThat(r.alto()).isNull();
    }

    @Test
    @DisplayName("identificar: con dos rostros avisa que hay dos, y sin recuadro")
    void identificar_variosRostros() {
        when(deteccionRostroService.extraerRostroNormalizado(any(), anyInt()))
            .thenReturn(new DeteccionRostroService.Extraccion(2, null));

        IdentificacionResultadoDto r = service.identificar(new byte[]{1, 2, 3});

        assertThat(r.rostroDetectado())
            .as("hay rostros: decir que no hay ninguno manda a corregir justo lo contrario")
            .isTrue();
        assertThat(r.reconocido()).isFalse();
        assertThat(r.mensaje()).contains("2 personas en cuadro");
        // Sin recuadro a propósito: pintar a una de las dos sugeriría que el sistema la eligió.
        assertThat(r.x()).isNull();
    }

    // ========================================================================
    //  El cache con varias instituciones
    // ========================================================================

    @Test
    @DisplayName("Identificar en una institución no le saca los modelos a otra")
    void unaInstitucionNoLeVaciaElCacheALaOtra() {
        when(deteccionRostroService.extraerRostroNormalizado(any(), anyInt()))
            .thenAnswer(i -> unRostro());
        when(cifradoService.descifrar(any())).thenReturn(new byte[]{9});
        when(motorLbph.distancia(any(), any())).thenReturn(LEJOS_DEL_UMBRAL);
        MotorLbphService.Histogramas deA = mock(MotorLbphService.Histogramas.class);
        MotorLbphService.Histogramas deB = mock(MotorLbphService.Histogramas.class);
        when(motorLbph.cargarHistogramas(any())).thenReturn(deA, deB);
        when(modeloFacialRepository.activosDelTenant(TENANT_A))
            .thenReturn(List.of(new ModeloActivo(10L, 100L)));
        when(modeloFacialRepository.activosDelTenant(TENANT_B))
            .thenReturn(List.of(new ModeloActivo(20L, 200L)));
        when(modeloFacialRepository.findAllById(List.of(10L))).thenReturn(List.of(cifrado(10L)));
        when(modeloFacialRepository.findAllById(List.of(20L))).thenReturn(List.of(cifrado(20L)));

        // Dos cámaras turnándose, una por institución: es lo que pasa a las seis de la tarde.
        service.identificar(new byte[]{1});
        TenantContext.set(TENANT_B);
        service.identificar(new byte[]{1});
        TenantContext.set(TENANT_A);
        service.identificar(new byte[]{1});
        TenantContext.set(TENANT_B);
        service.identificar(new byte[]{1});

        verify(motorLbph, times(2)).cargarHistogramas(any());
        verify(deA, never()).close();
        verify(deB, never()).close();
    }

    @Test
    @DisplayName("Un modelo que dejó de estar activo en esa institución se libera")
    void elModeloInactivoSeLibera() {
        when(deteccionRostroService.extraerRostroNormalizado(any(), anyInt()))
            .thenAnswer(i -> unRostro());
        when(cifradoService.descifrar(any())).thenReturn(new byte[]{9});
        when(motorLbph.distancia(any(), any())).thenReturn(LEJOS_DEL_UMBRAL);
        MotorLbphService.Histogramas viejo = mock(MotorLbphService.Histogramas.class);
        when(motorLbph.cargarHistogramas(any())).thenReturn(viejo);
        when(modeloFacialRepository.activosDelTenant(TENANT_A))
            .thenReturn(List.of(new ModeloActivo(10L, 100L)));
        when(modeloFacialRepository.findAllById(List.of(10L))).thenReturn(List.of(cifrado(10L)));

        service.identificar(new byte[]{1});
        when(modeloFacialRepository.activosDelTenant(TENANT_A))
            .thenReturn(List.of(new ModeloActivo(11L, 100L)));
        when(modeloFacialRepository.findAllById(List.of(11L))).thenReturn(List.of(cifrado(11L)));
        service.identificar(new byte[]{1});

        verify(viejo).close();
    }

    @Test
    @DisplayName("El dato cifrado se pide una sola vez: los cuadros siguientes no lo mueven")
    void elDatoCifradoNoSePideEnCadaCuadro() {
        when(deteccionRostroService.extraerRostroNormalizado(any(), anyInt()))
            .thenAnswer(i -> unRostro());
        when(cifradoService.descifrar(any())).thenReturn(new byte[]{9});
        when(motorLbph.distancia(any(), any())).thenReturn(LEJOS_DEL_UMBRAL);
        when(motorLbph.cargarHistogramas(any())).thenReturn(mock(MotorLbphService.Histogramas.class));
        when(modeloFacialRepository.activosDelTenant(TENANT_A))
            .thenReturn(List.of(new ModeloActivo(10L, 100L)));
        when(modeloFacialRepository.findAllById(List.of(10L))).thenReturn(List.of(cifrado(10L)));

        for (int cuadro = 0; cuadro < 5; cuadro++) {
            service.identificar(new byte[]{1});
        }

        verify(modeloFacialRepository, times(1)).findAllById(any());
    }

    @Test
    @DisplayName("Liberar un modelo espera a que termine de compararse: es memoria nativa")
    void liberarEsperaALosQueComparan() throws Exception {
        when(deteccionRostroService.extraerRostroNormalizado(any(), anyInt()))
            .thenAnswer(i -> unRostro());
        when(cifradoService.descifrar(any())).thenReturn(new byte[]{9});
        MotorLbphService.Histogramas histogramas = mock(MotorLbphService.Histogramas.class);
        when(motorLbph.cargarHistogramas(any())).thenReturn(histogramas);
        when(modeloFacialRepository.activosDelTenant(TENANT_A))
            .thenReturn(List.of(new ModeloActivo(10L, 100L)));
        when(modeloFacialRepository.findAllById(List.of(10L))).thenReturn(List.of(cifrado(10L)));

        CountDownLatch comparando = new CountDownLatch(1);
        CountDownLatch terminar = new CountDownLatch(1);
        when(motorLbph.distancia(any(), any())).thenAnswer(i -> {
            comparando.countDown();
            terminar.await(5, TimeUnit.SECONDS);
            return LEJOS_DEL_UMBRAL;
        });

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<?> camara = hilos.submit(() -> {
                TenantContext.set(TENANT_A);
                try {
                    service.identificar(new byte[]{1});
                } finally {
                    TenantContext.clear();
                }
            });
            assertThat(comparando.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> baja = hilos.submit(() -> service.evictarModelo(10L));
            Thread.sleep(200);
            verify(histogramas, never()).close();

            terminar.countDown();
            camara.get(5, TimeUnit.SECONDS);
            baja.get(5, TimeUnit.SECONDS);
            verify(histogramas).close();
        } finally {
            terminar.countDown();
            hilos.shutdownNow();
        }
    }

    @Test
    @DisplayName("Se acepta al mejor candidato con margen, y recién ahí se busca a la persona")
    void aceptaAlMejorConMargen() {
        when(deteccionRostroService.extraerRostroNormalizado(any(), anyInt()))
            .thenAnswer(i -> unRostro());
        when(cifradoService.descifrar(any())).thenReturn(new byte[]{9});
        MotorLbphService.Histogramas cerca = mock(MotorLbphService.Histogramas.class);
        MotorLbphService.Histogramas lejos = mock(MotorLbphService.Histogramas.class);
        when(motorLbph.cargarHistogramas(any())).thenReturn(cerca, lejos);
        when(motorLbph.distancia(org.mockito.ArgumentMatchers.eq(cerca), any())).thenReturn(40.0);
        when(motorLbph.distancia(org.mockito.ArgumentMatchers.eq(lejos), any())).thenReturn(60.0);
        when(modeloFacialRepository.activosDelTenant(TENANT_A))
            .thenReturn(List.of(new ModeloActivo(10L, 100L), new ModeloActivo(20L, 200L)));
        when(modeloFacialRepository.findAllById(any()))
            .thenReturn(List.of(cifrado(10L), cifrado(20L)));
        Docente docente = mock(Docente.class);
        when(docente.getId()).thenReturn(100L);
        when(docenteRepository.buscarDelTenant(TENANT_A, 100L))
            .thenReturn(java.util.Optional.of(docente));

        IdentificacionResultadoDto r = service.identificar(new byte[]{1});

        assertThat(r.reconocido()).isTrue();
        assertThat(r.docenteId()).isEqualTo(100L);
        assertThat(r.modeloFacialId()).isEqualTo(10L);
    }

    // ========================================================================
    //  La regla que decide si se acepta (falso positivo de 2026-08-01)
    //
    //  Las distancias de estos casos salen del log de una sesión real, cuando el
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
        // Con el umbral viejo de 100 esto entraba: es el agujero que se cerró.
        assertThat(service.decidir(84.8, 88.6))
            .isEqualTo(IdentificacionFacialService.Veredicto.NO_REGISTRADO);
        assertThat(service.decidir(108.8, null))
            .isEqualTo(IdentificacionFacialService.Veredicto.NO_REGISTRADO);
    }

    @Test
    @DisplayName("Dos modelos empatados se rechazan aunque entren en el umbral")
    void decidir_rechazaElEmpate() {
        // Caso real: 55,0 contra 56,0. Ambos cómodos bajo el umbral, pero separados
        // por un punto: cuál gana lo decide una sombra, no la cara.
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
    // Extracción con un único rostro utilizable, que es el caso normal.
    private DeteccionRostroService.Extraccion unRostro() {
        return new DeteccionRostroService.Extraccion(1,
            new DeteccionRostroService.RostroExtraido(new Mat(), 10, 20, 100, 100, null));
    }

    // Extracción sin ningún rostro en el cuadro.
    private DeteccionRostroService.Extraccion sinRostro() {
        return new DeteccionRostroService.Extraccion(0, null);
    }

    // Un modelo tal como vuelve de la base, con su dato cifrado.
    private ModeloFacial cifrado(Long id) {
        return ModeloFacial.builder().id(id).embeddingCifrado(new byte[]{1}).build();
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
