package edu.cent35.asistencias.service;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.dto.BloqueDeHorarios;
import edu.cent35.asistencias.model.BloquePresencia;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoCierre;
import edu.cent35.asistencias.model.EstadoConsentimiento;
import edu.cent35.asistencias.model.EstadoSalida;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.ModeloFacial;
import edu.cent35.asistencias.model.MotivoCargaManual;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.model.OrigenMarca;
import edu.cent35.asistencias.repository.BloquePresenciaRepository;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.ModeloFacialRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre el ciclo de vida del bloque de presencia (RF-74 a RF-82): que el sentido de la marca se
 * deduzca del estado, la permanencia mínima, la clasificación de la salida y la guarda del
 * consentimiento.
 * <p>
 * Los casos legales son los que más importan: sin consentimiento vigente no se abre ni se
 * cierra un bloque, y un bloque que no se pudo cerrar tiene que quedar abierto en vez de
 * cerrarse igual por conveniencia.
 */
@ExtendWith(MockitoExtension.class)
class BloquePresenciaServiceTest {

    private static final Long TENANT_A = 1L;
    private static final Long DOCENTE_ID = 50L;
    private static final Long BLOQUE_ID = 300L;

    // 2026-05-25 fue lunes.
    private static final LocalDate UN_LUNES = LocalDate.of(2026, 5, 25);

    @Mock private BloquePresenciaRepository bloqueRepository;
    @Mock private HorarioRepository horarioRepository;
    @Mock private DocenteRepository docenteRepository;
    @Mock private ModeloFacialRepository modeloFacialRepository;
    @Mock private ResolutorDeBloquesService resolutor;
    @Mock private AsistenciaService asistenciaService;
    @Mock private ConsentimientoBiometricoService consentimientoService;
    @Mock private edu.cent35.asistencias.repository.AsistenciaRepository asistenciaRepository;
    @Mock private edu.cent35.asistencias.repository.UsuarioRepository usuarioRepository;
    @Mock private edu.cent35.asistencias.repository.MotivoCargaManualRepository motivoCargaManualRepository;

