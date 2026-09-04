package edu.cent35.asistencias.model;

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
 * Un periodo de vinculo laboral entre una persona y la institucion (RF-07, ADR-0016): quien
 * da clases, desde cuando y hasta cuando. No se loguea al sistema.
 * Desde V016 la identidad no vive aca sino en {@code Persona}, y una misma persona puede
 * tener varias filas: si se fue y volvio, cada periodo es una fila propia, que es lo que
 * permite responder con precision ante una inspeccion.
 * <p>
 * Los datos personales y biometricos que se persistan estan sujetos a
 * la Ley 25.326 y la Resolucion AAIP 255/2022. El consentimiento
 * informado se modela aparte ({@code ConsentimientoBiometrico}).
 */
@Entity
@Table(name = "docentes")
@Filter(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "legajo", "fechaAlta", "activo"})
public class Docente extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Quien es. La identidad completa (DNI, nombre, apellido, contacto) vive del otro lado.
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "persona_id", nullable = false)
    private Persona persona;

    // Ya no es unico por institucion: un reingreso reutiliza el legajo del periodo anterior.
    // Que no se repita entre vinculos vigentes lo valida el service, que es donde se puede
    // expresar "vigente".
    @Column(length = 30)
    private String legajo;

    @Column(name = "fecha_alta", nullable = false)
    private LocalDate fechaAlta;

    // NULL significa que el docente no fue dado de baja; no es un dato faltante.
    @Column(name = "fecha_baja")
    private LocalDate fechaBaja;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    // Helper: delega en la persona. Sigue existiendo con esta firma porque medio sistema lo
    // llama; cambiarlo habria obligado a tocar plantillas y servicios sin ninguna ganancia.
    // Ojo: la persona es LAZY, asi que quien lo use fuera de una transaccion tiene que haber
    // traido el docente con JOIN FETCH (open-in-view esta apagado).
    public String getNombreCompleto() {
        return persona == null ? "" : persona.getNombreCompleto();
    }

    /**
     * Deja la persona en la misma institución que el vínculo.
     *
     * <p>No es una comodidad: es el invariante del que dependen las consultas. Todas filtran por
     * {@code persona.institucionId}, así que una persona guardada en otra institución que su
     * vínculo haría que ese docente desapareciera de los listados. Dejarlo librado a que cada
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
}
