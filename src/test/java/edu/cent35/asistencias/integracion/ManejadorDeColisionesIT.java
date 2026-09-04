package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.controller.ManejadorDeColisiones;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.sql.SQLIntegrityConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica el comportamiento HTTP del manejador de colisiones: que un choque contra la base
 * devuelva a la pantalla anterior con el motivo en vez de una pantalla de error, sin que el
 * Referer sirva de puente para mandar a alguien fuera del sitio.
 */
class ManejadorDeColisionesIT {

    private MockMvc mockMvc;

    // Un controlador que solo sabe chocar: sirve para probar el manejador sin depender de
    // que alguna pantalla real tenga hoy una forma de provocar el choque.
    @Controller
    static class ControladorQueChoca {

        @PostMapping("/prueba/pantalla")
        public String pantalla() {
            throw colision();
        }

        @PostMapping("/prueba/datos")
        @ResponseBody
        public String datos() {
            throw colision();
        }

        private static DataIntegrityViolationException colision() {
            return new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException(
                    "Duplicate entry '1-30111222' for key 'uq_docentes_inst_dni'"));
        }
    }

    @BeforeEach
    void preparar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ControladorQueChoca())
            .setControllerAdvice(new ManejadorDeColisiones())
            .build();
    }

    @Test
    @DisplayName("Un choque devuelve a la pantalla anterior explicando que paso")
    void devuelveALaPantallaAnteriorConElMotivo() throws Exception {
        mockMvc.perform(post("/prueba/pantalla")
                .header("Referer", "http://localhost:8080/docentes/nuevo"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/docentes/nuevo"))
            .andExpect(flash().attribute("flashError",
                org.hamcrest.Matchers.containsString("DNI")));
    }

    @Test
    @DisplayName("Sin Referer vuelve al inicio en vez de romperse")
    void sinRefererVuelveAlInicio() throws Exception {
        mockMvc.perform(post("/prueba/pantalla"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("Un Referer de otro sitio no se usa como destino")
    void noRedirigeAOtroSitio() throws Exception {
        // Sin este control, una pagina externa podria mandar a alguien —con la sesion ya
        // iniciada— a un formulario ajeno que imite al nuestro.
        var respuesta = mockMvc.perform(post("/prueba/pantalla")
                .header("Referer", "http://sitio-ajeno.example/docentes/nuevo"))
            .andExpect(status().is3xxRedirection())
            .andReturn().getResponse();

        assertThat(respuesta.getRedirectedUrl())
            .as("el destino tiene que ser del propio sitio")
            .isEqualTo("/");
    }

    @Test
    @DisplayName("Al pase de asistencia, que consulta por JSON, no se le manda una redireccion")
    void alClienteDeDatosNoSeLeRedirige() {
        // El pase hace fetch con el cuerpo en JSON. Una redireccion a HTML le rompe el
        // resp.json() con un error que no explica nada, asi que el manejador no la atiende y
        // el error sigue su curso: en la aplicacion corriendo termina en un codigo de error,
        // que es lo que ese cliente sabe leer.
        assertThatThrownBy(() -> mockMvc.perform(post("/prueba/datos")
                .contentType("application/json")
                .content("{}")))
            .rootCause()
            .isInstanceOf(SQLIntegrityConstraintViolationException.class);
    }
}
