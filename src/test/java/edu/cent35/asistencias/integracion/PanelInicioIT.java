package edu.cent35.asistencias.integracion;

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
import edu.cent35.asistencias.model.EstadoCiclo;
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
    @Autowired private CicloLectivoRepository cicloLectivoRepository;
    @Autowired private PeriodoLectivoRepository periodoLectivoRepository;
    @Autowired private DocenteRepository docenteRepository;
    @Autowired private MateriaRepository materiaRepository;
    @Autowired private ComisionRepository comisionRepository;
    @Autowired private CarreraRepository carreraRepository;
    @Autowired private HorarioRepository horarioRepository;

    // El nombre de institucion es unico: sin un sufijo propio, el segundo test de la clase
    // choca contra la fila que dejo el primero.
    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    private Long tenantId;

    // La institucion ajena de los tests de aislamiento; null en los demas.
    private Long otroTenantId;

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
        horarioRepository.deleteAll();
        comisionRepository.deleteAll();
        materiaRepository.deleteAll();
        carreraRepository.deleteAll();
        docenteRepository.deleteAll();
        TenantContext.clear();
        institucionRepository.deleteById(tenantId);
        if (otroTenantId != null) {
            institucionRepository.deleteById(otroTenantId);
        }
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
    @DisplayName("Con la institución recién creada dice por qué no hay clases, sin romperse")
    void institucionVacia() throws Exception {
        String html = mockMvc.perform(get("/").with(user(principal())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html)
            // Sin clases en curso NI por venir, el bloque ya no dice solo "no hay clases":
            // dice por que, que es lo que hace falta para resolverlo.
            .contains("No hay ningún ciclo lectivo activo")
            .contains("Hoy no hay clases programadas")
            .as("sin un equipo autorizado no hay pase posible")
            .contains("Ningún equipo está autorizado para tomar asistencia")
            .as("el administrativo no abre Ciclos lectivos: se le dice quien lo resuelve, "
                + "en vez de un enlace a un acceso denegado")
            .contains("Lo resuelve la cuenta de la institución.")
            .doesNotContain("Ir a Ciclos lectivos");
    }

    @Test
    @DisplayName("Un ciclo del año en preparación se avisa, y a la institución la lleva a activarlo")
    void cicloEnPreparacionLlevaAActivarlo() throws Exception {
        TenantContext.set(tenantId);
        CicloLectivo ciclo = DatosDePrueba.cicloAnualDelTenant(tenantId, LocalDate.now().getYear());
        ciclo.setEstado(EstadoCiclo.PREPARACION);
        cicloLectivoRepository.save(ciclo);
        TenantContext.clear();

        String html = mockMvc.perform(get("/").with(user(principalConRol("INSTITUCION"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html)
            .contains("El ciclo lectivo " + LocalDate.now().getYear() + " está en preparación")
            .contains("Ir a Ciclos lectivos")
            .doesNotContain("Lo resuelve la cuenta de la institución.");
    }

    @Test
    @DisplayName("Las clases de hoy sin docente explican el día vacío; las de otra institución no")
    void clasesDeHoySinDocente() throws Exception {
        // La institucion de la sesion, con su ciclo activo y nada mas.
        TenantContext.set(tenantId);
        periodoDe(tenantId);
        TenantContext.clear();

        // Otra con una clase de hoy sin docente: si la consulta perdiera el WHERE del tenant
        // en algun JOIN, se contaria en la de la sesion (TD-003).
        otroTenantId = institucionRepository.save(Institucion.builder()
            .nombre("Instituto ajeno " + SECUENCIA.incrementAndGet())
            .activo(true).build()).getId();
        TenantContext.set(otroTenantId);
        claseDeHoySinDocente(otroTenantId, "AJENA");
        TenantContext.clear();

        String sinLasPropias = mockMvc.perform(get("/").with(user(principal())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(sinLasPropias)
            .contains("no hay clases cargadas")
            .doesNotContain("sin docente asignado")
            .doesNotContain("AJENA");

        TenantContext.set(tenantId);
        claseDeHoySinDocente(tenantId, "PROPIA");
        TenantContext.clear();

        String conLasPropias = mockMvc.perform(get("/").with(user(principal())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(conLasPropias)
            .contains("Hoy hay 1 clase en comisiones sin docente asignado")
            .contains("Asignar docentes en Comisiones")
            .as("Requiere atención nombra la comision, no solo la cuenta")
            .contains("PROPIA");
    }

    @Test
    @DisplayName("Un docente activo sin consentimiento aparece en Requiere atención")
    void docenteSinConsentimientoAparece() throws Exception {
        TenantContext.set(tenantId);
        Docente d = Docente.builder().persona(DatosDePrueba.personaConDni("30111222", "Ana", "Pérez")).activo(true).fechaAlta(LocalDate.now()).build();
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
            .codigo("A").materia(m).docenteAsignado(null).activo(true)
            .periodo(periodoDe(tenantId)).build());
        TenantContext.clear();

        mockMvc.perform(get("/").with(user(principal())))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "comisiones sin docente asignado")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "comisiones sin horarios cargados")));
    }

    @Test
    @DisplayName("Con una clase en curso, el bloque 'Ahora mismo' se renderiza sin romperse")
    void claseEnCursoSeRenderiza() throws Exception {
        // Este test existe por un error concreto: el <li> de las clases en curso tenía
        // th:classappend="... ? 'home__clase--ok' : 'home__clase--falta'". Dos '__' en la
        // misma expresión hacen que Thymeleaf tome lo del medio como preprocesado y el
        // render falle con ParseException.
        //
        // Los demás tests de esta clase no lo veían porque Thymeleaf evalúa la expresión
        // recién cuando procesa el elemento: sin ninguna clase en curso, el <ul> que lo
        // contiene se descarta y la línea nunca se parsea. Es decir, la home explotaba
        // solamente en horario de clase, que es justo cuando alguien la usa.
        TenantContext.set(tenantId);

        Docente d = Docente.builder().persona(DatosDePrueba.personaConDni("30111222", "Ana", "Gómez")).fechaAlta(LocalDate.now()).activo(true).build();
        d.setInstitucionId(tenantId);
        docenteRepository.save(d);

        Carrera c = Carrera.builder().codigo("CAR-EC").nombre("Carrera").activo(true).build();
        c.setInstitucionId(tenantId);
        carreraRepository.save(c);

        Materia m = Materia.builder()
            .codigo("MAT-EC").nombre("Análisis Matemático").carrera(c).activo(true).build();
        m.setInstitucionId(tenantId);
        materiaRepository.save(m);

        Comision com = comisionRepository.save(Comision.builder()
            .codigo("N1").materia(m).docenteAsignado(d).activo(true)
            .periodo(periodoDe(tenantId)).build());

        horarioRepository.save(Horario.builder()
            .comision(com)
            .diaSemana((byte) LocalDate.now().getDayOfWeek().getValue())
            .horaInicio(inicioDeLaVentana())
            .horaFin(finDeLaVentana())
            // Sin tolerancia: con ella, un horario que arranca 00:0x restaría hacia el día
            // anterior —LocalTime da la vuelta— y la clase dejaría de estar "en curso".
            .toleranciaMin((short) 0)
            .activo(true).build());
        TenantContext.clear();

        mockMvc.perform(get("/").with(user(principal())))
            .andExpect(status().isOk())
            // Que la clase aparezca prueba que se entró por la rama de "en curso"; sin esto
            // el test seguiría pasando aunque un cambio futuro la dejara de mostrar.
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "Análisis Matemático")))
            // Y el modificador que armaba la expresión rota, tal cual tiene que salir.
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "home__clase--falta")));
    }

    // La ventana tiene que contener a "ahora" sin cruzar la medianoche: el CHECK de la tabla
    // exige hora_fin > hora_inicio, y una ventana centrada se daría vuelta a las 23:5x.
    private LocalTime inicioDeLaVentana() {
        LocalTime ahora = LocalTime.now().withSecond(0).withNano(0);
        return ahora.isBefore(LocalTime.of(0, 5)) ? LocalTime.MIN : ahora.minusMinutes(5);
    }

    private LocalTime finDeLaVentana() {
        LocalTime ahora = LocalTime.now().withSecond(0).withNano(0);
        return ahora.isAfter(LocalTime.of(23, 29)) ? LocalTime.of(23, 59) : ahora.plusMinutes(30);
    }

    // Principal de la aplicacion: el TenantInterceptor lo necesita para publicar el tenant.
    private UsuarioAutenticado principal() {
        return principalConRol("ADMIN");
    }

    private UsuarioAutenticado principalConRol(String codigo) {
        Rol r = new Rol();
        r.setId((short) 1);
        r.setCodigo(codigo);
        r.setDescripcion(codigo);

        Usuario u = Usuario.builder().persona(DatosDePrueba.persona("Test", "Inicio")).id(99L).username("test.inicio").passwordHash("no-se-usa").activo(true).rol(r).emailVerificadoEn(LocalDateTime.now()).build();
        u.setInstitucionId(tenantId);
        return new UsuarioAutenticado(u);
    }

    // Una comision sin docente con una clase hoy, en el ciclo activo de esa institucion.
    private void claseDeHoySinDocente(Long institucionId, String codigo) {
        Carrera c = Carrera.builder().codigo("CAR-" + codigo).nombre("Carrera").activo(true).build();
        c.setInstitucionId(institucionId);
        carreraRepository.save(c);
        Materia m = Materia.builder()
            .codigo("MAT-" + codigo).nombre("Materia").carrera(c).activo(true).build();
        m.setInstitucionId(institucionId);
        materiaRepository.save(m);
        Comision com = comisionRepository.save(Comision.builder()
            .codigo(codigo).materia(m).docenteAsignado(null).activo(true)
            .periodo(periodoDe(institucionId)).build());
        horarioRepository.save(Horario.builder()
            .comision(com)
            .diaSemana((byte) LocalDate.now().getDayOfWeek().getValue())
            .horaInicio(LocalTime.of(8, 0))
            .horaFin(LocalTime.of(9, 0))
            .toleranciaMin((short) 0)
            .activo(true).build());
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
