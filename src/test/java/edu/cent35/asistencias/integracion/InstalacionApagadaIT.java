package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El asistente de primer arranque, <b>apagado</b>: que es como corre en desarrollo y como tiene
 * que correr en cualquier despliegue expuesto a internet.
 *
 * <p>Esta clase existe por una sola razón, y es la peor forma de fallar que tiene el ADR-0022:
 * el asistente crea una institución y su cuenta administradora sin pedir credenciales. Sobre
 * una base vacía y encendido por descuido, eso es una institución gratis para el primero que
 * pase. Acá se comprueba que sin la propiedad no existe <b>aunque la base esté vacía</b>, que
 * es justo la condición en la que el otro test lo ve funcionar.
 *
 * <p>No se fija la propiedad a {@code false}: se la deja como viene de
 * {@code application.properties}, porque lo que se está comprobando es el valor por defecto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Asistente de primer arranque, apagado")
class InstalacionApagadaIT {

    @Autowired MockMvc mvc;
    @Autowired InstitucionRepository institucionRepository;
    @Autowired UsuarioRepository usuarioRepository;

    @BeforeEach
    void vaciarLaBase() {
        TenantContext.clear();
        usuarioRepository.deleteAll();
        institucionRepository.deleteAll();
    }

    @Test
    @DisplayName("no existe ni con la base vacía")
    void noExisteSinLaPropiedad() throws Exception {
        mvc.perform(get("/instalacion")).andExpect(status().isNotFound());
        mvc.perform(post("/instalacion").with(csrf())
                .param("nombreInstitucion", "Instituto que no deberia crearse")
                .param("cuit", "")
                .param("username", "instituto")
                .param("email", "instalacion@ejemplo.test")
                .param("password", "Prueba123")
                .param("confirmacion", "Prueba123"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("y el login sigue siendo el login")
    void elLoginNoSeRedirige() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("la recuperación por clave tampoco existe")
    void laRecuperacionPorClaveNoExiste() throws Exception {
        // Donde hay correo, recuperar va por codigo. Dos caminos abiertos a la vez serian
        // superficie de mas sin que nadie la necesite.
        mvc.perform(get("/recuperar/clave")).andExpect(status().isNotFound());
        mvc.perform(post("/recuperar/clave").with(csrf())
                .param("username", "instituto")
                .param("clave", "ABCDE-ABCDE-ABCDE-ABCDE")
                .param("nuevaPassword", "Prueba123")
                .param("confirmacion", "Prueba123"))
            .andExpect(status().isNotFound());
    }
}
