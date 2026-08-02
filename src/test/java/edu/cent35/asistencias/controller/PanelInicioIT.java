package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.config.CustomUserDetails;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.CarreraRepository;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.MateriaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renderiza la pantalla de inicio de verdad (RF-60).
 *
 * <p>Las expresiones de Thymeleaf fallan recién al renderizar, así que un test del servicio no
 * alcanza: una llamada mal escrita en la plantilla compila igual y revienta en la cara del
 * usuario. Acá se pide la página como un usuario logueado y se mira el HTML que sale.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PanelInicioIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private DocenteRepository docenteRepository;
    @Autowired private MateriaRepository materiaRepository;
    @Autowired private ComisionRepository comisionRepository;
    @Autowired private CarreraRepository carreraRepository;

    // El nombre de institucion es unico: sin un sufijo propio, el segundo test de la clase
    // choca contra la fila que dejo el primero.
    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    private Long tenantId;

    @BeforeEach
    void sembrar() {
        TenantContext.clear();
        Institucion i = institucionRepository.save(Institucion.builder()
            .nombre("Instituto de prueba " + SECUENCIA.incrementAndGet())
            .activo(true).build());
        tenantId = i.getId();
    }

    // Se borra lo sembrado para no dejarle filas colgadas a los demas tests de integracion,
    // que comparten la misma base y algunos vacian instituciones al arrancar.
    @AfterEach
    void limpiar() {
        TenantContext.set(tenantId);
        comisionRepository.deleteAll();
        materiaRepository.deleteAll();
        carreraRepository.deleteAll();
        docenteRepository.deleteAll();
        TenantContext.clear();
        institucionRepository.deleteById(tenantId);
    }

    @Test
    @DisplayName("El inicio renderiza los tres bloques y ya no repite los accesos del menú")
    void renderizaElPanel() throws Exception {
        mockMvc.perform(get("/").with(user(principal())))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Ahora mismo")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("El día en números")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Requiere atención")))
            // Los accesos duplicados del navbar tienen que haber desaparecido de la home.
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("home__accion"))))
            // Y el footer decorativo, de todas las pantallas.
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("<footer"))));
    }

    @Test
    @DisplayName("Con la institución recién creada dice que no hay nada, sin romperse")
    void institucionVacia() throws Exception {
        mockMvc.perform(get("/").with(user(principal())))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "No hay ninguna clase en curso")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "Hoy no hay clases programadas")));
    }

    @Test
    @DisplayName("Un docente activo sin consentimiento aparece en Requiere atención")
    void docenteSinConsentimientoAparece() throws Exception {
        TenantContext.set(tenantId);
        Docente d = Docente.builder()
            .nombre("Ana").apellido("Pérez").dni("30111222").activo(true)
            .fechaAlta(LocalDate.now()).build();
        d.setInstitucionId(tenantId);
        docenteRepository.save(d);
        TenantContext.clear();

        mockMvc.perform(get("/").with(user(principal())))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "docentes sin consentimiento vigente")));
    }

    @Test
    @DisplayName("Una comisión sin docente y sin horarios aparece en Requiere atención")
    void comisionIncompletaAparece() throws Exception {
        TenantContext.set(tenantId);
        Carrera c = Carrera.builder().codigo("CAR-1").nombre("Carrera").activo(true).build();
        c.setInstitucionId(tenantId);
        carreraRepository.save(c);
        Materia m = Materia.builder()
            .codigo("MAT-1").nombre("Matemática").carrera(c).activo(true).build();
        m.setInstitucionId(tenantId);
        materiaRepository.save(m);
        comisionRepository.save(Comision.builder()
            .codigo("A").materia(m).docenteAsignado(null).activo(true).build());
        TenantContext.clear();

        mockMvc.perform(get("/").with(user(principal())))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "comisiones sin docente asignado")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "comisiones sin horarios cargados")));
    }

    // Principal de la aplicacion: el TenantInterceptor lo necesita para publicar el tenant.
    private CustomUserDetails principal() {
        Rol r = new Rol();
        r.setId((short) 1);
        r.setCodigo("ADMIN");
        r.setDescripcion("ADMIN");

        Usuario u = Usuario.builder()
            .id(99L)
            .username("test.inicio")
            .passwordHash("no-se-usa")
            .nombre("Test")
            .apellido("Inicio")
            .activo(true)
            .rol(r)
            .emailVerificadoEn(LocalDateTime.now())
            .build();
        u.setInstitucionId(tenantId);
        return new CustomUserDetails(u);
    }
}
