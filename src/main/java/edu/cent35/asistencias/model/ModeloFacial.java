package edu.cent35.asistencias.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Modelo biométrico facial de un docente (RF-08, RF-09).
 * <p>
 * Guarda el modelo LBPH entrenado a partir de varias capturas del rostro,
 * serializado y <b>cifrado</b> (AES). Nunca se almacenan las imágenes
 * originales — solo este modelo, que no permite reconstruir la cara.
 * Cumple la Ley 25.326 y la Resolución AAIP 255/2022.
 * <p>
 * <b>Multi-tenant</b>: no extiende {@code BaseTenantEntity}; el tenant lo
 * determina el {@code Docente} padre y se valida en el service (mismo
 * patrón que {@code Comision} y {@code ConsentimientoBiometrico}).
 * <p>
 * <b>Historial / re-registro (RF-09)</b>: un docente puede tener varios
 * registros. Solo uno está {@code activo}; al re-registrar, el anterior se
 * marca inactivo con {@code fechaBaja}.
 */
@Entity
@Table(name = "modelos_faciales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "algoritmo", "versionAlgoritmo", "dimensiones", "activo"})
public class ModeloFacial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "docente_id", nullable = false)
    private Docente docente;

    /**
     * Modelo LBPH serializado y cifrado con AES. Columna LONGBLOB.
     * <p>
     * Hibernate 6 + MariaDB: {@code @Lob byte[]} mapea a {@code BLOB} o
     * {@code TINYBLOB} corto. Para forzar LONGBLOB (que es lo que necesita
     * un modelo LBPH cifrado) hay que indicar explícitamente el tipo JDBC
     * {@code LONGVARBINARY}.
     */
    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    @Column(name = "embedding_cifrado", nullable = false)
    private byte[] embeddingCifrado;

    // Algoritmo usado. En Sprint 4: {@code "LBPH"}.
    @Column(nullable = false, length = 50)
    private String algoritmo;

    // Versión del algoritmo / librería. Ej.: la versión de OpenCV.
    @Column(name = "version_algoritmo", nullable = false, length = 20)
    private String versionAlgoritmo;

    /**
     * Para embeddings sería el largo del vector. Con LBPH se reutiliza para
     * guardar el lado (px) de la imagen de rostro normalizada. Ver ADR-0007.
     * <p>
     * Es {@code Short} (no {@code Integer}) porque la columna en BD es
     * {@code SMALLINT} (heredado de V001); Hibernate {@code validate}
     * requiere que el tipo Java coincida con el SQL.
     */
    @Column(nullable = false)
    private Short dimensiones;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_usuario_id", nullable = false)
    private Usuario registradoPor;

    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    // Null mientras el modelo está activo; se completa al darlo de baja.
    @Column(name = "fecha_baja")
    private LocalDateTime fechaBaja;
}
