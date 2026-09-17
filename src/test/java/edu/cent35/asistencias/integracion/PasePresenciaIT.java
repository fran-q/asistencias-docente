package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.ConsentimientoBiometrico;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.MetodoConsentimiento;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.BloquePresenciaRepository;
import edu.cent35.asistencias.repository.CarreraRepository;
import edu.cent35.asistencias.repository.CicloLectivoRepository;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.ConsentimientoBiometricoRepository;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.MateriaRepository;
import edu.cent35.asistencias.repository.PeriodoLectivoRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.service.BloquePresenciaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El ciclo de marcas de un docente frente a la cámara, de punta a punta (RF-74 a RF-82).
 *
 * <p><b>Qué cuida.</b> Que una pasada de más no invente un registro: ni dos entradas, ni dos
 * salidas, ni dos asistencias de la misma clase. Es lo que más fácil ocurre en la realidad
 * —el docente se queda parado frente a la cámara, o vuelve a pasar porque no vio el cartel— y
 * lo que peor se detecta después, porque un registro duplicado no se distingue de uno real
 * mirando la tabla.
 *
 * <p><b>Por qué va como IT y no como unitario.</b> Lo que decide que no haya duplicados está
 * repartido entre el servicio, el estado que queda en la base y los índices únicos. Con los
 * repositorios mockeados, un test pasa igual aunque la fila se duplique.
 */
@SpringBootTest
@ActiveProfiles("test")
class PasePresenciaIT {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired private BloquePresenciaService bloqueService;

    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private CarreraRepository carreraRepository;
    @Autowired private MateriaRepository materiaRepository;
    @Autowired private ComisionRepository comisionRepository;
    @Autowired private HorarioRepository horarioRepository;
    @Autowired private DocenteRepository docenteRepository;
    @Autowired private CicloLectivoRepository cicloRepository;
    @Autowired private PeriodoLectivoRepository periodoRepository;
    @Autowired private AsistenciaRepository asistenciaRepository;
    @Autowired private BloquePresenciaRepository bloqueRepository;
    @Autowired private ConsentimientoBiometricoRepository consentimientoRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Long tenantId;
    private Docente docente;
    private Comision comision;

    @BeforeEach
    void sembrar() {
        Institucion inst = institucionRepository.save(Institucion.builder()
            .nombre("Instituto del pase " + SECUENCIA.incrementAndGet())
            .activo(true).build());
        tenantId = inst.getId();
        TenantContext.set(tenantId);

        Carrera carrera = Carrera.builder()
            .codigo("PAS-" + tenantId).nombre("Tecnicatura").duracionAnios((short) 3)
            .activo(true).build();
        carrera.setInstitucionId(tenantId);
        carrera = carreraRepository.save(carrera);

        Materia materia = Materia.builder()
            .codigo("PAS1-" + tenantId).nombre("Programación I").carrera(carrera)
            .anio((short) 1).activo(true).build();
        materia.setInstitucionId(tenantId);
        materia = materiaRepository.save(materia);

        docente = Docente.builder()
            .persona(DatosDePrueba.personaDelTenant(tenantId, "41" + tenantId, "Ana", "Pérez"))
            .fechaAlta(LocalDate.of(2020, 1, 1)).activo(true).build();
        docente.setInstitucionId(tenantId);
        docente = docenteRepository.save(docente);

        // El ciclo tiene que contener HOY: el pase solo encuentra clases dentro de un ciclo
        // activo, y la fecha de la marca es la del día en que corre el test.
        CicloLectivo ciclo = cicloRepository.save(
            DatosDePrueba.cicloAnualDelTenant(tenantId, LocalDate.now().getYear()));

        comision = comisionRepository.save(Comision.builder()
            .materia(materia).codigo("A").docenteAsignado(docente).activo(true)
            .periodo(ciclo.getPeriodos().get(0))
            .build());

        // La clase es hoy, de 18 a 20: así la marca de las 18:05 cae siempre en ventana,
        // corra el test el día que corra.
        horarioRepository.save(Horario.builder()
            .comision(comision)
            .diaSemana((byte) LocalDate.now().getDayOfWeek().getValue())
            .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(20, 0))
            .toleranciaMin((short) 15).activo(true)
            .build());

