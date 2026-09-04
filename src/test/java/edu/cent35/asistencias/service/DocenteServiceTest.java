package edu.cent35.asistencias.service;

import edu.cent35.asistencias.dto.ImpactoIdentidadDto;
import edu.cent35.asistencias.repository.PersonaRepository;
import edu.cent35.asistencias.model.Persona;
import edu.cent35.asistencias.DatosDePrueba;
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
import static org.mockito.ArgumentMatchers.eq;
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
    private static final Long USUARIO_ACTUAL = 42L;
    private static final Long TENANT_B = 2L;

    @Mock private DocenteRepository docenteRepository;
    @Mock private PersonaRepository personaRepository;
    @Mock private PersonaService personaService;
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
        when(personaRepository.buscarPorDni(any(), eq("30111222"))).thenReturn(Optional.empty());
        when(personaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(docenteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Docente d = service.crear("30111222", null, "Ana", "Perez", null, null, USUARIO_ACTUAL);

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
        Persona yaEsta = DatosDePrueba.personaConDni("30111222", "Ana", "Perez");
        yaEsta.setId(5L);
        when(personaRepository.buscarPorDni(any(), eq("30111222"))).thenReturn(Optional.of(yaEsta));
        when(docenteRepository.vinculoVigenteDe(any(), eq(5L)))
            .thenReturn(Optional.of(Docente.builder().id(9L).activo(true).build()));

        assertThatThrownBy(() -> service.crear("30111222", null, "Ana", "Perez", null, null, USUARIO_ACTUAL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DNI", USUARIO_ACTUAL);
        verify(docenteRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: el legajo vacio no cuenta como repetido")
    void crear_legajoVacioNoChoca() {
        when(personaRepository.buscarPorDni(any(), eq("30111222"))).thenReturn(Optional.empty());
        when(personaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(docenteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Docente d = service.crear("30111222", "   ", "Ana", "Perez", null, null, USUARIO_ACTUAL);

        // Si el vacio se guardara como cadena, dos docentes sin legajo chocarian entre si.
        assertThat(d.getLegajo()).isNull();
        verify(docenteRepository, never()).existeLegajoVigente(any(), any());
    }

    // ========================================================================
    //  Edicion
    // ========================================================================

    @Test
    @DisplayName("actualizar: no toca la fecha de alta")
    void actualizar_noPisaLaFechaDeAlta() {
        LocalDate ingreso = LocalDate.of(2024, 3, 1);
        Docente existente = docenteDe(TENANT_A, "30111222", ingreso);
        when(docenteRepository.buscarDelTenant(any(), eq(7L))).thenReturn(Optional.of(existente));
        when(docenteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Docente d = service.actualizar(7L, "30111222", null, "Ana Maria", "Perez", null, null, USUARIO_ACTUAL);

        assertThat(d.getFechaAlta())
            .as("la fecha de alta es el registro de cuando ingreso, no un campo mas del legajo")
            .isEqualTo(ingreso);
        assertThat(d.getPersona().getNombre()).isEqualTo("Ana Maria");
    }

    @Test
    @DisplayName("buscarPorId: un docente de otra institucion no existe para este tenant")
    void buscarPorId_crossTenant() {
        // Desde ADR-0016 el corte cross-tenant se hace en la consulta y no después: buscarPorId
        // dejó de usar findById —que no pasa por el filtro— y va por buscarDelTenant, que lleva
        // el WHERE institucionId adentro. Por eso el docente ajeno no vuelve vacío "por casualidad":
        // la consulta directamente no lo encuentra, y el service traduce eso a "no encontrado".
        when(docenteRepository.buscarDelTenant(TENANT_A, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(99L))
            .as("nunca 'no autorizado': eso revelaria que el id existe en otra institucion")
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

        service.darDeBaja(7L, ultimoDia, USUARIO_ACTUAL);

        // La baja se carga dias despues del hecho: forzar "hoy" falsearia el registro.
        assertThat(d.getFechaBaja()).isEqualTo(ultimoDia);
        assertThat(d.getActivo()).isFalse();
    }

    @Test
    @DisplayName("darDeBaja: rechaza una fecha futura")
    void darDeBaja_rechazaFutura() {
        prepararParaBaja(LocalDate.of(2024, 3, 1));

        assertThatThrownBy(() -> service.darDeBaja(7L, LocalDate.now().plusDays(1), USUARIO_ACTUAL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("futura", USUARIO_ACTUAL);
        verify(docenteRepository, never()).save(any());
    }

    @Test
    @DisplayName("darDeBaja: rechaza una fecha anterior al ingreso del docente")
    void darDeBaja_rechazaAnteriorAlAlta() {
        prepararParaBaja(LocalDate.of(2024, 3, 1));

        assertThatThrownBy(() -> service.darDeBaja(7L, LocalDate.of(2024, 2, 28), USUARIO_ACTUAL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("anterior", USUARIO_ACTUAL);
        verify(docenteRepository, never()).save(any());
    }

    @Test
    @DisplayName("darDeBaja: se bloquea si el docente todavia tiene materias o comisiones")
    void darDeBaja_bloqueadaPorAsignaciones() {
        Docente d = docenteDe(TENANT_A, "30111222", LocalDate.of(2024, 3, 1));
        when(docenteRepository.buscarDelTenant(any(), eq(7L))).thenReturn(Optional.of(d));
        when(materiaRepository.countByDocenteTitularIdAndActivoTrue(7L)).thenReturn(2L);
        when(comisionRepository.countByDocenteAsignadoIdAndActivoTrue(7L)).thenReturn(1L);

        assertThatThrownBy(() -> service.darDeBaja(7L, LocalDate.now(), USUARIO_ACTUAL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Reasignalas", USUARIO_ACTUAL);

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
        when(docenteRepository.buscarDelTenant(any(), eq(7L))).thenReturn(Optional.of(d));

        service.darDeAlta(7L);

        assertThat(d.getActivo()).isTrue();
        assertThat(d.getFechaBaja())
            .as("un docente activo que ademas figura desvinculado es una contradiccion")
            .isNull();
    }

    // ========================================================================
    //  helpers
    // ========================================================================


    // ========================================================================
    //  Confirmacion sobre una identidad que ya existe
    // ========================================================================

    @Test
    @DisplayName("crear: si el DNI ya existe pide confirmacion y NO escribe nada")
    void crear_dniExistente_pideConfirmacionSinTocarNada() {
        Persona otra = DatosDePrueba.personaConDni("30111222", "Juan", "Gomez");
        otra.setId(5L);
        when(personaRepository.buscarPorDni(any(), eq("30111222"))).thenReturn(Optional.of(otra));
        when(docenteRepository.vinculoVigenteDe(any(), eq(5L))).thenReturn(Optional.empty());
        when(personaService.impactoDeAlta(any(), any()))
            .thenReturn(ImpactoIdentidadDto.builder()
                .motivo(ImpactoIdentidadDto.Motivo.ALTA_SOBRE_PERSONA_EXISTENTE)
                .personaId(5L).dni("30111222")
                .nombreRegistrado("Gomez, Juan").nombrePropuesto("Perez, Ana")
                .build());

        assertThatThrownBy(() -> service.crear("30111222", null, "Ana", "Perez", null, null, USUARIO_ACTUAL))
            .isInstanceOf(ConfirmacionRequeridaException.class);

        // Lo que importa: un DNI mal tipeado no le reescribe el nombre a quien ya estaba.
        verify(personaRepository, never()).save(any());
        verify(docenteRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: confirmado, reutiliza la persona y le abre un periodo nuevo")
    void crear_confirmado_reutilizaLaPersona() {
        Persona misma = DatosDePrueba.personaConDni("30111222", "Ana", "Perez");
        misma.setId(5L);
        when(personaRepository.buscarPorDni(any(), eq("30111222"))).thenReturn(Optional.of(misma));
        when(docenteRepository.vinculoVigenteDe(any(), eq(5L))).thenReturn(Optional.empty());
        when(personaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(docenteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Docente d = service.crear("30111222", null, "Ana Maria", "Perez", null, null, true, USUARIO_ACTUAL);

        assertThat(d.getPersona().getId())
            .as("no se crea otra persona: se reutiliza la que ya estaba")
            .isEqualTo(5L);
        assertThat(d.getPersona().getNombre()).isEqualTo("Ana Maria");
        assertThat(d.getFechaAlta()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("actualizar: si la persona tiene varios roles pide confirmacion y no escribe")
    void actualizar_variosRoles_pideConfirmacion() {
        Docente existente = docenteDe(TENANT_A, "30111222", LocalDate.of(2024, 3, 1));
        existente.getPersona().setId(5L);
        when(docenteRepository.buscarDelTenant(any(), eq(7L))).thenReturn(Optional.of(existente));
        when(personaRepository.existeDniEnOtra(any(), any(), any())).thenReturn(false);
        when(personaService.edicionRequiereConfirmacion(any())).thenReturn(true);
        when(personaService.impactoDeEdicion(any(), any()))
            .thenReturn(ImpactoIdentidadDto.builder()
                .motivo(ImpactoIdentidadDto.Motivo.EDICION_ALCANZA_VARIOS_ROLES)
                .personaId(5L).tieneCuenta(true).periodosDocentes(1)
                .build());

        assertThatThrownBy(() -> service.actualizar(7L, "30111222", null, "Ana Maria", "Perez", null, null, USUARIO_ACTUAL))
            .isInstanceOf(ConfirmacionRequeridaException.class);

        verify(personaRepository, never()).save(any());
        verify(docenteRepository, never()).save(any());
    }

    private Docente docenteDe(Long tenant, String dni, LocalDate fechaAlta) {
        Docente d = Docente.builder().persona(DatosDePrueba.personaConDni(dni, "Ana", "Perez")).id(7L).fechaAlta(fechaAlta).activo(true).build();
        d.setInstitucionId(tenant);
        return d;
    }

    // Docente activo, sin materias ni comisiones colgando, listo para que la baja proceda.
    private Docente prepararParaBaja(LocalDate fechaAlta) {
        Docente d = docenteDe(TENANT_A, "30111222", fechaAlta);
        when(docenteRepository.buscarDelTenant(any(), eq(7L))).thenReturn(Optional.of(d));
        return d;
    }

    // ====================================================================
    //  Redaccion del motivo que impide la baja
    // ====================================================================

    @Test
    @DisplayName("motivoQueImpideLaBaja: redacta bien los tres casos")
    void motivoRedactadoCorrectamente() {
        // Solo comisiones. Antes decia "es asignado a", porque el verbo estaba en un
        // prefijo comun que solo servia para el caso de las materias.
        when(materiaRepository.countByDocenteTitularIdAndActivoTrue(1L)).thenReturn(0L);
        when(comisionRepository.countByDocenteAsignadoIdAndActivoTrue(1L)).thenReturn(2L);
        assertThat(service.motivoQueImpideLaBaja(1L))
            .isEqualTo("No se puede dar de baja: está asignado a 2 comisiones. Reasignalas primero.");

        // Solo materias, en singular.
        when(materiaRepository.countByDocenteTitularIdAndActivoTrue(2L)).thenReturn(1L);
        when(comisionRepository.countByDocenteAsignadoIdAndActivoTrue(2L)).thenReturn(0L);
        assertThat(service.motivoQueImpideLaBaja(2L))
            .isEqualTo("No se puede dar de baja: es titular de 1 materia. Reasignalas primero.");

        // Las dos cosas.
        when(materiaRepository.countByDocenteTitularIdAndActivoTrue(3L)).thenReturn(3L);
        when(comisionRepository.countByDocenteAsignadoIdAndActivoTrue(3L)).thenReturn(1L);
        assertThat(service.motivoQueImpideLaBaja(3L))
            .isEqualTo("No se puede dar de baja: es titular de 3 materias y está asignado a "
                     + "1 comisión. Reasignalas primero.");
    }

    @Test
    @DisplayName("motivoQueImpideLaBaja: null cuando no hay nada que lo impida")
    void motivoNullSiSePuede() {
        when(materiaRepository.countByDocenteTitularIdAndActivoTrue(9L)).thenReturn(0L);
        when(comisionRepository.countByDocenteAsignadoIdAndActivoTrue(9L)).thenReturn(0L);
        assertThat(service.motivoQueImpideLaBaja(9L)).isNull();
    }
}
