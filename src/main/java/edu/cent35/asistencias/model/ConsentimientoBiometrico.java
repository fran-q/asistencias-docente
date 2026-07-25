package edu.cent35.asistencias.model;

import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Consentimiento informado del docente para el tratamiento de sus datos
 * biometricos (Ley 25.326 + Resolucion AAIP 255/2022). Cubre RF-10 y RNF-13.
 * <p>
 * <b>Multi-tenant</b>: no extiende {@code BaseTenantEntity} (no tiene
 * {@code institucion_id} propio). El tenant lo determina el {@code Docente}
 * padre y se valida explicitamente en el service - mismo patron que
 * {@code Comision}.
 * <p>
 * <b>Diseno historico</b>: cada docente puede tener varios registros a lo
 * largo del tiempo (otorga -> revoca -> vuelve a otorgar con la version
 * nueva del texto). El "estado actual" se calcula con el registro mas
 * reciente. El campo {@code vigente} es un atajo redundante con
 * {@code fechaRevocacion == null} - se mantiene sincronizado a nivel
 * aplicacion para que las queries de listado sean directas.
 * <p>
 * <b>Auditoria forense</b>: capturamos IP + User-Agent tanto en el
 * otorgamiento como en la revocacion (V005). Permite demostrar ante una
 * eventual auditoria AAIP que hubo una sesion historica concreta.
 */
@Entity
@Table(name = "consentimientos_biometricos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "versionTerminos", "metodo", "vigente", "fechaConsentimiento", "fechaRevocacion"})
public class ConsentimientoBiometrico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "docente_id", nullable = false)
    private Docente docente;

    @Column(name = "version_terminos", nullable = false, length = 20)
    private String versionTerminos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MetodoConsentimiento metodo;

    // Opcional: URL/ruta a un PDF escaneado del documento firmado.
    @Column(name = "documento_url", length = 255)
    private String documentoUrl;

    // Fecha en que el docente firmó; puede ser anterior a la carga si el admin la registra después.
    @Column(name = "fecha_consentimiento", nullable = false)
    private LocalDateTime fechaConsentimiento;

    // Null si esta vigente.
    @Column(name = "fecha_revocacion")
    private LocalDateTime fechaRevocacion;

    // Atajo: true si fechaRevocacion == null.
    @Column(nullable = false)
    @Builder.Default
    private Boolean vigente = true;

    // ---- Auditoria del otorgamiento ----------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_usuario_id", nullable = false)
    private Usuario registradoPor;

    @Column(name = "ip_otorgamiento", length = 45)
    private String ipOtorgamiento;

    @Column(name = "user_agent_otorgamiento", length = 500)
    private String userAgentOtorgamiento;

    // ---- Auditoria de la revocacion (null hasta que se revoque) ------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revocado_por_usuario_id")
    private Usuario revocadoPor;

    @Column(name = "ip_revocacion", length = 45)
    private String ipRevocacion;

    @Column(name = "user_agent_revocacion", length = 500)
    private String userAgentRevocacion;

    @Column(name = "motivo_revocacion", length = 500)
    private String motivoRevocacion;

    // ---- Housekeeping ------------------------------------------------------

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}
