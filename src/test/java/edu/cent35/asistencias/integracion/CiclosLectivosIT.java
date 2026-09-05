package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoCiclo;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.PeriodoLectivo;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.CarreraRepository;
import edu.cent35.asistencias.repository.CicloLectivoRepository;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.MateriaRepository;
import edu.cent35.asistencias.service.CicloLectivoService;
import edu.cent35.asistencias.service.DiaNoLaborableService;
import edu.cent35.asistencias.service.GeneradorAusenciasService;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los ciclos lectivos de punta a punta (V023, V024): el copiado de la oferta de un año al
 * siguiente y las dos cosas que dejan de generar ausencias falsas.
 *
 * <p><b>Por qué va como IT y no como test unitario.</b> Lo que hay que probar es que la oferta
 * de un año no se mezcle con la del otro, y eso vive en las consultas —en los JOIN al período y
 * al ciclo—, no en la lógica del servicio. Con repositorios mockeados los tests pasarían aunque
 * la query trajera todos los años juntos, que es exactamente el error que este cambio corrige.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CiclosLectivosIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private CarreraRepository carreraRepository;
    @Autowired private MateriaRepository materiaRepository;
    @Autowired private ComisionRepository comisionRepository;
    @Autowired private HorarioRepository horarioRepository;
    @Autowired private DocenteRepository docenteRepository;
    @Autowired private CicloLectivoRepository cicloRepository;
    @Autowired private AsistenciaRepository asistenciaRepository;

    @Autowired private CicloLectivoService cicloService;
    @Autowired private DiaNoLaborableService diaService;
    @Autowired private GeneradorAusenciasService generador;

    // Cada metodo trabaja sobre su propia institucion: asi el estado de uno no filtra al
    // siguiente y cada caso arranca con un calendario limpio.
    private static final java.util.concurrent.atomic.AtomicInteger SECUENCIA =
        new java.util.concurrent.atomic.AtomicInteger();

    private Long tenantId;
    private Materia materia;
    private Docente docente;

    // Un martes cualquiera de cada ano, para que el horario del dia 2 caiga siempre en fecha.
    private static final LocalDate MARTES_2026 = LocalDate.of(2026, 4, 7);
    private static final LocalDate MARTES_2027 = LocalDate.of(2027, 4, 6);

    @BeforeEach
    void sembrar() {
        // El nombre de institucion es unico y estos tests no se limpian entre metodos: sin el
        // sufijo, el segundo choca contra el UNIQUE antes de llegar a lo que quiere probar.
        Institucion inst = institucionRepository.save(
            Institucion.builder()
                .nombre("Instituto de ciclos " + SECUENCIA.incrementAndGet())
                .activo(true).build());
        tenantId = inst.getId();
        TenantContext.set(tenantId);

        Carrera carrera = Carrera.builder()
            .codigo("TSP-" + tenantId).nombre("Tecnicatura").duracionAnios((short) 3).activo(true).build();
        carrera.setInstitucionId(tenantId);
        carrera = carreraRepository.save(carrera);

        materia = Materia.builder()
            .codigo("MAT1-" + tenantId).nombre("Matemática I").carrera(carrera)
            .anio((short) 1).activo(true).build();
        materia.setInstitucionId(tenantId);
        materia = materiaRepository.save(materia);

        docente = Docente.builder()
            .persona(DatosDePrueba.personaDelTenant(tenantId, "30" + tenantId, "Ana", "Pérez"))
            .fechaAlta(LocalDate.of(2020, 1, 1)).activo(true).build();
        docente.setInstitucionId(tenantId);
        docente = docenteRepository.save(docente);
    }

    /**
     * Borra las ausencias que generó este test.
     *
     * <p>La base de H2 se comparte entre clases y no se hace rollback: las asistencias que
     * quedaran acá apuntan a horarios que otro test después intenta borrar, y ese otro test
     * falla por una FK que no tiene nada que ver con lo que estaba probando. Se limpia lo
     * propio en vez de dejarle el problema al siguiente.
     */
    @AfterEach
    void limpiar() {
        asistenciaRepository.deleteAll(
            asistenciaRepository.findAll().stream()
                .filter(a -> tenantId.equals(a.getInstitucionId()))
                .toList());
        TenantContext.clear();
    }

    // ========================================================================
    //  Copiar la oferta al ano siguiente
    // ========================================================================

    @Test
    @DisplayName("Copiar la oferta trae comisiones y horarios, y empareja los periodos por nombre")
    void copiarLaOfertaEmparejaPorNombre() {
        CicloLectivo dosMilVeintiseis = cicloCon(2026, "Anual");
        CicloLectivo dosMilVeintisiete = cicloCon(2027, "Anual");

        Comision original = comisionEn(dosMilVeintiseis, "A");
        horarioEn(original, (byte) 2, "18:00", "20:00");
        horarioEn(original, (byte) 4, "18:00", "20:00");

        CicloLectivoService.ResultadoCopia r =
            cicloService.copiarOferta(dosMilVeintiseis.getId(), dosMilVeintisiete.getId());

        assertThat(r.comisiones()).isEqualTo(1);
        assertThat(r.horarios()).isEqualTo(2);
        assertThat(r.hayPendientes()).isFalse();

        List<Comision> del2027 = comisionRepository.findDelCiclo(dosMilVeintisiete.getId(), tenantId);
        assertThat(del2027).hasSize(1);
        assertThat(del2027.get(0).getCodigo()).isEqualTo("A");
        assertThat(del2027.get(0).getDocenteAsignado().getId())
            .as("el docente asignado viaja con la comision: reasignarlo es la excepcion, no la regla")
            .isEqualTo(docente.getId());
        assertThat(del2027.get(0).getId())
            .as("tiene que ser una comision NUEVA, no la de 2026 mudada de ano")
            .isNotEqualTo(original.getId());
    }

    @Test
    @DisplayName("Copiar dos veces no duplica: se saltea lo que ya existe")
    void copiarDosVecesNoDuplica() {
        CicloLectivo origen  = cicloCon(2026, "Anual");
        CicloLectivo destino = cicloCon(2027, "Anual");
        horarioEn(comisionEn(origen, "A"), (byte) 2, "18:00", "20:00");

        cicloService.copiarOferta(origen.getId(), destino.getId());
        CicloLectivoService.ResultadoCopia segunda =
            cicloService.copiarOferta(origen.getId(), destino.getId());

        assertThat(segunda.comisiones())
            .as("la segunda corrida no tiene nada que copiar")
            .isZero();
        assertThat(comisionRepository.findDelCiclo(destino.getId(), tenantId)).hasSize(1);
    }

    @Test
    @DisplayName("Una comision cuyo periodo no existe en el destino se saltea y se avisa")
    void sinPeriodoEquivalenteSeAvisa() {
        // 2026 tiene cuatrimestres y 2027 arranca solo con "Anual": meter una materia
        // cuatrimestral en un periodo anual cambia lo que el sistema espera de ella todo el ano,
        // asi que se deja afuera y se dice cual.
        CicloLectivo origen  = cicloCon(2026, "1er cuatrimestre");
        CicloLectivo destino = cicloCon(2027, "Anual");
        comisionEn(origen, "A");

        CicloLectivoService.ResultadoCopia r =
            cicloService.copiarOferta(origen.getId(), destino.getId());

        assertThat(r.comisiones()).isZero();
        assertThat(r.hayPendientes()).isTrue();
        assertThat(r.sinPeriodoEquivalente())
            .as("el aviso tiene que decir cual quedo afuera, no solo cuantas")
            .singleElement().asString().contains("Matemática I").contains("1er cuatrimestre");
    }

    @Test
    @DisplayName("A un ciclo cerrado no se le copia oferta")
    void noSeCopiaHaciaUnCicloCerrado() {
        CicloLectivo origen  = cicloCon(2026, "Anual");
        CicloLectivo destino = cicloCon(2027, "Anual");
        cicloService.cerrar(destino.getId(), null);

        assertThatThrownBy(() -> cicloService.copiarOferta(origen.getId(), destino.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cerrado");
    }

    @Test
    @DisplayName("Dos ciclos activos a la vez no: el pase no sabria contra cual registrar")
    void soloUnCicloActivo() {
        cicloCon(2026, "Anual");                       // queda ACTIVO
        CicloLectivo otro = cicloCon(2027, "Anual");
        otro.setEstado(EstadoCiclo.PREPARACION);
        cicloRepository.save(otro);

        assertThatThrownBy(() -> cicloService.activar(otro.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Ya hay un ciclo activo");
    }

    // ========================================================================
    //  Lo que deja de generar ausencias falsas
    // ========================================================================

    @Test
    @DisplayName("Fuera del periodo no se generan ausencias, aunque el horario siga activo")
    void fueraDelPeriodoNoHayAusencias() {
        // El error que esto corrige: antes la consulta traia todo horario activo de ese dia de
        // la semana, sin mirar el calendario. Un cuatrimestre terminado seguia generando
        // ausencias, y en 2027 lo hubieran hecho los horarios de 2026.
        CicloLectivo ciclo = cicloConRango(2026, "1er cuatrimestre",
                                           LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 15));
        horarioEn(comisionEn(ciclo, "A"), (byte) 2, "18:00", "20:00");

        int enAbril = generador.generarParaInstitucion(tenantId, MARTES_2026, LocalTime.of(23, 0));
        assertThat(enAbril)
            .as("en abril el cuatrimestre corre: la ausencia se genera")
            .isEqualTo(1);

        LocalDate martesDeSeptiembre = LocalDate.of(2026, 9, 1);
        assertThat(martesDeSeptiembre.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        int enSeptiembre = generador.generarParaInstitucion(
            tenantId, martesDeSeptiembre, LocalTime.of(23, 0));

        assertThat(enSeptiembre)
            .as("el cuatrimestre termino en julio: no hay clase que faltar")
            .isZero();
    }

    @Test
    @DisplayName("Un dia marcado sin clases no genera ninguna ausencia")
    void elDiaSinClasesNoGeneraAusencias() {
        CicloLectivo ciclo = cicloCon(2026, "Anual");
        horarioEn(comisionEn(ciclo, "A"), (byte) 2, "18:00", "20:00");

        diaService.crear(MARTES_2026, "Feriado de prueba", null);
        int creadas = generador.generarParaInstitucion(tenantId, MARTES_2026, LocalTime.of(23, 0));

        assertThat(creadas)
            .as("una ausencia en un dia sin clases no es un dato incompleto: es falso")
            .isZero();
        assertThat(asistenciaRepository.findDelDia(tenantId, MARTES_2026)).isEmpty();
    }

    @Test
    @DisplayName("La oferta de 2027 no aparece cuando se generan las ausencias de 2026")
    void losAniosNoSeMezclan() {
        CicloLectivo dosMilVeintiseis = cicloCon(2026, "Anual");
        horarioEn(comisionEn(dosMilVeintiseis, "A"), (byte) 2, "18:00", "20:00");

        // 2027 en preparacion, con su propia oferta ya cargada: es el caso real de diciembre,
        // cuando se arma el ano que viene mientras el actual sigue corriendo.
        CicloLectivo dosMilVeintisiete = cicloCon(2027, "Anual");
        dosMilVeintisiete.setEstado(EstadoCiclo.PREPARACION);
        cicloRepository.save(dosMilVeintisiete);
        horarioEn(comisionEn(dosMilVeintisiete, "B"), (byte) 2, "18:00", "20:00");

        int creadas = generador.generarParaInstitucion(tenantId, MARTES_2026, LocalTime.of(23, 0));

        assertThat(creadas)
            .as("solo la clase de 2026: la de 2027 todavia no empezo")
            .isEqualTo(1);
    }

    // ========================================================================
    //  Las pantallas nuevas, renderizadas de verdad
    // ========================================================================

    // Las expresiones de Thymeleaf fallan recien al renderizar: una llamada mal escrita en la
    // plantilla compila igual y revienta en la cara del usuario. Estos dos casos piden la
    // pagina como la cuenta institucional y miran el HTML que sale.

    @Test
    @DisplayName("La pantalla de ciclos renderiza con sus periodos y sus acciones")
    void laPantallaDeCiclosRenderiza() throws Exception {
        cicloCon(2026, "1er cuatrimestre");

        String html = mockMvc.perform(get("/ciclos").with(user(principalInstitucional())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html)
            .contains("Ciclos lectivos")
            .contains("2026")
            .contains("1er cuatrimestre")
            .as("un ciclo activo tiene que poder cerrarse desde la pantalla")
            .contains("Cerrar");
    }

    @Test
    @DisplayName("La pantalla de dias sin clase renderiza y aclara que no bloquea la camara")
    void laPantallaDeDiasRenderiza() throws Exception {
        diaService.crear(MARTES_2026, "Feriado de prueba", null);

        String html = mockMvc.perform(
                get("/dias-sin-clase").param("anio", "2026").with(user(principalInstitucional())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html)
            .contains("Feriado de prueba")
            .as("alguien podria cargar un feriado esperando que la camara se apague: la "
                + "pantalla tiene que decir que no es asi")
            .contains("no impide tomar asistencia");
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    private UsuarioAutenticado principalInstitucional() {
        Rol rol = new Rol();
        rol.setId((short) 1);
        rol.setCodigo("INSTITUCION");
        rol.setDescripcion("Institucion");

        Usuario u = Usuario.builder()
            .id(9000L + tenantId).username("ciclos.test." + tenantId)
            .passwordHash("no-se-usa").activo(true).rol(rol)
            .emailVerificadoEn(java.time.LocalDateTime.now())
            .build();
        u.setInstitucionId(tenantId);
        return new UsuarioAutenticado(u);
    }

    // Ciclo ACTIVO del ano, con un unico periodo que lo cubre entero.
    private CicloLectivo cicloCon(int anio, String nombrePeriodo) {
        return cicloConRango(anio, nombrePeriodo,
                             LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31));
    }

    private CicloLectivo cicloConRango(int anio, String nombrePeriodo,
                                       LocalDate desde, LocalDate hasta) {
        CicloLectivo ciclo = CicloLectivo.builder()
            .anio((short) anio)
            .fechaInicio(LocalDate.of(anio, 1, 1))
            .fechaFin(LocalDate.of(anio, 12, 31))
            .estado(EstadoCiclo.ACTIVO)
            .build();
        ciclo.setInstitucionId(tenantId);
        ciclo.agregarPeriodo(PeriodoLectivo.builder()
            .nombre(nombrePeriodo).fechaInicio(desde).fechaFin(hasta).orden((short) 1).build());
        return cicloRepository.save(ciclo);
    }

    private Comision comisionEn(CicloLectivo ciclo, String codigo) {
        return comisionRepository.save(Comision.builder()
            .materia(materia).codigo(codigo).docenteAsignado(docente).activo(true)
            .periodo(ciclo.getPeriodos().get(0))
            .build());
    }

    private Horario horarioEn(Comision comision, byte dia, String desde, String hasta) {
        return horarioRepository.save(Horario.builder()
            .comision(comision).diaSemana(dia)
            .horaInicio(LocalTime.parse(desde)).horaFin(LocalTime.parse(hasta))
            .toleranciaMin((short) 15).activo(true)
            .build());
    }
}