    @InjectMocks private BloquePresenciaService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_A);
        ReflectionTestUtils.setField(service, "permanenciaMinimaMin", 10);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    // ========================================================================
    //  Guarda legal
    // ========================================================================

    @Nested
    @DisplayName("consentimiento")
    class Consentimiento {

        @Test
        @DisplayName("sin consentimiento vigente no se abre ningún bloque")
        void sinConsentimientoNoAbre() {
            docenteDelTenant();
            when(consentimientoService.estadoActual(DOCENTE_ID))
                .thenReturn(EstadoConsentimiento.REVOCADO);

            var r = service.registrar(DOCENTE_ID, 9L, 40.0, UN_LUNES.atTime(18, 0));

            assertThat(r.tipo()).isEqualTo(BloquePresenciaService.TipoDeMarca.RECHAZADA);
            assertThat(r.motivo()).contains("consentimiento");
            verify(bloqueRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("revocado entre la entrada y la salida: el bloque queda abierto")
        void revocadoConBloqueAbiertoNoCierra() {
            // Caso del ADR-0017 decisión 8: la entrada ya registrada vale, porque era lícita
            // cuando ocurrió, pero el rostro no se puede usar para cerrar. Lo cierra un admin.
            docenteDelTenant();
            when(consentimientoService.estadoActual(DOCENTE_ID))
                .thenReturn(EstadoConsentimiento.REVOCADO);

            var r = service.registrar(DOCENTE_ID, 9L, 40.0, UN_LUNES.atTime(21, 0));

            assertThat(r.tipo()).isEqualTo(BloquePresenciaService.TipoDeMarca.RECHAZADA);
            assertThat(r.motivo()).contains("carga manual");
            // Ni siquiera se consultó si había un bloque abierto: la guarda va primero.
            verify(bloqueRepository, never()).findByDocenteIdAndEstadoCierre(any(), any());
            verify(bloqueRepository, never()).saveAndFlush(any());
        }
    }

    // ========================================================================
    //  Apertura
    // ========================================================================

    @Nested
    @DisplayName("entrada")
    class Entrada {

        @Test
        @DisplayName("sin bloque abierto y con clase en curso, abre e imputa solo esa clase")
        void abreEImputaLaClaseEnCurso() {
            docenteDelTenant();
            consentimientoActivo();
            sinBloqueAbierto();

            Horario primera = horario(1L, 18, 0, 20, 0);
            Horario segunda = horario(2L, 20, 0, 22, 0);
            bloqueEnCursoCon(LocalTime.of(18, 0), LocalTime.of(22, 0), primera, segunda);
            guardaElBloque();

            var r = service.registrar(DOCENTE_ID, null, 40.0, UN_LUNES.atTime(18, 2));

            assertThat(r.tipo()).isEqualTo(BloquePresenciaService.TipoDeMarca.ENTRADA);
            assertThat(r.clasesImputadas()).isEqualTo(1);

            // La clase de las 20 NO se marca al entrar: todavía no empezó. Si el docente se
            // fuera a las 20:00 quedaría registrado que dictó algo que no dictó.
            verify(asistenciaService).imputarDelBloque(any(), eq(primera), eq(LocalTime.of(18, 2)));
            verify(asistenciaService, never()).imputarDelBloque(any(), eq(segunda), any());
        }

        @Test
        @DisplayName("el bloque nace abierto, automático y sin datos de salida")
        void elBloqueNaceAbierto() {
            docenteDelTenant();
            consentimientoActivo();
            sinBloqueAbierto();
            bloqueEnCursoCon(LocalTime.of(18, 0), LocalTime.of(20, 0), horario(1L, 18, 0, 20, 0));
            guardaElBloque();

            service.registrar(DOCENTE_ID, null, 40.0, UN_LUNES.atTime(18, 2));

            ArgumentCaptor<BloquePresencia> captor = ArgumentCaptor.forClass(BloquePresencia.class);
            verify(bloqueRepository).saveAndFlush(captor.capture());
            BloquePresencia guardado = captor.getValue();

            assertThat(guardado.getEstadoCierre()).isEqualTo(EstadoCierre.ABIERTO);
            assertThat(guardado.getOrigenEntrada()).isEqualTo(OrigenMarca.AUTOMATICO);
            assertThat(guardado.getHoraEntrada()).isEqualTo(LocalTime.of(18, 2));
            assertThat(guardado.getInstitucionId()).isEqualTo(TENANT_A);
            // El CHECK ck_bloques_cierre_coherente exige que un bloque abierto no tenga nada
            // de la salida cargado.
            assertThat(guardado.getHoraSalida()).isNull();
            assertThat(guardado.getOrigenSalida()).isNull();
            assertThat(guardado.getEstadoSalida()).isNull();
        }

        @Test
        @DisplayName("sin clase en ventana no se abre nada")
        void sinClaseNoAbre() {
            docenteDelTenant();
            consentimientoActivo();
            sinBloqueAbierto();
            when(resolutor.bloqueEnCurso(eq(DOCENTE_ID), any())).thenReturn(Optional.empty());

            var r = service.registrar(DOCENTE_ID, null, 40.0, UN_LUNES.atTime(15, 0));

            assertThat(r.tipo()).isEqualTo(BloquePresenciaService.TipoDeMarca.RECHAZADA);
            assertThat(r.motivo()).contains("No hay clase");
            verify(bloqueRepository, never()).saveAndFlush(any());
        }
    }

    // ========================================================================
    //  Cierre
    // ========================================================================

    @Nested
    @DisplayName("salida")
    class Salida {

        @Test
        @DisplayName("antes de la permanencia mínima no se acepta la salida")
        void permanenciaMinimaNoCumplida() {
            // Sin esta guarda, el docente que se queda frente a la cámara después de entrar
            // recibe su propia salida a los pocos segundos (RF-77).
            docenteDelTenant();
            consentimientoActivo();
            conBloqueAbierto(LocalTime.of(18, 0), UN_LUNES);

            var r = service.registrar(DOCENTE_ID, null, 40.0, UN_LUNES.atTime(18, 7));

            assertThat(r.tipo()).isEqualTo(BloquePresenciaService.TipoDeMarca.RECHAZADA);
            assertThat(r.motivo()).contains("10 minutos");
            verify(bloqueRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("borde: a los 10 minutos exactos ya se acepta")
        void permanenciaMinimaJusta() {
            docenteDelTenant();
            consentimientoActivo();
            BloquePresencia abierto = conBloqueAbierto(LocalTime.of(18, 0), UN_LUNES);
            horariosDelDia(horario(1L, 18, 0, 20, 0));
            guardaElBloque();

            var r = service.registrar(DOCENTE_ID, null, 40.0, UN_LUNES.atTime(18, 10));

            assertThat(r.tipo()).isEqualTo(BloquePresenciaService.TipoDeMarca.SALIDA);
            assertThat(abierto.getEstadoCierre()).isEqualTo(EstadoCierre.CERRADO_POR_ROSTRO);
        }

        @Test
        @DisplayName("cierra por rostro y clasifica la salida en hora")
        void cierraEnHora() {
            docenteDelTenant();
            consentimientoActivo();
            BloquePresencia abierto = conBloqueAbierto(LocalTime.of(18, 0), UN_LUNES);
            horariosDelDia(horario(1L, 18, 0, 20, 0));
            guardaElBloque();

            // 19:50 está dentro de la tolerancia de 15 previa al fin: es salida en hora.
            var r = service.registrar(DOCENTE_ID, 9L, 45.0, UN_LUNES.atTime(19, 50));

            assertThat(r.tipo()).isEqualTo(BloquePresenciaService.TipoDeMarca.SALIDA);
            assertThat(abierto.getEstadoCierre()).isEqualTo(EstadoCierre.CERRADO_POR_ROSTRO);
            assertThat(abierto.getOrigenSalida()).isEqualTo(OrigenMarca.AUTOMATICO);
            assertThat(abierto.getHoraSalida()).isEqualTo(LocalTime.of(19, 50));
            assertThat(abierto.getEstadoSalida()).isEqualTo(EstadoSalida.EN_HORA);
        }

        @Test
        @DisplayName("irse antes de la tolerancia es salida anticipada")
        void cierraAnticipada() {
            docenteDelTenant();
            consentimientoActivo();
            BloquePresencia abierto = conBloqueAbierto(LocalTime.of(18, 0), UN_LUNES);
            horariosDelDia(horario(1L, 18, 0, 20, 0));
            guardaElBloque();

            // 19:30 es anterior a 20:00 menos la tolerancia de 15.
            service.registrar(DOCENTE_ID, null, 45.0, UN_LUNES.atTime(19, 30));

            assertThat(abierto.getEstadoSalida()).isEqualTo(EstadoSalida.ANTICIPADA);
        }

        @Test
        @DisplayName("imputa las clases cubiertas y deja afuera las que no alcanzó a dar")
        void imputaSoloLoCubierto() {
            docenteDelTenant();
            consentimientoActivo();
            BloquePresencia abierto = conBloqueAbierto(LocalTime.of(18, 0), UN_LUNES);

            Horario primera = horario(1L, 18, 0, 20, 0);
            Horario segunda = horario(2L, 20, 0, 22, 0);
            horariosDelDia(primera, segunda);
            guardaElBloque();

            // Se va 20:05: cubrió la primera entera y apenas cinco minutos de la segunda.
            var r = service.registrar(DOCENTE_ID, null, 45.0, UN_LUNES.atTime(20, 5));

            assertThat(r.clasesImputadas()).isEqualTo(2);
            // En la clase donde entró, la hora de llegada es la de la entrada real.
            verify(asistenciaService).imputarDelBloque(any(), eq(primera), eq(LocalTime.of(18, 0)));
            // En la que empezó con el docente ya adentro, su propia hora de inicio: estaba ahí.
            verify(asistenciaService).imputarDelBloque(any(), eq(segunda), eq(LocalTime.of(20, 0)));
            assertThat(abierto.getEstadoSalida()).isEqualTo(EstadoSalida.ANTICIPADA);
        }

        @Test
        @DisplayName("una clase que empieza después de la salida no se imputa")
        void noImputaLaClaseQueNoDio() {
            docenteDelTenant();
            consentimientoActivo();
            conBloqueAbierto(LocalTime.of(18, 0), UN_LUNES);

            Horario primera = horario(1L, 18, 0, 20, 0);
            Horario segunda = horario(2L, 20, 0, 22, 0);
            horariosDelDia(primera, segunda);
            guardaElBloque();

            var r = service.registrar(DOCENTE_ID, null, 45.0, UN_LUNES.atTime(19, 0));

            assertThat(r.clasesImputadas()).isEqualTo(1);
            verify(asistenciaService, never()).imputarDelBloque(any(), eq(segunda), any());
        }

        @Test
        @DisplayName("un bloque abierto de un día anterior se puede cerrar sin esperar")
        void bloqueDeAyerNoEsperaLaPermanencia() {
            // Las horas son LocalTime: restarlas entre días distintos da cualquier cosa, así
            // que la permanencia se da por cumplida.
            docenteDelTenant();
            consentimientoActivo();
            conBloqueAbierto(LocalTime.of(22, 0), UN_LUNES.minusDays(1));
            horariosDelDia(horario(1L, 18, 0, 20, 0));
            guardaElBloque();

            var r = service.registrar(DOCENTE_ID, null, 45.0, UN_LUNES.atTime(8, 5));

            assertThat(r.tipo()).isEqualTo(BloquePresenciaService.TipoDeMarca.SALIDA);
        }
    }

    // ========================================================================
    //  Cierre automatico
    // ========================================================================

    @Nested
    @DisplayName("cerrarBloquesVencidos")
    class CierreAutomatico {

        @Test
        @DisplayName("cierra el bloque de hoy cuya jornada ya terminó, como presunto")
        void cierraElBloqueVencido() {
            BloquePresencia abierto = bloqueAbiertoEnLista(LocalTime.of(18, 0), UN_LUNES);
            franjaDelDia(LocalTime.of(18, 0), LocalTime.of(22, 0), horario(1L, 18, 0, 22, 0));
            horariosDelDia(horario(1L, 18, 0, 22, 0));
            guardaElBloque();

            int cerrados = service.cerrarBloquesVencidos(UN_LUNES, LocalTime.of(22, 30));

            assertThat(cerrados).isEqualTo(1);
            assertThat(abierto.getEstadoCierre()).isEqualTo(EstadoCierre.SIN_CIERRE);
            assertThat(abierto.getOrigenSalida()).isEqualTo(OrigenMarca.PRESUNTO);
            assertThat(abierto.getEstadoSalida()).isEqualTo(EstadoSalida.SIN_MARCA);
            assertThat(abierto.getHoraSalida()).isEqualTo(LocalTime.of(22, 0));
        }

        @Test
        @DisplayName("una salida presunta no lleva evidencia biométrica")
        void laSalidaPresuntaNoTieneModelo() {
            // Nadie pasó por la cámara, así que no hay reconocimiento que guardar. Lo exige
            // ck_bloques_salida_modelo, y es lo que distingue una hora medida de una supuesta.
            BloquePresencia abierto = bloqueAbiertoEnLista(LocalTime.of(18, 0), UN_LUNES);
            franjaDelDia(LocalTime.of(18, 0), LocalTime.of(20, 0), horario(1L, 18, 0, 20, 0));
            horariosDelDia(horario(1L, 18, 0, 20, 0));
            guardaElBloque();

            service.cerrarBloquesVencidos(UN_LUNES, LocalTime.of(21, 0));

            assertThat(abierto.getModeloFacialSalida()).isNull();
            assertThat(abierto.getConfianzaSalida()).isNull();
        }

        @Test
        @DisplayName("no toca el bloque de hoy cuya jornada sigue corriendo")
        void noCierraLaJornadaEnCurso() {
            bloqueAbiertoEnLista(LocalTime.of(18, 0), UN_LUNES);
            franjaDelDia(LocalTime.of(18, 0), LocalTime.of(22, 0), horario(1L, 18, 0, 22, 0));

            int cerrados = service.cerrarBloquesVencidos(UN_LUNES, LocalTime.of(20, 0));

            assertThat(cerrados).isZero();
            verify(bloqueRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("la asistencia no queda rehén de la salida: imputa igual lo cubierto")
        void imputaAunqueNadieHayaMarcadoLaSalida() {
            // RF-80: un docente no puede figurar ausente de una clase que dio porque el
            // procedimiento administrativo falló.
            BloquePresencia abierto = bloqueAbiertoEnLista(LocalTime.of(18, 0), UN_LUNES);
            Horario primera = horario(1L, 18, 0, 20, 0);
            Horario segunda = horario(2L, 20, 0, 22, 0);
            franjaDelDia(LocalTime.of(18, 0), LocalTime.of(22, 0), primera, segunda);
            horariosDelDia(primera, segunda);
            guardaElBloque();

            service.cerrarBloquesVencidos(UN_LUNES, LocalTime.of(22, 30));

            verify(asistenciaService).imputarDelBloque(any(), eq(primera), eq(LocalTime.of(18, 0)));
            verify(asistenciaService).imputarDelBloque(any(), eq(segunda), eq(LocalTime.of(20, 0)));
            assertThat(abierto.getEstadoCierre()).isEqualTo(EstadoCierre.SIN_CIERRE);
        }

        @Test
        @DisplayName("un bloque abierto de un día anterior se cierra siempre")
        void cierraElBloqueDeAyer() {
            // Mientras siga abierto, el docente no puede volver a entrar: el UNIQUE de un solo
            // bloque abierto por docente se lo impide.
            BloquePresencia abierto = bloqueAbiertoEnLista(LocalTime.of(18, 0), UN_LUNES.minusDays(1));
            franjaDelDia(LocalTime.of(18, 0), LocalTime.of(20, 0), horario(1L, 18, 0, 20, 0));
            horariosDelDia(horario(1L, 18, 0, 20, 0));
            guardaElBloque();

            int cerrados = service.cerrarBloquesVencidos(UN_LUNES, LocalTime.of(9, 0));

            assertThat(cerrados).isEqualTo(1);
            assertThat(abierto.getEstadoCierre()).isEqualTo(EstadoCierre.SIN_CIERRE);
        }

        @Test
        @DisplayName("si la franja ya no existe lo cierra igual, sin dejarlo abierto")
        void franjaBorradaNoDejaElBloqueAbierto() {
            // Alguien cambió la grilla entre la entrada y ahora. No hay de dónde deducir la
            // hora, pero dejarlo abierto bloquearía al docente para siempre.
            BloquePresencia abierto = bloqueAbiertoEnLista(LocalTime.of(18, 0), UN_LUNES.minusDays(1));
            when(resolutor.bloquesDelDia(eq(DOCENTE_ID), any())).thenReturn(List.of());
            horariosDelDia();
            guardaElBloque();

            int cerrados = service.cerrarBloquesVencidos(UN_LUNES, LocalTime.of(9, 0));

            assertThat(cerrados).isEqualTo(1);
            assertThat(abierto.getEstadoCierre()).isEqualTo(EstadoCierre.SIN_CIERRE);
            // Tiene que ser posterior a la entrada: lo exige ck_bloques_salida_posterior.
            assertThat(abierto.getHoraSalida()).isAfter(abierto.getHoraEntrada());
        }
    }

    // ========================================================================
    //  Cierre manual (RF-83)
    // ========================================================================

    @Nested
    @DisplayName("cerrarManualmente")
    class CierreManual {

        @Test
        @DisplayName("cierra el bloque dejando quien lo hizo y por que")
        void cierraConAutorYMotivo() {
            BloquePresencia b = bloquePorId(LocalTime.of(18, 0), UN_LUNES, EstadoCierre.SIN_CIERRE);
            conMotivo((short) 2, "FALLA_RECONOCIMIENTO");
            conAdmin();
            horariosDelDia(horario(1L, 18, 0, 20, 0));
            asistenciasDelBloque();
            guardaElBloque();

            var r = service.cerrarManualmente(BLOQUE_ID, LocalTime.of(20, 0), (short) 2, null, 99L);

            assertThat(r.bloque().getEstadoCierre()).isEqualTo(EstadoCierre.CERRADO_POR_ADMIN);
            assertThat(r.bloque().getOrigenSalida()).isEqualTo(OrigenMarca.MANUAL);
            assertThat(r.bloque().getHoraSalida()).isEqualTo(LocalTime.of(20, 0));
            assertThat(r.bloque().getCerradoPor()).isNotNull();
            assertThat(r.bloque().getMotivoCierre()).isNotNull();
            assertThat(r.imputadas()).isEqualTo(1);
        }

        @Test
        @DisplayName("corregir un cierre por rostro borra su evidencia biometrica")
        void corregirBorraLaEvidencia() {
            // El CHECK ck_bloques_salida_modelo solo admite modelo y confianza en un cierre
            // AUTOMATICO, y ademas la hora ya no la sostiene una medicion sino una persona.
            BloquePresencia b = bloquePorId(LocalTime.of(18, 0), UN_LUNES, EstadoCierre.CERRADO_POR_ROSTRO);
            b.setModeloFacialSalida(ModeloFacial.builder().id(7L).build());
            b.setConfianzaSalida(new java.math.BigDecimal("0.9000"));
            conMotivo((short) 4, "OTRO");
            conAdmin();
            horariosDelDia(horario(1L, 18, 0, 20, 0));
            asistenciasDelBloque();
            guardaElBloque();

            var r = service.cerrarManualmente(
                BLOQUE_ID, LocalTime.of(19, 30), (short) 4, "El reloj estaba mal", 99L);

            assertThat(r.bloque().getModeloFacialSalida()).isNull();
            assertThat(r.bloque().getConfianzaSalida()).isNull();
            assertThat(r.bloque().getDetalleCierre()).isEqualTo("El reloj estaba mal");
        }

        @Test
        @DisplayName("el motivo OTRO sin detalle se rechaza")
        void otroSinDetalle() {
            bloquePorId(LocalTime.of(18, 0), UN_LUNES, EstadoCierre.SIN_CIERRE);
            conMotivo((short) 4, "OTRO");

            assertThatThrownBy(() -> service.cerrarManualmente(
                    BLOQUE_ID, LocalTime.of(20, 0), (short) 4, "   ", 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("detalle");
        }

        @Test
        @DisplayName("una salida anterior a la entrada se rechaza")
        void salidaAnteriorALaEntrada() {
            bloquePorId(LocalTime.of(18, 0), UN_LUNES, EstadoCierre.SIN_CIERRE);

            assertThatThrownBy(() -> service.cerrarManualmente(
                    BLOQUE_ID, LocalTime.of(17, 0), (short) 1, null, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posterior");
        }

        @Test
        @DisplayName("un bloque de otra institucion responde no encontrado, no no autorizado")
        void cruzaTenant() {
            BloquePresencia ajeno = BloquePresencia.builder()
                .id(BLOQUE_ID).fecha(UN_LUNES).horaEntrada(LocalTime.of(18, 0))
                .estadoCierre(EstadoCierre.SIN_CIERRE).build();
            ajeno.setInstitucionId(999L);
            when(bloqueRepository.findById(BLOQUE_ID)).thenReturn(Optional.of(ajeno));

            assertThatThrownBy(() -> service.cerrarManualmente(
                    BLOQUE_ID, LocalTime.of(20, 0), (short) 1, null, 99L))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
                .hasMessageContaining("no encontrado");
        }

        @Test
        @DisplayName("acortar el rango informa que clases quedan afuera, sin borrarlas")
        void informaLasQueQuedanAfuera() {
            // Quitar una marca de asistencia es otro acto administrativo y tiene su propio
            // flujo: aca solo se avisa, para que el admin decida.
            BloquePresencia b = bloquePorId(LocalTime.of(18, 0), UN_LUNES, EstadoCierre.SIN_CIERRE);
            Horario primera = horario(1L, 18, 0, 20, 0);
            Horario segunda = horario(2L, 20, 0, 22, 0);
            horariosDelDia(primera, segunda);
            conMotivo((short) 1, "FALLA_CAMARA");
            conAdmin();
            guardaElBloque();

            // El bloque tenia las dos clases imputadas por una salida anterior de las 22.
            Asistencia aDeLaSegunda = Asistencia.builder()
                .id(2L).horario(segunda).horaRegistrada(LocalTime.of(20, 0)).build();
            when(asistenciaRepository.findByBloqueIdOrderByHoraRegistradaAsc(BLOQUE_ID))
                .thenReturn(List.of(
                    Asistencia.builder().id(1L).horario(primera)
                        .horaRegistrada(LocalTime.of(18, 0)).build(),
                    aDeLaSegunda));

            var r = service.cerrarManualmente(BLOQUE_ID, LocalTime.of(19, 0), (short) 1, null, 99L);

            assertThat(r.imputadas()).isEqualTo(1);
            assertThat(r.fueraDeRango()).extracting(Asistencia::getId).containsExactly(2L);
            verify(asistenciaRepository, never()).delete(any());
        }
    }

    // ========================================================================
    //  Fixtures
    // ========================================================================

    private Docente docenteDelTenant() {
        Docente d = Docente.builder()
            .id(DOCENTE_ID)
            .persona(DatosDePrueba.personaConDni("12345678", "Juana", "Pérez"))
            .activo(true).build();
        d.setInstitucionId(TENANT_A);
        when(docenteRepository.findById(DOCENTE_ID)).thenReturn(Optional.of(d));
        return d;
    }

    private void consentimientoActivo() {
        when(consentimientoService.estadoActual(DOCENTE_ID))
            .thenReturn(EstadoConsentimiento.ACTIVO);
    }

    private void sinBloqueAbierto() {
        when(bloqueRepository.findByDocenteIdAndEstadoCierre(DOCENTE_ID, EstadoCierre.ABIERTO))
            .thenReturn(Optional.empty());
    }

    // Deja al docente con un bloque ya abierto a esa hora y fecha.
    private BloquePresencia conBloqueAbierto(LocalTime horaEntrada, LocalDate fecha) {
        Docente d = Docente.builder().id(DOCENTE_ID).activo(true).build();
        d.setInstitucionId(TENANT_A);
        BloquePresencia b = BloquePresencia.builder()
            .id(BLOQUE_ID)
            .docente(d)
            .fecha(fecha)
            .horaEntrada(horaEntrada)
            .origenEntrada(OrigenMarca.AUTOMATICO)
            .estadoCierre(EstadoCierre.ABIERTO)
            .build();
        b.setInstitucionId(TENANT_A);
        when(bloqueRepository.findByDocenteIdAndEstadoCierre(DOCENTE_ID, EstadoCierre.ABIERTO))
            .thenReturn(Optional.of(b));
        return b;
    }

    // Un bloque cualquiera buscado por id, para el cierre manual.
    private BloquePresencia bloquePorId(LocalTime horaEntrada, LocalDate fecha, EstadoCierre estado) {
        Docente d = Docente.builder().id(DOCENTE_ID).activo(true).build();
        d.setInstitucionId(TENANT_A);
        BloquePresencia b = BloquePresencia.builder()
            .id(BLOQUE_ID).docente(d).fecha(fecha).horaEntrada(horaEntrada)
            .origenEntrada(OrigenMarca.AUTOMATICO).estadoCierre(estado)
            .build();
        b.setInstitucionId(TENANT_A);
        when(bloqueRepository.findById(BLOQUE_ID)).thenReturn(Optional.of(b));
        return b;
    }

    private void conMotivo(short id, String codigo) {
        when(motivoCargaManualRepository.findById(id)).thenReturn(Optional.of(
            MotivoCargaManual.builder().id(id).codigo(codigo).descripcion(codigo).activo(true).build()));
    }

    private void conAdmin() {
        when(usuarioRepository.findById(99L))
            .thenReturn(Optional.of(Usuario.builder().id(99L).username("admin").build()));
    }

    private void asistenciasDelBloque(Asistencia... asistencias) {
        when(asistenciaRepository.findByBloqueIdOrderByHoraRegistradaAsc(BLOQUE_ID))
            .thenReturn(List.of(asistencias));
    }

    // Deja un bloque abierto devuelto por la query del job, en vez de por docente.
    private BloquePresencia bloqueAbiertoEnLista(LocalTime horaEntrada, LocalDate fecha) {
        Docente d = Docente.builder().id(DOCENTE_ID).activo(true).build();
        d.setInstitucionId(TENANT_A);
        BloquePresencia b = BloquePresencia.builder()
            .id(BLOQUE_ID)
            .docente(d)
            .fecha(fecha)
            .horaEntrada(horaEntrada)
            .origenEntrada(OrigenMarca.AUTOMATICO)
            .estadoCierre(EstadoCierre.ABIERTO)
            .build();
        b.setInstitucionId(TENANT_A);
        when(bloqueRepository.findAbiertosHasta(eq(TENANT_A), any())).thenReturn(List.of(b));
        return b;
    }

    // La franja que el resolutor devuelve para el día del bloque.
    private void franjaDelDia(LocalTime inicio, LocalTime fin, Horario... horarios) {
        when(resolutor.bloquesDelDia(eq(DOCENTE_ID), any()))
            .thenReturn(List.of(new BloqueDeHorarios(List.of(horarios), inicio, fin)));
    }

    private void bloqueEnCursoCon(LocalTime inicio, LocalTime fin, Horario... horarios) {
        when(resolutor.bloqueEnCurso(eq(DOCENTE_ID), any()))
            .thenReturn(Optional.of(new BloqueDeHorarios(List.of(horarios), inicio, fin)));
    }

    private void horariosDelDia(Horario... horarios) {
        when(horarioRepository.findHoyParaDocente(eq(DOCENTE_ID), any(), any(), eq(TENANT_A)))
            .thenReturn(List.of(horarios));
    }

    private void guardaElBloque() {
        when(bloqueRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // Horario de lunes con su propia comisión y la tolerancia por defecto de 15.
    private Horario horario(Long id, int inicioHora, int inicioMin, int finHora, int finMin) {
        Materia materia = Materia.builder().id(id).codigo("MAT" + id).nombre("Materia " + id).build();
        materia.setInstitucionId(TENANT_A);
        Comision comision = Comision.builder()
            .id(id).codigo("C" + id).materia(materia).activo(true).build();
        return Horario.builder()
            .id(id).comision(comision)
            .diaSemana((byte) 1)
            .horaInicio(LocalTime.of(inicioHora, inicioMin))
            .horaFin(LocalTime.of(finHora, finMin))
            .toleranciaMin((short) 15)
            .activo(true)
            .build();
    }
}
