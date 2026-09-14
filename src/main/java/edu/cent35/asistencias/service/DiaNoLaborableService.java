package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.DiaNoLaborable;
import edu.cent35.asistencias.model.TipoDiaNoLaborable;
import edu.cent35.asistencias.repository.DiaNoLaborableRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Un rango más largo que esto es casi seguro un error de tipeo en el año: 2062 en vez de
     * 2026 marcaría miles de días. El receso de invierno son dos semanas.
     */
    static final int DIAS_MAXIMOS_POR_RANGO = 60;

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DiaNoLaborableRepository repository;

    /** Cuántos días se marcaron y cuántos ya estaban, para decírselo a quien cargó el rango. */
    public record Resultado(int marcados, int yaEstaban) {}

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

    /**
     * Por qué ese día no hay clases, o vacío si es un día normal.
     *
     * <p>Devuelve el motivo y no un booleano porque el pase lo muestra en pantalla: un docente
     * parado frente a la cámara al que le dicen "hoy no hay clases" sin decirle por qué no
     * sabe si el sistema se equivocó o si él se equivocó de día.
     *
     * <p>Recibe el institucionId por la misma razón que {@link #esDiaSinClases}: lo llaman
     * flujos que no siempre tienen el tenant en contexto.
     */
    @Transactional(readOnly = true)
    public Optional<String> motivoSinClases(Long institucionId, LocalDate fecha) {
        return repository.findByInstitucionIdAndFecha(institucionId, fecha)
            .map(DiaNoLaborable::getMotivo);
    }

    // El listado de un ano, que es como se los carga y se los revisa.
    @Transactional(readOnly = true)
    public List<DiaNoLaborable> listarDelAnio(int anio) {
        return repository.entreFechas(TenantContext.getRequired(),
                                      LocalDate.of(anio, 1, 1),
                                      LocalDate.of(anio, 12, 31));
    }

    /**
     * Marca como sin clases un día, o todos los de un rango: el receso son dos semanas, y
     * cargarlas de a una era catorce veces el mismo formulario.
     *
     * <p>Cada día queda como una fila, con el mismo tipo y el mismo motivo: el job de ausencias
     * y el pase preguntan por una fecha, no por un rango. Los fines de semana entran también
     * —hay institutos con clases los sábados—, y los días que ya estaban marcados se saltean en
     * vez de rechazar el rango entero, así un feriado cargado antes no traba el receso que lo
     * contiene. Tampoco se pisa: conserva su tipo y su motivo.
     *
     * <p>Un solo día que ya estaba sí se rechaza, y se valida acá además del índice único: un
     * {@code Duplicate entry} no le dice nada a quien está cargando feriados.
     *
     * @param hasta el último día del rango, o null para marcar solo {@code desde}
     */
    @Transactional
    public Resultado marcar(LocalDate desde, LocalDate hasta, TipoDiaNoLaborable tipo,
                            String motivo, Long usuarioActualId) {
        Long tenantId = TenantContext.getRequired();

        if (desde == null) {
            throw new IllegalArgumentException("Elegí la fecha del día sin clases.");
        }
        if (tipo == null) {
            throw new IllegalArgumentException(
                "Elegí el tipo: feriado nacional o provincial, institucional, receso u otro.");
        }
        String limpio = motivo == null ? "" : motivo.trim();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException(
                "Poné el motivo. Dentro de un año nadie va a acordarse de por qué ese día "
                + "estaba marcado.");
        }
        LocalDate ultimo = hasta == null ? desde : hasta;
        if (ultimo.isBefore(desde)) {
            throw new IllegalArgumentException("El rango termina el " + ultimo.format(FORMATO)
                + ", antes de empezar el " + desde.format(FORMATO) + ".");
        }
        long dias = ChronoUnit.DAYS.between(desde, ultimo) + 1;
        if (dias > DIAS_MAXIMOS_POR_RANGO) {
            throw new IllegalArgumentException("Son " + dias + " días seguidos, y el máximo por vez es "
                + DIAS_MAXIMOS_POR_RANGO + ". Revisá el año de las fechas; si de verdad son tantos, "
                + "cargalos en tramos.");
        }

        Set<LocalDate> yaMarcados = repository.entreFechas(tenantId, desde, ultimo).stream()
            .map(DiaNoLaborable::getFecha)
            .collect(Collectors.toSet());
        if (yaMarcados.size() == dias) {
            throw new IllegalArgumentException(dias == 1
                ? "Ese día ya está cargado como sin clases."
                : "Todos esos días ya estaban cargados como sin clases.");
        }

        List<DiaNoLaborable> nuevos = new ArrayList<>();
        for (LocalDate f = desde; !f.isAfter(ultimo); f = f.plusDays(1)) {
            if (yaMarcados.contains(f)) continue;
            DiaNoLaborable dia = DiaNoLaborable.builder()
                .fecha(f)
                .tipo(tipo)
                .motivo(limpio)
                .creadoPor(usuarioActualId)
                .build();
            dia.setInstitucionId(tenantId);
            nuevos.add(dia);
        }
        repository.saveAll(nuevos);
        log.info("Dias sin clases cargados: {} a {}, tipo={}, motivo='{}', marcados={}, ya estaban={}, "
                 + "institucion={}", desde, ultimo, tipo, limpio, nuevos.size(), yaMarcados.size(), tenantId);
        return new Resultado(nuevos.size(), yaMarcados.size());
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
