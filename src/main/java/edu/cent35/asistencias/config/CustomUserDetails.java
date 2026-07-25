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

    public CustomUserDetails(Usuario usuario) {
        this.usuarioId = usuario.getId();
        this.institucionId = usuario.getInstitucionId();
        this.username = usuario.getUsername();
        this.passwordHash = usuario.getPasswordHash();
        this.nombreCompleto = usuario.getNombre() + " " + usuario.getApellido();
        this.activo = Boolean.TRUE.equals(usuario.getActivo());
        this.authorities = List.of(
            new SimpleGrantedAuthority("ROLE_" + usuario.getRol().getCodigo())
        );
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
