package edu.cent35.asistencias.seguridad;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El login acepta el usuario o el correo. Lo que distingue uno del otro es el "@", que un
 * usuario no puede llevar (UsuarioValido): con "@" se busca solo por correo, sin él solo por
 * usuario, y en los dos casos una coincidencia en dos instituciones no elige ninguna.
 */
@ExtendWith(MockitoExtension.class)
class CargadorDeUsuariosTest {

    @Mock private UsuarioRepository usuarioRepository;
    @InjectMocks private CargadorDeUsuarios cargador;

    @Test
    @DisplayName("con @ busca por correo, sin distinguir mayúsculas, y no por usuario")
    void conArrobaBuscaPorCorreo() {
        when(usuarioRepository.findByEmailIgnoreCase("Ana.Perez@Instituto.edu.ar"))
            .thenReturn(List.of(cuenta("ana.perez", true)));

        UserDetails principal = cargador.loadUserByUsername("  Ana.Perez@Instituto.edu.ar ");

        assertThat(principal.getUsername())
            .as("la sesion queda a nombre del usuario, no de lo que se tipeo")
            .isEqualTo("ana.perez");
        verify(usuarioRepository, never()).findByUsername(anyString());
    }

    @Test
    @DisplayName("sin @ busca por usuario, como siempre, y no por correo")
    void sinArrobaBuscaPorUsuario() {
        when(usuarioRepository.findByUsername("ana.perez")).thenReturn(List.of(cuenta("ana.perez", true)));

        assertThat(cargador.loadUserByUsername("ana.perez").getUsername()).isEqualTo("ana.perez");
        verify(usuarioRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    @DisplayName("un correo cargado en dos instituciones no elige ninguna")
    void correoAmbiguoNoEntra() {
        when(usuarioRepository.findByEmailIgnoreCase("ana@x.test"))
            .thenReturn(List.of(cuenta("ana.a", true), cuenta("ana.b", true)));

        assertThatThrownBy(() -> cargador.loadUserByUsername("ana@x.test"))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessage("Usuario o contraseña incorrectos");
    }

    @Test
    @DisplayName("un correo que no existe, o de una cuenta de baja, falla igual que un usuario")
    void correoInexistenteOInactivo() {
        when(usuarioRepository.findByEmailIgnoreCase("nadie@x.test")).thenReturn(List.of());
        when(usuarioRepository.findByEmailIgnoreCase("baja@x.test")).thenReturn(List.of(cuenta("baja", false)));

        assertThatThrownBy(() -> cargador.loadUserByUsername("nadie@x.test"))
            .hasMessage("Usuario o contraseña incorrectos");
        assertThatThrownBy(() -> cargador.loadUserByUsername("baja@x.test"))
            .as("el mismo mensaje: la respuesta no dice qué cuentas existen")
            .hasMessage("Usuario o contraseña incorrectos");
    }

    private static Usuario cuenta(String username, boolean activo) {
        Rol rol = Rol.builder().codigo("ADMIN").descripcion("Administrador").build();
        Usuario u = Usuario.builder()
            .persona(DatosDePrueba.persona("Ana", "Pérez"))
            .id(1L).username(username).email(username + "@x.test").passwordHash("hash")
            .activo(activo).rol(rol).build();
        u.setInstitucionId(10L);
        return u;
    }
}
