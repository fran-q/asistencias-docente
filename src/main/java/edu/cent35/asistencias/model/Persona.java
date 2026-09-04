package edu.cent35.asistencias.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * Identidad de una persona dentro de una institución (ADR-0016): quién es, con independencia
 * de si opera el sistema, si da clases, o las dos cosas.
 * Es tenant-scoped a propósito y no global: la misma persona física que trabaja en dos
 * institutos son dos filas, porque una fila compartida dejaría que una institución dedujera
 * dónde más trabaja alguien.
 */
@Entity
@Table(
    name = "personas",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_personas_inst_dni", columnNames = {"institucion_id", "dni"})
    }
)
@Filter(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "dni", "apellido", "nombre"})
public class Persona extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Puede faltar: una persona creada a partir de una cuenta de acceso no lo trae.
    @Column(length = 15)
    private String dni;

    @Column(nullable = false, length = 80)
    private String nombre;

    // NULL en las cuentas institucionales, que no representan a una persona física.
    @Column(length = 80)
    private String apellido;

    // Correo de contacto. El de acceso al sistema vive en Usuario, y pueden ser distintos.
    @Column(length = 120)
    private String email;

    @Column(length = 30)
    private String telefono;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    // Helper: "Apellido, Nombre", el formato de los listados. Tolera el apellido nulo de las
    // cuentas institucionales, que si no apareceria como "null, Secretaria".
    public String getNombreCompleto() {
        return apellido == null || apellido.isBlank() ? nombre : apellido + ", " + nombre;
    }

    // Helper: "Nombre Apellido", el formato del saludo y la barra de navegacion. Son dos
    // formatos distintos a proposito y los dos viven aca para que ninguna pantalla los arme
    // por su cuenta y termine mostrando el apellido nulo colgando.
    public String getNombreParaMostrar() {
        return apellido == null || apellido.isBlank() ? nombre : nombre + " " + apellido;
    }
}
