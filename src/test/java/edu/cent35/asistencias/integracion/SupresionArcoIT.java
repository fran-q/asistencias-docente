package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.EscenarioDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.academico.Carrera;
import edu.cent35.asistencias.model.academico.CicloLectivo;
import edu.cent35.asistencias.model.academico.Comision;
import edu.cent35.asistencias.model.academico.Horario;
import edu.cent35.asistencias.model.academico.Materia;
import edu.cent35.asistencias.model.asistencia.Asistencia;
import edu.cent35.asistencias.model.asistencia.BloquePresencia;
import edu.cent35.asistencias.model.asistencia.EstadoAsistencia;
import edu.cent35.asistencias.model.asistencia.EstadoCierre;
import edu.cent35.asistencias.model.asistencia.EstadoSalida;
import edu.cent35.asistencias.model.asistencia.MetodoAsistencia;
import edu.cent35.asistencias.model.asistencia.OrigenMarca;
import edu.cent35.asistencias.model.biometria.ModeloFacial;
import edu.cent35.asistencias.model.institucion.Usuario;
import edu.cent35.asistencias.model.persona.Docente;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.BloquePresenciaRepository;
import edu.cent35.asistencias.repository.ModeloFacialRepository;
import edu.cent35.asistencias.service.ModeloFacialService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La supresión ARCO con filas que citan al modelo, contra una base de verdad: borra el dato
 * biométrico y conserva las asistencias y las jornadas.
 *
 * <p>Hasta V038 esto lo resolvía la base con {@code ON DELETE SET NULL}, y sobre H2 no se probaba:
 * el esquema lo genera Hibernate, sin esa acción. Ahora lo hace el servicio, y si un paso falta la
 * clave foránea rechaza el borrado. {@code BaseInicialIT} corre estos mismos casos sobre MariaDB 11.4,
 * con sus CHECK.
 */
@SpringBootTest
@ActiveProfiles("test")
class SupresionArcoIT {

    private static final LocalDate LUNES = LocalDate.of(2026, 3, 2);

    @Autowired protected EscenarioDePrueba escenario;
    @Autowired private ModeloFacialService modeloFacialService;
    @Autowired private ModeloFacialRepository modelos;
    @Autowired private AsistenciaRepository asistencias;
    @Autowired private BloquePresenciaRepository bloques;

    protected Long tenantId;
    protected Usuario cuenta;
    protected Docente docente;
    protected Horario horario;

    @BeforeEach
    void sembrar() {
        TenantContext.clear();
        tenantId = escenario.institucion("Instituto de la supresion");
        cuenta = escenario.cuenta(tenantId, "INSTITUCION", "arco" + tenantId);
        Carrera carrera = escenario.carrera(tenantId, "ARC" + tenantId);
        Materia materia = escenario.materia(carrera, "M" + tenantId, "Materia de prueba");
        docente = escenario.docente(tenantId, "40" + (100000 + tenantId), "Rosa", "Suprimida");
        CicloLectivo ciclo = escenario.ciclo(tenantId, 2026);
        Comision comision = escenario.comision(materia, ciclo, docente, "A");
        horario = escenario.horario(comision, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0));
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
        escenario.borrarTodoDe(tenantId);
    }

    @Test
    @DisplayName("Borra el modelo y conserva la asistencia y la jornada, sin la referencia")
    void borraElModeloYConservaElHistorial() {
        ModeloFacial modelo = modelos.saveAndFlush(ModeloFacial.builder()
            .docente(docente).embeddingCifrado(new byte[] {1, 2, 3})
            .algoritmo("LBPH").versionAlgoritmo("4.10.0").dimensiones((short) 200)
            .activo(true).registradoPor(cuenta)
            .build());

        BloquePresencia jornada = BloquePresencia.builder()
            .docente(docente).fecha(LUNES)
            .horaEntrada(LocalTime.of(18, 0)).horaSalida(LocalTime.of(22, 0))
            .origenEntrada(OrigenMarca.AUTOMATICO).origenSalida(OrigenMarca.AUTOMATICO)
            .modeloFacialEntrada(modelo).confianzaEntrada(new BigDecimal("0.9000"))
            .modeloFacialSalida(modelo).confianzaSalida(new BigDecimal("0.8000"))
            .estadoCierre(EstadoCierre.CERRADO_POR_ROSTRO).estadoSalida(EstadoSalida.EN_HORA)
            .build();
        jornada.setInstitucionId(tenantId);
        Long jornadaId = bloques.saveAndFlush(jornada).getId();

        Asistencia marca = Asistencia.builder()
            .docente(docente).comision(horario.getComision()).horario(horario)
            .fecha(LUNES).horaRegistrada(LocalTime.of(18, 0))
            .estado(EstadoAsistencia.PRESENTE).metodo(MetodoAsistencia.AUTOMATICO)
            .modeloFacial(modelo).confianza(new BigDecimal("0.9000"))
            .bloque(bloques.findById(jornadaId).orElseThrow())
            .build();
        marca.setInstitucionId(tenantId);
        Long marcaId = asistencias.saveAndFlush(marca).getId();

        TenantContext.set(tenantId);
        int suprimidos = modeloFacialService.suprimirDatosBiometricos(docente.getId(), cuenta.getId());
        TenantContext.clear();

        assertThat(suprimidos).isEqualTo(1);
        assertThat(modelos.findById(modelo.getId())).isEmpty();

        Asistencia marcaDespues = asistencias.findById(marcaId).orElseThrow();
        assertThat(marcaDespues.getModeloFacial()).isNull();
        assertThat(marcaDespues.getEstado()).isEqualTo(EstadoAsistencia.PRESENTE);

        BloquePresencia jornadaDespues = bloques.findById(jornadaId).orElseThrow();
        assertThat(jornadaDespues.getModeloFacialEntrada()).isNull();
        assertThat(jornadaDespues.getModeloFacialSalida()).isNull();
        assertThat(jornadaDespues.getHoraSalida()).isEqualTo(LocalTime.of(22, 0));
    }
}
