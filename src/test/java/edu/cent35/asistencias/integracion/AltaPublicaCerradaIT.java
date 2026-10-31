package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.repository.InstitucionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Con el alta pública cerrada, como queda en el perfil de producción, la ruta deja de existir y el
 * login deja de ofrecerla. Se prueban las dos: un enlace escondido sigue siendo una ruta que responde.
 */
@SpringBootTest(properties = "app.alta.publica=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AltaPublicaCerradaIT {

    @Autowired private MockMvc mvc;
    @Autowired private InstitucionRepository institucionRepository;

    @Test
    @DisplayName("El alta responde 404, también por POST, y no crea nada")
    void laRutaNoExiste() throws Exception {
        long antes = institucionRepository.count();

        mvc.perform(get("/alta-institucion")).andExpect(status().isNotFound());
        mvc.perform(get("/alta-institucion/codigo")).andExpect(status().isNotFound());
        mvc.perform(post("/alta-institucion").with(csrf())
                .param("nombreInstitucion", "Instituto que no tendria que existir")
                .param("email", "alta@ejemplo.test"))
            .andExpect(status().isNotFound());

        assertThat(institucionRepository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("El login no ofrece un trámite que no se atiende")
    void elLoginNoLaOfrece() throws Exception {
        mvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("/alta-institucion"))));
    }
}
