package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.interceptor.TenantInterceptor;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.CarreraRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dos personas de instituciones distintas usando el sistema al mismo tiempo.
 *
 * <p><b>Qué se está buscando.</b> La institución activa viaja en un {@code ThreadLocal}
 * ({@link TenantContext}) que un interceptor llena al empezar cada petición y limpia al
 * terminarla. Tomcat <b>reutiliza los hilos</b>: la petición de una persona corre sobre el
 * mismo hilo que acaba de atender a otra. Si esa limpieza fallara, quien entra segundo vería
 * los datos del primero, y sería un error silencioso —nadie recibe un mensaje de error, solo
 * datos que no le corresponden—.
 *
 * <p>Los tests de abajo alternan peticiones de dos usuarios sobre el mismo hilo, que es
 * exactamente lo que pasa con dos pestañas o dos navegadores contra el mismo servidor.
 *
 * <p>Sobre las <b>pestañas del mismo navegador</b>: comparten la cookie de sesión, así que
 * comparten la sesión. Eso no es un conflicto, es cómo funciona la web: si alguien cierra
 * sesión en una pestaña, queda cerrada en todas. Lo que este test comprueba es lo otro, que
 * sí sería un problema: que dos sesiones DISTINTAS no se pisen entre sí.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SesionesSimultaneasIT {

    private static final AtomicInteger SEC = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private CarreraRepository carreraRepository;

    private Long tenantA;
    private Long tenantB;

    @BeforeEach
    void sembrarDosInstituciones() {
        TenantContext.clear();
        int n = SEC.incrementAndGet();

        tenantA = institucionRepository.save(Institucion.builder()
            .nombre("Colegio Alfa " + n).activo(true).build()).getId();
        tenantB = institucionRepository.save(Institucion.builder()
            .nombre("Colegio Beta " + n).activo(true).build()).getId();

        TenantContext.set(tenantA);
        Carrera deA = Carrera.builder()
            .codigo("ALFA" + n).nombre("Carrera solo de Alfa")
            .duracionAnios((short) 3).activo(true).build();
        deA.setInstitucionId(tenantA);
        carreraRepository.save(deA);

        TenantContext.set(tenantB);
        Carrera deB = Carrera.builder()
            .codigo("BETA" + n).nombre("Carrera solo de Beta")
            .duracionAnios((short) 3).activo(true).build();
        deB.setInstitucionId(tenantB);
        carreraRepository.save(deB);

        TenantContext.clear();
    }

    @AfterEach
    void limpiar() {
        for (Long t : new Long[]{tenantA, tenantB}) {
            TenantContext.set(t);
            carreraRepository.deleteAll();
        }
        TenantContext.clear();
        institucionRepository.deleteById(tenantA);
        institucionRepository.deleteById(tenantB);
    }

    @Test
    @DisplayName("Dos sesiones alternadas no se ven los datos entre sí")
    void dosSesionesAlternadasNoSePisan() throws Exception {
        // Alfa, Beta, Alfa, Beta: si el tenant quedara pegado del pedido anterior, la
        // segunda vuelta lo delataria.
        for (int vuelta = 0; vuelta < 2; vuelta++) {
            mockMvc.perform(get("/carreras").with(user(principalDe(tenantA))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Carrera solo de Alfa")))
                .andExpect(content().string(not(containsString("Carrera solo de Beta"))));

            mockMvc.perform(get("/carreras").with(user(principalDe(tenantB))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Carrera solo de Beta")))
                .andExpect(content().string(not(containsString("Carrera solo de Alfa"))));
        }
    }

    @Test
    @DisplayName("Al terminar cada pedido el hilo queda sin institución pegada")
    void elHiloQuedaLimpio() throws Exception {
        mockMvc.perform(get("/carreras").with(user(principalDe(tenantA))))
            .andExpect(status().isOk());

        // Si el interceptor no limpiara, este hilo seguiria teniendo el tenant A y la
        // proxima persona en tocarlo veria datos ajenos sin enterarse.
        org.assertj.core.api.Assertions
            .assertThat(TenantContext.get())
            .as("el ThreadLocal tiene que quedar vacio despues de responder")
            .isEmpty();
    }

    @Test
    @DisplayName("Una carrera ajena responde 'no existe', no 'no tenés permiso'")
    void carreraAjenaNoRevelaQueExiste() throws Exception {
        TenantContext.set(tenantB);
        Long idDeBeta = carreraRepository.findAll().stream()
            .filter(c -> tenantB.equals(c.getInstitucionId()))
            .findFirst().orElseThrow().getId();
        TenantContext.clear();

        // Alfa pide una carrera de Beta. Distinguir "no existe" de "no tenes permiso"
        // confirmaria que el registro esta ahi, y con eso se enumeran los datos ajenos
        // probando identificadores.
        mockMvc.perform(get("/carreras/" + idDeBeta + "/editar")
                .with(user(principalDe(tenantA))))
            .andExpect(status().is3xxRedirection());
    }

    // Principal de una institucion: es lo que el TenantInterceptor lee para publicar el tenant.
    private UsuarioAutenticado principalDe(Long tenantId) {
        Rol r = new Rol();
        r.setId((short) 1);
        r.setCodigo("INSTITUCION");
        r.setDescripcion("Institución");

        Usuario u = Usuario.builder().persona(DatosDePrueba.persona("Sesión", "Simultánea")).id(8000L + tenantId).username("sesion" + tenantId).passwordHash("no-se-usa").activo(true).rol(r).emailVerificadoEn(LocalDateTime.now()).build();
        u.setInstitucionId(tenantId);
        return new UsuarioAutenticado(u);
    }
}
