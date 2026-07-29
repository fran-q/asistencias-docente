package edu.cent35.asistencias.config;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.model.Usuario;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Principal de Spring Security que además del username expone el institucionId, que es lo
 * que TenantInterceptor lee en cada request para armar el TenantContext. El rol se mapea a
 * una autoridad con el prefijo estándar ROLE_ (ROLE_ADMIN, ROLE_INSTITUCION).
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long usuarioId;
    private final Long institucionId;
    private final String username;
    private final String passwordHash;
    private final String nombreCompleto;
    private final boolean activo;
    private final Collection<? extends GrantedAuthority> authorities;

    // Solo puede pasar de false a true, y dentro de la misma sesion: es una foto del momento
    // del login que VerificacionInterceptor refresca cuando la persona valida su codigo, para
    // no obligarla a volver a entrar. Nunca se desverifica una cuenta.
    private boolean emailVerificado;

    public CustomUserDetails(Usuario usuario) {
        this.usuarioId = usuario.getId();
        this.institucionId = usuario.getInstitucionId();
        this.username = usuario.getUsername();
        this.passwordHash = usuario.getPasswordHash();
        this.nombreCompleto = usuario.getNombre() + " " + usuario.getApellido();
        this.activo = Boolean.TRUE.equals(usuario.getActivo());
        this.emailVerificado = usuario.getEmailVerificadoEn() != null;
        this.authorities = List.of(
            new SimpleGrantedAuthority("ROLE_" + usuario.getRol().getCodigo())
        );
    }

    // Indica si la cuenta ya confirmo su correo.
    public boolean isEmailVerificado() {
        return emailVerificado;
    }

    // La marca como verificada en la sesion en curso, tras confirmarlo contra la base.
    public void marcarEmailVerificado() {
        this.emailVerificado = true;
    }

    // Spring Security compara contra este hash BCrypt al autenticar.
    @Override
    public String getPassword() {
        return passwordHash;
    }

    // Un usuario dado de baja queda inhabilitado por las cuatro vías a la vez.
    @Override public boolean isAccountNonExpired()      { return activo; }
    @Override public boolean isAccountNonLocked()       { return activo; }
    @Override public boolean isCredentialsNonExpired()  { return activo; }
    @Override public boolean isEnabled()                { return activo; }
}
