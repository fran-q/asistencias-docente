package edu.cent35.asistencias.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Equipo autorizado a capturar datos biométricos: el pase de asistencia y el registro del
 * rostro solo funcionan desde uno de estos.
 *
 * <p>La autorización se guarda contra el EQUIPO y no contra la persona. Un rol viaja con
 * quien inicia sesión; esto tiene que quedarse con la máquina, de modo que la misma cuenta
 * entrando desde otro lado no pueda tomar asistencia. Ver ADR-0015.
 *
 * <p>Del token solo se guarda el hash, igual que con las contraseñas y con los códigos de
 * un solo uso: una copia de la base no alcanza para fabricar un puesto válido.
 */
@Entity
@Table(name = "puestos_captura")
@Filter(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "nombre", "activo", "ultimoUsoEn"})
public class PuestoCaptura extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Como lo llama la institucion. Es lo unico que distingue un puesto de otro en la
    // pantalla de revocacion, asi que no puede repetirse dentro de la institucion.
    @Column(nullable = false, length = 80)
    private String nombre;

    // Hash del token que viaja en la cookie del equipo; el token en claro solo existe en
    // ese navegador y se muestra una unica vez, al designarlo.
    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    // Cuando se revoco. NULL = sigue habilitado.
    @Column(name = "fecha_baja")
    private LocalDate fechaBaja;

    /**
     * Cuenta que autorizó el equipo. Queda como rastro de quién lo habilitó, no como
     * dependencia: si esa cuenta se elimina la columna pasa a NULL y el puesto sigue
     * funcionando. Borrar un usuario no puede dejar a la institución sin poder tomar
     * asistencia.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designado_por")
    private Usuario designadoPor;

    // Ultima vez que el puesto paso el control. Sirve para reconocer cual es cual cuando
    // hay varios cargados y ya nadie recuerda a que maquina corresponde cada nombre.
    @Column(name = "ultimo_uso_en")
    private LocalDateTime ultimoUsoEn;

    /**
     * Si este equipo puede tomar asistencia <b>sin ninguna sesión abierta</b> (RF-84, RF-85).
     *
     * <p>Es una decisión aparte de designar el equipo, y por eso una columna aparte. Designar
     * dice "la captura ocurre acá"; habilitar el kiosco dice "además puede ocurrir sin nadie
     * mirando", que es una atenuación deliberada de un control de seguridad.
     */
    @Column(name = "kiosco_habilitado", nullable = false)
    @Builder.Default
    private Boolean kioscoHabilitado = false;

    /**
     * Cuándo se habilitó el kiosco por última vez.
     *
     * <p><b>No se limpia al deshabilitar.</b> Conservarlo es lo que permite decir "este equipo
     * estuvo operando desatendido desde tal fecha" cuando haya que responder por un período
     * pasado; borrarlo sería perder justo ese dato.
     */
    @Column(name = "kiosco_habilitado_en")
    private LocalDateTime kioscoHabilitadoEn;

    // Quien lo habilito. NULL si esa cuenta se suprimio: la FK hace SET NULL, igual que
    // designadoPor, para que suprimir una cuenta no rompa una fila ya escrita.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kiosco_habilitado_por")
    private Usuario kioscoHabilitadoPor;

    /**
     * Si este equipo está operando sin supervisión ahora mismo.
     *
     * <p>Un puesto revocado no opera de ninguna forma, así que la bandera del kiosco sola no
     * alcanza: hay que mirar las dos. Vive acá y no en cada servicio porque la responden el
     * interceptor que resuelve el tenant y la pantalla de puestos, y con una copia en cada
     * lado alcanza con tocar una para que dejen de coincidir.
     */
    public boolean operaDesatendido() {
        return Boolean.TRUE.equals(activo) && Boolean.TRUE.equals(kioscoHabilitado);
    }

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    /** Un puesto habilita la captura solamente mientras siga activo. */
    public boolean habilitado() {
        return Boolean.TRUE.equals(activo);
    }

    // Quien ejecuto la baja logica. NULL mientras la fila siga activa, y tambien en las bajas
    // anteriores a V017, que no lo registraban. Ver ADR-0016.
    @Column(name = "dado_de_baja_por")
    private Long dadoDeBajaPor;
}
