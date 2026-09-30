package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.RolCodigo;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.service.InstalacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * El asistente de primer arranque (ADR-0022), con la instalación habilitándolo.
 *
 * <p><b>Qué cuida.</b> Que una instalación recién hecha tenga por dónde crear su primera
 * cuenta sin correo y sin terminal, y que esa puerta se cierre sola apenas hay una institución.
 * Lo segundo es lo que importa de verdad: una pantalla pública que crea instituciones y que
 * quede abierta después de la primera es una cuenta gratis para cualquiera.
 *
 * <p>Lo contrario —que con la propiedad apagada el asistente no exista— vive en
 * {@link InstalacionApagadaIT}, porque es otro contexto de Spring.
 */
@SpringBootTest(properties = "app.instalacion.asistente-inicial=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Asistente de primer arranque")
class InstalacionInicialIT {

    @Autowired MockMvc mvc;
    @Autowired InstitucionRepository institucionRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RolRepository rolRepository;
    @Autowired InstalacionService instalacion;

    @BeforeEach
    void dejarLaBaseComoReciénInstalada() {
        TenantContext.clear();
        usuarioRepository.deleteAll();
        institucionRepository.deleteAll();
        // El servicio recuerda que ya hubo una institución para no consultar la base en cada
        // request. Ese recuerdo es correcto en producción --una institución no deja de
        // existir-- y acá hay que deshacerlo a mano, porque los tests sí vacían la base.
        ReflectionTestUtils.setField(instalacion, "yaHayInstitucion", false);

        // El perfil de test corre con Flyway apagado y el esquema generado por Hibernate, asi
        // que la semilla de roles de V001 no existe. Sin el rol INSTITUCION el asistente falla
        // con el mismo error que una base a medio migrar, que no es lo que se esta probando.
        rolRepository.findByCodigo(RolCodigo.INSTITUCION.name())
            .orElseGet(() -> rolRepository.save(
                Rol.builder().codigo(RolCodigo.INSTITUCION.name()).descripcion("Institución").build()));
    }

    @Test
    @DisplayName("sin ninguna institución, el asistente atiende")
    void atiendeEnUnaInstalacionNueva() throws Exception {
        mvc.perform(get("/instalacion"))
            .andExpect(status().isOk())
            .andExpect(view().name("auth/instalacion"));
    }

    @Test
    @DisplayName("el login lleva al asistente, en vez de pedir una cuenta que no existe")
    void elLoginRedirigeAlAsistente() throws Exception {
        mvc.perform(get("/login"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/instalacion"));
    }

    @Test
    @DisplayName("crea la institución y una cuenta que puede entrar sin verificar nada")
    void creaLaInstitucionYSuCuenta() throws Exception {
        mvc.perform(post("/instalacion").with(csrf())
                .param("nombreInstitucion", "Instituto de la instalacion")
                .param("cuit", "")
                .param("username", "instituto")
                .param("email", "instalacion@ejemplo.test")
                .param("password", "Prueba123")
                .param("confirmacion", "Prueba123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));

        assertThat(institucionRepository.count()).isEqualTo(1);

        List<Usuario> usuarios = usuarioRepository.findAll();
        assertThat(usuarios).hasSize(1);
        Usuario creado = usuarios.get(0);
        assertThat(creado.getUsername()).isEqualTo("instituto");
        assertThat(creado.getRol().getCodigo()).isEqualTo(RolCodigo.INSTITUCION.name());
        assertThat(creado.getActivo()).isTrue();
        // Sin esto la cuenta nace bloqueada por VerificacionInterceptor, esperando un correo que
        // esta instalación no tiene por dónde mandar: quedaría instalada y sin forma de entrar.
        assertThat(creado.getEmailVerificadoEn()).isNotNull();
    }

    @Test
    @DisplayName("una contraseña que no coincide no crea nada")
    void noCreaNadaSiLasContrasenasNoCoinciden() throws Exception {
        mvc.perform(post("/instalacion").with(csrf())
                .param("nombreInstitucion", "Instituto que no se crea")
                .param("cuit", "")
                .param("username", "instituto")
                .param("email", "instalacion@ejemplo.test")
                .param("password", "Prueba123")
                .param("confirmacion", "Prueba124"))
            .andExpect(status().isOk())
            .andExpect(view().name("auth/instalacion"));

        assertThat(institucionRepository.count()).isZero();
    }

    @Test
    @DisplayName("una vez configurada, el asistente deja de existir y el login vuelve a ser el login")
    void seApagaSoloDespuesDeLaPrimera() throws Exception {
        mvc.perform(post("/instalacion").with(csrf())
                .param("nombreInstitucion", "Instituto ya configurado")
                .param("cuit", "")
                .param("username", "instituto")
                .param("email", "instalacion@ejemplo.test")
                .param("password", "Prueba123")
                .param("confirmacion", "Prueba123"))
            .andExpect(status().is3xxRedirection());

        // 404 y no 403: una pantalla que ya no corresponde no tiene por qué admitir que existió.
        mvc.perform(get("/instalacion")).andExpect(status().isNotFound());
        mvc.perform(post("/instalacion").with(csrf())
                .param("nombreInstitucion", "Segunda institucion")
                .param("cuit", "")
                .param("username", "segunda")
                .param("email", "otra@ejemplo.test")
                .param("password", "Prueba123")
                .param("confirmacion", "Prueba123"))
            .andExpect(status().isNotFound());

        assertThat(institucionRepository.count()).isEqualTo(1);
        mvc.perform(get("/login")).andExpect(status().isOk());
    }
}
