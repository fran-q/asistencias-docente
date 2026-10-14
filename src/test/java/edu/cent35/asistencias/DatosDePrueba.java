package edu.cent35.asistencias;

import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.model.EstadoCiclo;
import edu.cent35.asistencias.model.PeriodoLectivo;
import edu.cent35.asistencias.model.Persona;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.repository.RolRepository;
import org.springframework.data.repository.CrudRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Fábricas de datos para los tests, para no repetir el armado de una {@code Persona} en cada uno.
 * Existe desde ADR-0016: al separarse la identidad del vínculo, construir un docente o un usuario
 * pasó a ser dos objetos en vez de uno, y sin esto cada test cargaba con ese detalle.
 *
 * <p>Todo es estático y no necesita Spring: lo usan también los tests unitarios. Lo que guarda
 * en la base un escenario entero, y lo borra, está en {@link EscenarioDePrueba}.
 */
public final class DatosDePrueba {

    private DatosDePrueba() {
    }

    // Persona con documento, para los tests que arman docentes.
    public static Persona personaConDni(String dni, String nombre, String apellido) {
        return Persona.builder()
            .dni(dni)
            .nombre(nombre)
            .apellido(apellido)
            .build();
    }

    // Persona sin documento, para los tests que arman cuentas de acceso: el alta de usuario no
    // pide DNI, así que en la práctica esas personas nacen sin él.
    public static Persona persona(String nombre, String apellido) {
        return Persona.builder()
            .nombre(nombre)
            .apellido(apellido)
            .build();
    }

    // Persona de un solo nombre, como las cuentas institucionales, que no son una persona física.
    public static Persona persona(String nombre) {
        return persona(nombre, null);
    }

    /**
     * Un ciclo lectivo activo con un único período "Anual" que lo cubre entero.
     *
     * <p>Existe desde V023: la comisión pasó a tener {@code periodo_id NOT NULL}, así que todo
     * test que persista una comisión de verdad necesita antes un ciclo y un período. Sin esto,
     * cada uno lo armaba a mano y el detalle —alinear la institución del período con la del
     * ciclo— se olvidaba en alguno.
     *
     * <p>El ciclo va del 1 de enero al 31 de diciembre del año que se pase: los tests trabajan
     * con fechas de todo el año y un rango realista los dejaría a la mitad afuera por un
     * motivo que no es el que están probando.
     */
    public static CicloLectivo cicloAnualDelTenant(Long institucionId, int anio) {
        CicloLectivo ciclo = CicloLectivo.builder()
            .anio((short) anio)
            .fechaInicio(LocalDate.of(anio, 1, 1))
            .fechaFin(LocalDate.of(anio, 12, 31))
            .estado(EstadoCiclo.ACTIVO)
            .build();
        ciclo.setInstitucionId(institucionId);

        PeriodoLectivo anual = PeriodoLectivo.builder()
            .nombre("Anual")
            .fechaInicio(ciclo.getFechaInicio())
            .fechaFin(ciclo.getFechaFin())
            .orden((short) 1)
            .build();
        ciclo.agregarPeriodo(anual);
        return ciclo;
    }

    // Igual que personaConDni pero fijando la institución, para los tests de integración que
    // persisten de verdad y necesitan que la fila quede en el tenant correcto.
    public static Persona personaDelTenant(Long institucionId, String dni,
                                           String nombre, String apellido) {
        Persona p = personaConDni(dni, nombre, apellido);
        p.setInstitucionId(institucionId);
        return p;
    }

    /**
     * El rol con ese código, creándolo si la base de test todavía no lo tiene.
     *
     * <p>Los tests de integración comparten la base --Spring reutiliza el contexto entre
     * clases-- y el código del rol es único: el primero que lo necesita lo crea y los demás
     * lo encuentran. La descripción es la misma que siembra V001.
     */
    public static Rol rol(RolRepository roles, String codigo) {
        return roles.findByCodigo(codigo).orElseGet(() -> roles.save(Rol.builder()
            .codigo(codigo)
            .descripcion(switch (codigo) {
                case "INSTITUCION" -> "Cuenta institucional - gestiona los administradores";
                case "ADMIN" -> "Personal administrativo - opera el sistema dia a dia";
                default -> codigo;
            })
            .build()));
    }

    // Borra de un repositorio las filas que cumplan la condición. Se filtra en memoria porque
    // son datos de un solo test: la claridad vale más que la consulta. Estaba copiado en diez
    // tests de integración.
    public static <T> void borrar(CrudRepository<T, ?> repo, Predicate<T> condicion) {
        List<T> filas = new ArrayList<>();
        for (T fila : repo.findAll()) {
            if (condicion.test(fila)) filas.add(fila);
        }
        repo.deleteAll(filas);
    }
}
