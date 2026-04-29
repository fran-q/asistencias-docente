package edu.cent35.asistencias.institucion.domain;

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

import java.time.LocalDateTime;

/**
 * Institucion educativa - tenant root del sistema multi-tenant.
 * Cubre RF-05 (alta de institucion).
 * <p>
 * No extiende {@link edu.cent35.asistencias.shared.multitenant.BaseTenantEntity}
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

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}
