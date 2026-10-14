package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.EscenarioDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.ConsentimientoBiometrico;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.MetodoConsentimiento;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.BloquePresenciaRepository;
import edu.cent35.asistencias.repository.ConsentimientoBiometricoRepository;
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

    @Autowired private BloquePresenciaService bloqueService;
    @Autowired private EscenarioDePrueba escenario;

    @Autowired private AsistenciaRepository asistenciaRepository;
    @Autowired private BloquePresenciaRepository bloqueRepository;
    @Autowired private ConsentimientoBiometricoRepository consentimientoRepository;

    private Long tenantId;
    private Docente docente;
    private Comision comision;

    @BeforeEach
    void sembrar() {
        tenantId = escenario.institucion("Instituto del pase");
        TenantContext.set(tenantId);

        Carrera carrera = escenario.carrera(tenantId, "PAS-" + tenantId);
        Materia materia = escenario.materia(carrera, "PAS1-" + tenantId, "Programación I");
        docente = escenario.docente(tenantId, "41" + tenantId);

        // El ciclo tiene que contener HOY: el pase solo encuentra clases dentro de un ciclo
        // activo, y la fecha de la marca es la del día en que corre el test.
        CicloLectivo ciclo = escenario.ciclo(tenantId, LocalDate.now().getYear());
        comision = escenario.comision(materia, ciclo, docente, "A");

        // La clase es hoy, de 18 a 20: así la marca de las 18:05 cae siempre en ventana,
        // corra el test el día que corra.
        escenario.horario(comision, LocalDate.now().getDayOfWeek(),
            LocalTime.of(18, 0), LocalTime.of(20, 0));

        conConsentimientoVigente();
    }

    @AfterEach
    void limpiar() {
        escenario.borrarTodoDe(tenantId);
        TenantContext.clear();
    }

    // ========================================================================
    //  Una pasada de más no inventa un registro
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

        // Dos minutos después vuelve a quedar frente a la cámara.
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

        // Dos minutos después vuelve a pasar: ya registro su entrada y su salida de esta clase.
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
        // El límite de la regla de arriba: lo que se bloquea es repetir LA MISMA clase, no
        // volver a entrar. Sin esta distinción, el docente que se va y vuelve más tarde se
        // quedaría sin poder marcar el resto del día.
        escenario.horario(comision, LocalDate.now().getDayOfWeek(),
            LocalTime.of(20, 0), LocalTime.of(22, 0));
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
    // (RF-82). Que esa regla corra primero es deliberado, así que el test se acomoda a ella.
    private void conConsentimientoVigente() {
        Usuario registrante = escenario.cuenta(tenantId, "INSTITUCION", "pase." + tenantId);

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
}
