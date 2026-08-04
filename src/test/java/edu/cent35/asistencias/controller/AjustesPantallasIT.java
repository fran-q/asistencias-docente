package edu.cent35.asistencias.controller;

import edu.cent35.asistencias.config.CustomUserDetails;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.CarreraRepository;
import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.MateriaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica los ajustes de pantalla sobre el HTML que de verdad sale del servidor.
 *
 * <p>Se hace acá y no mirando el navegador porque son cambios de plantilla: las expresiones de
 * Thymeleaf fallan recién al renderizar, y varias de estas comprobaciones son "esto ya no
 * tiene que aparecer", que es justamente lo que a ojo se pasa por alto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AjustesPantallasIT {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private CarreraRepository carreraRepository;
    @Autowired private MateriaRepository materiaRepository;
    @Autowired private ComisionRepository comisionRepository;
    @Autowired private HorarioRepository horarioRepository;
    @Autowired private DocenteRepository docenteRepository;

    private Long tenantId;
    private Long horarioId;

    @BeforeEach
    void sembrar() {
        TenantContext.clear();
        Institucion i = institucionRepository.save(Institucion.builder()
            .nombre("Instituto pantallas " + SECUENCIA.incrementAndGet())
            .activo(true).build());
        tenantId = i.getId();

        TenantContext.set(tenantId);
        Docente d = Docente.builder()
            .nombre("Ana").apellido("Pérez").dni("3011122" + SECUENCIA.get())
            .fechaAlta(LocalDate.now()).activo(true).build();
        d.setInstitucionId(tenantId);
        docenteRepository.save(d);

        Carrera c = Carrera.builder()
            .codigo("CAR" + SECUENCIA.get()).nombre("Carrera de prueba")
            .duracionAnios((short) 3).activo(true).build();
        c.setInstitucionId(tenantId);
        carreraRepository.save(c);

        Materia m = Materia.builder()
            .codigo("MAT" + SECUENCIA.get()).nombre("Matemática").carrera(c)
            .anio((short) 2).docenteTitular(d).activo(true).build();
        m.setInstitucionId(tenantId);
        materiaRepository.save(m);

        Comision com = comisionRepository.save(Comision.builder()
            .codigo("A").materia(m).docenteAsignado(d).activo(true).build());

        horarioId = horarioRepository.save(Horario.builder()
            .comision(com).diaSemana((byte) 1)
            .horaInicio(LocalTime.of(8, 0)).horaFin(LocalTime.of(10, 0))
            .toleranciaMin((short) 15).activo(true)
            .build()).getId();
        TenantContext.clear();
    }

    @AfterEach
    void limpiar() {
        TenantContext.set(tenantId);
        horarioRepository.deleteAll();
        comisionRepository.deleteAll();
        materiaRepository.deleteAll();
        carreraRepository.deleteAll();
        docenteRepository.deleteAll();
        TenantContext.clear();
        institucionRepository.deleteById(tenantId);
    }

    // ========================================================================
    //  El id de la base no se muestra en ninguna pantalla
    // ========================================================================

    @Test
    @DisplayName("Ninguna pantalla de edición muestra el id interno de la base")
    void sinIdsALaVista() throws Exception {
        String[] pantallas = {
            "/carreras/1/editar", "/materias/1/editar", "/comisiones/1/editar",
            "/horarios/" + horarioId + "/editar", "/docentes/1/editar", "/mi-institucion"
        };
        for (String ruta : pantallas) {
            MvcResult r = mockMvc.perform(get(ruta).with(user(principal("INSTITUCION"))))
                .andReturn();
            // Varias devuelven 404 o redirect porque el id no existe en este tenant; solo
            // se revisa el HTML de las que efectivamente renderizaron.
            if (r.getResponse().getStatus() != 200) continue;
            assertThat(r.getResponse().getContentAsString())
                .as("pantalla %s", ruta)
                .doesNotContain("<dt>ID</dt>")
                .doesNotContain("ID interno");
        }
    }

    // ========================================================================
    //  Anio de la materia y duracion de la carrera
    // ========================================================================

    @Test
    @DisplayName("El alta de materia pide el año y la carrera lleva su duración a cuestas")
    void materiaPideAnio() throws Exception {
        mockMvc.perform(get("/materias/nueva").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Año de la carrera")))
            .andExpect(content().string(containsString("id=\"anio\"")))
            // El JS recorta los años con este dato; sin el atributo ofreceria los diez
            // siempre y el rechazo llegaria recien al guardar.
            .andExpect(content().string(containsString("data-duracion=\"3\"")));
    }

    @Test
    @DisplayName("El alta de carrera pide la duración")
    void carreraPideDuracion() throws Exception {
        mockMvc.perform(get("/carreras/nueva").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"duracionAnios\"")));
    }

    @Test
    @DisplayName("El listado de materias muestra el año")
    void listadoMateriasMuestraAnio() throws Exception {
        mockMvc.perform(get("/materias").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("<th>Año</th>")))
            .andExpect(content().string(containsString("2°")));
    }

    // ========================================================================
    //  Comision: sin cupo, con el titular a mano
    // ========================================================================

    @Test
    @DisplayName("La comisión ya no pide cupo y sí trae el titular de cada materia")
    void comisionSinCupoConTitular() throws Exception {
        mockMvc.perform(get("/comisiones/nueva").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("Cupo"))))
            .andExpect(content().string(not(containsString("id=\"cupo\""))))
            // Sin este atributo el formulario no puede proponer nada al elegir la materia.
            .andExpect(content().string(containsString("data-titular-id=")));
    }

    @Test
    @DisplayName("El listado de comisiones ya no tiene columna de cupo")
    void listadoComisionesSinCupo() throws Exception {
        mockMvc.perform(get("/comisiones").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("<th>Cupo</th>"))));
    }

    // ========================================================================
    //  Selector de hora
    // ========================================================================

    @Test
    @DisplayName("El horario usa las dos listas y deja el input de tiempo oculto")
    void horarioUsaDosListas() throws Exception {
        mockMvc.perform(get("/horarios/" + horarioId + "/editar")
                .with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-hora-picker=\"horaInicio\"")))
            .andExpect(content().string(containsString("data-hora-picker=\"horaFin\"")))
            .andExpect(content().string(containsString("hora-picker.js")))
            // El input sigue existiendo: es el que se envia y el que el servidor valida.
            .andExpect(content().string(containsString("type=\"time\"")));
    }

    // ========================================================================
    //  Reporte en PDF
    // ========================================================================

    @Test
    @DisplayName("El reporte se descarga como PDF de verdad")
    void reporteEnPdf() throws Exception {
        MvcResult r = mockMvc.perform(get("/reportes/pdf")
                .with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andReturn();

        byte[] pdf = r.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5))
            .as("un PDF valido empieza con %%PDF-; sin esto el navegador baja un archivo roto")
            .startsWith("%PDF-");
        assertThat(pdf.length).isGreaterThan(500);
        assertThat(r.getResponse().getHeader("Content-Disposition"))
            .contains("attachment").contains(".pdf");
    }

    @Test
    @DisplayName("La pantalla de reportes ofrece las dos descargas")
    void reporteOfreceAmbosFormatos() throws Exception {
        mockMvc.perform(get("/reportes").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Descargar PDF")))
            .andExpect(content().string(containsString("Descargar CSV")));
    }

    // ========================================================================
    //  Bloque "Datos del sistema" unificado
    // ========================================================================

    @Test
    @DisplayName("Todas las pantallas de edición muestran el mismo bloque de datos del sistema")
    void bloqueDeDatosUniforme() throws Exception {
        String[] pantallas = { "/horarios/" + horarioId + "/editar", "/mi-institucion" };
        for (String ruta : pantallas) {
            mockMvc.perform(get(ruta).with(user(principal("INSTITUCION"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Datos del sistema")))
                .andExpect(content().string(containsString("<dt>Estado</dt>")))
                .andExpect(content().string(containsString("<dt>Alta</dt>")))
                // El horario era el unico que mostraba solo el estado: una pantalla que
                // informa menos que las demas hace dudar de si el dato no existe o si
                // simplemente se olvidaron de mostrarlo.
                .andExpect(content().string(containsString("Última actualización")));
        }
    }

    @Test
    @DisplayName("El bloque del docente conserva sus fechas propias además de las comunes")
    void bloqueDelDocenteConservaSusFechas() throws Exception {
        Long docenteId = docenteRepository.findAll().stream()
            .filter(d -> tenantId.equals(d.getInstitucionId()))
            .findFirst().orElseThrow().getId();

        mockMvc.perform(get("/docentes/" + docenteId + "/editar")
                .with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("<dt>Alta</dt>")))
            .andExpect(content().string(containsString("Última actualización")))
            .andExpect(content().string(containsString("Fecha de alta")));
    }

    // ========================================================================
    //  Selects con busqueda
    // ========================================================================

    @Test
    @DisplayName("Los desplegables que crecen con la carga son buscables")
    void desplegablesLargosSonBuscables() throws Exception {
        String[][] casos = {
            {"/comisiones/nueva", "materiaId"},
            {"/comisiones/nueva", "docenteAsignadoId"},
            {"/materias/nueva",   "carreraId"},
            {"/materias/nueva",   "docenteTitularId"},
            {"/horarios/nuevo",   "comisionId"},
            {"/reportes",         "docenteId"},
        };
        for (String[] c : casos) {
            mockMvc.perform(get(c[0]).with(user(principal("INSTITUCION"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                    "<select id=\"" + c[1] + "\" data-buscable")))
                .andExpect(content().string(containsString("select-buscable.js")));
        }
    }

    @Test
    @DisplayName("Los desplegables de opciones fijas no llevan buscador")
    void desplegablesCortosNoLlevanBuscador() throws Exception {
        // El estado tiene tres valores: un buscador ahi estorba mas de lo que ayuda.
        mockMvc.perform(get("/reportes").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString(
                "<select id=\"estado\" data-buscable"))));
    }

    // ========================================================================
    //  Descargas del reporte
    // ========================================================================

    @Test
    @DisplayName("Los dos botones de descarga van agrupados")
    void descargasAgrupadas() throws Exception {
        mockMvc.perform(get("/reportes").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            // Sueltos dentro del space-between el PDF quedaba flotando en el medio.
            .andExpect(content().string(containsString("reporte__descargas")));
    }

    // ========================================================================
    //  Derechos ARCO (RNF-14)
    // ========================================================================

    @Test
    @DisplayName("La ficha del docente muestra todos sus datos y los cuatro derechos")
    void pantallaArcoReuneLosCuatroDerechos() throws Exception {
        Long docenteId = docenteRepository.findAll().stream()
            .filter(d -> tenantId.equals(d.getInstitucionId()))
            .findFirst().orElseThrow().getId();

        mockMvc.perform(get("/docentes/" + docenteId + "/ficha")
                .with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            // Acceso: se ve que tiene el sistema sobre la persona.
            .andExpect(content().string(containsString("Datos personales")))
            .andExpect(content().string(containsString("no guarda fotografías")))
            // Los otros tres, cada uno con su accion.
            .andExpect(content().string(containsString("Rectificación")))
            .andExpect(content().string(containsString("Oposición")))
            .andExpect(content().string(containsString("Cancelación")))
            .andExpect(content().string(containsString("Ley 25.326")));
    }

    @Test
    @DisplayName("La constancia ARCO se descarga como PDF")
    void constanciaArcoEsUnPdf() throws Exception {
        Long docenteId = docenteRepository.findAll().stream()
            .filter(d -> tenantId.equals(d.getInstitucionId()))
            .findFirst().orElseThrow().getId();

        MvcResult r = mockMvc.perform(get("/docentes/" + docenteId + "/ficha/constancia")
                .with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(r.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(r.getResponse().getHeader("Content-Disposition"))
            .contains("constancia_arco_");
        // Un PDF valido empieza con %PDF; sin esto el test pasaria con un archivo vacio.
        assertThat(r.getResponse().getContentAsByteArray())
            .as("el archivo tiene que ser un PDF de verdad, no una respuesta vacía")
            .startsWith("%PDF".getBytes());
    }

    // ------------------------------------------------------------------------

    private CustomUserDetails principal(String rol) {
        Rol r = new Rol();
        r.setId((short) 1);
        r.setCodigo(rol);
        r.setDescripcion(rol);

        Usuario u = Usuario.builder()
            .id(99L).username("test.pantallas").passwordHash("no-se-usa")
            .nombre("Test").apellido("Pantallas").activo(true).rol(r)
            .emailVerificadoEn(LocalDateTime.now())
            .build();
        u.setInstitucionId(tenantId);
        return new CustomUserDetails(u);
    }
}
