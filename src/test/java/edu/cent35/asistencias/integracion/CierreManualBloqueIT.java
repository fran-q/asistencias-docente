package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.BloquePresencia;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoCierre;
import edu.cent35.asistencias.model.EstadoSalida;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.OrigenMarca;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.BloquePresenciaRepository;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
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
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre la pantalla de salidas pendientes y el cierre manual de un bloque de punta a punta
 * (RF-79, RF-83), sobre el HTML que de verdad sale del servidor.
 *
 * <p><b>Por qué es un test de integración y no unitario.</b> Las expresiones de Thymeleaf
 * fallan recién al renderizar: un {@code th:object} mal ubicado o un formato de fecha que la
 * plantilla no sabe aplicar compilan igual y explotan en la pantalla. Los unitarios del
 * servicio no tocan la plantilla, y mirar el navegador exige credenciales que un test no
 * debería manejar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CierreManualBloqueIT {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private DocenteRepository docenteRepository;
    @Autowired private BloquePresenciaRepository bloqueRepository;
    @Autowired private edu.cent35.asistencias.repository.MotivoCargaManualRepository motivoRepository;
    @Autowired private edu.cent35.asistencias.repository.UsuarioRepository usuarioRepository;
    @Autowired private edu.cent35.asistencias.repository.RolRepository rolRepository;

    private Long tenantId;
    private Long bloqueId;
    private Short motivoFallaId;
    private Short motivoOtroId;
    private Usuario admin;

    @BeforeEach
    void sembrar() {
        TenantContext.clear();

        // El catalogo de motivos se siembra en V001, y el perfil test corre sobre H2 con
        // Flyway apagado: aca no existe si no se crea a mano.
        motivoFallaId = motivoRepository.save(edu.cent35.asistencias.model.MotivoCargaManual.builder()
            .codigo("FALLA_RECONOCIMIENTO_" + SECUENCIA.get())
            .descripcion("Falla en el reconocimiento facial").activo(true).build()).getId();
        motivoOtroId = motivoRepository.save(edu.cent35.asistencias.model.MotivoCargaManual.builder()
            .codigo("OTRO").descripcion("Otro motivo").activo(true).build()).getId();
        Institucion i = institucionRepository.save(Institucion.builder()
            .nombre("Instituto cierre " + SECUENCIA.incrementAndGet())
            .activo(true).build());
        tenantId = i.getId();

        TenantContext.set(tenantId);
        Docente d = Docente.builder()
            .persona(DatosDePrueba.personaConDni("4011122" + SECUENCIA.get(), "Ana", "Pérez"))
            .fechaAlta(LocalDate.now()).activo(true).build();
        d.setInstitucionId(tenantId);
        docenteRepository.save(d);

        // Un bloque que el job cerró solo: nadie registró la salida y la hora es presumida.
        BloquePresencia b = BloquePresencia.builder()
            .docente(d)
            .fecha(LocalDate.now().minusDays(1))
            .horaEntrada(LocalTime.of(18, 0))
            .horaSalida(LocalTime.of(22, 0))
            .origenEntrada(OrigenMarca.AUTOMATICO)
            .origenSalida(OrigenMarca.PRESUNTO)
            .estadoCierre(EstadoCierre.SIN_CIERRE)
            .estadoSalida(EstadoSalida.SIN_MARCA)
            .build();
        b.setInstitucionId(tenantId);
        bloqueId = bloqueRepository.save(b).getId();

        // El admin tiene que existir de verdad: el service lo busca por id para dejar
        // asentado quien cerro, y un principal solo en memoria hace fallar el cierre con
        // "usuario no encontrado" — que el ExceptionHandler convierte en un redirect y
        // disimula el problema.
        Rol rol = rolRepository.findByCodigo("ADMIN").orElseGet(() -> {
            Rol nuevo = new Rol();
            nuevo.setCodigo("ADMIN");
            nuevo.setDescripcion("Administrador");
            return rolRepository.save(nuevo);
        });
        Usuario u = Usuario.builder()
            .persona(DatosDePrueba.persona("Test", "Cierre"))
            .username("test.cierre." + SECUENCIA.get()).passwordHash("test-clave-dev")
            .email("cierre" + SECUENCIA.get() + "@test.local")
            .activo(true).rol(rol).emailVerificadoEn(LocalDateTime.now()).build();
        u.setInstitucionId(tenantId);
        admin = usuarioRepository.save(u);
    }

    /**
     * Borra lo sembrado, en orden inverso de dependencia.
     *
     * <p>No es prolijidad: la base H2 se comparte entre los tests de integración y
     * {@code bloques_presencia} referencia al docente. Un bloque que sobrevive impide que
     * otro test borre sus propios docentes, y ese test falla por algo que no tiene nada que
     * ver con lo que estaba probando.
     */
    @AfterEach
    void limpiar() {
        TenantContext.set(tenantId);
        bloqueRepository.findById(bloqueId).ifPresent(bloqueRepository::delete);
        usuarioRepository.deleteById(admin.getId());
        TenantContext.clear();
    }

    @Test
    @DisplayName("La pantalla de pendientes renderiza y muestra el bloque sin cerrar")
    void pantallaRenderiza() throws Exception {
        mockMvc.perform(get("/asistencias/bloques/pendientes").with(user(principal())))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Salidas pendientes")))
            .andExpect(content().string(containsString("Pérez")))
            // La hora presumida se muestra como tal: es la diferencia entre un dato medido y
            // uno completado por el sistema.
            .andExpect(content().string(containsString("la completó el sistema")))
            .andExpect(content().string(containsString("22:00")));
    }

    @Test
    @DisplayName("Cerrar a mano deja el bloque confirmado, con autor y motivo")
    void cierreManualConfirmaElBloque() throws Exception {
        mockMvc.perform(post("/asistencias/bloques/{id}/cerrar", bloqueId)
                .with(user(principal())).with(csrf())
                .param("horaSalida", "21:30")
                .param("motivoId", motivoFallaId.toString()))
            .andExpect(status().is3xxRedirection());

        TenantContext.set(tenantId);
        BloquePresencia b = bloqueRepository.findById(bloqueId).orElseThrow();
        assertThat(b.getEstadoCierre()).isEqualTo(EstadoCierre.CERRADO_POR_ADMIN);
        assertThat(b.getOrigenSalida()).isEqualTo(OrigenMarca.MANUAL);
        assertThat(b.getHoraSalida()).isEqualTo(LocalTime.of(21, 30));
        assertThat(b.getMotivoCierre()).isNotNull();
        assertThat(b.getCerradoPor()).isNotNull();
    }

    @Test
    @DisplayName("El motivo Otro sin detalle vuelve a la pantalla con el error, sin cerrar")
    void otroSinDetalleNoCierra() throws Exception {
        mockMvc.perform(post("/asistencias/bloques/{id}/cerrar", bloqueId)
                .with(user(principal())).with(csrf())
                .param("horaSalida", "21:30")
                .param("motivoId", motivoOtroId.toString()))
            .andExpect(status().isOk())
            // El texto exacto del error: "detalle" a secas tambien aparece en el
            // placeholder del formulario, asi que el test pasaria sin el error.
            .andExpect(content().string(containsString("contá en el detalle qué pasó")));

        TenantContext.set(tenantId);
        assertThat(bloqueRepository.findById(bloqueId).orElseThrow().getEstadoCierre())
            .isEqualTo(EstadoCierre.SIN_CIERRE);
    }

    @Test
    @DisplayName("Una salida anterior a la entrada vuelve con el error, sin cerrar")
    void salidaAnteriorNoCierra() throws Exception {
        mockMvc.perform(post("/asistencias/bloques/{id}/cerrar", bloqueId)
                .with(user(principal())).with(csrf())
                .param("horaSalida", "17:00")
                .param("motivoId", motivoFallaId.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("posterior")));

        TenantContext.set(tenantId);
        assertThat(bloqueRepository.findById(bloqueId).orElseThrow().getEstadoCierre())
            .isEqualTo(EstadoCierre.SIN_CIERRE);
    }

    @Test
    @DisplayName("El panel de inicio anuncia las salidas sin registrar, y primero")
    void panelAnunciaLasSalidasPendientes() throws Exception {
        // RF-79: la salida es obligatoria, asi que su falta se informa donde el equipo
        // administrativo mira todos los dias, no solo en una pantalla aparte.
        mockMvc.perform(get("/").with(user(principal())))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("salida sin registrar")))
            .andExpect(content().string(containsString("/asistencias/bloques/pendientes")))
            .andExpect(content().string(containsString("nadie la observó")));
    }

    @Test
    @DisplayName("El listado del día tiene columna de salida además de la de entrada")
    void listadoMuestraLaSalida() throws Exception {
        mockMvc.perform(get("/asistencias").with(user(principal())))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(">Entrada</th>")))
            .andExpect(content().string(containsString(">Salida</th>")));
    }

    // Admin del tenant sembrado, el mismo que existe en la base.
    private UsuarioAutenticado principal() {
        return new UsuarioAutenticado(admin);
    }
}
