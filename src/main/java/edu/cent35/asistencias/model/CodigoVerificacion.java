package edu.cent35.asistencias.model;

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
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/**
 * Código de un solo uso enviado por correo, para verificar el buzón de una cuenta o para
 * recuperar su contraseña. Guarda el hash y no el código, con vencimiento, marca de consumo y
 * tope de intentos, de modo que ni leyendo la base se pueda aprovechar uno pendiente.
 */
@Entity
@Table(name = "codigos_verificacion")
@Filter(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "proposito", "expiraEn", "usadoEn", "intentos"})
public class CodigoVerificacion extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PropositoCodigo proposito;

    // Direccion a la que se envio. Se guarda aparte del usuario porque si alguien cambia su
    // correo despues de pedir el codigo, el codigo sigue atado al buzon que lo recibio.
    @Column(nullable = false, length = 120)
    private String email;

    // Hash del OTP; el codigo en claro solo existe en el correo enviado.
    @Column(name = "codigo_hash", nullable = false)
    private String codigoHash;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    // NULL mientras siga disponible; con fecha, ya se consumio.
    @Column(name = "usado_en")
    private LocalDateTime usadoEn;

    // Validaciones fallidas acumuladas; al llegar al tope el codigo se descarta.
    @Column(nullable = false)
    @Builder.Default
    private Short intentos = 0;

    @Column(name = "ip_solicitud", length = 45)
    private String ipSolicitud;

    @Column(name = "creado_en", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime creadoEn = LocalDateTime.now();

    // Un codigo sirve si no se uso y todavia no vencio.
    public boolean estaDisponible(LocalDateTime ahora) {
        return usadoEn == null && ahora.isBefore(expiraEn);
    }
}
