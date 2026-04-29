package edu.cent35.asistencias.usuario.domain;

import edu.cent35.asistencias.institucion.domain.Institucion;
import edu.cent35.asistencias.shared.multitenant.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalDateTime;

/**
 * Usuario del sistema (Superadmin de institucion o Administrador).
 * El docente NO es un Usuario - su perfil esta en el modulo docente.
 * <p>
 * Cubre RF-01 (login), RF-02 (gestion de contrasenas), RF-03 (control por rol)
 * y RF-06 (CRUD de administradores).
 * <p>
 * Tenant-scoped: pertenece a una unica institucion (heredado de
 * {@link BaseTenantEntity}).
 */
@Entity
@Table(
    name = "usuarios",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_usuarios_inst_username", columnNames = {"institucion_id", "username"}),
        @UniqueConstraint(name = "uq_usuarios_inst_email",    columnNames = {"institucion_id", "email"})
    }
)
@Filter(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "username", "activo"})
public class Usuario extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Institucion del usuario (read-only desde JPA).
     * El valor de {@code institucion_id} se gestiona via
     * {@link BaseTenantEntity#setInstitucionId(Long)}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institucion_id", insertable = false, updatable = false)
    private Institucion institucion;

    /**
     * Rol del usuario (eager: lo necesitamos en cada check de seguridad).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    @Column(nullable = false, length = 60)
    private String username;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(nullable = false, length = 80)
    private String apellido;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Column(name = "ultimo_login")
    private LocalDateTime ultimoLogin;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}
