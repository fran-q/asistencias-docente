package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.repository.CambioIdentidadRepository;
import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Persona;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.PersonaRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre la confirmación que aparece cuando una operación alcanza una identidad que ya existe
 * (ADR-0016), sobre el HTML que de verdad sale del servidor.
 *
 * <p><b>Por qué existe.</b> Antes de esta pantalla, un DNI mal tipeado en el alta de un docente
 * le reescribía el nombre a la persona a la que ese documento sí pertenecía, en silencio. El
 * caso se prueba de punta a punta —y no solo en el servicio— porque la mitad del mecanismo es
 * que el formulario vuelva entero desde la pantalla de aviso: si esos campos ocultos se
 * perdieran, el segundo intento guardaría datos vacíos y ningún test de servicio lo notaría.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfirmacionIdentidadIT {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(7000);

    @Autowired private MockMvc mockMvc;
    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private PersonaRepository personaRepository;
    @Autowired private DocenteRepository docenteRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CambioIdentidadRepository cambioIdentidadRepository;

    private Long tenantId;
    private String dniOcupado;

    @BeforeEach
    void prepararInstitucionConUnaPersona() {
        int n = SECUENCIA.incrementAndGet();
        Institucion i = institucionRepository.save(Institucion.builder()
            .nombre("Instituto confirmacion " + n)
            .activo(true).build());
        tenantId = i.getId();
        dniOcupado = "3900000" + n;

        TenantContext.set(tenantId);
        // Una persona que ya está en el sistema, con un vínculo docente cerrado: es el escenario
        // donde el alta no puede decidir sola si es un reingreso o un error de tipeo.
        //
        // Se guardan juntos, en una sola operación: la persona viaja con el vínculo por el
        // cascade PERSIST. Persistirla aparte primero la dejaría desligada para cuando se guarde
        // el docente, y Hibernate rechazaría el cascade sobre una entidad que ya tiene id.
        Docente cerrado = Docente.builder()
            .persona(DatosDePrueba.personaConDni(dniOcupado, "Juan", "Gomez"))
            .fechaAlta(LocalDate.now().minusYears(2))
            .fechaBaja(LocalDate.now().minusYears(1)).activo(false).build();
        cerrado.setInstitucionId(tenantId);
        docenteRepository.save(cerrado);
        TenantContext.clear();
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Un DNI ya cargado detiene el alta y muestra a quién pertenece")
    void altaConDniOcupado_muestraLaConfirmacion() throws Exception {
        mockMvc.perform(post("/docentes/nuevo").with(user(principal())).with(csrf())
                .param("dni", dniOcupado)
                .param("nombre", "Ana")
                .param("apellido", "Perez"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Ese documento ya está registrado")))
            // Lo que convierte el aviso en útil: se ve el nombre del que ya estaba enfrentado
            // al que se acaba de tipear. "Ya existe" a secas no dejaría notar el error.
            .andExpect(content().string(containsString("Gomez, Juan")))
            .andExpect(content().string(containsString("Perez, Ana")))
            .andExpect(content().string(containsString("Los nombres no coinciden")));

        TenantContext.set(tenantId);
        assertThat(personaRepository.buscarPorDni(tenantId, dniOcupado).orElseThrow().getNombre())
            .as("mientras no se confirme, la persona que ya estaba no se toca")
            .isEqualTo("Juan");
        assertThat(docenteRepository.periodosDe(tenantId,
                   personaRepository.buscarPorDni(tenantId, dniOcupado).orElseThrow().getId()))
            .as("y no se abrio ningun vinculo nuevo")
            .hasSize(1);
    }

    @Test
    @DisplayName("Confirmado, reutiliza la identidad y le abre un período nuevo")
    void altaConfirmada_reutilizaLaPersona() throws Exception {
        mockMvc.perform(post("/docentes/nuevo").with(user(principal())).with(csrf())
                .param("dni", dniOcupado)
                .param("nombre", "Juan Carlos")
                .param("apellido", "Gomez")
                .param("confirmado", "true"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/docentes"));

        TenantContext.set(tenantId);
        Persona p = personaRepository.buscarPorDni(tenantId, dniOcupado).orElseThrow();
        assertThat(p.getNombre())
            .as("confirmado, si se actualizan los datos con lo que vino del formulario")
            .isEqualTo("Juan Carlos");
        assertThat(docenteRepository.periodosDe(tenantId, p.getId()))
            .as("el reingreso es un periodo nuevo, no pisa el anterior")
            .hasSize(2);
    }

    @Test
    @DisplayName("Un DNI libre no pide ninguna confirmación")
    void altaConDniLibre_pasaDerecho() throws Exception {
        mockMvc.perform(post("/docentes/nuevo").with(user(principal())).with(csrf())
                .param("dni", "3111111" + SECUENCIA.incrementAndGet())
                .param("nombre", "Marcela")
                .param("apellido", "Ruiz"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/docentes"));
    }


    // ========================================================================
    //  Trazabilidad: que quede registro de lo que se cambio
    // ========================================================================

    @Test
    @DisplayName("Editar la identidad deja una fila por campo modificado, con quien lo hizo")
    void edicion_dejaRastroDeCadaCampo() throws Exception {
        TenantContext.set(tenantId);
        Docente d = docenteRepository.listarDelTenant(tenantId).get(0);
        Long personaId = d.getPersona().getId();
        TenantContext.clear();

        mockMvc.perform(post("/docentes/" + d.getId() + "/editar")
                .with(user(principal())).with(csrf())
                .param("dni", dniOcupado)
                .param("nombre", "Juan Carlos")
                .param("apellido", "Gomez")
                .param("telefono", "2901555000"))
            .andExpect(status().is3xxRedirection());

        TenantContext.set(tenantId);
        var historial = cambioIdentidadRepository.historialDe(tenantId, personaId);

        // Dos campos cambiaron —nombre y telefono— y el apellido quedo igual: tienen que ser
        // dos filas y no tres. Registrar un campo que no cambio ensucia el historial y hace
        // que buscar en el sea inutil.
        assertThat(historial).hasSize(2);
        assertThat(historial).extracting("campo")
            .containsExactlyInAnyOrder("nombre", "telefono");

        var cambioDelNombre = historial.stream()
            .filter(c -> c.getCampo().equals("nombre")).findFirst().orElseThrow();
        assertThat(cambioDelNombre.getValorAnterior())
            .as("lo que decia antes es justamente el dato que sirve para reconstruir")
            .isEqualTo("Juan");
        assertThat(cambioDelNombre.getValorNuevo()).isEqualTo("Juan Carlos");
        assertThat(cambioDelNombre.getUsuarioId()).isEqualTo(99L);
        assertThat(cambioDelNombre.getOrigen()).isEqualTo("DOCENTE");
        assertThat(cambioDelNombre.getFecha()).isNotNull();
    }

    @Test
    @DisplayName("Guardar sin cambiar nada no deja ninguna fila")
    void edicionSinCambios_noDejaRastro() throws Exception {
        TenantContext.set(tenantId);
        Docente d = docenteRepository.listarDelTenant(tenantId).get(0);
        Long personaId = d.getPersona().getId();
        TenantContext.clear();

        mockMvc.perform(post("/docentes/" + d.getId() + "/editar")
                .with(user(principal())).with(csrf())
                .param("dni", dniOcupado)
                .param("nombre", "Juan")
                .param("apellido", "Gomez"))
            .andExpect(status().is3xxRedirection());

        TenantContext.set(tenantId);
        assertThat(cambioIdentidadRepository.historialDe(tenantId, personaId))
            .as("abrir el formulario y guardar sin tocar nada no es un cambio")
            .isEmpty();
    }

    @Test
    @DisplayName("La baja guarda quien la ejecuto")
    void baja_guardaElAutor() throws Exception {
        TenantContext.set(tenantId);
        Docente d = docenteRepository.listarDelTenant(tenantId).stream()
            .filter(x -> Boolean.FALSE.equals(x.getActivo())).findFirst().orElseThrow();
        // Se reactiva para poder darlo de baja de nuevo desde la pantalla.
        d.setActivo(true);
        d.setFechaBaja(null);
        docenteRepository.save(d);
        TenantContext.clear();

        mockMvc.perform(post("/docentes/" + d.getId() + "/baja")
                .with(user(principal())).with(csrf())
                .param("fechaBaja", LocalDate.now().toString()))
            .andExpect(status().is3xxRedirection());

        TenantContext.set(tenantId);
        Docente despues = docenteRepository.buscarDelTenant(tenantId, d.getId()).orElseThrow();
        assertThat(despues.getActivo()).isFalse();
        assertThat(despues.getDadoDeBajaPor())
            .as("antes quedaba la fecha de la baja pero no quien la hizo")
            .isEqualTo(99L);
    }

    private UsuarioAutenticado principal() {
        // El rol se arma en memoria y no se busca en la base: en el perfil de test Flyway esta
        // apagado y el esquema lo genera Hibernate, asi que el catalogo de roles no existe. Solo
        // hace falta para el principal de seguridad, que no lo persiste.
        Rol r = new Rol();
        r.setId((short) 1);
        r.setCodigo("INSTITUCION");
        r.setDescripcion("INSTITUCION");
        Usuario u = Usuario.builder()
            .persona(DatosDePrueba.persona("Cuenta", "Prueba"))
            .id(99L).username("test.confirmacion")
            .email("test.confirmacion@ejemplo.edu.ar")
            .passwordHash("no-se-usa").activo(true).rol(r)
            .emailVerificadoEn(java.time.LocalDateTime.now())
            .build();
        u.setInstitucionId(tenantId);
        return new UsuarioAutenticado(u);
    }
}
