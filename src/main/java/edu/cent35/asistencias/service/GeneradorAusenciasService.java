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
 * Job programado que cierra el día: primero cierra los bloques de presencia que quedaron sin
 * marca de salida e imputa lo que cada docente cubrió (RF-79 a RF-81), y recién después
 * materializa las ausencias de los horarios que nadie marcó (RF-19), con método AUTOMATICO y
 * hora_registrada = hora_fin, de modo que se puedan justificar y entren en los reportes.
 * Como el scheduler no pasa por TenantInterceptor, itera las instituciones activas seteando
 * y limpiando el TenantContext a mano, y es idempotente: el UNIQUE de la base resuelve la
 * carrera contra un pase facial simultáneo y correrlo dos veces no duplica nada.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeneradorAusenciasService {

    private final InstitucionRepository institucionRepository;
    private final HorarioRepository horarioRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final BloquePresenciaService bloquePresenciaService;
    // Los feriados y el receso: el ciclo pone el limite grueso, esto resuelve lo de adentro.
    private final DiaNoLaborableService diaNoLaborableService;

    @Value("${app.asistencia.ausencias-habilitado:true}")
    private boolean habilitado;

    // Entrada del cron (por defecto cada 30 minutos): recorre todas las instituciones activas.
    @Scheduled(cron = "${app.asistencia.ausencias-cron:0 */30 * * * *}")
    public void ejecutar() {
        if (!habilitado) {
            log.debug("Job de ausencias deshabilitado por configuracion.");
            return;
        }
        LocalDate hoy = LocalDate.now();
        LocalTime ahora = LocalTime.now();
        int totalCreadas = 0;
        int totalCerrados = 0;

        for (Institucion institucion : institucionRepository.findAll()) {
            if (Boolean.FALSE.equals(institucion.getActivo())) continue;
            try {
                // Propagacion manual del tenant: los hilos del scheduler no
                // pasan por TenantInterceptor (ver javadoc de TenantContext).
                TenantContext.set(institucion.getId());
                // El orden no es indistinto: cerrar los bloques imputa las clases que el
                // docente efectivamente cubrio, y esas ya no son candidatas a ausencia. Al
                // reves, la ausencia se escribiria primero y la imputacion chocaria contra el
                // UNIQUE, dejando como ausente una clase que si se dio.
                totalCerrados += bloquePresenciaService.cerrarBloquesVencidos(hoy, ahora);
                totalCreadas += generarParaInstitucion(institucion.getId(), hoy, ahora);
            } catch (RuntimeException ex) {
                // Una institucion con error no debe frenar a las demas.
                log.error("Job de ausencias fallo para institucion {}: {}",
                          institucion.getId(), ex.getMessage(), ex);
            } finally {
                TenantContext.clear();
            }
        }
        if (totalCerrados > 0 || totalCreadas > 0) {
            log.info("Job de cierre del dia: {} bloque(s) cerrado(s) sin marca de salida, "
                     + "{} ausencia(s) generada(s).", totalCerrados, totalCreadas);
        }
    }

    /**
     * Genera las ausencias de una institución en la fecha y hora dadas; público para testearlo
     * sin depender del scheduler. Devuelve cuántas creó.
     *
     * <p><b>Dos filtros nuevos desde V023 y V024, y los dos evitan datos falsos.</b> La consulta
     * ahora solo trae horarios cuyo período contiene esta fecha y cuyo ciclo está activo: antes
     * traía todo horario activo de ese día de la semana, así que generaba ausencias en enero y
     * habría seguido generándolas en 2027 con los horarios de 2026.
     *
     * <p>Y si el día está marcado como sin clases, no genera nada. Un feriado producía una
     * ausencia AUTOMATICA por cada docente que tenía clase, que no es un dato incompleto sino
     * uno falso: dice que alguien faltó un día en que la institución estaba cerrada. Se corta
     * antes de consultar los horarios porque no hay nada que evaluar.
     */
    public int generarParaInstitucion(Long institucionId, LocalDate fecha, LocalTime ahora) {
        if (diaNoLaborableService.esDiaSinClases(institucionId, fecha)) {
            log.debug("Sin ausencias para la institucion {}: {} esta marcado como dia sin clases",
                      institucionId, fecha);
            return 0;
        }

        byte diaSemana = (byte) fecha.getDayOfWeek().getValue();
        List<Horario> horarios =
            horarioRepository.findActivosDelDiaConDocente(diaSemana, fecha, institucionId);

        int creadas = 0;
        for (Horario h : horarios) {
            if (!ahora.isAfter(h.getHoraFin())) continue;      // la clase no termino
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

}
