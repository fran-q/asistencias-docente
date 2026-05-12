package edu.cent35.asistencias.academico.application;

import edu.cent35.asistencias.academico.domain.Carrera;
import edu.cent35.asistencias.academico.domain.Materia;
import edu.cent35.asistencias.academico.infrastructure.CarreraRepository;
import edu.cent35.asistencias.academico.infrastructure.ComisionRepository;
import edu.cent35.asistencias.academico.infrastructure.MateriaRepository;
import edu.cent35.asistencias.shared.multitenant.TenantContext;
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

@ExtendWith(MockitoExtension.class)
class MateriaServiceTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final Long CARRERA_ID = 10L;

    @Mock private MateriaRepository materiaRepository;
    @Mock private CarreraRepository carreraRepository;
    @Mock private ComisionRepository comisionRepository;
    @InjectMocks private MateriaService service;

    @BeforeEach void setUp() { TenantContext.set(TENANT_A); }
    @AfterEach  void clear() { TenantContext.clear(); }

    @Test
    @DisplayName("crear: rechaza carrera de otro tenant (camuflada como 'no existe')")
    void crear_carreraAjena_camuflada() {
        Carrera ajena = Carrera.builder().id(CARRERA_ID).codigo("X").nombre("X").activo(true).build();
        ajena.setInstitucionId(TENANT_B);
        when(carreraRepository.findById(CARRERA_ID)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> service.crear("MAT", "Matematica", CARRERA_ID))
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

        assertThatThrownBy(() -> service.crear("MAT", "X", CARRERA_ID))
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

        Materia m = service.crear("MAT", "Matematica", CARRERA_ID);

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

        assertThatThrownBy(() -> service.crear("MAT", "X", CARRERA_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Ya existe");
    }

    @Test
    @DisplayName("darDeBaja: bloquea si tiene comisiones activas")
    void darDeBaja_bloqueaSiTieneComisiones() {
        Materia m = materiaActivaA();
        when(materiaRepository.findById(20L)).thenReturn(Optional.of(m));
        when(comisionRepository.countByMateriaIdAndActivoTrue(20L)).thenReturn(2L);

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

    private Carrera carreraActivaA() {
        Carrera c = Carrera.builder().id(CARRERA_ID).codigo("ECO").nombre("Eco").activo(true).build();
        c.setInstitucionId(TENANT_A);
        return c;
    }

    private Materia materiaActivaA() {
        Materia m = Materia.builder()
            .id(20L).codigo("MAT").nombre("Matematica").carrera(carreraActivaA()).activo(true).build();
        m.setInstitucionId(TENANT_A);
        return m;
    }
}
