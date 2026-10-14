package edu.cent35.asistencias;

import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.Persona;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.BloquePresenciaRepository;
import edu.cent35.asistencias.repository.CarreraRepository;
import edu.cent35.asistencias.repository.CicloLectivoRepository;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.ConsentimientoBiometricoRepository;
import edu.cent35.asistencias.repository.DiaNoLaborableRepository;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.MateriaRepository;
import edu.cent35.asistencias.repository.PeriodoLectivoRepository;
import edu.cent35.asistencias.repository.PuestoCapturaRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static edu.cent35.asistencias.DatosDePrueba.borrar;

/**
 * Arma en la base el escenario de un test de integración y lo desarma al final.
 *
 * <p>Los tests de reportes y de marcas armaban la misma cadena --institución, cuenta,
 * carrera, materia, docente, ciclo, comisión, horario-- y la borraban en el mismo orden,
 * copiada en cada uno con otro prefijo. Acá quedan los valores que no hacen al test (activo,
 * año de la materia, tolerancia, fecha de alta) y el orden de borrado; lo que identifica al
 * escenario --códigos, DNI, horas-- lo sigue escribiendo cada test a la vista.
 *
 * <p>Vive en los tests y Spring lo encuentra solo: basta con {@code @Autowired}.
 */
@Component
@RequiredArgsConstructor
public class EscenarioDePrueba {

    // El nombre de la institución es único y los tests no la borran: sin un número que no se
    // repita, el segundo test de la corrida choca contra el UNIQUE.
    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    private final InstitucionRepository instituciones;
    private final RolRepository roles;
    private final UsuarioRepository usuarios;
    private final CarreraRepository carreras;
    private final MateriaRepository materias;
    private final DocenteRepository docentes;
    private final CicloLectivoRepository ciclos;
    private final PeriodoLectivoRepository periodos;
    private final ComisionRepository comisiones;
    private final HorarioRepository horarios;
    private final AsistenciaRepository asistencias;
    private final BloquePresenciaRepository bloques;
    private final ConsentimientoBiometricoRepository consentimientos;
    private final PuestoCapturaRepository puestos;
    private final DiaNoLaborableRepository diasSinClase;

    // ========================================================================
    //  Armado
    // ========================================================================

    // Una institución activa. Devuelve el id, que es lo que los tests usan como tenant.
    public Long institucion(String nombre) {
        return instituciones.save(Institucion.builder()
            .nombre(nombre + " " + SECUENCIA.incrementAndGet())
            .activo(true).build()).getId();
    }

    // Una cuenta con el correo ya verificado: sin eso VerificacionInterceptor la frena antes de
    // llegar a lo que el test quiere probar. El correo sale del usuario.
    public Usuario cuenta(Long tenantId, String codigoRol, String usuario) {
        return cuenta(tenantId, codigoRol, usuario, null);
    }

    // Igual, con una persona detrás: las cuentas ADMIN la tienen; la institucional no (V018).
    public Usuario cuenta(Long tenantId, String codigoRol, String usuario, Persona persona) {
        Usuario u = Usuario.builder()
            .persona(persona)
            .username(usuario).email(usuario + "@test.local")
            .passwordHash("no-se-usa").activo(true)
            .rol(DatosDePrueba.rol(roles, codigoRol))
            .emailVerificadoEn(LocalDateTime.now())
            .build();
        u.setInstitucionId(tenantId);
        return usuarios.save(u);
    }

    // Una tecnicatura de tres años.
    public Carrera carrera(Long tenantId, String codigo) {
        Carrera c = Carrera.builder()
            .codigo(codigo).nombre("Tecnicatura").duracionAnios((short) 3)
            .activo(true).build();
        c.setInstitucionId(tenantId);
        return carreras.save(c);
    }

