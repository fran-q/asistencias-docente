package edu.cent35.asistencias.service;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.DiaSemana;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre el ABM de franjas horarias: hora de fin posterior a la de inicio, tolerancia dentro de
 * rango y, sobre todo, que no se solapen dos franjas de la misma comisión el mismo día.
 */
@ExtendWith(MockitoExtension.class)
class HorarioServiceTest {

    private static final Long TENANT_A = 1L;
    private static final Long COMISION_ID = 50L;

    @Mock private HorarioRepository horarioRepository;
    @Mock private ComisionRepository comisionRepository;
    @InjectMocks private HorarioService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_A);

        Comision com = comisionActivaA();
        lenient().when(comisionRepository.findById(COMISION_ID)).thenReturn(Optional.of(com));
        lenient().when(horarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(horarioRepository.findSolapamientos(
            eq(COMISION_ID), any(Byte.class), any(LocalTime.class), any(LocalTime.class), isNull()
        )).thenReturn(List.of());
    }

    @AfterEach void clear() { TenantContext.clear(); }

    @Test
    @DisplayName("crear: ok con datos validos")
    void crear_ok() {
        Horario h = service.crear(COMISION_ID, DiaSemana.LUNES,
            LocalTime.of(8, 0), LocalTime.of(10, 0),
            (short) 15);

        assertThat(h.getHoraInicio()).isEqualTo(LocalTime.of(8, 0));
        assertThat(h.getHoraFin()).isEqualTo(LocalTime.of(10, 0));
        assertThat(h.getDia()).isEqualTo(DiaSemana.LUNES);
        assertThat(h.getActivo()).isTrue();
    }

    @Test
    @DisplayName("crear: rechaza horaFin <= horaInicio")
    void crear_horaFinAntesQueInicio() {
        assertThatThrownBy(() -> service.crear(COMISION_ID, DiaSemana.LUNES,
                LocalTime.of(10, 0), LocalTime.of(9, 0),
                (short) 15))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("posterior a la hora de inicio");
        verify(horarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: rechaza horaFin == horaInicio")
    void crear_horasIguales() {
        assertThatThrownBy(() -> service.crear(COMISION_ID, DiaSemana.LUNES,
                LocalTime.of(8, 0), LocalTime.of(8, 0),
                (short) 15))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("posterior");
    }

    @Test
    @DisplayName("crear: rechaza tolerancia > 120")
    void crear_toleranciaAlta() {
        assertThatThrownBy(() -> service.crear(COMISION_ID, DiaSemana.LUNES,
                LocalTime.of(8, 0), LocalTime.of(10, 0),
                (short) 130))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("0 y 120");
    }

    @Test
    @DisplayName("crear: rechaza tolerancia negativa")
    void crear_toleranciaNegativa() {
        assertThatThrownBy(() -> service.crear(COMISION_ID, DiaSemana.LUNES,
                LocalTime.of(8, 0), LocalTime.of(10, 0),
                (short) -5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("0 y 120");
    }

    @Test
    @DisplayName("crear: rechaza si hay solapamiento con otro horario en la misma comision/dia")
    void crear_conSolapamiento() {
        Horario existente = Horario.builder()
            .id(900L)
            .comision(comisionActivaA())
            .horaInicio(LocalTime.of(8, 0))
            .horaFin(LocalTime.of(10, 0))
            .activo(true)
            .build();
        existente.setDia(DiaSemana.LUNES);

        when(horarioRepository.findSolapamientos(
            eq(COMISION_ID), eq(DiaSemana.LUNES.getNumero()),
            eq(LocalTime.of(9, 0)), eq(LocalTime.of(11, 0)), isNull()
        )).thenReturn(List.of(existente));

        assertThatThrownBy(() -> service.crear(COMISION_ID, DiaSemana.LUNES,
                LocalTime.of(9, 0), LocalTime.of(11, 0),
                (short) 15))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Se superpone")
            .hasMessageContaining("Lunes");
    }

    @Test
    @DisplayName("crear: ok cuando NO hay solapamiento (mismo dia, distinta franja)")
    void crear_sinSolapamiento_distintoHorario() {
        Horario h = service.crear(COMISION_ID, DiaSemana.LUNES,
            LocalTime.of(11, 0), LocalTime.of(13, 0),
            (short) 15);
        assertThat(h).isNotNull();
    }

    @Test
    @DisplayName("crear: ok cuando NO hay solapamiento (distinto dia)")
    void crear_sinSolapamiento_distintoDia() {
        Horario h = service.crear(COMISION_ID, DiaSemana.MARTES,
            LocalTime.of(8, 0), LocalTime.of(10, 0),
            (short) 15);
        assertThat(h.getDia()).isEqualTo(DiaSemana.MARTES);
    }

    @Test
    @DisplayName("crear: tolerancia null se defaultea a 15 al persistir")
    void crear_toleranciaNullDefault() {
        Horario h = service.crear(COMISION_ID, DiaSemana.LUNES,
            LocalTime.of(8, 0), LocalTime.of(10, 0),
            null);
        assertThat(h.getToleranciaMin()).isEqualTo((short) 15);
    }

    // ====== Helpers ======
    private Comision comisionActivaA() {
        Carrera car = Carrera.builder().id(1L).codigo("ECO").nombre("Eco").activo(true).build();
        car.setInstitucionId(TENANT_A);
        Materia mat = Materia.builder().id(2L).codigo("MAT").nombre("Mat").carrera(car).activo(true).build();
        mat.setInstitucionId(TENANT_A);
        return Comision.builder().id(COMISION_ID).codigo("A").materia(mat).activo(true).build();
    }
}
