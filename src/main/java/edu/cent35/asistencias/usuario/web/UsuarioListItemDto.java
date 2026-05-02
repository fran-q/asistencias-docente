package edu.cent35.asistencias.usuario.web;

import edu.cent35.asistencias.usuario.domain.Usuario;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Item del listado de usuarios. NO contiene el password_hash
 * (defensa basica - no filtramos hashes a la vista, ni siquiera
 * en logs accidentales).
 */
@Value
@Builder
public class UsuarioListItemDto {
    Long id;
    String username;
    String email;
    String nombreCompleto;
    String rolCodigo;
    boolean activo;
    LocalDateTime ultimoLogin;

    public static UsuarioListItemDto from(Usuario u) {
        return UsuarioListItemDto.builder()
            .id(u.getId())
            .username(u.getUsername())
            .email(u.getEmail())
            .nombreCompleto(u.getNombre() + " " + u.getApellido())
            .rolCodigo(u.getRol().getCodigo())
            .activo(Boolean.TRUE.equals(u.getActivo()))
            .ultimoLogin(u.getUltimoLogin())
            .build();
    }
}
