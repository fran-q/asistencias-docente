package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Asistencia;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.EstadoAsistencia;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.MetodoAsistencia;
import edu.cent35.asistencias.repository.AsistenciaRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Job programado que materializa las ausencias del dia (cierra el hueco
 * del RF-19: "Ausente = sin registro", pero nadie creaba ese registro).
 * <p>
 * <b>Que hace</b>: para cada horario activo del dia cuyo {@code hora_fin}
 * ya paso y cuyo docente asignado no tiene marca, crea una fila
 * {@code Asistencia} con estado AUSENTE. Con la fila persistida, la
 * ausencia se puede justificar directamente (RF-25/RF-26) y entra en los
 * reportes sin pasos intermedios.
 * <p>
 * <b>Convenciones</b>:
 * <ul>
 *   <li>{@code metodo = AUTOMATICO}: la marca la genera el sistema sin
 *       intervencion del administrador (mismo sentido que el pase facial;
 *       MANUAL queda reservado para cargas hechas por una persona). Sin
 *       modelo facial ni confianza — el CHECK de la BD lo permite.</li>
 *   <li>{@code hora_registrada = hora_fin} del horario: el momento en que
 *       la ausencia se volvio definitiva.</li>
 * </ul>
 * <p>
 * <b>Multi-tenant</b>: los hilos del scheduler NO pasan por
 * {@code TenantInterceptor}, asi que no hay contexto de tenant. El job
 * itera las instituciones activas, setea {@code TenantContext} por cada
 * una (y lo limpia en {@code finally}), y ademas todas las queries llevan
 * el {@code institucionId} como parametro explicito — el aislamiento no
 * depende del filtro automatico de Hibernate.
 * <p>
 * <b>Idempotencia y carreras</b>: se re-verifica la existencia de marca
 * antes de insertar, y el UNIQUE {@code (docente, horario, fecha)} de la
 * BD resuelve la carrera con un pase facial simultaneo: si el docente
 * marca justo cuando corre el job, gana la marca real y la ausencia se
 * descarta sin error. Correr el job dos veces no duplica nada.
 * <p>
 * <b>Sin transaccion global</b>: cada insercion es su propia transaccion
 * (las del repositorio). Si el job muere a mitad de camino, lo ya insertado
 * queda y la proxima corrida completa el resto — la idempotencia lo hace
 * seguro y evita una transaccion larga sobre muchas instituciones.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeneradorAusenciasService {

    private final InstitucionRepository institucionRepository;
    private final HorarioRepository horarioRepository;
    private final AsistenciaRepository asistenciaRepository;

    @Value("${app.asistencia.ausencias-habilitado:true}")
    private boolean habilitado;

    /**
     * Punto de entrada programado. Corre segun el cron configurado
     * ({@code app.asistencia.ausencias-cron}, por defecto cada 30 minutos)
     * y procesa todas las instituciones activas.
     */
    @Scheduled(cron = "${app.asistencia.ausencias-cron:0 */30 * * * *}")
    public void ejecutar() {
        if (!habilitado) {
            log.debug("Job de ausencias deshabilitado por configuracion.");
            return;
        }
        LocalDate hoy = LocalDate.now();
        LocalTime ahora = LocalTime.now();
        int totalCreadas = 0;

        for (Institucion institucion : institucionRepository.findAll()) {
            if (Boolean.FALSE.equals(institucion.getActivo())) continue;
            try {
                // Propagacion manual del tenant: los hilos del scheduler no
                // pasan por TenantInterceptor (ver javadoc de TenantContext).
                TenantContext.set(institucion.getId());
                totalCreadas += generarParaInstitucion(institucion.getId(), hoy, ahora);
            } catch (RuntimeException ex) {
                // Una institucion con error no debe frenar a las demas.
                log.error("Job de ausencias fallo para institucion {}: {}",
                          institucion.getId(), ex.getMessage(), ex);
            } finally {
                TenantContext.clear();
            }
        }
        if (totalCreadas > 0) {
            log.info("Job de ausencias: {} ausencia(s) generada(s) en total.", totalCreadas);
        }
    }

    /**
     * Genera las ausencias pendientes de UNA institucion para una fecha y
     * hora dadas. Publico y determinista para poder testearlo sin scheduler.
     *
     * @return cantidad de ausencias creadas
     */
    public int generarParaInstitucion(Long institucionId, LocalDate fecha, LocalTime ahora) {
        byte diaSemana = (byte) fecha.getDayOfWeek().getValue();
        List<Horario> horarios =
            horarioRepository.findActivosDelDiaConDocente(diaSemana, institucionId);

        int creadas = 0;
        for (Horario h : horarios) {
            if (!ahora.isAfter(h.getHoraFin())) continue;      // la clase no termino
            if (!estaVigente(h, fecha)) continue;              // fuera de vigencia
            Docente docente = h.getComision().getDocenteAsignado();
            if (docente == null || Boolean.FALSE.equals(docente.getActivo())) continue;

            // Idempotencia a nivel aplicacion: si ya hay marca, no hay ausencia.
            boolean yaTieneMarca = asistenciaRepository
                .findByDocenteIdAndHorarioIdAndFecha(docente.getId(), h.getId(), fecha)
                .isPresent();
            if (yaTieneMarca) continue;

            Asistencia ausencia = Asistencia.builder()
                .docente(docente)
                .comision(h.getComision())
                .horario(h)
                .fecha(fecha)
                .horaRegistrada(h.getHoraFin())
                .estado(EstadoAsistencia.AUSENTE)
                .metodo(MetodoAsistencia.AUTOMATICO)
                .build();
            ausencia.setInstitucionId(institucionId);

            try {
                asistenciaRepository.saveAndFlush(ausencia);
                creadas++;
                log.info("Ausencia generada: docente={}, horario={}, fecha={}, institucion={}",
                         docente.getId(), h.getId(), fecha, institucionId);
            } catch (DataIntegrityViolationException ex) {
                // Carrera con el pase facial o con una carga manual: el UNIQUE
                // de BD la resolvio a favor de la marca real. Nada que hacer.
                log.info("Ausencia descartada por marca concurrente: docente={}, horario={}, fecha={}",
                         docente.getId(), h.getId(), fecha);
            }
        }
        return creadas;
    }

    /** Vigencia del horario en la fecha dada: desde <= fecha <= hasta (hasta nullable). */
    private boolean estaVigente(Horario h, LocalDate fecha) {
        if (h.getVigenteDesde() != null && fecha.isBefore(h.getVigenteDesde())) return false;
        return h.getVigenteHasta() == null || !fecha.isAfter(h.getVigenteHasta());
    }
}
