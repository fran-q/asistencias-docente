package edu.cent35.asistencias.model;

import jakarta.persistence.Column;
import java.time.LocalDateTime;
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
@lombok.Builder
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

    /**
     * Cuándo se creó esta fila del catálogo.
     *
     * <p>La escribe la base con su valor por defecto; la aplicación solo la lee, y por eso va
     * como {@code insertable = false, updatable = false}. Los roles se siembran en la migración
     * V001 y ninguna pantalla los edita.
     *
     * <p>Existía en la base desde V012 sin que ninguna entidad la mapeara: la columna estaba y
     * el sistema no podía verla. Mapearla no agrega comportamiento, cierra la diferencia entre
     * lo que la base guarda y lo que la aplicación conoce.
     */
    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}
