package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.repository.CarreraRepository;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.MateriaRepository;
import edu.cent35.asistencias.service.CarreraService;
import edu.cent35.asistencias.service.ComisionService;
import edu.cent35.asistencias.service.MateriaService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica de punta a punta que una institución no puede ver los datos de otra, levantando el
 * contexto completo contra H2 para que el aspecto, el filtro de Hibernate y las queries reales
 * intervengan de verdad. Existe porque el resto de la suite es unitaria con Mockito, que nunca
 * toca Hibernate: por eso el filtro pudo quedar inactivo sin que ningún test lo notara (TD-007).
 */
@SpringBootTest
@ActiveProfiles("test")
class AislamientoMultiTenantIT {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;

    @Autowired private CarreraService carreraService;
    @Autowired private MateriaService materiaService;
    @Autowired private ComisionService comisionService;

    @Autowired private CarreraRepository carreraRepository;
    @Autowired private MateriaRepository materiaRepository;
    @Autowired private ComisionRepository comisionRepository;

    private Long carreraDeB;

    // Siembra datos de dos instituciones. Se escribe por repositorio y no por service: el aspecto
    // solo avanza sobre beans @Service, asi que aca no hay filtro y se puede cargar de ambas.
    @BeforeEach
    void sembrar() {
        TenantContext.clear();
        limpiar();

        Carrera a1 = guardarCarrera(TENANT_A, "A-ECO", "Economia A");
        guardarCarrera(TENANT_A, "A-MAT", "Matematica A");
        Carrera b1 = guardarCarrera(TENANT_B, "B-ING", "Ingenieria B");
        carreraDeB = b1.getId();

        Materia matA = guardarMateria(TENANT_A, a1, "A-FIS", "Fisica A");
        Materia matB = guardarMateria(TENANT_B, b1, "B-QUI", "Quimica B");
        guardarComision(matA, "AA");
        guardarComision(matB, "BB");
    }

    @AfterEach
    void limpiarDespues() {
        TenantContext.clear();
        limpiar();
    }

    // ========================================================================
    //  Capa 1: filtro de Hibernate sobre queries sin WHERE explicito
    // ========================================================================

    @Test
    @DisplayName("El listado de carreras solo devuelve las de la institucion activa")
    void listadoDeCarrerasSeAislaPorTenant() {
        // listar() delega en una query derivada que NO menciona la institucion: si el filtro de
        // Hibernate no se activa, devuelve las tres carreras y la fuga queda a la vista.
        TenantContext.set(TENANT_A);
        List<Carrera> deA = carreraService.listar();

        assertThat(deA)
            .extracting(Carrera::getCodigo)
            .containsExactlyInAnyOrder("A-ECO", "A-MAT")
            .doesNotContain("B-ING");
        assertThat(deA).allSatisfy(c ->
            assertThat(c.getInstitucionId()).isEqualTo(TENANT_A));
    }

    @Test
    @DisplayName("Cambiar de institucion cambia lo que se ve: el filtro no queda pegado")
    void elFiltroSeReevaluaEnCadaLlamada() {
        TenantContext.set(TENANT_A);
        assertThat(carreraService.listar()).hasSize(2);

        // Si el parametro del filtro quedara cacheado de la llamada anterior, esto seguiria
        // devolviendo las carreras de A.
        TenantContext.set(TENANT_B);
        List<Carrera> deB = carreraService.listar();

        assertThat(deB)
            .extracting(Carrera::getCodigo)
            .containsExactly("B-ING");
    }

    @Test
    @DisplayName("El listado de materias tambien se aisla")
    void listadoDeMateriasSeAislaPorTenant() {
        TenantContext.set(TENANT_A);
        assertThat(materiaService.listar())
            .extracting(Materia::getCodigo)
            .containsExactly("A-FIS");

        TenantContext.set(TENANT_B);
        assertThat(materiaService.listar())
            .extracting(Materia::getCodigo)
            .containsExactly("B-QUI");
    }

    // ========================================================================
    //  Capa 2: JOIN con institucionId explicito (comision no es tenant-scoped)
    // ========================================================================

    @Test
    @DisplayName("Las comisiones se aislan por el JOIN con materia, no por el filtro")
    void listadoDeComisionesSeAislaPorElJoin() {
        TenantContext.set(TENANT_A);
        assertThat(comisionService.listar())
            .extracting(Comision::getCodigo)
            .containsExactly("AA");

        TenantContext.set(TENANT_B);
        assertThat(comisionService.listar())
            .extracting(Comision::getCodigo)
            .containsExactly("BB");
    }

    // ========================================================================
    //  Capa 3: validacion explicita en el service para el acceso por id
    // ========================================================================

    @Test
    @DisplayName("Pedir por id una carrera ajena responde 'no encontrada'")
    void buscarPorIdAjenoRespondeNoEncontrada() {
        TenantContext.set(TENANT_A);

        // findById no pasa por el filtro de Hibernate (las cargas por clave primaria lo saltean),
        // por eso el service tiene que comparar el tenant a mano. Sin esa validacion, esto
        // devolveria la carrera de la otra institucion.
        assertThatThrownBy(() -> carreraService.buscarPorId(carreraDeB))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Sin institucion en contexto, el service se niega a operar")
    void sinTenantEnContextoFalla() {
        TenantContext.clear();

        assertThatThrownBy(() -> carreraService.buscarPorId(carreraDeB))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TenantContext");
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    // Borra en orden de dependencia para no chocar con las claves foraneas.
    private void limpiar() {
        comisionRepository.deleteAll();
        materiaRepository.deleteAll();
        carreraRepository.deleteAll();
    }

    private Carrera guardarCarrera(Long tenantId, String codigo, String nombre) {
        Carrera c = Carrera.builder().codigo(codigo).nombre(nombre).activo(true).build();
        c.setInstitucionId(tenantId);
        return carreraRepository.save(c);
    }

    private Materia guardarMateria(Long tenantId, Carrera carrera, String codigo, String nombre) {
        Materia m = Materia.builder()
            .carrera(carrera).codigo(codigo).nombre(nombre).activo(true).build();
        m.setInstitucionId(tenantId);
        return materiaRepository.save(m);
    }

    private Comision guardarComision(Materia materia, String codigo) {
        return comisionRepository.save(
            Comision.builder().materia(materia).codigo(codigo).activo(true).build());
    }
}