    // Una materia de primer año, en el tenant de su carrera.
    public Materia materia(Carrera carrera, String codigo, String nombre) {
        Materia m = Materia.builder()
            .codigo(codigo).nombre(nombre).carrera(carrera)
            .anio((short) 1).activo(true).build();
        m.setInstitucionId(carrera.getInstitucionId());
        return materias.save(m);
    }

    // Una docente activa desde 2020: Ana Pérez, salvo que el test necesite otro nombre.
    public Docente docente(Long tenantId, String dni) {
        return docente(tenantId, dni, "Ana", "Pérez");
    }

    public Docente docente(Long tenantId, String dni, String nombre, String apellido) {
        Docente d = Docente.builder()
            .persona(DatosDePrueba.personaDelTenant(tenantId, dni, nombre, apellido))
            .fechaAlta(LocalDate.of(2020, 1, 1)).activo(true).build();
        d.setInstitucionId(tenantId);
        return docentes.save(d);
    }

    // El ciclo activo del año, con un período "Anual" que lo cubre (DatosDePrueba).
    public CicloLectivo ciclo(Long tenantId, int anio) {
        return ciclos.save(DatosDePrueba.cicloAnualDelTenant(tenantId, anio));
    }

    // Una comisión activa del primer período del ciclo, con la docente asignada.
    public Comision comision(Materia materia, CicloLectivo ciclo, Docente docente, String codigo) {
        return comisiones.save(Comision.builder()
            .materia(materia).codigo(codigo).docenteAsignado(docente).activo(true)
            .periodo(ciclo.getPeriodos().get(0))
            .build());
    }

    // Un horario activo con los 15 minutos de tolerancia de siempre.
    public Horario horario(Comision comision, DayOfWeek dia, LocalTime inicio, LocalTime fin) {
        return horarios.save(Horario.builder()
            .comision(comision)
            .diaSemana((byte) dia.getValue())
            .horaInicio(inicio).horaFin(fin)
            .toleranciaMin((short) 15).activo(true)
            .build());
    }

    // ========================================================================
    //  Desarmado
    // ========================================================================

    /**
     * Borra todo lo del tenant, en el orden que piden las claves foráneas: primero lo que
     * apunta a otra cosa. La institución y las personas quedan: no estorban a ningún test.
     *
     * <p>Los consentimientos y los horarios no tienen tenant propio y se buscan por su
     * docente y su comisión. Y solo por el id: cualquier otro getter de esos proxies lazy
     * intenta ir a la base fuera de sesión y revienta con LazyInitializationException.
     */
    public void borrarTodoDe(Long tenantId) {
        if (tenantId == null) return;
        List<Long> docenteIds = docentes.findAll().stream()
            .filter(d -> tenantId.equals(d.getInstitucionId())).map(Docente::getId).toList();
        List<Long> comisionIds = comisiones.findAllDelTenant(tenantId).stream()
            .map(Comision::getId).toList();

        borrar(asistencias, a -> tenantId.equals(a.getInstitucionId()));
        borrar(bloques, b -> tenantId.equals(b.getInstitucionId()));
        borrar(consentimientos, c -> docenteIds.contains(c.getDocente().getId()));
        borrar(puestos, p -> tenantId.equals(p.getInstitucionId()));
        borrar(horarios, h -> comisionIds.contains(h.getComision().getId()));
        borrar(comisiones, c -> comisionIds.contains(c.getId()));
        borrar(periodos, p -> tenantId.equals(p.getInstitucionId()));
        borrar(ciclos, c -> tenantId.equals(c.getInstitucionId()));
        borrar(diasSinClase, d -> tenantId.equals(d.getInstitucionId()));
        borrar(materias, m -> tenantId.equals(m.getInstitucionId()));
        borrar(carreras, c -> tenantId.equals(c.getInstitucionId()));
        borrar(docentes, d -> docenteIds.contains(d.getId()));
        borrar(usuarios, u -> tenantId.equals(u.getInstitucionId()));
    }
}
