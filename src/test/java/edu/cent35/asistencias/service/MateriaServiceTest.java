package edu.cent35.asistencias.service;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.repository.CarreraRepository;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.MateriaRepository;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre el ABM de materias: código único, carrera del mismo tenant y el bloqueo de la baja
 * mientras queden comisiones activas.
 */
@ExtendWith(MockitoExtension.class)
class MateriaServiceTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final Long CARRERA_ID = 10L;

    @Mock private MateriaRepository materiaRepository;
    @Mock private CarreraRepository carreraRepository;
    @Mock private ComisionRepository comisionRepository;
    @Mock private DocenteRepository docenteRepository;
    @InjectMocks private MateriaService service;

    @BeforeEach void setUp() { TenantContext.set(TENANT_A); }
    @AfterEach  void clear() { TenantContext.clear(); }

    @Test
    @DisplayName("crear: rechaza carrera de otro tenant (camuflada como 'no existe')")
    void crear_carreraAjena_camuflada() {
        Carrera ajena = Carrera.builder().id(CARRERA_ID).codigo("X").nombre("X").activo(true).build();
        ajena.setInstitucionId(TENANT_B);
        when(carreraRepository.findById(CARRERA_ID)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> service.crear("MAT", "Matematica", CARRERA_ID, (short) 1, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("La carrera seleccionada no existe");
        verify(materiaRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: rechaza si la carrera del tenant esta inactiva")
    void crear_carreraInactiva() {
        Carrera c = Carrera.builder().id(CARRERA_ID).codigo("ECO").nombre("Eco").activo(false).build();
        c.setInstitucionId(TENANT_A);
        when(carreraRepository.findById(CARRERA_ID)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.crear("MAT", "X", CARRERA_ID, (short) 1, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("inactiva");
    }

    @Test
    @DisplayName("crear: ok con carrera del tenant + activa")
    void crear_ok() {
        Carrera c = carreraActivaA();
        when(carreraRepository.findById(CARRERA_ID)).thenReturn(Optional.of(c));
        when(materiaRepository.existsByCodigo("MAT")).thenReturn(false);
        when(materiaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Materia m = service.crear("MAT", "Matematica", CARRERA_ID, (short) 1, null);

        assertThat(m.getCarrera()).isSameAs(c);
        assertThat(m.getInstitucionId()).isEqualTo(TENANT_A);
        assertThat(m.getActivo()).isTrue();
    }

    @Test
    @DisplayName("crear: rechaza codigo duplicado")
    void crear_codigoDuplicado() {
        Carrera c = carreraActivaA();
        when(carreraRepository.findById(CARRERA_ID)).thenReturn(Optional.of(c));
        when(materiaRepository.existsByCodigo("MAT")).thenReturn(true);

        assertThatThrownBy(() -> service.crear("MAT", "X", CARRERA_ID, (short) 1, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Ya existe");
    }

    @Test
    @DisplayName("darDeBaja: bloquea si tiene comisiones activas")
    void darDeBaja_bloqueaSiTieneComisiones() {
        Materia m = materiaActivaA();
        when(materiaRepository.findById(20L)).thenReturn(Optional.of(m));
        when(comisionRepository.contarActivasEnCiclosAbiertos(20L, TENANT_A)).thenReturn(2L);

        assertThatThrownBy(() -> service.darDeBaja(20L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("2");
    }

    @Test
    @DisplayName("darDeAlta: bloquea si la carrera padre esta inactiva")
    void darDeAlta_carreraInactiva() {
        Materia m = materiaActivaA();
        m.setActivo(false);
        m.getCarrera().setActivo(false);
        when(materiaRepository.findById(20L)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.darDeAlta(20L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("carrera");
    }

    // Carrera activa del tenant A.
    // ========================================================================
    //  El anio contra la duracion de la carrera
    // ========================================================================

    @Test
    @DisplayName("crear: rechaza una materia de un año que la carrera no tiene")
    void crear_anioFueraDeLaCarrera() {
        Carrera c = carreraActivaA();          // dura 3 anios
        when(carreraRepository.findById(CARRERA_ID)).thenReturn(Optional.of(c));
        when(materiaRepository.existsByCodigo("MAT")).thenReturn(false);

        assertThatThrownBy(() -> service.crear("MAT", "Matemática", CARRERA_ID, (short) 5, null))
            .as("una tecnicatura de tres anios no puede tener materias de quinto; sin esto "
                + "el anio seria un entero suelto que no se compara contra nada")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dura 3 años");
    }

    @Test
    @DisplayName("crear: el último año de la carrera sí se acepta")
    void crear_anioEnElBorde() {
        Carrera c = carreraActivaA();
        when(carreraRepository.findById(CARRERA_ID)).thenReturn(Optional.of(c));
        when(materiaRepository.existsByCodigo("MAT")).thenReturn(false);
        when(materiaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Materia m = service.crear("MAT", "Matemática", CARRERA_ID, (short) 3, null);

        assertThat(m.getAnio())
            .as("el tope es inclusivo: tercero existe en una carrera de tres años")
            .isEqualTo((short) 3);
    }

    private Carrera carreraActivaA() {
        Carrera c = Carrera.builder().id(CARRERA_ID).codigo("ECO").nombre("Eco")
            .duracionAnios((short) 3).activo(true).build();
        c.setInstitucionId(TENANT_A);
        return c;
    }

    // Materia activa colgando de una carrera del tenant A.
    private Materia materiaActivaA() {
        Materia m = Materia.builder()
            .id(20L).codigo("MAT").nombre("Matematica").carrera(carreraActivaA()).activo(true).build();
        m.setInstitucionId(TENANT_A);
        return m;
    }
}
