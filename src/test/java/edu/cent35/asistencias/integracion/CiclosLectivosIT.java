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
import edu.cent35.asistencias.repository.BloquePresenciaRepository;
import edu.cent35.asistencias.repository.DiaNoLaborableRepository;
import edu.cent35.asistencias.repository.PeriodoLectivoRepository;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.MateriaRepository;
import edu.cent35.asistencias.model.ConsentimientoBiometrico;
import edu.cent35.asistencias.model.MetodoConsentimiento;
import edu.cent35.asistencias.repository.ConsentimientoBiometricoRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.service.BloquePresenciaService;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
    @Autowired private BloquePresenciaService bloqueService;
    @Autowired private ConsentimientoBiometricoRepository consentimientoRepository;
    @Autowired private BloquePresenciaRepository bloqueRepository;
    @Autowired private DiaNoLaborableRepository diaRepository;
    @Autowired private PeriodoLectivoRepository periodoLectivoRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;

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
     * Borra todo lo que sembró este test, en orden de dependencias.
     *
     * <p><b>Por qué hace falta.</b> La base de H2 se comparte entre clases y no hay rollback:
     * cada método de acá crea una institución entera —carrera, materia, docente, ciclos,
     * comisiones, horarios— y lo que queda vivo hace fallar la limpieza de <i>otras</i> clases
     * por una FK que no tiene nada que ver con lo que estaban probando. Se limpia lo propio en
     * vez de dejarle el problema al siguiente.
     *
     * <p>El orden es el inverso al de creación y no es negociable: borrar una carrera antes que
     * sus materias es exactamente el error que esto evita.
     */
    @AfterEach
    void limpiar() {
        if (tenantId != null) {
            borrar(asistenciaRepository, a -> tenantId.equals(a.getInstitucionId()));
            borrar(bloqueRepository, b -> tenantId.equals(b.getInstitucionId()));
            borrar(consentimientoRepository,
                   c -> docente != null && docente.getId().equals(c.getDocente().getId()));
            // Solo getId() sobre los proxies lazy: Hibernate lo devuelve sin ir a la base.
            // Cualquier otro getter --getInstitucionId(), por ejemplo-- intenta inicializar el
            // proxy fuera de sesion y revienta con LazyInitializationException.
            List<Long> comisionIds = comisionRepository.findAllDelTenant(tenantId).stream()
                .map(Comision::getId).toList();
            borrar(horarioRepository, h -> comisionIds.contains(h.getComision().getId()));
            borrar(comisionRepository, c -> comisionIds.contains(c.getId()));
            borrar(periodoLectivoRepository, p -> tenantId.equals(p.getInstitucionId()));
            borrar(cicloRepository, c -> tenantId.equals(c.getInstitucionId()));
            borrar(diaRepository, d -> tenantId.equals(d.getInstitucionId()));
            borrar(materiaRepository, m -> tenantId.equals(m.getInstitucionId()));
            borrar(carreraRepository, c -> tenantId.equals(c.getInstitucionId()));
            borrar(docenteRepository, d -> tenantId.equals(d.getInstitucionId()));
            borrar(usuarioRepository, u -> tenantId.equals(u.getInstitucionId()));
        }
        TenantContext.clear();
    }

    // Borra de un repositorio las filas que cumplan la condicion. Se filtra en memoria porque
    // son fixtures de un solo test: la claridad vale mas que la consulta.
    private <T> void borrar(org.springframework.data.jpa.repository.JpaRepository<T, ?> repo,
                            java.util.function.Predicate<T> condicion) {
        repo.deleteAll(repo.findAll().stream().filter(condicion).toList());
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

    @Test
    @DisplayName("En un dia sin clases el pase tampoco abre bloque")
    void elPaseNoAbreEnDiaSinClases() {
        // La otra mitad del arreglo. Antes el job --correctamente-- no generaba ausencias pero
        // el pase SI marcaba PRESENTE, asi que el feriado quedaba con marcas de unos docentes
        // y nada de los demas. Y como la asistencia se guarda contra un horario, esa marca
        // afirmaba que se dicto una clase que la institucion habia cancelado.
        CicloLectivo ciclo = cicloCon(2026, "Anual");
        horarioEn(comisionEn(ciclo, "A"), (byte) 2, "18:00", "20:00");
        diaService.crear(MARTES_2026, "Feriado de prueba", null);
        conConsentimientoVigente();

        BloquePresenciaService.ResultadoPresencia r = bloqueService.registrar(
            docente.getId(), null, null, MARTES_2026.atTime(18, 5), null);

        assertThat(r.tipo())
            .as("un feriado no puede abrir jornada")
            .isEqualTo(BloquePresenciaService.TipoDeMarca.RECHAZADA);
        assertThat(r.motivo())
            .as("tiene que decir el motivo y adonde ir: la carga manual es la salida")
            .contains("Feriado de prueba");
        assertThat(asistenciaRepository.findDelDia(tenantId, MARTES_2026))
            .as("ni una marca ese dia, ni presencia ni ausencia")
            .isEmpty();
    }

    // ========================================================================
    //  Editar el calendario (V027)
    // ========================================================================

    @Test
    @DisplayName("Achicar un periodo no puede dejar afuera un dia que ya tiene asistencias")
    void achicarUnPeriodoNoDejaAsistenciasAfuera() {
        // El caso real: V023 armo el ciclo del ano entero y las clases empezaron en marzo. Hay
        // que poder recortarlo, pero no dejar una asistencia de abril fuera de su propio periodo.
        CicloLectivo ciclo = cicloCon(2026, "Anual");
        horarioEn(comisionEn(ciclo, "A"), (byte) 2, "18:00", "20:00");
        generador.generarParaInstitucion(tenantId, MARTES_2026, LocalTime.of(23, 0));
        Long periodoId = ciclo.getPeriodos().get(0).getId();

        assertThatThrownBy(() -> cicloService.editarPeriodo(ciclo.getId(), periodoId, "Anual",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 12, 15)))
            .isInstanceOf(IllegalArgumentException.class)
            .as("el mensaje tiene que decir que dia lo impide, o no hay forma de encontrarlo")
            .hasMessageContaining("07/04/2026");

        cicloService.editarPeriodo(ciclo.getId(), periodoId, "Anual",
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));

        PeriodoLectivo recortado = periodoLectivoRepository.findById(periodoId).get();
        assertThat(recortado.getFechaInicio()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(recortado.getFechaFin()).isEqualTo(LocalDate.of(2026, 12, 15));
    }

    @Test
    @DisplayName("El ciclo se achica despues de sus periodos, y deja de generar ausencias en diciembre")
    void achicarElCiclo() {
        CicloLectivo ciclo = cicloCon(2026, "Anual");
        horarioEn(comisionEn(ciclo, "A"), (byte) 2, "18:00", "20:00");
        Long periodoId = ciclo.getPeriodos().get(0).getId();
        LocalDate marzo = LocalDate.of(2026, 3, 1);
        LocalDate diciembre = LocalDate.of(2026, 12, 15);

        assertThatThrownBy(() -> cicloService.actualizar(ciclo.getId(), null, marzo, diciembre))
            .as("un periodo que empieza antes que su ciclo es una contradiccion")
            .hasMessageContaining("quedaría fuera del ciclo");

        cicloService.editarPeriodo(ciclo.getId(), periodoId, "Anual", marzo, diciembre);
        cicloService.actualizar(ciclo.getId(), null, marzo, diciembre);

        CicloLectivo guardado = cicloRepository.findById(ciclo.getId()).get();
        assertThat(guardado.getFechaInicio()).isEqualTo(marzo);
        assertThat(guardado.getFechaFin()).isEqualTo(diciembre);
        assertThat(guardado.getAnio())
            .as("sin ano en el pedido queda el que estaba: la pantalla no lo manda si no se edita")
            .isEqualTo((short) 2026);

        LocalDate martesVeintidos = LocalDate.of(2026, 12, 22);
        assertThat(martesVeintidos.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(generador.generarParaInstitucion(tenantId, martesVeintidos, LocalTime.of(23, 0)))
            .as("es para lo que hacia falta: terminadas las clases, no hay ausencias que generar")
            .isZero();
    }

    @Test
    @DisplayName("El ano se corrige solo en preparacion, y sin pisar otro")
    void elAnioSoloEnPreparacion() {
        CicloLectivo activo = cicloCon(2026, "Anual");
        assertThatThrownBy(() -> cicloService.actualizar(activo.getId(), (short) 2025,
                activo.getFechaInicio(), activo.getFechaFin()))
            .as("un ciclo que ya corrio tiene asistencias de ese ano")
            .hasMessageContaining("preparación");

        // Un tipeo al crear el del ano que viene: queria 2027 y puso 2028.
        CicloLectivo mal = cicloCon(2028, "Anual");
        enPreparacion(mal);
        cicloService.actualizar(mal.getId(), (short) 2027, mal.getFechaInicio(), mal.getFechaFin());
        assertThat(cicloRepository.findById(mal.getId()).get().getAnio()).isEqualTo((short) 2027);

        assertThatThrownBy(() -> cicloService.actualizar(mal.getId(), (short) 2026,
                mal.getFechaInicio(), mal.getFechaFin()))
            .hasMessageContaining("Ya existe un ciclo lectivo 2026");
    }

    @Test
    @DisplayName("Un periodo se agrega al final, con las mismas reglas que en el alta")
    void agregarPeriodo() {
        CicloLectivo ciclo = cicloCon(2026, "Anual");

        cicloService.agregarPeriodo(ciclo.getId(), "1er cuatrimestre",
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 15));

        List<PeriodoLectivo> periodos = cicloService.buscarPorId(ciclo.getId()).getPeriodos();
        assertThat(periodos).extracting(PeriodoLectivo::getNombre)
            .containsExactly("Anual", "1er cuatrimestre");
        assertThat(periodos.get(1).getOrden())
            .as("va al final: el orden es el de carga")
            .isEqualTo((short) 2);

        assertThatThrownBy(() -> cicloService.agregarPeriodo(ciclo.getId(), "anual",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 15)))
            .as("el nombre empareja los anos al copiar: con otras mayusculas sigue siendo el mismo")
            .hasMessageContaining("Ya hay un período");
        assertThatThrownBy(() -> cicloService.agregarPeriodo(ciclo.getId(), "Verano",
                LocalDate.of(2027, 1, 5), LocalDate.of(2027, 2, 20)))
            .hasMessageContaining("dentro del ciclo");
    }

    @Test
    @DisplayName("Se quita un periodo vacio; uno con comisiones, no")
    void quitarPeriodo() {
        CicloLectivo ciclo = cicloCon(2026, "Anual");
        comisionEn(ciclo, "A");                                   // cuelga del "Anual"
        cicloService.agregarPeriodo(ciclo.getId(), "Sobrante",
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 15));
        Long anual = ciclo.getPeriodos().get(0).getId();
        Long sobrante = cicloService.buscarPorId(ciclo.getId()).getPeriodos().get(1).getId();

        assertThatThrownBy(() -> cicloService.quitarPeriodo(ciclo.getId(), anual))
            .as("una comision apunta a el: es historia")
            .hasMessageContaining("comisión");

        cicloService.quitarPeriodo(ciclo.getId(), sobrante);
        assertThat(periodoLectivoRepository.findById(sobrante))
            .as("borrado de verdad: nada apuntaba a el, y una baja logica lo dejaria en el combo")
            .isEmpty();
    }

    @Test
    @DisplayName("El unico periodo no se quita, aunque este vacio")
    void elUnicoPeriodoNoSeQuita() {
        CicloLectivo ciclo = cicloCon(2026, "Anual");

        assertThatThrownBy(() -> cicloService.quitarPeriodo(ciclo.getId(),
                ciclo.getPeriodos().get(0).getId()))
            .as("un ciclo sin periodos no admite comisiones")
            .hasMessageContaining("único período");
    }

    @Test
    @DisplayName("Un ciclo en preparacion y vacio se borra; activo o con oferta, no")
    void borrarCiclo() {
        CicloLectivo activo = cicloCon(2026, "Anual");
        assertThatThrownBy(() -> cicloService.borrar(activo.getId()))
            .as("un ciclo que corrio es historia de la institucion")
            .hasMessageContaining("preparación");

        CicloLectivo conOferta = cicloCon(2027, "Anual");
        enPreparacion(conOferta);
        comisionEn(conOferta, "A");
        assertThatThrownBy(() -> cicloService.borrar(conOferta.getId()))
            .hasMessageContaining("comisión");

        CicloLectivo vacio = cicloCon(2028, "Anual");
        Long suPeriodo = vacio.getPeriodos().get(0).getId();
        enPreparacion(vacio);
        cicloService.borrar(vacio.getId());

        assertThat(cicloRepository.findById(vacio.getId())).isEmpty();
        assertThat(periodoLectivoRepository.findById(suPeriodo))
            .as("sus periodos se van con el")
            .isEmpty();
    }

    @Test
    @DisplayName("En un ciclo cerrado no se tocan ni las fechas ni los periodos")
    void elCicloCerradoNoSeEdita() {
        CicloLectivo ciclo = cicloCon(2026, "Anual");
        cicloService.cerrar(ciclo.getId(), null);
        Long periodoId = ciclo.getPeriodos().get(0).getId();
        LocalDate marzo = LocalDate.of(2026, 3, 1);
        LocalDate diciembre = LocalDate.of(2026, 12, 15);

        assertThatThrownBy(() -> cicloService.actualizar(ciclo.getId(), null,
                LocalDate.of(2026, 1, 1), diciembre))
            .hasMessageContaining("cerrado");
        assertThatThrownBy(() -> cicloService.editarPeriodo(ciclo.getId(), periodoId, "Anual",
                marzo, diciembre))
            .hasMessageContaining("cerrado");
        assertThatThrownBy(() -> cicloService.agregarPeriodo(ciclo.getId(), "Extra", marzo, diciembre))
            .hasMessageContaining("cerrado");
        assertThatThrownBy(() -> cicloService.quitarPeriodo(ciclo.getId(), periodoId))
            .hasMessageContaining("cerrado");
    }

    // ========================================================================
    //  Reabrir un ciclo cerrado (V027)
    // ========================================================================

    @Test
    @DisplayName("Reabrir: el ultimo cerrado vuelve a preparacion y el cierre queda registrado")
    void reabrirElUltimoCerrado() throws Exception {
        CicloLectivo ciclo = cicloCon(2026, "Anual");
        cicloService.cerrar(ciclo.getId(), null);

        mockMvc.perform(post("/ciclos/" + ciclo.getId() + "/reabrir")
                .with(user(principalInstitucional())).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("flashMensaje"));

        CicloLectivo reabierto = cicloRepository.findById(ciclo.getId()).get();
        assertThat(reabierto.getEstado())
            .as("a preparacion y no a activo: activarlo es una decision aparte, con su propia "
                + "confirmacion")
            .isEqualTo(EstadoCiclo.PREPARACION);
        assertThat(reabierto.getCerradoEn())
            .as("el cierre no se borra: si fue un error, es lo que se va a querer mirar")
            .isNotNull();
        assertThat(reabierto.getReabiertoEn()).isNotNull();
        assertThat(reabierto.getReabiertoPor())
            .as("queda quien lo reabrio")
            .isEqualTo(principalInstitucional().getUsuarioId());

        // Y desde preparacion se activa como siempre. La peticion de arriba limpia el tenant del
        // hilo al terminar, asi que se vuelve a fijar antes de llamar al servicio.
        TenantContext.set(tenantId);
        cicloService.activar(ciclo.getId());
        assertThat(cicloRepository.findById(ciclo.getId()).get().getEstado())
            .isEqualTo(EstadoCiclo.ACTIVO);
    }

    @Test
    @DisplayName("Reabrir no: ni con otro ciclo activo, ni uno que no es el ultimo cerrado")
    void reabrirTieneLimites() {
        CicloLectivo viejo = cicloCon(2025, "Anual");
        cicloService.cerrar(viejo.getId(), null);
        CicloLectivo reciente = cicloCon(2026, "Anual");
        cicloService.cerrar(reciente.getId(), null);
        // Fechas explicitas: dos cierres seguidos pueden caer en el mismo instante, y lo que se
        // prueba es cual se cerro ultimo, no el desempate.
        fijarCierre(viejo.getId(), LocalDateTime.of(2025, 12, 20, 10, 0));
        fijarCierre(reciente.getId(), LocalDateTime.of(2026, 12, 20, 10, 0));

        assertThatThrownBy(() -> cicloService.reabrir(viejo.getId(), null))
            .as("reabrir un ano de hace tiempo no es corregir un error")
            .hasMessageContaining("último ciclo que se cerró");

        cicloCon(2027, "Anual");                                  // nace ACTIVO
        assertThatThrownBy(() -> cicloService.reabrir(reciente.getId(), null))
            .as("dejaria editable la oferta de un ano terminado mientras corre el siguiente")
            .hasMessageContaining("otro ciclo activo");
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
        assertThat(html)
            .as("el script del boton de agregar periodo tiene que llegar a la pagina: estuvo "
                + "afuera de la section y el layout lo descartaba sin avisar")
            .contains("getElementById('agregar-periodo')");
    }

    @Test
    @DisplayName("La pantalla de dias sin clase renderiza y aclara que apaga tambien el pase")
    void laPantallaDeDiasRenderiza() throws Exception {
        diaService.crear(MARTES_2026, "Feriado de prueba", null);

        String html = mockMvc.perform(
                get("/dias-sin-clase").param("anio", "2026").with(user(principalInstitucional())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html)
            .contains("Feriado de prueba")
            .as("alguien podria cargar un feriado sin darse cuenta de que ademas apaga el "
                + "pase: la pantalla tiene que decirlo, y decir cual es la salida")
            .contains("el pase tampoco")
            .contains("a mano");
    }

    @Test
    @DisplayName("El detalle del ciclo renderiza con sus periodos, sus comisiones y la edicion")
    void elDetalleRenderiza() throws Exception {
        CicloLectivo ciclo = cicloCon(2026, "Anual");
        comisionEn(ciclo, "A");
        diaService.crear(MARTES_2026, "Feriado de prueba", null);

        String html = mockMvc.perform(get("/ciclos/" + ciclo.getId())
                .with(user(principalInstitucional())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html)
            .contains("Ciclo lectivo 2026")
            .contains("1 comisión")
            .as("un ciclo abierto se corrige desde aca")
            .contains("/ciclos/" + ciclo.getId() + "/datos")
            .as("los dias sin clase que caen dentro del ciclo")
            .contains("Feriado de prueba")
            .as("el detalle enlaza a Comisiones ya filtrada por este ano")
            .contains("/comisiones?ciclo=" + ciclo.getId());
        assertThat(html)
            .as("con una comision colgando, el periodo no ofrece quitarse")
            .doesNotContain("/quitar");
    }

    @Test
    @DisplayName("El detalle de un ciclo cerrado es de solo lectura y ofrece reabrirlo")
    void elDetalleDeUnCicloCerrado() throws Exception {
        CicloLectivo ciclo = cicloCon(2026, "Anual");
        cicloService.cerrar(ciclo.getId(), null);

        String html = mockMvc.perform(get("/ciclos/" + ciclo.getId())
                .with(user(principalInstitucional())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html)
            .as("cerrado, no se ofrece editar nada: el servicio lo rechazaria igual")
            .doesNotContain("/ciclos/" + ciclo.getId() + "/datos")
            .doesNotContain("/ciclos/" + ciclo.getId() + "/periodos");
        assertThat(html)
            .as("es el ultimo cerrado y no hay otro activo: se puede reabrir")
            .contains("/ciclos/" + ciclo.getId() + "/reabrir");
    }

    @Test
    @DisplayName("Un periodo mal cargado vuelve al detalle con el error a la vista, no en un aviso que se va")
    void elErrorDeUnPeriodoQuedaEnLaPantalla() throws Exception {
        CicloLectivo ciclo = cicloCon(2026, "Anual");
        Long periodoId = ciclo.getPeriodos().get(0).getId();

        mockMvc.perform(post("/ciclos/" + ciclo.getId() + "/periodos/" + periodoId)
                .with(user(principalInstitucional())).with(csrf())
                .param("nombre", "Anual")
                .param("fechaInicio", "2026-07-01")
                .param("fechaFin", "2026-03-01"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/ciclos/" + ciclo.getId()))
            .andExpect(flash().attribute("error",
                org.hamcrest.Matchers.containsString("anterior a la de inicio")));
    }

    @Test
    @DisplayName("Comisiones filtra por ano: el enlace del detalle llega ya filtrado")
    void comisionesFiltraPorCiclo() throws Exception {
        CicloLectivo dosMilVeintiseis = cicloCon(2026, "Anual");
        CicloLectivo dosMilVeintisiete = cicloCon(2027, "Anual");
        enPreparacion(dosMilVeintisiete);
        comisionEn(dosMilVeintiseis, "QZ-26");
        comisionEn(dosMilVeintisiete, "QZ-27");

        String html = mockMvc.perform(get("/comisiones")
                .param("ciclo", dosMilVeintisiete.getId().toString())
                .with(user(principalInstitucional())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html)
            .contains("QZ-27")
            .as("la de 2026 no es de este ano")
            .doesNotContain("QZ-26");
    }

    @Test
    @DisplayName("Copiar la oferta arranca del ano anterior hacia el mas nuevo")
    void copiarArrancaDelAnioAnterior() throws Exception {
        CicloLectivo anterior = cicloCon(2026, "Anual");
        CicloLectivo nuevo = cicloCon(2027, "Anual");
        enPreparacion(nuevo);

        String html = mockMvc.perform(get("/ciclos").with(user(principalInstitucional())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html)
            .as("antes origen y destino arrancaban los dos en el mas nuevo, y apretar Copiar "
                + "sin tocar nada respondia 'el origen y el destino son el mismo'")
            .containsPattern("<option value=\"" + anterior.getId()
                             + "\"\\s+selected=\"selected\">2026</option>")
            .contains("/ciclos/" + nuevo.getId() + "/copiar-desde");
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

    // Pasa a preparacion un ciclo de los helpers, que nacen ACTIVO. No devuelve lo que guardo a
    // proposito: es una copia desprendida, y sus periodos no se pueden leer fuera de la sesion.
    // Se sigue usando la instancia original, que es la que conoce sus periodos.
    private void enPreparacion(CicloLectivo ciclo) {
        ciclo.setEstado(EstadoCiclo.PREPARACION);
        cicloRepository.save(ciclo);
    }

    private void fijarCierre(Long cicloId, LocalDateTime cuando) {
        CicloLectivo c = cicloRepository.findById(cicloId).get();
        c.setCerradoEn(cuando);
        cicloRepository.save(c);
    }

    /**
     * Le da al docente de prueba un consentimiento biométrico vigente.
     *
     * <p>Sin esto el pase rebota en la regla dura —sin consentimiento no se usa un rostro— y
     * el test nunca llega a ejercitar lo que quiere probar. Que esa regla corra primero es
     * deliberado (RF-82, RNF-13), así que el test se acomoda a ella y no al revés.
     */
    private void conConsentimientoVigente() {
        Rol rol = new Rol();
        rol.setCodigo("INSTITUCION");
        rol.setDescripcion("Institucion");
        rol = rolRepository.save(rol);

        Usuario registrante = Usuario.builder()
            .username("consent." + tenantId).email("consent." + tenantId + "@x.com")
            .passwordHash("x").activo(true).rol(rol).build();
        registrante.setInstitucionId(tenantId);
        registrante = usuarioRepository.save(registrante);

        ConsentimientoBiometrico c = ConsentimientoBiometrico.builder()
            .docente(docente)
            .versionTerminos("v1")
            .metodo(MetodoConsentimiento.DIGITAL)
            .fechaConsentimiento(LocalDate.of(2026, 1, 1).atStartOfDay())
            .vigente(true)
            .registradoPor(registrante)
            .build();
        consentimientoRepository.save(c);
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
