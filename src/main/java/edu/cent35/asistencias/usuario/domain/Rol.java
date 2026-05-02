package edu.cent35.asistencias.usuario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Catalogo global de roles del sistema. NO es tenant-scoped.
 * <p>
 * Codigos definidos en {@link RolCodigo}:
 * <ul>
 *   <li>INSTITUCION: cuenta raiz de la institucion.</li>
 *   <li>ADMIN: operador del dia a dia.</li>
 * </ul>
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(of = {"id", "codigo"})
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false, length = 30, unique = true)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String descripcion;
}
