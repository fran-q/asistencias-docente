package edu.cent35.asistencias.shared.security;

import edu.cent35.asistencias.usuario.domain.Usuario;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Implementacion de Spring Security {@link UserDetails} que adicionalmente
 * expone el {@code institucionId} del usuario logueado.
 * <p>
 * Lo usa {@link CustomUserDetailsService} para construir el principal
 * que vive en la {@code SecurityContext}, y
 * {@link edu.cent35.asistencias.shared.multitenant.TenantInterceptor}
 * lo lee en cada request para popular el {@code TenantContext}.
 * <p>
 * El rol del usuario se mapea a una autoridad de Spring Security con el
 * prefijo {@code ROLE_} (convencion estandar): {@code ROLE_ADMIN},
 * {@code ROLE_SUPERADMIN_INSTITUCION}.
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

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override public boolean isAccountNonExpired()      { return activo; }
    @Override public boolean isAccountNonLocked()       { return activo; }
    @Override public boolean isCredentialsNonExpired()  { return activo; }
    @Override public boolean isEnabled()                { return activo; }
}
