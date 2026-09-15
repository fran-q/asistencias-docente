package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.model.Persona;
import edu.cent35.asistencias.repository.PersonaRepository;
import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.interceptor.TenantInterceptor;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.model.PeriodoLectivo;
import edu.cent35.asistencias.repository.CicloLectivoRepository;
import edu.cent35.asistencias.repository.PeriodoLectivoRepository;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.CarreraRepository;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.MateriaRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
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
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renderiza de verdad las pantallas que cambiaron en esta tanda de correcciones.
 *
 * <p>Hace falta porque una expresión de Thymeleaf mal escrita compila igual y revienta recién
 * al renderizar: los 297 tests podían estar en verde con cuatro pantallas rotas. Y varias
 * comprobaciones son de la forma "esto ya <b>no</b> tiene que aparecer", que es exactamente lo
 * que se pasa por alto mirando a ojo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorreccionesPantallasIT {

    private static final AtomicInteger SEC = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private CicloLectivoRepository cicloLectivoRepository;
    @Autowired private PeriodoLectivoRepository periodoLectivoRepository;
    @Autowired private PersonaRepository personaRepository;
    @Autowired private CarreraRepository carreraRepository;
    @Autowired private MateriaRepository materiaRepository;
    @Autowired private ComisionRepository comisionRepository;
    @Autowired private HorarioRepository horarioRepository;
    @Autowired private DocenteRepository docenteRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;

    private Long tenantId;
    private Long carreraId;
    private Long materiaId;
    private Long docenteId;
    private String dniDocente;
    private Long usuarioInstitucionId;
    private Long usuarioAdminId;

    @BeforeEach
    void sembrar() {
        TenantContext.clear();
        int n = SEC.incrementAndGet();
        Institucion i = institucionRepository.save(Institucion.builder()
            .nombre("Instituto correcciones " + n).activo(true).build());
        tenantId = i.getId();

        TenantContext.set(tenantId);

        dniDocente = "4011122" + n;
        Docente d = Docente.builder().persona(DatosDePrueba.personaConDni(dniDocente, "Ana", "Pérez")).fechaAlta(LocalDate.now()).activo(true).build();
        d.setInstitucionId(tenantId);
        docenteId = docenteRepository.save(d).getId();

        Carrera c = Carrera.builder()
            .codigo("COR" + n).nombre("Carrera de correcciones")
            .duracionAnios((short) 3).activo(true).build();
        c.setInstitucionId(tenantId);
        carreraId = carreraRepository.save(c).getId();

        Materia m = Materia.builder()
            .codigo("MCOR" + n).nombre("Materia de prueba").carrera(c)
            .anio((short) 2).docenteTitular(d).activo(true).build();
        m.setInstitucionId(tenantId);
        materiaId = materiaRepository.save(m).getId();

        Comision com = comisionRepository.save(Comision.builder()
            .codigo("A").materia(m).docenteAsignado(d).activo(true)
            .periodo(periodoDe(tenantId)).build());

        horarioRepository.save(Horario.builder()
            .comision(com).diaSemana((byte) 1)
            .horaInicio(LocalTime.of(18, 30)).horaFin(LocalTime.of(20, 30))
            .toleranciaMin((short) 15).activo(true).build());

        // Los roles son un catalogo global que en produccion siembra la migracion V001. Acá
        // no existen: los tests corren sobre H2 con Flyway apagado y el esquema generado por
        // Hibernate, asi que las tablas nacen vacias. Se crean si faltan.
        Rol rolInst = rolRepository.findByCodigo("INSTITUCION")
            .orElseGet(() -> rolRepository.save(Rol.builder().codigo("INSTITUCION").descripcion("Institución").build()));
        Rol rolAdmin = rolRepository.findByCodigo("ADMIN")
            .orElseGet(() -> rolRepository.save(Rol.builder().codigo("ADMIN").descripcion("Administrador").build()));

        // La cuenta institucional va SIN persona (V018): representa al establecimiento y no a
        // alguien concreto, asi que su nombre para mostrar sale de la institucion.
        Usuario inst = Usuario.builder().username("inst" + n).email("inst" + n + "@x.test").passwordHash("x").rol(rolInst).activo(true).emailVerificadoEn(LocalDateTime.now()).build();
        inst.setInstitucionId(tenantId);
        usuarioInstitucionId = usuarioRepository.save(inst).getId();

        Usuario adm = Usuario.builder().persona(DatosDePrueba.persona("Marcelo", "Quinteros")).username("adm" + n).email("adm" + n + "@x.test").passwordHash("x").rol(rolAdmin).activo(true).emailVerificadoEn(LocalDateTime.now()).build();
        adm.setInstitucionId(tenantId);
        usuarioAdminId = usuarioRepository.save(adm).getId();

        TenantContext.clear();
    }

    @AfterEach
    void limpiar() {
        TenantContext.set(tenantId);
        horarioRepository.deleteAll();
        comisionRepository.deleteAll();
        materiaRepository.deleteAll();
        carreraRepository.deleteAll();
        docenteRepository.deleteAll();
        usuarioRepository.deleteById(usuarioInstitucionId);
        usuarioRepository.deleteById(usuarioAdminId);
        TenantContext.clear();
        institucionRepository.deleteById(tenantId);
    }

    // ========================================================================
    //  Explicaciones que se cierran y se vuelven a abrir
    // ========================================================================

    @Test
    @DisplayName("Las explicaciones largas vienen en un cuadro que se cierra y se vuelve a abrir")
    void lasExplicacionesSeCierran() throws Exception {
        String[] pantallas = { "/ciclos", "/dias-sin-clase", "/asistencias/bloques/pendientes", "/usuarios/nuevo" };
        for (String ruta : pantallas) {
            String html = mockMvc.perform(get(ruta).with(user(principal("INSTITUCION"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            assertThat(html)
                .as(ruta + ": el cuadro trae su clave, que es lo que se recuerda al cerrarlo")
                .containsPattern("<aside class=\"ayuda\" data-ayuda=\"[a-z-]+\"");
            assertThat(html)
                .as(ruta + ": sin el script no hay cruz ni \"i\", y el cuadro queda siempre abierto")
                .contains("/js/comun/ayuda.js");
        }
    }

    // ========================================================================
    //  Usuarios: el rol dejo de elegirse y de editarse
    // ========================================================================

    @Test
    @DisplayName("El alta de usuario no ofrece elegir el rol y pide repetir la contraseña")
    void altaSinRolYConConfirmacion() throws Exception {
        mockMvc.perform(get("/usuarios/nuevo").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("id=\"rol\""))))
            .andExpect(content().string(containsString("id=\"confirmacion\"")))
            .andExpect(content().string(containsString("Repetir la contraseña")));
    }

    @Test
    @DisplayName("Un usuario nuevo necesita al menos 3 letras: '...' ya no pasa")
    void usuarioConTresLetras() throws Exception {
        mockMvc.perform(get("/usuarios/nuevo").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            // El navegador avisa antes de enviar, con la misma regla que el servidor.
            .andExpect(content().string(containsString(
                "data-error-patron=\"Tiene que tener al menos 3 letras")));

        mockMvc.perform(post("/usuarios/nuevo").with(user(principal("INSTITUCION"))).with(csrf())
                .param("username", "...")
                .param("email", "puntos@x.test")
                .param("nombre", "Tres")
                .param("apellido", "Puntos")
                .param("password", "Clave123")
                .param("confirmacion", "Clave123"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Tiene que tener al menos 3 letras.")));

        assertThat(usuarioRepository.findAll())
            .as("rechazado en el formulario: la cuenta no se crea")
            .noneMatch(u -> "...".equals(u.getUsername()));
    }

    @Test
    @DisplayName("El código de una comisión admite hasta 30 caracteres, lo que ya permitía la base")
    void codigoDeComisionLargo() throws Exception {
        String formulario = mockMvc.perform(get("/comisiones/nueva").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(formulario).containsPattern("id=\"codigo\"[^>]*maxlength=\"30\"");

        Long periodoId = periodoLectivoRepository.findAll().stream()
            .filter(p -> tenantId.equals(p.getInstitucionId()))
            .findFirst().orElseThrow().getId();

        mockMvc.perform(post("/comisiones/nueva").with(user(principal("INSTITUCION"))).with(csrf())
                .param("codigo", "Turno noche B")
                .param("materiaId", materiaId.toString())
                .param("periodoId", periodoId.toString()))
            .andExpect(status().is3xxRedirection());
        assertThat(comisionRepository.findAll())
            .as("antes el formulario cortaba en 8: 'Turno noche B' no entraba")
            .anyMatch(c -> "Turno noche B".equals(c.getCodigo()));

        mockMvc.perform(post("/comisiones/nueva").with(user(principal("INSTITUCION"))).with(csrf())
                .param("codigo", "x".repeat(31))
                .param("materiaId", materiaId.toString())
                .param("periodoId", periodoId.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("entre 1 y 30 caracteres")));
    }

    @Test
    @DisplayName("La edición de usuario no ofrece cambiar el rol")
    void edicionSinSelectorDeRol() throws Exception {
        mockMvc.perform(get("/usuarios/" + usuarioAdminId + "/editar")
                .with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("<select id=\"rol\""))))
            .andExpect(content().string(containsString("dale de baja esta cuenta y creá una nueva")));
    }

    @Test
    @DisplayName("Una cuenta de institución no ofrece la casilla para darla de baja")
    void institucionSinBaja() throws Exception {
        mockMvc.perform(get("/usuarios/" + usuarioInstitucionId + "/editar")
                .with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("no se puede dar de baja")))
            // Un solo campo de nombre: un colegio no tiene apellido.
            .andExpect(content().string(containsString("Nombre de la institución")))
            .andExpect(content().string(not(containsString("id=\"apellido\""))));
    }

    @Test
    @DisplayName("Una cuenta de administrador sí ofrece la casilla de baja, con los dos nombres")
    void adminConBajaYApellido() throws Exception {
        mockMvc.perform(get("/usuarios/" + usuarioAdminId + "/editar")
                .with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Cuenta activa")))
            .andExpect(content().string(containsString("id=\"apellido\"")));
    }

    @Test
    @DisplayName("La cuenta institucional se nombra con el establecimiento, no con una persona")
    void institucionSeNombraConElEstablecimiento() throws Exception {
        // Este test probaba otra cosa: que una cuenta de institución cargada con nombre y
        // apellido no perdiera el apellido al abrir el formulario. Desde V018 ese escenario
        // no existe, porque la cuenta institucional dejó de tener una persona detrás: el alta
        // ya no pide nombre ni apellido de nadie.
        //
        // Lo que sí sigue importando es que la cuenta se nombre de algún modo reconocible, y
        // lo que corresponde es el nombre del establecimiento. Una cuenta sin persona que
        // apareciera con el campo vacío se guardaría en blanco al primer guardado.
        TenantContext.set(tenantId);
        Usuario inst = usuarioRepository.findById(usuarioInstitucionId).orElseThrow();
        assertThat(inst.getPersona())
            .as("la cuenta que representa al establecimiento no es una persona")
            .isNull();
        String nombreDelEstablecimiento = inst.getInstitucion().getNombre();
        TenantContext.clear();

        mockMvc.perform(get("/usuarios/" + usuarioInstitucionId + "/editar")
                .with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("value=\"" + nombreDelEstablecimiento + "\"")));
    }

    // ========================================================================
    //  Mi cuenta: la contrasena empieza pidiendo el codigo
    // ========================================================================

    @Test
    @DisplayName("Mi cuenta arranca ofreciendo el código, no el campo de contraseña nueva")
    void cambioDePasswordEmpiezaPorElCodigo() throws Exception {
        // Esta pantalla lee de la base la cuenta del principal, asi que aca hace falta una
        // que exista de verdad; el principal ficticio de los demas tests no alcanza.
        mockMvc.perform(get("/mi-cuenta").with(user(principalReal(usuarioAdminId, "ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Enviarme el código")))
            // El formulario de la contrasena nueva no se dibuja hasta validar el codigo.
            .andExpect(content().string(not(containsString("id=\"nuevaPassword\""))))
            // Y la contrasena actual ya no se pide en ningun paso.
            .andExpect(content().string(not(containsString("Contraseña actual"))));
    }

    // ========================================================================
    //  Docentes
    // ========================================================================

    @Test
    @DisplayName("El listado de docentes ya no tiene el botón Datos")
    void docentesSinBotonDatos() throws Exception {
        mockMvc.perform(get("/docentes").with(user(principal("ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString(">Datos</a>"))));
    }

    @Test
    @DisplayName("Un docente con materias a cargo avisa por qué no se puede dar de baja, sin modal")
    void bajaImposibleAvisaDirecto() throws Exception {
        // El docente sembrado es titular de una materia y esta asignado a una comision.
        mockMvc.perform(get("/docentes").with(user(principal("ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-aviso-toast")))
            .andExpect(content().string(containsString("No se puede dar de baja")));
    }

    @Test
    @DisplayName("La edición del docente lleva a la ficha ARCO y permite darlo de baja")
    void edicionDeDocenteTieneFichaYBaja() throws Exception {
        mockMvc.perform(get("/docentes/" + docenteId + "/editar")
                .with(user(principal("ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("derechos ARCO")))
            .andExpect(content().string(containsString("/docentes/" + docenteId + "/ficha")))
            .andExpect(content().string(containsString("Ficha y estado del docente")));
    }

    // ========================================================================
    //  Nombres: mayuscula inicial, se tipeen como se tipeen
    // ========================================================================

    @Test
    @DisplayName("Nombre y apellido se guardan con mayúscula inicial, en docentes y en usuarios")
    void nombresConMayusculaInicial() throws Exception {
        String alta = mockMvc.perform(get("/docentes/nuevo").with(user(principal("ADMIN"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(alta)
            .as("el navegador corrige al salir del campo, con la misma regla que el servidor")
            .containsPattern("id=\"apellido\"[^>]*data-nombre-propio")
            .containsPattern("id=\"nombre\"[^>]*data-nombre-propio")
            .contains("/js/comun/nombre-propio.js");

        String dni = "3222333" + SEC.incrementAndGet();
        mockMvc.perform(post("/docentes/nuevo").with(user(principal("ADMIN"))).with(csrf())
                .param("dni", dni)
                .param("nombre", "maría de los ángeles")
                .param("apellido", "GONZÁLEZ DE LA VEGA"))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/docentes/" + docenteId + "/editar").with(user(principal("ADMIN"))).with(csrf())
                .param("dni", dniDocente)
                .param("nombre", "ana")
                .param("apellido", "pérez-gómez"))
            .andExpect(status().is3xxRedirection());

        // Lo que ya trae mayúsculas elegidas no se toca, y el resto del campo sí se corrige.
        String username = "ronald" + SEC.incrementAndGet();
        mockMvc.perform(post("/usuarios/nuevo").with(user(principal("INSTITUCION"))).with(csrf())
                .param("username", username)
                .param("email", username + "@x.test")
                .param("nombre", "  ronald  ")
                .param("apellido", "McDonald")
                .param("password", "Clave123")
                .param("confirmacion", "Clave123"))
            .andExpect(status().is3xxRedirection());

        Usuario creado = usuarioRepository.findAll().stream()
            .filter(u -> username.equals(u.getUsername())).findFirst().orElseThrow();
        try {
            assertThat(creado.getPersona().getNombreCompleto()).isEqualTo("McDonald, Ronald");
            assertThat(personaRepository.buscarPorDni(tenantId, dni).orElseThrow().getNombreCompleto())
                .isEqualTo("González de la Vega, María de los Ángeles");
            assertThat(personaRepository.buscarPorDni(tenantId, dniDocente).orElseThrow().getNombreCompleto())
                .isEqualTo("Pérez-Gómez, Ana");
        } finally {
            // La cuenta apunta a la institucion, y limpiar() la borra al final.
            usuarioRepository.delete(creado);
        }
    }

    @Test
    @DisplayName("Solo los campos de una persona llevan la mayúscula inicial")
    void nombrePropioSoloEnPersonas() throws Exception {
        mockMvc.perform(get("/usuarios/" + usuarioAdminId + "/editar")
                .with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-nombre-propio")));

        // El nombre de un establecimiento lleva siglas: la regla haría "Cent 35" de "CENT 35".
        mockMvc.perform(get("/usuarios/" + usuarioInstitucionId + "/editar")
                .with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("data-nombre-propio"))));
    }

    // ========================================================================
    //  Academico: ver materias, ver comisiones, grilla por anio
    // ========================================================================

    @Test
    @DisplayName("Las materias de una carrera se ven agrupadas por año")
    void materiasDeLaCarrera() throws Exception {
        mockMvc.perform(get("/carreras/" + carreraId + "/materias")
                .with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Materia de prueba")))
            .andExpect(content().string(containsString("2° año")));
    }

    @Test
    @DisplayName("Las comisiones de una materia se ven con sus horarios")
    void comisionesDeLaMateria() throws Exception {
        mockMvc.perform(get("/materias/" + materiaId + "/comisiones")
                .with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Comisión")))
            .andExpect(content().string(containsString("18:30")))
            .andExpect(content().string(containsString("Lunes")));
    }

    @Test
    @DisplayName("Cada comisión muestra su año y su período, en la materia y al cargar un horario")
    void comisionConAnioYPeriodo() throws Exception {
        String materia = mockMvc.perform(get("/materias/" + materiaId + "/comisiones")
                .with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(materia)
            .as("dos comisiones con el mismo código en períodos distintos se veían iguales")
            .containsPattern("class=\"badge badge--neutral\">\\d{4} · [^<]+</span>");

        String horario = mockMvc.perform(get("/horarios/nuevo").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(horario)
            .as("el desplegable de comisión también: si no, se carga la franja en la que no es")
            .containsPattern("— Comisión A · [^<]+ \\d{4}\\s*</option>");
    }

    @Test
    @DisplayName("El listado de materias ya no muestra la última actualización")
    void materiasSinUltimaActualizacion() throws Exception {
        mockMvc.perform(get("/materias").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("Última actualización"))))
            .andExpect(content().string(containsString("Ver comisiones")));
    }

    @Test
    @DisplayName("El listado de carreras tampoco muestra la última actualización")
    void carrerasSinUltimaActualizacion() throws Exception {
        mockMvc.perform(get("/carreras").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("Última actualización"))))
            .andExpect(content().string(containsString("Ver materias")));
    }

    @Test
    @DisplayName("La grilla muestra un año por vez, arranca en 1° y no ofrece \"Todos\"")
    void grillaFiltraPorAnio() throws Exception {
        // Sin año elegido arranca en 1°: la materia de 2do no aparece, y lo dice sin
        // sugerir que la carrera esté vacía.
        String html = mockMvc.perform(get("/grilla").param("carreraId", carreraId.toString())
                .with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"anio\"")))
            .andExpect(content().string(not(containsString("MCOR"))))
            .andExpect(content().string(containsString("no tiene horarios")))
            .andReturn().getResponse().getContentAsString();
        int selector = html.indexOf("id=\"anio\"");
        assertThat(html.substring(selector, html.indexOf("</select>", selector)))
            .as("con todos los años juntos las cohortes se pisan en las mismas franjas")
            .doesNotContain("Todos");

        // Con 2do elegido, aparece.
        mockMvc.perform(get("/grilla")
                .param("carreraId", carreraId.toString()).param("anio", "2")
                .with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("MCOR")));
    }

    // Principal apuntando a una cuenta que existe en la base. Lo necesitan las pantallas
    // que releen el usuario, como Mi cuenta.
    private UsuarioAutenticado principalReal(Long usuarioId, String rol) {
        Rol r = new Rol();
        r.setId((short) 1);
        r.setCodigo(rol);
        r.setDescripcion(rol);

        Usuario u = Usuario.builder().persona(DatosDePrueba.persona("Real", "Cuenta")).id(usuarioId).username("real").passwordHash("no-se-usa").activo(true).rol(r).emailVerificadoEn(LocalDateTime.now()).build();
        u.setInstitucionId(tenantId);
        return new UsuarioAutenticado(u);
    }

    // Principal de la aplicacion: el TenantInterceptor lo necesita para publicar el tenant.
    private UsuarioAutenticado principal(String rol) {
        Rol r = new Rol();
        r.setId((short) 1);
        r.setCodigo(rol);
        r.setDescripcion(rol);

        Usuario u = Usuario.builder().persona(DatosDePrueba.persona("Test", "Correcciones")).id(9999L).username("test.correcciones").passwordHash("no-se-usa").activo(true).rol(r).emailVerificadoEn(LocalDateTime.now()).build();
        u.setInstitucionId(tenantId);
        return new UsuarioAutenticado(u);
    }

    // ------------------------------------------------------------------------
    //  Ciclo lectivo para los datos de prueba (V023)
    // ------------------------------------------------------------------------
    //  La comision tiene periodo_id NOT NULL desde V023, asi que persistir una exige antes un
    //  ciclo con al menos un periodo. Se crea uno anual por tenant, reutilizado por todo el
    //  test: lo que estos casos prueban no es el calendario.
    //  Se busca antes de crear, y no se cachea en un campo: las filas sobreviven de un metodo
    //  al siguiente pero la instancia del test no, asi que un cache por instancia intentaba
    //  crear el mismo ciclo otra vez y chocaba contra uq_ciclos_inst_anio.
    private PeriodoLectivo periodoDe(Long tenantId) {
        return periodoLectivoRepository.seleccionablesDelTenant(tenantId).stream()
            .findFirst()
            .orElseGet(() -> cicloLectivoRepository
                .save(DatosDePrueba.cicloAnualDelTenant(
                    tenantId, java.time.LocalDate.now().getYear()))
                .getPeriodos().get(0));
    }
}
