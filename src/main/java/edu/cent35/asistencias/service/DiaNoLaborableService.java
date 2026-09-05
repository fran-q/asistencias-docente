package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.DiaNoLaborable;
import edu.cent35.asistencias.repository.DiaNoLaborableRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Los días de adentro del ciclo en los que no se dicta clase: feriados, receso, jornadas
 * institucionales, paros (V024). El job de ausencias los saltea.
 *
 * <p>Los ciclos ponen el límite grueso —fuera del ciclo no hay clases— y esto resuelve lo que
 * queda adentro, que es donde el job generaba ausencias falsas: filas que dicen que alguien
 * faltó un día en que la institución estaba cerrada.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiaNoLaborableService {

    private final DiaNoLaborableRepository repository;

    /**
     * Si ese día está marcado como sin clases.
     *
     * <p>Recibe el institucionId en vez de leerlo del contexto porque lo llama el job de
     * ausencias, que recorre todas las instituciones desde un hilo propio donde no hay tenant
     * seteado por el interceptor.
     */
    @Transactional(readOnly = true)
    public boolean esDiaSinClases(Long institucionId, LocalDate fecha) {
        return repository.existsByInstitucionIdAndFecha(institucionId, fecha);
    }

    // El listado de un ano, que es como se los carga y se los revisa.
    @Transactional(readOnly = true)
    public List<DiaNoLaborable> listarDelAnio(int anio) {
        return repository.entreFechas(TenantContext.getRequired(),
                                      LocalDate.of(anio, 1, 1),
                                      LocalDate.of(anio, 12, 31));
    }

    /**
     * Marca un día como sin clases.
     *
     * <p>Se valida acá además de en el índice único de la base: un {@code Duplicate entry} no le
     * dice nada a quien está cargando feriados, y el mensaje tiene que explicar que ese día ya
     * estaba.
     */
    @Transactional
    public DiaNoLaborable crear(LocalDate fecha, String motivo, Long usuarioActualId) {
        Long tenantId = TenantContext.getRequired();

        if (fecha == null) {
            throw new IllegalArgumentException("Elegí la fecha del día sin clases.");
        }
        String limpio = motivo == null ? "" : motivo.trim();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException(
                "Poné el motivo. Dentro de un año nadie va a acordarse de por qué ese día "
                + "estaba marcado.");
        }
        if (repository.existsByInstitucionIdAndFecha(tenantId, fecha)) {
            throw new IllegalArgumentException("Ese día ya está cargado como sin clases.");
        }

        DiaNoLaborable dia = DiaNoLaborable.builder()
            .fecha(fecha)
            .motivo(limpio)
            .creadoPor(usuarioActualId)
            .build();
        dia.setInstitucionId(tenantId);

        DiaNoLaborable guardado = repository.save(dia);
        log.info("Dia sin clases cargado: {}, motivo='{}', institucion={}",
                 fecha, limpio, tenantId);
        return guardado;
    }

    /**
     * Borra un día sin clases. Es borrado físico, al revés que el resto del sistema.
     *
     * <p>No hay nada que dependa de esta fila —ninguna asistencia la referencia— así que una
     * baja lógica solo dejaría basura marcada como inactiva ensuciando el listado. Y sobre el
     * pasado no cambia nada: las ausencias que no se generaron ese día no se generan
     * retroactivamente por borrarlo.
     */
    @Transactional
    public void borrar(Long id) {
        Long tenantId = TenantContext.getRequired();
        DiaNoLaborable dia = repository.findById(id)
            .filter(d -> tenantId.equals(d.getInstitucionId()))
            .orElseThrow(() -> new EntityNotFoundException("Día no encontrado: " + id));

        repository.delete(dia);
        log.info("Dia sin clases borrado: {}, institucion={}", dia.getFecha(), tenantId);
    }
}
