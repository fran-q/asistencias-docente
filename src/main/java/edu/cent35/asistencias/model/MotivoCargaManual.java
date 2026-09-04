package edu.cent35.asistencias.model;

import jakarta.persistence.Column;
import java.time.LocalDateTime;
import java.time.LocalDate;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Motivo predefinido para cargar una asistencia manualmente (RF-23).
 * Catálogo global (sin tenant), seedeado en V001.
 */
@Entity
@Table(name = "motivos_carga_manual")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "codigo", "activo"})
public class MotivoCargaManual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false, length = 40)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String descripcion;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    /**
     * Cuándo se dio de baja este motivo. NULL = sigue vigente.
     *
     * <p>Acompaña a {@code activo} por la misma regla que el resto del sistema: saber que algo
     * está inactivo no dice desde cuándo, y {@code actualizado_en} no sirve porque cambia con
     * cualquier edición posterior.
     */
    @Column(name = "fecha_baja")
    private LocalDate fechaBaja;

    /**
     * Cuándo se creó esta fila del catálogo. La escribe la base; la aplicación solo la lee.
     *
     * <p>Igual que en {@code roles}: la columna existía desde V012 sin entidad que la mapeara.
     */
    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}
