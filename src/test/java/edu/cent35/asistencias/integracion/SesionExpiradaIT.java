package edu.cent35.asistencias.integracion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Qué ve quien se queda sin sesión (heurística 1: el sistema dice lo que está pasando).
 *
 * <p>Antes, a los 30 minutos sin pedidos, la pantalla siguiente era el formulario de ingreso
 * sin una palabra. Quien estaba trabajando no sabía si se había cortado algo, si lo habían
 * echado o si había tocado algo mal.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SesionExpiradaIT {

    @Autowired private MockMvc mockMvc;

    /** El navegador sigue mandando el identificador de una sesión que el servidor ya no tiene. */
    private static final RequestPostProcessor CON_SESION_VENCIDA = peticion -> {
        peticion.setRequestedSessionId("una-sesion-que-ya-vencio");
        peticion.setRequestedSessionIdValid(false);
        return peticion;
    };

    @Test
    @DisplayName("Con la sesión vencida, la pantalla lleva al login diciendo por qué")
    void conSesionVencidaElLoginDiceQueSeCerro() throws Exception {
        mockMvc.perform(get("/docentes").header("Accept", "text/html").with(CON_SESION_VENCIDA))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?expirada"));
    }

    @Test
    @DisplayName("Sin sesión previa no se habla de vencimiento: nadie estaba adentro")
    void sinSesionPreviaNoDiceQueVencio() throws Exception {
        mockMvc.perform(get("/docentes").header("Accept", "text/html"))
            .andExpect(status().is3xxRedirection())
            .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                .endsWith("/login"));
    }

    @Test
    @DisplayName("El login explica el cierre solo cuando viene de una sesión vencida")
    void elLoginExplicaElCierre() throws Exception {
        mockMvc.perform(get("/login").param("expirada", ""))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Se cerró tu sesión")));

        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("Se cerró tu sesión"))));
    }

    @Test
    @DisplayName("Renovar la sesión contesta 204 con sesión y 401 sin ella, nunca el login")
    void renovarLaSesion() throws Exception {
        mockMvc.perform(get("/sesion/mantener").with(user("institucion").roles("INSTITUCION")))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/sesion/mantener").header("Accept", "application/json"))
            .andExpect(status().isUnauthorized());

        // Con la sesión vencida también: del otro lado hay un fetch que tiene que poder
        // distinguir "renovada" de "ya estaba cerrada", y una redirección no se distingue.
        mockMvc.perform(get("/sesion/mantener").with(CON_SESION_VENCIDA))
            .andExpect(status().isUnauthorized());
    }
}
