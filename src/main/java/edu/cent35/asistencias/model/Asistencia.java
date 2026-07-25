package edu.cent35.asistencias.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Marca de asistencia de un docente a una clase concreta (RF-17 a RF-21).
 * <p>
 * Cubre tanto el flujo automático (reconocimiento facial) como el manual
 * (carga por un admin con motivo del catálogo). La distinción es la columna
 * {@link #metodo}: {@code AUTOMATICO} guarda además {@link #modeloFacial}
 * y {@link #confianza}; {@code MANUAL} requiere un detalle adicional en
 * la tabla {@code asistencias_manuales}.
 * <p>
 * <b>Tenant-scoped</b>: tiene {@code institucion_id} denormalizado para
 * reforzar el aislamiento y acelerar reportes.
 * <p>
 * <b>Idempotencia</b>: la BD garantiza que no haya dos marcas para el
 * mismo (docente, horario, fecha) con el UNIQUE
 * {@code uq_asistencias_doc_horario_fecha}. El service la respeta
 * devolviendo la marca existente en vez de fallar.
 */
@Entity
@Table(
    name = "asistencias",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_asistencias_doc_horario_fecha",
            columnNames = {"docente_id", "horario_id", "fecha"})
    }
)
@Filter(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "fecha", "horaRegistrada", "estado", "metodo"})
public class Asistencia extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "docente_id", nullable = false)
    private Docente docente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comision_id", nullable = false)
    private Comision comision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horario_id", nullable = false)
    private Horario horario;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_registrada", nullable = false)
    private LocalTime horaRegistrada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoAsistencia estado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private MetodoAsistencia metodo;

    // Sólo presente cuando metodo == AUTOMATICO.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modelo_facial_id")
    private ModeloFacial modeloFacial;

    // Score 0-1 del reconocimiento, solo cuando la marca es AUTOMATICO. LBPH devuelve una
    // distancia (menor = mejor) que el service convierte a score para no atarse al algoritmo.
    @Column(precision = 5, scale = 4)
    private BigDecimal confianza;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}
