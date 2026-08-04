package edu.cent35.asistencias.service;

import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoAsistencia;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.MetodoAsistencia;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre el job que materializa las ausencias: que solo tome horarios ya terminados y sin marca,
 * y que sea idempotente, de modo que correrlo dos veces no duplique nada.
 */
@ExtendWith(MockitoExtension.class)
class GeneradorAusenciasServiceTest {

    private static final Long TENANT_A = 1L;
    private static final Long DOCENTE_ID = 50L;
    private static final Long HORARIO_ID = 60L;
    // Lunes (2026-05-25 fue lunes).
    private static final LocalDate LUNES = LocalDate.of(2026, 5, 25);

    @Mock private InstitucionRepository institucionRepository;
    @Mock private HorarioRepository horarioRepository;
    @Mock private AsistenciaRepository asistenciaRepository;

    @InjectMocks private GeneradorAusenciasService service;

    @Test
    @DisplayName("genera AUSENTE para horario terminado sin marca, con la convencion correcta")
    void generaAusencia() {
        Horario h = horarioLunes18a20();
        when(horarioRepository.findActivosDelDiaConDocente((byte) 1, TENANT_A))
            .thenReturn(List.of(h));
        when(asistenciaRepository.findByDocenteIdAndHorarioIdAndFecha(DOCENTE_ID, HORARIO_ID, LUNES))
            .thenReturn(Optional.empty());
        when(asistenciaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        // 21:00 -> la clase de 18-20 ya termino
        int creadas = service.generarParaInstitucion(TENANT_A, LUNES, LocalTime.of(21, 0));

        assertThat(creadas).isEqualTo(1);
        ArgumentCaptor<Asistencia> captor = ArgumentCaptor.forClass(Asistencia.class);
        verify(asistenciaRepository).saveAndFlush(captor.capture());
        Asistencia ausencia = captor.getValue();
        assertThat(ausencia.getEstado()).isEqualTo(EstadoAsistencia.AUSENTE);
        assertThat(ausencia.getMetodo()).isEqualTo(MetodoAsistencia.AUTOMATICO);
        assertThat(ausencia.getModeloFacial()).isNull();
        assertThat(ausencia.getConfianza()).isNull();
        // Convencion: hora_registrada = hora_fin del horario
        assertThat(ausencia.getHoraRegistrada()).isEqualTo(LocalTime.of(20, 0));
        assertThat(ausencia.getInstitucionId()).isEqualTo(TENANT_A);
        assertThat(ausencia.getFecha()).isEqualTo(LUNES);
    }

    @Test
    @DisplayName("no genera si la clase todavia no termino")
    void noGenera_claseEnCurso() {
        when(horarioRepository.findActivosDelDiaConDocente((byte) 1, TENANT_A))
            .thenReturn(List.of(horarioLunes18a20()));

        // 19:00 -> la clase de 18-20 esta corriendo
        int creadas = service.generarParaInstitucion(TENANT_A, LUNES, LocalTime.of(19, 0));

        assertThat(creadas).isZero();
        verify(asistenciaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("idempotente: no genera si ya existe una marca para (docente, horario, fecha)")
    void noGenera_yaHayMarca() {
        Horario h = horarioLunes18a20();
        when(horarioRepository.findActivosDelDiaConDocente((byte) 1, TENANT_A))
            .thenReturn(List.of(h));
        when(asistenciaRepository.findByDocenteIdAndHorarioIdAndFecha(DOCENTE_ID, HORARIO_ID, LUNES))
            .thenReturn(Optional.of(Asistencia.builder().id(99L)
                .estado(EstadoAsistencia.PRESENTE).build()));

        int creadas = service.generarParaInstitucion(TENANT_A, LUNES, LocalTime.of(21, 0));

        assertThat(creadas).isZero();
        verify(asistenciaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("no genera para docente inactivo")
    void noGenera_docenteInactivo() {
        Horario h = horarioLunes18a20();
        h.getComision().getDocenteAsignado().setActivo(false);
        when(horarioRepository.findActivosDelDiaConDocente((byte) 1, TENANT_A))
            .thenReturn(List.of(h));

        int creadas = service.generarParaInstitucion(TENANT_A, LUNES, LocalTime.of(21, 0));

        assertThat(creadas).isZero();
        verify(asistenciaRepository, never()).saveAndFlush(any());
    }


    @Test
    @DisplayName("carrera con el pase facial: el UNIQUE la resuelve y el job no explota")
    void carreraConPase_noExplota() {
        Horario h = horarioLunes18a20();
        when(horarioRepository.findActivosDelDiaConDocente((byte) 1, TENANT_A))
            .thenReturn(List.of(h));
        when(asistenciaRepository.findByDocenteIdAndHorarioIdAndFecha(DOCENTE_ID, HORARIO_ID, LUNES))
            .thenReturn(Optional.empty());
        when(asistenciaRepository.saveAndFlush(any()))
            .thenThrow(new DataIntegrityViolationException("uq_asistencias_doc_horario_fecha"));

        int creadas = service.generarParaInstitucion(TENANT_A, LUNES, LocalTime.of(21, 0));

        // La marca real gano la carrera: 0 creadas, sin excepcion propagada.
        assertThat(creadas).isZero();
    }

    // ------------------------------------------------------------------------

    // Horario lunes 18:00-20:00 vigente, con docente activo asignado.
    private Horario horarioLunes18a20() {
        Docente docente = Docente.builder()
            .id(DOCENTE_ID).dni("12345678").nombre("Juana").apellido("Pérez").activo(true)
            .build();
        docente.setInstitucionId(TENANT_A);
        Materia materia = Materia.builder().id(80L).codigo("MAT").nombre("Matemática").build();
        materia.setInstitucionId(TENANT_A);
        Comision comision = Comision.builder()
            .id(70L).codigo("A").materia(materia).docenteAsignado(docente).activo(true)
            .build();
        return Horario.builder()
            .id(HORARIO_ID).comision(comision)
            .diaSemana((byte) 1)
            .horaInicio(LocalTime.of(18, 0))
            .horaFin(LocalTime.of(20, 0))
            .toleranciaMin((short) 15)
                        .activo(true)
            .build();
    }
}
