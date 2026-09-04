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
 * Lapso continuo durante el cual un docente estuvo en la institución, con su marca de
 * entrada y su marca de salida (RF-74 a RF-83, ADR-0017).
 * <p>
 * Un bloque abarca <b>todos</b> los horarios consecutivos que el umbral de separación de
 * la institución mantenga juntos, sin importar de qué materia o carrera sean: lo que
 * acredita es que la persona estuvo, no qué dictó. Por eso el horario sigue siendo la
 * unidad de la asistencia y el bloque es la unidad de la permanencia — son dos cosas
 * distintas y viven en dos tablas.
 * <p>
 * <b>Tenant-scoped</b>: tiene {@code institucion_id} denormalizado, igual que
 * {@link Asistencia}.
 * <p>
 * <b>Invariantes que garantiza la base</b> (V019, ninguno se ejercita en los tests: el
 * perfil test genera el esquema desde estas entidades y no desde la migración):
 * <ul>
 *   <li>Un docente no puede tener dos bloques {@link EstadoCierre#ABIERTO} a la vez —
 *       {@code uq_bloques_un_solo_abierto_por_docente}, sobre una columna generada que no
 *       se mapea acá.</li>
 *   <li>Un bloque abierto no tiene hora de salida, y uno cerrado sí —
 *       {@code ck_bloques_cierre_coherente}.</li>
 *   <li>Solo un cierre por reconocimiento lleva modelo facial y confianza: una hora
 *       cargada a mano o presumida no tiene evidencia biométrica detrás.</li>
 * </ul>
 */
@Entity
@Table(
    name = "bloques_presencia",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_bloques_doc_fecha_entrada",
            columnNames = {"docente_id", "fecha", "hora_entrada"})
    }
)
@Filter(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "fecha", "horaEntrada", "horaSalida", "estadoCierre"})
public class BloquePresencia extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "docente_id", nullable = false)
    private Docente docente;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_entrada", nullable = false)
    private LocalTime horaEntrada;

    // NULL mientras el bloque siga abierto.
    @Column(name = "hora_salida")
    private LocalTime horaSalida;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen_entrada", nullable = false, length = 15)
    private OrigenMarca origenEntrada;

    // PRESUNTO cuando la completó el sistema porque nadie registró la salida (RF-80).
    @Enumerated(EnumType.STRING)
    @Column(name = "origen_salida", length = 15)
    private OrigenMarca origenSalida;

    // Sólo presente cuando origenEntrada == AUTOMATICO.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modelo_facial_entrada_id")
    private ModeloFacial modeloFacialEntrada;

    @Column(name = "confianza_entrada", precision = 5, scale = 4)
    private BigDecimal confianzaEntrada;

    // Sólo presente cuando origenSalida == AUTOMATICO.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modelo_facial_salida_id")
    private ModeloFacial modeloFacialSalida;

    @Column(name = "confianza_salida", precision = 5, scale = 4)
    private BigDecimal confianzaSalida;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_cierre", nullable = false, length = 20)
    @Builder.Default
    private EstadoCierre estadoCierre = EstadoCierre.ABIERTO;

    // NULL mientras el bloque siga abierto.
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_salida", length = 15)
    private EstadoSalida estadoSalida;

    /**
     * Quién cerró o corrigió la salida a mano (RF-83). NULL en los otros cierres.
     *
     * <p>La FK va con {@code ON DELETE SET NULL}: si algún día se suprime la cuenta, el
     * registro del cierre sobrevive sin su autor. Por eso el CHECK de V020 exige el
     * <b>motivo</b> y no el usuario — exigir el usuario haría que suprimir una cuenta
     * rompiera filas ya escritas.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cerrado_por_usuario_id")
    private Usuario cerradoPor;

    /**
     * Por qué hubo que cerrarlo a mano, del <b>mismo</b> catálogo que la carga manual de
     * asistencia (RF-23). No hay un catálogo aparte: los motivos por los que el
     * reconocimiento falla al salir son los mismos por los que falla al entrar, y dos
     * listas paralelas se desincronizan solas.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "motivo_cierre_id")
    private MotivoCargaManual motivoCierre;

    // Texto libre del admin. Obligatorio cuando el motivo es OTRO, validado en el service.
    @Column(name = "detalle_cierre", columnDefinition = "TEXT")
    private String detalleCierre;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    // Indica si la hora de salida la fijó un administrador y no el reconocimiento (RF-83).
    public boolean cerradoAMano() {
        return estadoCierre == EstadoCierre.CERRADO_POR_ADMIN;
    }

    // Indica si el bloque todavía espera una marca de salida.
    public boolean estaAbierto() {
        return estadoCierre == EstadoCierre.ABIERTO;
    }

    /**
     * Indica si el bloque quedó sin que nadie registrara su salida.
     *
     * <p>Vive acá y no en un servicio porque lo preguntan dos pantallas distintas: el panel
     * de inicio, para listar los pendientes (RF-79), y el listado de asistencias, para
     * mostrar que esa hora de salida es presumida y no observada. Con una copia en cada
     * lado alcanzaba con tocar una para que las dos dejaran de coincidir.
     */
    public boolean quedoSinCierre() {
        return estadoCierre == EstadoCierre.SIN_CIERRE;
    }
}
