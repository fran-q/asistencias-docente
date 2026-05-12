package edu.cent35.asistencias.academico.application;

import edu.cent35.asistencias.academico.domain.Carrera;
import edu.cent35.asistencias.academico.domain.Comision;
import edu.cent35.asistencias.academico.domain.Materia;
import edu.cent35.asistencias.academico.infrastructure.ComisionRepository;
import edu.cent35.asistencias.academico.infrastructure.HorarioRepository;
import edu.cent35.asistencias.academico.infrastructure.MateriaRepository;
import edu.cent35.asistencias.shared.multitenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
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
class ComisionServiceTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final Long MATERIA_ID = 30L;

    @Mock private ComisionRepository comisionRepository;
    @Mock private MateriaRepository materiaRepository;
    @Mock private HorarioRepository horarioRepository;
    @InjectMocks private ComisionService service;

    @BeforeEach void setUp() { TenantContext.set(TENANT_A); }
    @AfterEach  void clear() { TenantContext.clear(); }

    @Test
    @DisplayName("crear: rechaza materia de otro tenant (camuflada)")
    void crear_materiaAjena_camuflada() {
        Materia ajena = materiaConTenant(TENANT_B);
        when(materiaRepository.findById(MATERIA_ID)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> service.crear("A", MATERIA_ID, 30))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("La materia seleccionada no existe");
        verify(comisionRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: ok con materia del tenant + activa")
    void crear_ok() {
        Materia m = materiaConTenant(TENANT_A);
        when(materiaRepository.findById(MATERIA_ID)).thenReturn(Optional.of(m));
        when(comisionRepository.existsByMateriaIdAndCodigo(MATERIA_ID, "A")).thenReturn(false);
        when(comisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comision c = service.crear("A", MATERIA_ID, 30);

        assertThat(c.getCodigo()).isEqualTo("A");
        assertThat(c.getMateria()).isSameAs(m);
        assertThat(c.getCupo()).isEqualTo(30);
        assertThat(c.getActivo()).isTrue();
    }

    @Test
    @DisplayName("crear: rechaza codigo duplicado dentro de la misma materia")
    void crear_codigoDuplicadoEnMateria() {
        Materia m = materiaConTenant(TENANT_A);
        when(materiaRepository.findById(MATERIA_ID)).thenReturn(Optional.of(m));
        when(comisionRepository.existsByMateriaIdAndCodigo(MATERIA_ID, "A")).thenReturn(true);

        assertThatThrownBy(() -> service.crear("A", MATERIA_ID, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Ya existe");
    }

    @Test
    @DisplayName("crear: rechaza cupo cero o negativo")
    void crear_cupoInvalido() {
        Materia m = materiaConTenant(TENANT_A);
        when(materiaRepository.findById(MATERIA_ID)).thenReturn(Optional.of(m));
        when(comisionRepository.existsByMateriaIdAndCodigo(MATERIA_ID, "A")).thenReturn(false);

        assertThatThrownBy(() -> service.crear("A", MATERIA_ID, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("positivo");
    }

    @Test
    @DisplayName("darDeBaja: bloquea si tiene horarios activos")
    void darDeBaja_bloqueaSiTieneHorarios() {
        Comision c = comisionActivaA();
        when(comisionRepository.findById(40L)).thenReturn(Optional.of(c));
        when(horarioRepository.countByComisionIdAndActivoTrue(40L)).thenReturn(3L);

        assertThatThrownBy(() -> service.darDeBaja(40L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("3");
    }

    @Test
    @DisplayName("buscarPorId: cross-tenant via materia padre tira EntityNotFound")
    void buscarPorId_crossTenant() {
        Materia ajena = materiaConTenant(TENANT_B);
        Comision c = Comision.builder().id(40L).codigo("A").materia(ajena).activo(true).build();
        when(comisionRepository.findById(40L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.buscarPorId(40L))
            .isInstanceOf(EntityNotFoundException.class);
    }

    private Materia materiaConTenant(Long tenant) {
        Carrera car = Carrera.builder().id(1L).codigo("ECO").nombre("Eco").activo(true).build();
        car.setInstitucionId(tenant);
        Materia m = Materia.builder().id(MATERIA_ID).codigo("MAT").nombre("Mat").carrera(car).activo(true).build();
        m.setInstitucionId(tenant);
        return m;
    }

    private Comision comisionActivaA() {
        return Comision.builder()
            .id(40L).codigo("A").materia(materiaConTenant(TENANT_A)).activo(true).build();
    }
}
