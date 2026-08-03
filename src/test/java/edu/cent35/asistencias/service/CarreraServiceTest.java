package edu.cent35.asistencias.service;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.repository.CarreraRepository;
import edu.cent35.asistencias.repository.MateriaRepository;
import edu.cent35.asistencias.config.TenantContext;
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

/**
 * Cubre el ABM de carreras: código único dentro de la institución, aislamiento entre tenants y
 * el bloqueo de la baja mientras queden materias activas colgando.
 */
@ExtendWith(MockitoExtension.class)
class CarreraServiceTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;

    @Mock private CarreraRepository carreraRepository;
    @Mock private MateriaRepository materiaRepository;
    @InjectMocks private CarreraService service;

    @BeforeEach void setUp() { TenantContext.set(TENANT_A); }
    @AfterEach  void clear() { TenantContext.clear(); }

    @Test
    @DisplayName("crear: persiste con datos validos y setea tenant")
    void crear_ok() {
        when(carreraRepository.existsByCodigo("ECO")).thenReturn(false);
        when(carreraRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Carrera c = service.crear("ECO", "Economia", (short) 3);

        assertThat(c.getCodigo()).isEqualTo("ECO");
        assertThat(c.getNombre()).isEqualTo("Economia");
        assertThat(c.getInstitucionId()).isEqualTo(TENANT_A);
        assertThat(c.getActivo()).isTrue();
    }

    @Test
    @DisplayName("crear: rechaza codigo duplicado en la institucion")
    void crear_codigoDuplicado() {
        when(carreraRepository.existsByCodigo("ECO")).thenReturn(true);

        assertThatThrownBy(() -> service.crear("ECO", "X", (short) 3))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Ya existe");
        verify(carreraRepository, never()).save(any());
    }

    @Test
    @DisplayName("buscarPorId: lanza EntityNotFound si la carrera es de otro tenant")
    void buscarPorId_crossTenant() {
        Carrera ajena = new Carrera();
        ajena.setInstitucionId(TENANT_B);
        when(carreraRepository.findById(99L)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> service.buscarPorId(99L))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Carrera no encontrada");
    }

    @Test
    @DisplayName("darDeBaja: bloquea si hay materias activas")
    void darDeBaja_bloqueaSiTieneMaterias() {
        Carrera c = activa();
        when(carreraRepository.findById(1L)).thenReturn(Optional.of(c));
        when(materiaRepository.countByCarreraIdAndActivoTrue(1L)).thenReturn(3L);

        assertThatThrownBy(() -> service.darDeBaja(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("3");
    }

    @Test
    @DisplayName("darDeBaja: marca inactiva si no tiene materias activas")
    void darDeBaja_ok() {
        Carrera c = activa();
        when(carreraRepository.findById(1L)).thenReturn(Optional.of(c));
        when(materiaRepository.countByCarreraIdAndActivoTrue(1L)).thenReturn(0L);

        service.darDeBaja(1L);

        assertThat(c.getActivo()).isFalse();
        verify(carreraRepository).save(c);
    }

    @Test
    @DisplayName("darDeAlta: reactiva una carrera inactiva")
    void darDeAlta_ok() {
        Carrera c = activa();
        c.setActivo(false);
        when(carreraRepository.findById(1L)).thenReturn(Optional.of(c));

        service.darDeAlta(1L);

        assertThat(c.getActivo()).isTrue();
    }

    // ========================================================================
    //  Acortar la duracion con materias ya cargadas
    // ========================================================================

    @Test
    @DisplayName("actualizar: no deja acortar la carrera por debajo del año de sus materias")
    void actualizar_acortarDejariaMateriasHuerfanas() {
        Carrera c = activa();
        when(carreraRepository.findById(1L)).thenReturn(Optional.of(c));
        when(materiaRepository.maxAnioDeLaCarrera(1L)).thenReturn((short) 3);

        assertThatThrownBy(() -> service.actualizar(1L, "ECO", "Economia", (short) 2))
            .as("bajar a 2 dejaria las materias de tercero en un año que ya no existe, "
                + "y nada volveria a avisar de eso")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("materias de 3° año");
    }

    @Test
    @DisplayName("actualizar: acortar sí se permite si ninguna materia queda afuera")
    void actualizar_acortarSinMateriasAfectadas() {
        Carrera c = activa();
        when(carreraRepository.findById(1L)).thenReturn(Optional.of(c));
        when(materiaRepository.maxAnioDeLaCarrera(1L)).thenReturn((short) 2);
        when(carreraRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.actualizar(1L, "ECO", "Economia", (short) 2);

        assertThat(c.getDuracionAnios()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("actualizar: una carrera sin materias se puede acortar libremente")
    void actualizar_carreraSinMaterias() {
        Carrera c = activa();
        when(carreraRepository.findById(1L)).thenReturn(Optional.of(c));
        when(materiaRepository.maxAnioDeLaCarrera(1L)).thenReturn(null);
        when(carreraRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.actualizar(1L, "ECO", "Economia", (short) 1);

        assertThat(c.getDuracionAnios())
            .as("sin materias no hay nada que quede fuera del plan")
            .isEqualTo((short) 1);
    }

    // Carrera activa del tenant A.
    private Carrera activa() {
        Carrera c = Carrera.builder().id(1L).codigo("ECO").nombre("Economia")
            .duracionAnios((short) 3).activo(true).build();
        c.setInstitucionId(TENANT_A);
        return c;
    }
}
