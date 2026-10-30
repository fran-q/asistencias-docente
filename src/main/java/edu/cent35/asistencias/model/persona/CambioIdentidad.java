package edu.cent35.asistencias.model.persona;

import edu.cent35.asistencias.model.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/**
 * Un campo de identidad que cambió: cuál, qué decía antes, qué dice ahora, quién lo cambió y
 * cuándo (ADR-0016).
 * Es una fila por campo y no por operación: cambiar solo el teléfono deja un renglón y no una
 * copia entera de la persona, que es menos dato personal guardado para la misma capacidad de
 * responder qué pasó.
 */
@Entity
@Table(name = "cambios_identidad")
@Filter(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "personaId", "campo", "fecha"})
public class CambioIdentidad extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "persona_id", nullable = false, updatable = false)
    private Long personaId;

    // Quien hizo el cambio. Se guarda el id y no la entidad: esta fila es un registro histórico
    // y no tiene por qué arrastrar el usuario entero cada vez que se lee.
    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private CampoIdentidad campo;

    // NULL cuando el campo estaba vacío antes del cambio.
    @Column(name = "valor_anterior", length = 120, updatable = false)
    private String valorAnterior;

    @Column(name = "valor_nuevo", length = 120, updatable = false)
    private String valorNuevo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private OrigenCambioIdentidad origen;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;
}
