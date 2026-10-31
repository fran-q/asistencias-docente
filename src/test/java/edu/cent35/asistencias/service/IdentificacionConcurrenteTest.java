package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.dto.biometria.ModeloActivo;
import edu.cent35.asistencias.model.biometria.ModeloFacial;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.ModeloFacialRepository;
import org.bytedeco.opencv.opencv_core.Mat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Varias cámaras identificando a la vez mientras otro hilo saca modelos del cache, con OpenCV real.
 *
 * <p>Prueba que el recorrido entero aguante varias cámaras con memoria nativa real: que nada se
 * trabe ni termine en una excepción. <b>No prueba que el candado esté</b>: sacándolo, esta corrida
 * siguió pasando, porque leer memoria recién liberada no siempre se nota. Eso lo fija, sin depender
 * de la suerte, {@code IdentificacionFacialServiceTest.liberarEsperaALosQueComparan}.
 */
class IdentificacionConcurrenteTest {

    private static final long INSTITUCION_A = 1L;
    private static final long INSTITUCION_B = 2L;

    private final Random azar = new Random(7);
    private final MotorLbphService motor = new MotorLbphService();

    @Test
    @DisplayName("Identificar y liberar modelos al mismo tiempo no voltea el proceso")
    void identificarYLiberarAlMismoTiempo() throws Exception {
        ModeloFacialRepository modelos = Mockito.mock(ModeloFacialRepository.class);
        DeteccionRostroService deteccion = Mockito.mock(DeteccionRostroService.class);
        CifradoBiometricoService cifrado = Mockito.mock(CifradoBiometricoService.class);
        when(cifrado.descifrar(any())).thenAnswer(i -> i.getArgument(0));

        byte[] modelo = motor.entrenar(capturas());
        List<ModeloActivo> deA = List.of(new ModeloActivo(1L, 11L), new ModeloActivo(2L, 12L));
        List<ModeloActivo> deB = List.of(new ModeloActivo(3L, 13L));
        when(modelos.activosDelTenant(INSTITUCION_A)).thenReturn(deA);
        when(modelos.activosDelTenant(INSTITUCION_B)).thenReturn(deB);
        when(modelos.findAllById(any())).thenAnswer(i -> {
            List<ModeloFacial> faltantes = new ArrayList<>();
            ((Collection<?>) i.getArgument(0)).forEach(id -> faltantes.add(
                ModeloFacial.builder().id((Long) id).embeddingCifrado(modelo).build()));
            return faltantes;
        });
        // Cada cuadro cierra su rostro: tiene que recibir uno propio.
        when(deteccion.extraerRostroNormalizado(any(), anyInt())).thenAnswer(i ->
            new DeteccionRostroService.Extraccion(1,
                new DeteccionRostroService.RostroExtraido(imagen(), 0, 0, 200, 200, null)));

        IdentificacionFacialService servicio = new IdentificacionFacialService(
            modelos, Mockito.mock(DocenteRepository.class), deteccion, motor, cifrado);
        ReflectionTestUtils.setField(servicio, "tamanoRostro", 200);
        ReflectionTestUtils.setField(servicio, "umbralConfianza", 1.0);
        ReflectionTestUtils.setField(servicio, "margenMinimo", 12.0);

        AtomicBoolean seguir = new AtomicBoolean(true);
        ExecutorService hilos = Executors.newFixedThreadPool(5);
        List<Future<Integer>> camaras = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            long institucion = i % 2 == 0 ? INSTITUCION_A : INSTITUCION_B;
            camaras.add(hilos.submit(() -> {
                TenantContext.set(institucion);
                try {
                    int cuadros = 0;
                    while (seguir.get()) {
                        servicio.identificar(new byte[] {1});
                        cuadros++;
                    }
                    return cuadros;
                } finally {
                    TenantContext.clear();
                }
            }));
        }
        Future<?> liberador = hilos.submit(() -> {
            while (seguir.get()) {
                servicio.evictarModelo(1L);
                servicio.evictarModelo(3L);
            }
        });

        Thread.sleep(1500);
        seguir.set(false);

        liberador.get();
        int cuadros = 0;
        for (Future<Integer> camara : camaras) {
            cuadros += camara.get();
        }
        hilos.shutdown();

        assertThat(cuadros).as("las cuatro cámaras llegaron a procesar cuadros").isPositive();
    }

    private List<Mat> capturas() {
        List<Mat> capturas = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            capturas.add(imagen());
        }
        return capturas;
    }

    private synchronized Mat imagen() {
        Mat imagen = new Mat(200, 200, CV_8UC1);
        byte[] pixeles = new byte[200 * 200];
        azar.nextBytes(pixeles);
        imagen.data().put(pixeles);
        return imagen;
    }
}
