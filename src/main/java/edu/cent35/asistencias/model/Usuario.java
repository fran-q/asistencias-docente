package edu.cent35.asistencias.model;

import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.BaseTenantEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
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

import java.time.LocalDate;
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

    // Institucion del usuario, de solo lectura: el id se maneja desde BaseTenantEntity.
    // Eager porque su nombre es el que se muestra cuando la cuenta no tiene persona (V018).
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "institucion_id", insertable = false, updatable = false)
    private Institucion institucion;

    // Rol del usuario; se trae eager porque hace falta en cada chequeo de seguridad.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    @Column(nullable = false, length = 60)
    private String username;

    @Column(nullable = false, length = 120)
    private String email;

    // Cuando la persona confirmo que controla este buzon; NULL mientras no lo haya hecho.
    @Column(name = "email_verificado_en")
    private LocalDateTime emailVerificadoEn;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * Quién es el dueño de esta cuenta, o {@code null} si la cuenta es la institución misma.
     *
     * <p><b>NULL no es un dato faltante: es la distinción.</b> Una cuenta de rol INSTITUCION
     * representa al establecimiento, no a una persona física, así que no tiene identidad
     * personal detrás (V018). Las cuentas de administrador y las de docente sí.
     *
     * <p>Se trae eager por el mismo motivo que el rol: el nombre para mostrar aparece en la
     * barra de navegación de todas las pantallas, y con {@code open-in-view=false} una
     * relación perezosa acá reventaría al renderizar, fuera ya de la transacción.
     */
    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "persona_id")
    private Persona persona;

    /**
     * Cómo nombrar a esta cuenta en pantalla.
     *
     * <p>Si hay persona detrás, su nombre. Si no la hay es la cuenta institucional, y entonces
     * lo que corresponde mostrar es el nombre del establecimiento: es exactamente lo que esa
     * cuenta representa. El username queda como último recurso y no debería usarse nunca.
     */
    public String getNombreParaMostrar() {
        if (persona != null) {
            return persona.getNombreParaMostrar();
        }
        return institucion != null ? institucion.getNombre() : username;
    }

    // true si esta cuenta representa al establecimiento y no a alguien concreto. Se decide por
    // la ausencia de persona y no por el codigo del rol: es el dato estructural, y no depende
    // de que el catalogo de roles diga lo que uno espera.
    public boolean esCuentaInstitucional() {
        return persona == null;
    }

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    // Cuando se dio de baja. NULL = no fue dada de baja.
    @Column(name = "fecha_baja")
    private LocalDate fechaBaja;

    @Column(name = "ultimo_login")
    private LocalDateTime ultimoLogin;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    /**
     * Deja la persona en la misma institución que la cuenta.
     *
     * <p>No es una comodidad: es el invariante del que dependen las consultas. Todas filtran por
     * {@code persona.institucionId}, así que una persona guardada en otra institución que su
     * cuenta haría que esa cuenta desapareciera de los listados. Dejarlo librado a que cada
     * llamador se acuerde es la clase de cosa que se olvida una vez y no se nota.
     */
    @PrePersist
    private void alinearInstitucionDeLaPersona() {
        if (persona != null && persona.getInstitucionId() == null) {
            persona.setInstitucionId(getInstitucionId());
        }
    }

    // Quien ejecuto la baja logica. NULL mientras la fila siga activa, y tambien en las bajas
    // anteriores a V017, que no lo registraban. Ver ADR-0016.
    @Column(name = "dado_de_baja_por")
    private Long dadoDeBajaPor;

    // Ultima vez que se fijo una contrasena nueva, por cualquiera de los dos caminos que lo
    // permiten. NULL en las cuentas anteriores a V021: nunca cambiaron y no estan trabadas.
    @Column(name = "password_cambiada_en")
    private LocalDateTime passwordCambiadaEn;

    // Cuando un administrador levanto el bloqueo de 24 horas. Ver puedeCambiarPassword.
    @Column(name = "cambio_password_habilitado_en")
    private LocalDateTime cambioPasswordHabilitadoEn;

    // Quien lo levanto. Mismo criterio que dadoDeBajaPor: el id pelado, no la relacion.
    @Column(name = "cambio_password_habilitado_por")
    private Long cambioPasswordHabilitadoPor;

    /**
     * Si la cuenta puede fijar una contraseña nueva en este momento.
     *
     * <p>La regla vive en la entidad porque la aplican dos servicios distintos —el cambio
     * voluntario desde Mi cuenta y la recuperación pública— y tienen que aplicar exactamente
     * la misma. Duplicada en cada uno, alcanza con tocar una para que dejen de coincidir.
     *
     * <p>Tres formas de poder: nunca se cambió, ya pasó la ventana, o un administrador
     * habilitó un cambio <b>después</b> del último. Ese "después" es lo que impide que un
     * destrabe viejo siga sirviendo para siempre.
     */
    public boolean puedeCambiarPassword(LocalDateTime ahora, java.time.Duration ventana) {
        if (passwordCambiadaEn == null) {
            return true;
        }
        if (!passwordCambiadaEn.isAfter(ahora.minus(ventana))) {
            return true;
        }
        return cambioPasswordHabilitadoEn != null
            && cambioPasswordHabilitadoEn.isAfter(passwordCambiadaEn);
    }
}
