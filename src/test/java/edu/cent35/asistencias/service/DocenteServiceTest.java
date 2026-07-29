package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.MateriaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre el ABM de docentes: DNI y legajo únicos dentro de la institución, aislamiento entre
 * tenants, y sobre todo el manejo de las dos fechas, que son las que dejan constancia de
 * desde y hasta cuándo esa persona estuvo en funciones.
 */
@ExtendWith(MockitoExtension.class)
class DocenteServiceTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;

    @Mock private DocenteRepository docenteRepository;
    @Mock private MateriaRepository materiaRepository;
    @Mock private ComisionRepository comisionRepository;
    @InjectMocks private DocenteService service;

    @BeforeEach void setUp() { TenantContext.set(TENANT_A); }
    @AfterEach  void clear() { TenantContext.clear(); }

    // ========================================================================
    //  Alta
    // ========================================================================

    @Test
    @DisplayName("crear: la fecha de alta la pone el sistema, no la elige quien carga")
    void crear_fechaAltaEsHoy() {
        when(docenteRepository.existsByDni("30111222")).thenReturn(false);
        when(docenteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Docente d = service.crear("30111222", null, "Ana", "Perez", null, null);

        assertThat(d.getFechaAlta())
            .as("el alta es el momento en que se carga: pedirla solo habilita el error de tipeo")
            .isEqualTo(LocalDate.now());
        assertThat(d.getFechaBaja()).isNull();
        assertThat(d.getActivo()).isTrue();
        assertThat(d.getInstitucionId()).isEqualTo(TENANT_A);
    }

    @Test
    @DisplayName("crear: rechaza un DNI ya cargado en la institucion")
    void crear_dniDuplicado() {
        when(docenteRepository.existsByDni("30111222")).thenReturn(true);

        assertThatThrownBy(() -> service.crear("30111222", null, "Ana", "Perez", null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DNI");
        verify(docenteRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: el legajo vacio no cuenta como repetido")
    void crear_legajoVacioNoChoca() {
        when(docenteRepository.existsByDni("30111222")).thenReturn(false);
        when(docenteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Docente d = service.crear("30111222", "   ", "Ana", "Perez", null, null);

        // Si el vacio se guardara como cadena, dos docentes sin legajo chocarian entre si.
        assertThat(d.getLegajo()).isNull();
        verify(docenteRepository, never()).existsByLegajo(any());
    }

    // ========================================================================
    //  Edicion
    // ========================================================================

    @Test
    @DisplayName("actualizar: no toca la fecha de alta")
    void actualizar_noPisaLaFechaDeAlta() {
        LocalDate ingreso = LocalDate.of(2024, 3, 1);
        Docente existente = docenteDe(TENANT_A, "30111222", ingreso);
        when(docenteRepository.findById(7L)).thenReturn(Optional.of(existente));
        when(docenteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Docente d = service.actualizar(7L, "30111222", null, "Ana Maria", "Perez", null, null);

        assertThat(d.getFechaAlta())
            .as("la fecha de alta es el registro de cuando ingreso, no un campo mas del legajo")
            .isEqualTo(ingreso);
        assertThat(d.getNombre()).isEqualTo("Ana Maria");
    }

    @Test
    @DisplayName("buscarPorId: un docente de otra institucion no existe para este tenant")
    void buscarPorId_crossTenant() {
        Docente ajeno = docenteDe(TENANT_B, "30111222", LocalDate.now());
        when(docenteRepository.findById(99L)).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.buscarPorId(99L))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ========================================================================
    //  Baja
    // ========================================================================

    @Test
    @DisplayName("darDeBaja: guarda la fecha elegida, no la de hoy")
    void darDeBaja_guardaLaFechaElegida() {
        Docente d = prepararParaBaja(LocalDate.of(2024, 3, 1));
        LocalDate ultimoDia = LocalDate.now().minusDays(5);

        service.darDeBaja(7L, ultimoDia);

        // La baja se carga dias despues del hecho: forzar "hoy" falsearia el registro.
        assertThat(d.getFechaBaja()).isEqualTo(ultimoDia);
        assertThat(d.getActivo()).isFalse();
    }

    @Test
    @DisplayName("darDeBaja: rechaza una fecha futura")
    void darDeBaja_rechazaFutura() {
        prepararParaBaja(LocalDate.of(2024, 3, 1));

        assertThatThrownBy(() -> service.darDeBaja(7L, LocalDate.now().plusDays(1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("futura");
        verify(docenteRepository, never()).save(any());
    }

    @Test
    @DisplayName("darDeBaja: rechaza una fecha anterior al ingreso del docente")
    void darDeBaja_rechazaAnteriorAlAlta() {
        prepararParaBaja(LocalDate.of(2024, 3, 1));

        assertThatThrownBy(() -> service.darDeBaja(7L, LocalDate.of(2024, 2, 28)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("anterior");
        verify(docenteRepository, never()).save(any());
    }

    @Test
    @DisplayName("darDeBaja: se bloquea si el docente todavia tiene materias o comisiones")
    void darDeBaja_bloqueadaPorAsignaciones() {
        Docente d = docenteDe(TENANT_A, "30111222", LocalDate.of(2024, 3, 1));
        when(docenteRepository.findById(7L)).thenReturn(Optional.of(d));
        when(materiaRepository.countByDocenteTitularIdAndActivoTrue(7L)).thenReturn(2L);
        when(comisionRepository.countByDocenteAsignadoIdAndActivoTrue(7L)).thenReturn(1L);

        assertThatThrownBy(() -> service.darDeBaja(7L, LocalDate.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Reasignalas");

        // Ni la baja ni la fecha quedan escritas si la operacion no procede.
        assertThat(d.getActivo()).isTrue();
        assertThat(d.getFechaBaja()).isNull();
        verify(docenteRepository, never()).save(any());
    }

    @Test
    @DisplayName("darDeAlta: reactivar borra la fecha de baja")
    void darDeAlta_limpiaLaFechaDeBaja() {
        Docente d = docenteDe(TENANT_A, "30111222", LocalDate.of(2024, 3, 1));
        d.setActivo(false);
        d.setFechaBaja(LocalDate.of(2025, 6, 30));
        when(docenteRepository.findById(7L)).thenReturn(Optional.of(d));

        service.darDeAlta(7L);

        assertThat(d.getActivo()).isTrue();
        assertThat(d.getFechaBaja())
            .as("un docente activo que ademas figura desvinculado es una contradiccion")
            .isNull();
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    private Docente docenteDe(Long tenant, String dni, LocalDate fechaAlta) {
        Docente d = Docente.builder()
            .id(7L).dni(dni).nombre("Ana").apellido("Perez")
            .fechaAlta(fechaAlta).activo(true)
            .build();
        d.setInstitucionId(tenant);
        return d;
    }

    // Docente activo, sin materias ni comisiones colgando, listo para que la baja proceda.
    private Docente prepararParaBaja(LocalDate fechaAlta) {
        Docente d = docenteDe(TENANT_A, "30111222", fechaAlta);
        when(docenteRepository.findById(7L)).thenReturn(Optional.of(d));
        return d;
    }
}
