package edu.cent35.asistencias.service;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.repository.CarreraRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.dto.GrillaSemanalDto;
import edu.cent35.asistencias.config.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Arma la grilla horaria semanal para una carrera del tenant actual.
 * Cada item viene con sus coordenadas de grid CSS pre-calculadas.
 * <p>
 * Ventana fija: 07:00 a 23:00, slots de 30 minutos = 32 slots.
 * Si un horario cae fuera de la ventana, se clampa a los bordes
 * (aparece en el primer o ultimo slot).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GrillaService {

    private static final LocalTime GRID_INICIO = LocalTime.of(7, 0);
    private static final LocalTime GRID_FIN    = LocalTime.of(23, 0);
    private static final int MINUTOS_POR_SLOT  = 30;
    private static final int TOTAL_SLOTS       =
        (int) Duration.between(GRID_INICIO, GRID_FIN).toMinutes() / MINUTOS_POR_SLOT;  // 32

    private final HorarioRepository horarioRepository;
    private final CarreraRepository carreraRepository;
    // Para resolver que ciclo mostrar cuando la pantalla no trae uno elegido.
    private final CicloLectivoService cicloLectivoService;

    // Carreras activas del tenant para popular el selector.
    @Transactional(readOnly = true)
    public List<Carrera> carrerasActivasParaSelector() {
        return carreraRepository.findByActivoTrueOrderByNombreAsc();
    }

    /**
     * Construye la grilla con los horarios activos de una carrera, opcionalmente acotada a un
     * año del plan.
     *
     * <p><b>Por qué el año.</b> Una carrera de tres años dicta las tres cohortes en las mismas
     * franjas, así que la grilla completa apila bloques encima de otros y deja de servir para
     * lo único que sirve una grilla: ver qué está libre y qué se superpone. Filtrando por año
     * se ve el horario de un curso, que es como se arma en la realidad.
     *
     * <p>Con {@code anio} en null se muestran todos, que sigue siendo útil para detectar a un
     * docente que quedó con dos clases a la misma hora en años distintos.
     *
     * <p><b>La grilla es de un ciclo, desde V023.</b> Sin acotar, en cuanto exista 2027 el mismo
     * casillero mostraría la clase de 2026 y la de 2027 superpuestas, y la grilla dejaría de
     * servir para lo único que sirve: ver qué está libre y qué se pisa.
     *
     * @param cicloId el ciclo a mostrar; si viene null se usa el que corre hoy, y si no hay
     *                ninguno, el más reciente
     */
    @Transactional(readOnly = true)
    public GrillaSemanalDto cargarGrillaPara(Long carreraId, Short anio, Long cicloId) {
        Long tenantId = TenantContext.getRequired();

        Carrera carrera = carreraRepository.findById(carreraId)
            .orElseThrow(() -> new EntityNotFoundException("Carrera no encontrada"));
        if (!tenantId.equals(carrera.getInstitucionId())) {
            log.warn("Cross-tenant blocked: tenant {} intento grilla de carrera id={} (tenant {})",
                     tenantId, carreraId, carrera.getInstitucionId());
            throw new EntityNotFoundException("Carrera no encontrada");
        }

        // Sin ciclo elegido la pantalla muestra el que corre hoy. Si la institucion todavia
        // no abrio ninguno, la grilla queda vacia en vez de fallar: no hay oferta que mostrar,
        // que es distinto de que algo se haya roto.
        CicloLectivo ciclo = cicloLectivoService.cicloParaMostrar(cicloId).orElse(null);
        List<Horario> horarios = ciclo == null
            ? List.of()
            : horarioRepository.findActivosPorCarreraYCiclo(carreraId, ciclo.getId(), tenantId);

        // El filtro por anio se aplica en memoria y no en la consulta a proposito: son los
        // horarios de UNA carrera, decenas como mucho, y agregar una variante de la query
        // por un filtro opcional obliga a mantener dos consultas que hacen casi lo mismo.
        if (anio != null) {
            horarios = horarios.stream()
                .filter(h -> anio.equals(h.getComision().getMateria().getAnio()))
                .toList();
        }

        List<GrillaSemanalDto.GrillaItem> items = new ArrayList<>(horarios.size());
        for (Horario h : horarios) {
            items.add(toGridItem(h));
        }

        List<GrillaSemanalDto.HoraLabel> labels = buildHoraLabels();

        return GrillaSemanalDto.builder()
            .carreraId(carrera.getId())
            .carreraCodigo(carrera.getCodigo())
            .carreraNombre(carrera.getNombre())
            .duracionAnios(carrera.getDuracionAnios())
            .anioFiltrado(anio)
            .cicloId(ciclo == null ? null : ciclo.getId())
            .cicloAnio(ciclo == null ? null : ciclo.getAnio())
            .totalHorarios(horarios.size())
            .horaMin(GRID_INICIO)
            .horaMax(GRID_FIN)
            .totalSlots(TOTAL_SLOTS)
            .labels(labels)
            .items(items)
            .build();
    }

    // Traduce un horario a un bloque de la grilla: en qué fila arranca y cuántas ocupa.
    private GrillaSemanalDto.GrillaItem toGridItem(Horario h) {
        // Touch lazy
        h.getComision().getCodigo();
        h.getComision().getMateria().getCodigo();

        int diaNum = h.getDiaSemana();          // 1=Lunes ... 7=Domingo
        int slotInicio = clamp(slotsDesdeInicio(h.getHoraInicio()));
        int slotFin    = clamp(slotsDesdeInicio(h.getHoraFin()));
        if (slotFin <= slotInicio) slotFin = slotInicio + 1;  // mostrar al menos 1 slot

        // Grid rows: row 1 = header. Las filas de slots van 2..(2+TOTAL_SLOTS).
        int gridRowStart = slotInicio + 2;
        int gridRowEnd   = slotFin + 2;

        return GrillaSemanalDto.GrillaItem.builder()
            .horarioId(h.getId())
            .comisionCodigo(h.getComision().getCodigo())
            .materiaCodigo(h.getComision().getMateria().getCodigo())
            .materiaNombre(h.getComision().getMateria().getNombre())
            .docenteNombre(h.getComision().getDocenteAsignado() != null
                ? h.getComision().getDocenteAsignado().getNombreCompleto() : null)
            .horaInicio(h.getHoraInicio())
            .horaFin(h.getHoraFin())
            .toleranciaMin(h.getToleranciaMin())
            .diaNum(diaNum)
            .gridColumn(diaNum + 1)             // col 1 = etiquetas de hora; col 2-8 = lun-dom
            .gridRowStart(gridRowStart)
            .gridRowEnd(gridRowEnd)
            .build();
    }

    // Cuántas franjas de media hora hay entre el inicio de la grilla (07:00) y esa hora.
    private int slotsDesdeInicio(LocalTime t) {
        long min = Duration.between(GRID_INICIO, t).toMinutes();
        return (int) Math.floorDiv(min, (long) MINUTOS_POR_SLOT);
    }

    // Recorta el slot al rango visible de la grilla, para que nada se dibuje fuera.
    private int clamp(int slot) {
        if (slot < 0) return 0;
        if (slot > TOTAL_SLOTS) return TOTAL_SLOTS;
        return slot;
    }

    // Etiquetas "07:00", "08:00", ... una cada 2 slots (cada hora completa).
    private List<GrillaSemanalDto.HoraLabel> buildHoraLabels() {
        List<GrillaSemanalDto.HoraLabel> labels = new ArrayList<>();
        LocalTime t = GRID_INICIO;
        for (int slot = 0; slot <= TOTAL_SLOTS; slot += 2) {
            labels.add(GrillaSemanalDto.HoraLabel.builder()
                .texto(String.format("%02d:%02d", t.getHour(), t.getMinute()))
                .gridRowStart(slot + 2)
                .build());
            t = t.plusMinutes(60);
        }
        return labels;
    }
}
