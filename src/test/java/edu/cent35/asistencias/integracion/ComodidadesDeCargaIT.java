package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.PeriodoLectivo;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.CarreraRepository;
import edu.cent35.asistencias.repository.CicloLectivoRepository;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.MateriaRepository;
import edu.cent35.asistencias.repository.PeriodoLectivoRepository;
import edu.cent35.asistencias.repository.PuestoCapturaRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.service.PuestoCapturaService;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las dos comodidades de carga: el código sugerido de una comisión nueva y el tope de equipos
 * editable desde la pantalla donde se topa.
 *
 * <p>Las dos existen para sacar pasos, y las dos tocan algo que ya estaba: el código ya se
 * validaba contra el índice único, y el tope ya se editaba en "Mi institución". Por eso lo que
 * se prueba acá no es el cálculo —eso está en {@code ComisionServiceTest}— sino que la vía
 * nueva respete lo que ya existía: el aislamiento entre instituciones en un caso, y la
 * coherencia del límite en el otro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ComodidadesDeCargaIT {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private CarreraRepository carreraRepository;
    @Autowired private MateriaRepository materiaRepository;
    @Autowired private ComisionRepository comisionRepository;
    @Autowired private CicloLectivoRepository cicloRepository;
    @Autowired private PeriodoLectivoRepository periodoRepository;
    @Autowired private PuestoCapturaRepository puestoRepository;
    @Autowired private PuestoCapturaService puestoService;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Long tenantA;
    private Long tenantB;
    private Usuario cuentaA;
    private Materia materiaA;
    private PeriodoLectivo periodoA;

    @BeforeEach
    void sembrar() {
        tenantA = crearInstitucion();
        TenantContext.set(tenantA);
        cuentaA = crearCuenta(tenantA, "INSTITUCION");
        materiaA = crearMateria(tenantA);
        periodoA = crearCiclo(tenantA).getPeriodos().get(0);

        tenantB = crearInstitucion();
        TenantContext.set(tenantB);
        crearCuenta(tenantB, "INSTITUCION");

        TenantContext.set(tenantA);
    }

    @AfterEach
    void limpiar() {
        for (Long t : List.of(tenantA, tenantB)) {
            if (t == null) continue;
            TenantContext.set(t);
            final Long tenant = t;
            List<Long> comisionIds = comisionRepository.findAllDelTenant(tenant).stream()
                .map(Comision::getId).toList();
            borrar(comisionRepository, c -> comisionIds.contains(c.getId()));
            borrar(puestoRepository, p -> tenant.equals(p.getInstitucionId()));
            borrar(periodoRepository, p -> tenant.equals(p.getInstitucionId()));
            borrar(cicloRepository, c -> tenant.equals(c.getInstitucionId()));
            borrar(materiaRepository, m -> tenant.equals(m.getInstitucionId()));
            borrar(carreraRepository, c -> tenant.equals(c.getInstitucionId()));
            borrar(usuarioRepository, u -> tenant.equals(u.getInstitucionId()));
        }
        TenantContext.clear();
    }

    // ========================================================================
    //  Codigo sugerido
    // ========================================================================

    @Test
    @DisplayName("El formulario pide el codigo sugerido y el servidor lo contesta")
    void elCodigoSugeridoLlegaAlFormulario() throws Exception {
        assertThat(pedir("/comisiones/codigo-sugerido?materiaId=" + materiaA.getId()
                         + "&periodoId=" + periodoA.getId()))
            .as("la materia no tiene comisiones todavía")
            .isEqualTo("A");

        comision("A");

        assertThat(pedir("/comisiones/codigo-sugerido?materiaId=" + materiaA.getId()
                         + "&periodoId=" + periodoA.getId()))
            .isEqualTo("B");
    }

    @Test
    @DisplayName("La sugerencia NO mira las comisiones de otra institucion")
    void laSugerenciaNoCruzaInstituciones() throws Exception {
        // Una materia de la otra institución, con su comisión A cargada. Si la consulta no
        // llevara su WHERE de tenant, pediría la B para una materia que no tiene ninguna.
        TenantContext.set(tenantB);
        Materia materiaB = crearMateria(tenantB);
        PeriodoLectivo periodoB = crearCiclo(tenantB).getPeriodos().get(0);
        comisionRepository.save(Comision.builder()
            .materia(materiaB).codigo("A").periodo(periodoB).activo(true).build());
        TenantContext.set(tenantA);

        assertThat(pedir("/comisiones/codigo-sugerido?materiaId=" + materiaB.getId()
                         + "&periodoId=" + periodoB.getId()))
            .as("la cuenta de A no puede ver lo que tiene cargado B, ni siquiera para contar")
            .isEqualTo("A");
    }

    @Test
    @DisplayName("El formulario de alta trae el gancho; el de edicion no")
    void soloElAltaSugiere() throws Exception {
        assertThat(pedir("/comisiones/nueva"))
            .contains("data-codigo-sugerido")
            .contains("comision-codigo.js");

        Comision c = comision("Mañana");
        assertThat(pedir("/comisiones/" + c.getId() + "/editar"))
            .as("editando, el código ya existe: sugerir otro sería pisarlo")
            .doesNotContain("data-codigo-sugerido");
    }

    // ========================================================================
    //  Tope de equipos desde la pantalla de equipos
    // ========================================================================

    @Test
    @DisplayName("El tope se cambia desde Equipos, sin pasar por Mi institucion")
    void elTopeSeCambiaDesdeEquipos() throws Exception {
        mockMvc.perform(post("/puestos/tope").param("tope", "3")
                .with(user(new UsuarioAutenticado(cuentaA))).with(csrf()))
            .andExpect(status().is3xxRedirection());

        assertThat(puestoService.topeDeEquipos(tenantA)).isEqualTo((short) 3);

        // Vacio = sin tope, que es como lo guarda la institucion que no sabe cuantas
        // entradas va a cubrir.
        mockMvc.perform(post("/puestos/tope")
                .with(user(new UsuarioAutenticado(cuentaA))).with(csrf()))
            .andExpect(status().is3xxRedirection());

        assertThat(puestoService.topeDeEquipos(tenantA)).isNull();
    }

    @Test
    @DisplayName("No se puede dejar el tope por debajo de los equipos que ya hay")
    void elTopeNoPuedeQuedarIncumplido() throws Exception {
        puestoService.designar(tenantA, "Entrada norte", cuentaA);
        puestoService.designar(tenantA, "Entrada sur", cuentaA);

        mockMvc.perform(post("/puestos/tope").param("tope", "1")
                .with(user(new UsuarioAutenticado(cuentaA))).with(csrf()))
            .andExpect(status().is3xxRedirection());

        assertThat(puestoService.topeDeEquipos(tenantA))
            .as("bajarlo no revoca ninguno, así que el tope quedaría incumplido por el "
                + "propio sistema desde el momento de guardarlo")
            .isNull();
    }

    @Test
    @DisplayName("Un ADMIN no cambia el tope: no se amplia su propio limite")
    void elAdminNoCambiaElTope() throws Exception {
        Usuario admin = crearCuenta(tenantA, "ADMIN");

        mockMvc.perform(post("/puestos/tope").param("tope", "9")
                .with(user(new UsuarioAutenticado(admin))).with(csrf()))
            .andExpect(status().isForbidden());

        assertThat(puestoService.topeDeEquipos(tenantA)).isNull();
    }

    @Test
    @DisplayName("El tope se cambia en la misma pantalla, pero no queda editable a la vista")
    void laPantallaOfreceElCampoCerrado() throws Exception {
        String html = pedir("/puestos");

        assertThat(html)
            .contains("Cuántos equipos pueden tomar asistencia")
            .contains("/puestos/tope")
            .as("el mensaje ya no manda a Mi institución a cambiar un número")
            .doesNotContain("subí el tope en");

        // Sin el atributo 'open': el campo arranca cerrado y hay que abrirlo con el botón.
        // Un número editable a la vista se cambia sin querer, y éste gobierna cuántas
        // máquinas pueden tener una credencial de captura.
        assertThat(html)
            .contains("<details class=\"puesto__tope\">")
            .contains("Cambiar el tope");
    }

    // ------------------------------------------------------------------------

    private String pedir(String ruta) throws Exception {
        return mockMvc.perform(get(ruta).with(user(new UsuarioAutenticado(cuentaA))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private Comision comision(String codigo) {
        return comisionRepository.save(Comision.builder()
            .materia(materiaA).codigo(codigo).periodo(periodoA).activo(true).build());
    }

    private Long crearInstitucion() {
        Institucion i = institucionRepository.save(Institucion.builder()
            .nombre("Instituto comodidad " + SECUENCIA.incrementAndGet()).activo(true).build());
        return i.getId();
    }

    private Usuario crearCuenta(Long tenant, String codigoRol) {
        Rol rol = rolRepository.findByCodigo(codigoRol).orElseGet(() -> {
            Rol nuevo = new Rol();
            nuevo.setCodigo(codigoRol);
            nuevo.setDescripcion(codigoRol);
            return rolRepository.save(nuevo);
        });
        Usuario u = Usuario.builder()
            .username(codigoRol.toLowerCase() + "." + tenant + "." + SECUENCIA.incrementAndGet())
            .email("c" + tenant + "." + SECUENCIA.get() + "@test.local")
            .passwordHash("no-se-usa").activo(true).rol(rol)
            .emailVerificadoEn(LocalDateTime.now())
            .build();
        u.setInstitucionId(tenant);
        return usuarioRepository.save(u);
    }

    private Materia crearMateria(Long tenant) {
        Carrera carrera = Carrera.builder()
            .codigo("COM-" + tenant + "-" + SECUENCIA.incrementAndGet()).nombre("Tecnicatura")
            .duracionAnios((short) 3).activo(true).build();
        carrera.setInstitucionId(tenant);
        carrera = carreraRepository.save(carrera);

        Materia m = Materia.builder()
            .codigo("COM1-" + tenant + "-" + SECUENCIA.get()).nombre("Programación I")
            .carrera(carrera).anio((short) 1).activo(true).build();
        m.setInstitucionId(tenant);
        return materiaRepository.save(m);
    }

    private CicloLectivo crearCiclo(Long tenant) {
        return cicloRepository.save(DatosDePrueba.cicloAnualDelTenant(tenant, LocalDate.now().getYear()));
    }

    private <T> void borrar(org.springframework.data.jpa.repository.JpaRepository<T, ?> repo,
                            java.util.function.Predicate<T> condicion) {
        repo.deleteAll(repo.findAll().stream().filter(condicion).toList());
    }
}
