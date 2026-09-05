package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Usuario;
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
    // Una cuenta sin verificar no puede operar el sistema, asi que el listado lo muestra:
    // de lo contrario se descubre recien cuando la persona avisa que no entra a ningun lado.
    boolean emailVerificado;
    LocalDateTime ultimoLogin;
    // Si la cuenta esta dentro de la ventana de 24 horas y todavia no puede cambiar su
    // contrasena. Lo resuelve el servicio, que es quien conoce la ventana configurada; el
    // listado solo lo necesita para no ofrecer un destrabe donde no hay nada trabado.
    boolean cambioPasswordBloqueado;

    // Arma la fila del listado a partir de la entidad, resolviendo lo que el template va a mostrar.
    public static UsuarioListItemDto from(Usuario u, boolean cambioPasswordBloqueado) {
        return UsuarioListItemDto.builder()
            .cambioPasswordBloqueado(cambioPasswordBloqueado)
            .id(u.getId())
            .username(u.getUsername())
            .email(u.getEmail())
            .nombreCompleto(u.getNombreParaMostrar())
            .rolCodigo(u.getRol().getCodigo())
            .activo(Boolean.TRUE.equals(u.getActivo()))
            .emailVerificado(u.getEmailVerificadoEn() != null)
            .ultimoLogin(u.getUltimoLogin())
            .build();
    }
}
