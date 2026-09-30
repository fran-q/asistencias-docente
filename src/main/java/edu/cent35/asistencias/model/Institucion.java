package edu.cent35.asistencias.model;

import jakarta.persistence.Column;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Institucion educativa - tenant root del sistema multi-tenant.
 * Cubre RF-05 (alta de institucion).
 * <p>
 * No extiende {@link edu.cent35.asistencias.model.BaseTenantEntity}
 * porque ES el tenant - no pertenece a otra institucion.
 */
@Entity
@Table(name = "instituciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "nombre", "activo"})
public class Institucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150, unique = true)
    private String nombre;

    @Column(length = 13, unique = true)
    private String cuit;

    @Column(length = 200)
    private String direccion;

    @Column(name = "email_contacto", length = 120)
    private String emailContacto;

    @Column(name = "telefono_contacto", length = 30)
    private String telefonoContacto;

    /**
     * Minutos de hueco entre dos clases consecutivas que las mantienen dentro del mismo
     * bloque de presencia (RF-76, ADR-0017). Menor o igual: un bloque. Mayor: dos.
     *
     * <p>Vive en la institución y no en una constante porque lo determina la realidad
     * edilicia: un instituto con recreos de quince minutos y otro con turnos separados por
     * cuarenta y cinco no pueden compartir el número.
     *
     * <p>Agrupa <b>horarios de la grilla</b>, no pasadas frente a la cámara. Es la
     * confusión fácil de cometer con este valor: se puede calcular qué bloques tiene un
     * docente antes de que el docente aparezca.
     */
    @Column(name = "umbral_separacion_min", nullable = false)
    @Builder.Default
    private Short umbralSeparacionMin = (short) 60;

    /**
     * Cuántos equipos de captura puede tener autorizados a la vez. NULL = sin tope (V030).
     *
     * <p>Hasta V030 el tope era uno solo y estaba en el esquema, igual para todos. Una
     * institución con tres entradas necesita una cámara en cada una, y otra con una sola
     * puerta no quiere que un segundo equipo quede habilitado por descuido: el número
     * depende del edificio, así que lo decide cada institución.
     *
     * <p>Lo que <b>no</b> cambia es cómo se autoriza cada equipo: siempre desde esa misma
     * máquina (ADR-0015).
     */
    @Column(name = "max_puestos_habilitados")
    private Short maxPuestosHabilitados;

    /**
     * Hash de la clave de recuperación de la institución (V032, ADR-0022).
     *
     * <p>Null en toda institución creada por el alta pública: ahí el correo está comprobado y la
     * recuperación por código alcanza. La clave la genera el asistente de primer arranque, que
     * es el único camino que corre sin correo.
     *
     * <p>Se guarda el hash y no la clave por el mismo motivo que las contraseñas: una copia de
     * la base no alcanza para fabricar una válida.
     */
    @Column(name = "clave_recuperacion_hash", length = 100)
    private String claveRecuperacionHash;

    // Cual de las copias guardadas es la que vale: al regenerarla, la anterior deja de servir y
    // sin esta fecha no habria como saber cual es cual.
    @Column(name = "clave_recuperacion_creada_en")
    private LocalDateTime claveRecuperacionCreadaEn;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    // Cuando se dio de baja. NULL = no fue dada de baja.
    @Column(name = "fecha_baja")
    private LocalDate fechaBaja;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}
