package edu.cent35.asistencias.integracion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Qué contesta un endpoint JSON cuando la sesión ya no está.
 *
 * <p>Por defecto Spring Security responde toda petición sin autenticar con una redirección al
 * login. Para una pantalla es lo correcto; para un {@code fetch} es un fallo mudo: fetch sigue
 * la redirección, recibe el HTML del login con estado 200, y del lado del cliente
 * {@code resp.ok} da true. El {@code resp.json()} siguiente revienta con un error de sintaxis
 * que el {@code catch} de la pantalla toma por un corte de red y no muestra.
 *
 * <p>En el pase eso se veía así: la cámara encendida mandando un cuadro por segundo, ninguna
 * asistencia registrada y la pantalla sin decir nada, con la clase esperando.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SesionVencidaEnApiIT {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("El pase contesta 401 con JSON, no la redirección al login")
    void elPaseContesta401() throws Exception {
        mockMvc.perform(post("/asistencia/pase/marcar")
                        .with(csrf()).header("Accept", "*/*")
                        .contentType("application/json").content("{\"imagen\":\"x\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                .as("el cuerpo tiene que ser JSON para que la pantalla pueda leerlo")
                .contains("SESION_VENCIDA"));
    }

    @Test
    @DisplayName("La detección de rostro y el registro también")
    void laCapturaBiometricaContesta401() throws Exception {
        mockMvc.perform(post("/reconocimiento/detectar")
                        .with(csrf()).header("Accept", "*/*")
                        .contentType("application/json").content("{\"imagen\":\"x\"}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/docentes/7/rostro/registrar")
                        .with(csrf()).header("Accept", "*/*")
                        .contentType("application/json").content("{\"capturas\":[]}"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * La navegación de pantallas sigue redirigiendo al login.
     *
     * <p>Es el riesgo real del cambio, y no era obvio: al registrar una entrada de
     * autenticación propia, Spring la toma también como comportamiento por defecto de todo lo
     * que no pide HTML. Si esa red se corriera un poco más --si alcanzara también a la
     * navegación-- abrir cualquier pantalla con la sesión vencida devolvería un JSON crudo en
     * lugar del formulario de ingreso. Eso sería mucho peor que el problema original.
     *
     * <p>Se comprueban las dos mitades en el mismo test a propósito: lo que importa no es cada
     * una por separado sino que sigan estando separadas.
     */
    @Test
    @DisplayName("Una pantalla redirige al login y una llamada JSON no")
    void lasPantallasSiguenRedirigiendo() throws Exception {
        for (String pantalla : List.of("/", "/docentes", "/asistencias", "/asistencia/pase")) {
            mockMvc.perform(get(pantalla).header("Accept", "text/html,application/xhtml+xml"))
                .andExpect(status().is3xxRedirection());
        }

        mockMvc.perform(post("/asistencia/pase/marcar").with(csrf()).header("Accept", "*/*")
                        .contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
    }
}