        conConsentimientoVigente();
    }

    @AfterEach
    void limpiar() {
        if (tenantId != null) {
            borrar(asistenciaRepository, a -> tenantId.equals(a.getInstitucionId()));
            borrar(bloqueRepository, b -> tenantId.equals(b.getInstitucionId()));
            borrar(consentimientoRepository,
                   c -> docente != null && docente.getId().equals(c.getDocente().getId()));
            List<Long> comisionIds = comisionRepository.findAllDelTenant(tenantId).stream()
                .map(Comision::getId).toList();
            borrar(horarioRepository, h -> comisionIds.contains(h.getComision().getId()));
            borrar(comisionRepository, c -> comisionIds.contains(c.getId()));
            borrar(periodoRepository, p -> tenantId.equals(p.getInstitucionId()));
            borrar(cicloRepository, c -> tenantId.equals(c.getInstitucionId()));
            borrar(materiaRepository, m -> tenantId.equals(m.getInstitucionId()));
            borrar(carreraRepository, c -> tenantId.equals(c.getInstitucionId()));
            borrar(docenteRepository, d -> tenantId.equals(d.getInstitucionId()));
            borrar(usuarioRepository, u -> tenantId.equals(u.getInstitucionId()));
        }
        TenantContext.clear();
    }

    // ========================================================================
    //  Una pasada de mas no inventa un registro
    // ========================================================================

    @Test
    @DisplayName("Quedarse frente a la camara despues de entrar no abre otra jornada ni marca de nuevo")
    void laSegundaPasadaNoDuplicaLaEntrada() {
        LocalDate hoy = LocalDate.now();

        BloquePresenciaService.ResultadoPresencia entrada =
            bloqueService.registrar(docente.getId(), null, 40.0, hoy.atTime(18, 5), null);

        assertThat(entrada.tipo()).isEqualTo(BloquePresenciaService.TipoDeMarca.ENTRADA);
        assertThat(bloquesDelTenant()).hasSize(1);
        assertThat(asistenciasDe(hoy)).hasSize(1);

        // Dos minutos despues vuelve a quedar frente a la camara.
        BloquePresenciaService.ResultadoPresencia repetida =
            bloqueService.registrar(docente.getId(), null, 40.0, hoy.atTime(18, 7), null);

        assertThat(repetida.tipo())
            .as("con la jornada abierta, la pasada siguiente es una salida, y todavia no "
                + "corresponde")
            .isEqualTo(BloquePresenciaService.TipoDeMarca.RECHAZADA);
        assertThat(repetida.motivo())
            .as("el docente tiene que entender que le falta tiempo, no que fallo el sistema")
            .contains("10 minutos");
        assertThat(bloquesDelTenant())
            .as("sigue habiendo una sola jornada")
            .hasSize(1);
        assertThat(asistenciasDe(hoy))
            .as("y una sola asistencia de esa clase")
            .hasSize(1);
    }

    @Test
    @DisplayName("Pasar de nuevo despues de la salida no abre otra jornada ni marca de nuevo")
    void laPasadaDespuesDeLaSalidaNoDuplica() {
        LocalDate hoy = LocalDate.now();

        bloqueService.registrar(docente.getId(), null, 40.0, hoy.atTime(18, 5), null);
        BloquePresenciaService.ResultadoPresencia salida =
            bloqueService.registrar(docente.getId(), null, 40.0, hoy.atTime(19, 30), null);

        assertThat(salida.tipo()).isEqualTo(BloquePresenciaService.TipoDeMarca.SALIDA);
        assertThat(bloquesDelTenant()).hasSize(1);
        assertThat(asistenciasDe(hoy)).hasSize(1);

        // Dos minutos despues vuelve a pasar: ya registro su entrada y su salida de esta clase.
        BloquePresenciaService.ResultadoPresencia otraVez =
            bloqueService.registrar(docente.getId(), null, 40.0, hoy.atTime(19, 32), null);

        assertThat(otraVez.tipo())
            .as("no es una jornada nueva: es la misma clase, que ya quedo registrada")
            .isEqualTo(BloquePresenciaService.TipoDeMarca.RECHAZADA);
        assertThat(otraVez.motivo())
            .as("el mensaje tiene que decir que ya quedo registrada, y con que horas")
            .contains("ya quedaron registradas").contains("18:05").contains("19:30");
        assertThat(bloquesDelTenant())
            .as("una segunda jornada dejaria una salida pendiente y una entrada que nadie hizo")
            .hasSize(1);
        assertThat(asistenciasDe(hoy)).hasSize(1);
    }

    @Test
    @DisplayName("Irse antes y volver para la clase siguiente si abre una jornada nueva")
    void volverParaLaClaseSiguienteSiAbre() {
        // El limite de la regla de arriba: lo que se bloquea es repetir LA MISMA clase, no
        // volver a entrar. Sin esta distincion, el docente que se va y vuelve mas tarde se
        // quedaria sin poder marcar el resto del dia.
        horarioRepository.save(Horario.builder()
            .comision(comision)
            .diaSemana((byte) LocalDate.now().getDayOfWeek().getValue())
            .horaInicio(LocalTime.of(20, 0)).horaFin(LocalTime.of(22, 0))
            .toleranciaMin((short) 15).activo(true)
            .build());
        LocalDate hoy = LocalDate.now();

        bloqueService.registrar(docente.getId(), null, 40.0, hoy.atTime(18, 5), null);
        bloqueService.registrar(docente.getId(), null, 40.0, hoy.atTime(19, 30), null);

        BloquePresenciaService.ResultadoPresencia vuelta =
            bloqueService.registrar(docente.getId(), null, 40.0, hoy.atTime(20, 10), null);

        assertThat(vuelta.tipo())
            .as("la clase de las 20 no la cubrio ninguna jornada cerrada")
            .isEqualTo(BloquePresenciaService.TipoDeMarca.ENTRADA);
        assertThat(bloquesDelTenant()).hasSize(2);
        assertThat(asistenciasDe(hoy))
            .as("una asistencia por clase: la de las 18 y la de las 20")
            .hasSize(2);
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    private List<edu.cent35.asistencias.model.BloquePresencia> bloquesDelTenant() {
        return bloqueRepository.findAll().stream()
            .filter(b -> tenantId.equals(b.getInstitucionId()))
            .toList();
    }

    private List<edu.cent35.asistencias.model.Asistencia> asistenciasDe(LocalDate fecha) {
        return asistenciaRepository.findDelDia(tenantId, fecha);
    }

    // Sin consentimiento vigente la marca se rechaza antes de llegar a lo que se quiere probar
    // (RF-82). Que esa regla corra primero es deliberado, asi que el test se acomoda a ella.
    private void conConsentimientoVigente() {
        Rol rol = rolRepository.findByCodigo("INSTITUCION").orElseGet(() -> {
            Rol nuevo = new Rol();
            nuevo.setCodigo("INSTITUCION");
            nuevo.setDescripcion("Institucion");
            return rolRepository.save(nuevo);
        });

        Usuario registrante = Usuario.builder()
            .username("pase." + tenantId).email("pase." + tenantId + "@test.local")
            .passwordHash("no-se-usa").activo(true).rol(rol)
            .emailVerificadoEn(java.time.LocalDateTime.now())
            .build();
        registrante.setInstitucionId(tenantId);
        registrante = usuarioRepository.save(registrante);

        ConsentimientoBiometrico c = ConsentimientoBiometrico.builder()
            .docente(docente)
            .versionTerminos("v1")
            .metodo(MetodoConsentimiento.DIGITAL)
            .fechaConsentimiento(LocalDate.now().minusDays(1).atStartOfDay())
            .vigente(true)
            .registradoPor(registrante)
            .build();
        consentimientoRepository.save(c);
    }

    private <T> void borrar(org.springframework.data.jpa.repository.JpaRepository<T, ?> repo,
                            java.util.function.Predicate<T> condicion) {
        repo.deleteAll(repo.findAll().stream().filter(condicion).toList());
    }
}
