package edu.cent35.asistencias.integracion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lo que el sistema le pide al navegador en cada respuesta, y el chequeo de salud.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EncabezadosDeSeguridadIT {

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("La política de contenido lleva el nonce que traen los scripts de la página")
    void elNonceDeLaPoliticaEsElDeLaPagina() throws Exception {
        MvcResult respuesta = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();

        String politica = respuesta.getResponse().getHeader("Content-Security-Policy");
        assertThat(politica).contains("frame-ancestors 'none'").contains("object-src 'none'");

        Matcher enPolitica = Pattern.compile("'nonce-([^']+)'").matcher(politica);
        assertThat(enPolitica.find()).isTrue();

        // Si no coinciden, el navegador no corre el script del tema y la página parpadea.
        assertThat(respuesta.getResponse().getContentAsString())
            .contains("<script nonce=\"" + enPolitica.group(1) + "\">");
    }

    @Test
    @DisplayName("El nonce cambia en cada pedido")
    void elNonceCambia() throws Exception {
        String una = mvc.perform(get("/login")).andReturn().getResponse()
            .getHeader("Content-Security-Policy");
        String otra = mvc.perform(get("/login")).andReturn().getResponse()
            .getHeader("Content-Security-Policy");

        assertThat(una).isNotEqualTo(otra);
    }

    @Test
    @DisplayName("La cámara se permite solo al propio sitio, también en el kiosco")
    void laCamaraSoloAlPropioSitio() throws Exception {
        for (String ruta : new String[] {"/login", "/kiosco"}) {
            mvc.perform(get(ruta))
                .andExpect(header().string("Permissions-Policy", org.hamcrest.Matchers.containsString("camera=(self)")))
                .andExpect(header().string("Referrer-Policy", "same-origin"))
                .andExpect(header().exists("Content-Security-Policy"));
        }
    }

    @Test
    @DisplayName("Sin servidor de correo el sistema sigue sano: un corte del SMTP no es una caída")
    void laSaludNoDependeDelCorreo() throws Exception {
        // En los tests no hay ningún SMTP: con el indicador de correo prendido, esto da 503.
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
