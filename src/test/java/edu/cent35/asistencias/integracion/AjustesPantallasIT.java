package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.DatosDePrueba;
import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Carrera;
import edu.cent35.asistencias.model.CicloLectivo;
import edu.cent35.asistencias.model.PeriodoLectivo;
import edu.cent35.asistencias.repository.CicloLectivoRepository;
import edu.cent35.asistencias.repository.PeriodoLectivoRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
    @Autowired private CicloLectivoRepository cicloLectivoRepository;
    @Autowired private PeriodoLectivoRepository periodoLectivoRepository;
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
        Docente d = Docente.builder().persona(DatosDePrueba.personaConDni("3011122" + SECUENCIA.get(), "Ana", "Pérez")).fechaAlta(LocalDate.now()).activo(true).build();
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
            .codigo("A").materia(m).docenteAsignado(d).activo(true)
            .periodo(periodoDe(tenantId)).build());

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
            // ">Año</th>" y no "<th>Año</th>": los encabezados de los listados llevan
            // data-orden para el orden por columna, así que fijar la etiqueta de apertura
            // ata el test al marcado en vez de a lo que quiere comprobar, que es que la
            // columna esté y traiga el dato.
            .andExpect(content().string(containsString(">Año</th>")))
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
            // Esta era la comprobación que MENOS se notaba que se había roto: al agregarle
            // data-orden a los encabezados, un "<th>Cupo</th>" reaparecido se escribiría
            // "<th data-orden=...>Cupo</th>" y esta línea seguiría pasando sin proteger nada.
            // Una aserción negativa que deja de coincidir no falla: se vuelve muda.
            .andExpect(content().string(not(containsString(">Cupo</th>"))));
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
            // El recuadro "Datos biométricos" se saco de la ficha: repetia lo que ya
            // muestra la pantalla de edicion del docente. Lo que si tiene que seguir
            // estando es el acceso a las cuatro operaciones sobre el dato sensible.
            .andExpect(content().string(containsString("Acciones sobre datos biométricos")))
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

    // ========================================================================
    //  Donde aparece cada mensaje
    // ========================================================================

    @Test
    @DisplayName("Un error de formulario se queda sobre el formulario, no sale por toast")
    void errorDeFormularioVaEnElFormulario() throws Exception {
        // Codigo repetido: es una regla de negocio que se descubre al guardar, pero sigue
        // siendo un problema del formulario que hay que poder releer mientras se corrige.
        MvcResult r = mockMvc.perform(post("/carreras/nueva")
                .with(user(principal("INSTITUCION"))).with(csrf())
                .param("codigo", "CAR" + SECUENCIA.get())
                .param("nombre", "Otra carrera con el mismo código")
                .param("duracionAnios", "3"))
            .andExpect(status().isOk())          // se queda en el formulario, no redirige
            .andReturn();

        String html = r.getResponse().getContentAsString();
        assertThat(html)
            .as("el mensaje tiene que estar sobre el formulario")
            .contains("alert--error")
            .contains("Ya existe una carrera");
        assertThat(html)
            .as("un toast se va solo a los pocos segundos: no sirve para algo que hay que "
                + "releer mientras se corrige el campo")
            .doesNotContain("data-error=");
    }

    @Test
    @DisplayName("Un error de sistema sale por toast, no sobre una pantalla")
    void errorDeSistemaVaPorToast() throws Exception {
        // Dar de baja algo que no existe no es un problema de ningun formulario: es el
        // resultado de una accion, y el lugar de eso es el aviso flotante.
        mockMvc.perform(post("/carreras/999999/baja")
                .with(user(principal("INSTITUCION"))).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("flashError"));
    }

    @Test
    @DisplayName("Todos los formularios pueden mostrar errores que no son de un campo")
    void todosLosFormulariosMuestranErroresGlobales() throws Exception {
        // Se revisan las PLANTILLAS y no el HTML renderizado: el bloque lleva th:if, así que
        // cuando no hay errores Thymeleaf no lo emite y buscarlo en la salida no probaría
        // nada. Lo que se quiere fijar es que el hueco exista en el archivo.
        java.nio.file.Path base = java.nio.file.Path.of("src/main/resources/templates");
        try (java.util.stream.Stream<java.nio.file.Path> archivos =
                 java.nio.file.Files.walk(base)) {
            java.util.List<String> sinHueco = archivos
                .filter(p -> p.toString().endsWith(".html"))
                .filter(p -> {
                    try { return java.nio.file.Files.readString(p).contains("novalidate"); }
                    catch (Exception e) { return false; }
                })
                .filter(p -> {
                    try {
                        String c = java.nio.file.Files.readString(p);
                        // Vale cualquiera de las dos formas: los errores de binding y los
                        // que el controlador manda como atributo suelto.
                        return !c.contains("hasGlobalErrors") && !c.contains("${error}");
                    } catch (Exception e) { return true; }
                })
                .map(p -> base.relativize(p).toString())
                .toList();

            assertThat(sinHueco)
                .as("sin este bloque, un error que no pertenece a un campo concreto no tiene "
                    + "dónde mostrarse y se pierde: el formulario vuelve como si nada")
                .isEmpty();
        }
    }

    // ========================================================================
    //  Grilla: mirar y editar cuestan distinto
    // ========================================================================

    @Test
    @DisplayName("Los bloques de la grilla ya no son enlaces directos a la edición")
    void grillaNoLlevaDirectoAEditar() throws Exception {
        MvcResult r = mockMvc.perform(get("/grilla").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andReturn();

        String html = r.getResponse().getContentAsString();
        assertThat(html)
            .as("un click hecho para 'ver de qué es esta clase' terminaba en un formulario")
            .doesNotContain("<a th:href=\"@{/horarios/")
            .contains("grilla-detalle.js");
    }

    // ========================================================================
    //  Pantallas intermedias de cada grupo del menu
    // ========================================================================

    @Test
    @DisplayName("La miga 'Asistencias' ahora lleva a una página que existe")
    void laMigaDeAsistenciasLlevaAAlgo() throws Exception {
        // Este es el bug que las origino: /asistencia/pase mostraba la miga
        // "Inicio / Asistencias / Pase de asistencia" y al hacer click en "Asistencias"
        // se llegaba a una URL sin pantalla detras.
        mockMvc.perform(get("/asistencia").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Pase de asistencia")))
            .andExpect(content().string(containsString("Listado del día")))
            .andExpect(content().string(containsString("Reportes")));
    }

    @Test
    @DisplayName("Los tres grupos tienen su pantalla, con lo mismo que el menú")
    void losTresGruposTienenPantalla() throws Exception {
        mockMvc.perform(get("/academico").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Carreras")))
            .andExpect(content().string(containsString("Grilla semanal")));

        mockMvc.perform(get("/personal").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Docentes")))
            .andExpect(content().string(containsString("Usuarios del sistema")))
            .andExpect(content().string(containsString("Mi institución")));
    }

    @Test
    @DisplayName("Como ADMIN, Personal va derecho a Docentes en vez de a una lista de uno")
    void personalDeAdminVaDerechoADocentes() throws Exception {
        // El ADMIN solo ve Docentes en ese grupo: una pantalla intermedia que ofrece una
        // unica opcion es un click que no decide nada.
        mockMvc.perform(get("/personal").with(user(principal("ADMIN"))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/docentes"));
    }

    @Test
    @DisplayName("Como INSTITUCIÓN, Personal sí muestra la pantalla intermedia")
    void personalDeInstitucionMuestraLaPantalla() throws Exception {
        // Con tres pantallas en el grupo, la intermedia si sirve para elegir.
        mockMvc.perform(get("/personal").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk());
    }

    /**
     * Todo destino del menú aparece en la pantalla intermedia de su grupo.
     *
     * <p>Son dos listas que se escriben en lugares distintos —la barra lateral en
     * {@code layout/base.html}, las tarjetas en {@code SeccionService}— y nada obliga a que
     * coincidan. Ya se separaron una vez: al sumar Ciclos lectivos y Días sin clase al menú,
     * la pantalla de Académico siguió ofreciendo cinco destinos de siete, y la miga de pan de
     * esas dos pantallas mostraba el segmento crudo de la URL porque tampoco estaban en el
     * mapa de {@code migas.js}.
     *
     * <p>No falla nada cuando pasa: el menú funciona, la pantalla intermedia funciona, y las
     * dos muestran cosas distintas. Por eso hace falta comprobarlo.
     *
     * <p>Se lee el menú del archivo y no del HTML renderizado a propósito: lo que se quiere
     * fijar es la convención de que agregar un destino implica agregarlo en los tres lados.
     */
    @Test
    @DisplayName("Cada destino del menú está en la pantalla intermedia de su grupo")
    void elMenuYLasPantallasIntermediasNoSeSeparan() throws Exception {
        String base = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/resources/templates/layout/base.html"));
        String nav = base.substring(base.indexOf("id=\"lateral-nav\""), base.indexOf("lateral__pie"));

        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("th:href=\"@\\{(/[\\w/-]*)}\"").matcher(nav);
        java.util.List<String> destinos = new java.util.ArrayList<>();
        while (m.find()) {
            if (!"/".equals(m.group(1))) destinos.add(m.group(1));
        }
        assertThat(destinos).as("si el menú quedó vacío, este test no está comprobando nada")
            .hasSizeGreaterThan(5);

        // Las tres intermedias juntas: un destino vive en una sola, pero cuál es no lo decide
        // este test. Lo que importa es que ninguno quede afuera de las tres.
        //
        // Se recorta la grilla de tarjetas y NO se busca en la página entera. Toda pantalla
        // trae la barra lateral, así que buscar el href en el HTML completo lo encuentra
        // siempre: el test pasaba con Grilla semanal sacada del servicio a propósito, porque
        // lo estaba leyendo del propio menú que decía comprobar.
        StringBuilder intermedias = new StringBuilder();
        for (String ruta : java.util.List.of("/academico", "/asistencia", "/personal")) {
            String html = mockMvc.perform(get(ruta).with(user(principal("INSTITUCION"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

            int desde = html.indexOf("seccion__tarjetas");
            int hasta = html.indexOf("</section>", Math.max(desde, 0));
            assertThat(desde).as("la pantalla %s no trae la grilla de tarjetas", ruta).isNotNegative();
            assertThat(hasta).as("la pantalla %s no cierra su <section>", ruta).isGreaterThan(desde);
            intermedias.append(html, desde, hasta);
        }

        String todas = intermedias.toString();
        java.util.List<String> ausentes = destinos.stream()
            .filter(d -> !todas.contains("href=\"" + d + "\""))
            .toList();

        assertThat(ausentes)
            .as("estos destinos están en la barra lateral pero SeccionService no los ofrece; "
                + "agregarlos también al mapa de migas.js")
            .isEmpty();

        // Y el mapa de las migas, que es el tercer lugar donde se olvidan.
        String migas = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/resources/static/js/comun/migas.js"));
        java.util.List<String> sinMiga = destinos.stream()
            .map(d -> d.substring(1).split("/")[0])
            .distinct()
            .filter(seg -> !migas.contains("'" + seg + "'"))
            .toList();

        assertThat(sinMiga)
            .as("sin entrada en el MAPA de migas.js, la ruta muestra el segmento crudo de la URL")
            .isEmpty();
    }

    /**
     * Dentro de un grupo, cada destino tiene su propio icono.
     *
     * <p>El icono existe para reconocer el destino sin leer, en el menú y en la tarjeta. Dos
     * destinos del mismo grupo con el mismo dibujo no aportan nada: pasan a ser adorno, y de
     * paso hacen creer que son lo mismo. Ya pasó con Ciclos lectivos, Días sin clase y Grilla
     * semanal, que compartían el calendario: tres de siete tarjetas iguales.
     *
     * <p>Se lee del archivo del menú porque es ahí donde se elige el icono al agregar una
     * pantalla, y es el momento en que conviene enterarse.
     */
    @Test
    @DisplayName("Dentro de un grupo no se repite ningún icono")
    void losIconosDeUnGrupoNoSeRepiten() throws Exception {
        String base = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/resources/templates/layout/base.html"));
        String nav = base.substring(base.indexOf("id=\"lateral-nav\""), base.indexOf("lateral__pie"));

        // El menú va en orden, así que cada título de grupo abre una sección nueva.
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "class=\"lateral__grupo\"|iconos :: (\\w+)}").matcher(nav);

        java.util.List<java.util.List<String>> grupos = new java.util.ArrayList<>();
        java.util.List<String> actual = new java.util.ArrayList<>();
        while (m.find()) {
            if (m.group(1) == null) {              // arranca un grupo
                grupos.add(actual);
                actual = new java.util.ArrayList<>();
            } else {
                actual.add(m.group(1));
            }
        }
        grupos.add(actual);

        assertThat(grupos).as("si no se detectó ningún grupo, el test no comprueba nada")
            .hasSizeGreaterThan(3);

        java.util.List<String> repetidos = new java.util.ArrayList<>();
        for (java.util.List<String> g : grupos) {
            java.util.Set<String> vistos = new java.util.HashSet<>();
            g.stream().filter(i -> !vistos.add(i)).forEach(repetidos::add);
        }

        assertThat(repetidos)
            .as("estos iconos aparecen dos veces dentro de un mismo grupo del menú")
            .isEmpty();
    }

    /**
     * La fecha del panel de inicio sale en castellano.
     *
     * <p>Es el único lugar de la aplicación donde Java formatea un nombre de día o de mes: el
     * resto de las fechas van en dd/MM/yyyy, que no depende del idioma, y los días de la
     * semana salen de la etiqueta del enum. Sin {@code spring.web.locale} el formato sigue al
     * Accept-Language del navegador, así que en una máquina configurada en inglés el panel
     * abre con "Friday 4 of September" en medio de una pantalla en castellano.
     *
     * <p>La petición manda Accept-Language en inglés a propósito: con el resolvedor por
     * defecto ese encabezado gana, y el test tiene que fallar si alguien saca la propiedad.
     */
    @Test
    @DisplayName("La fecha del inicio no sigue al idioma del navegador")
    void laFechaDelInicioVaEnCastellano() throws Exception {
        String html = mockMvc.perform(get("/")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .with(user(principal("INSTITUCION"))))
            .andReturn().getResponse().getContentAsString();

        String enCastellano = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter
                .ofPattern("EEEE d 'de' MMMM", new java.util.Locale("es", "AR")));

        assertThat(html)
            .as("el encabezado tiene que decir la fecha en castellano y no en el idioma pedido")
            .contains(enCastellano);
    }

    /**
     * El botón de tema sale con sus dos iconos marcados.
     *
     * <p>Parece un detalle y es una trampa de Thymeleaf: {@code th:replace} reemplaza el
     * elemento COMPLETO, atributos incluidos, así que una clase escrita en el {@code <svg>}
     * que invoca al fragmento se descarta al renderizar. Las clases tienen que estar dentro
     * de los fragmentos {@code sol} y {@code luna} de {@code layout/iconos.html}.
     *
     * <p>Cuando faltan no falla nada: el HTML es válido, el botón existe y responde. Lo único
     * que pasa es que las reglas que muestran uno y esconden el otro no enganchan con nada y
     * el botón aparece con el sol y la luna a la vez, en los dos temas.
     */
    @Test
    @DisplayName("El botón de tema renderiza sol y luna con su clase")
    void elBotonDeTemaTraeSusDosIconosMarcados() throws Exception {
        String html = mockMvc.perform(get("/").with(user(principal("INSTITUCION"))))
            .andReturn().getResponse().getContentAsString();

        assertThat(html)
            .as("sin la clase en el SVG renderizado, el CSS no puede esconder ninguno de los dos")
            .contains("class=\"tema-toggle__sol\"")
            .contains("class=\"tema-toggle__luna\"");
    }

    /**
     * El menú respeta el rol, comprobado en sus dos mitades.
     *
     * <p>Este test cambió de forma con la barra lateral. Antes el menú era un navbar de grupos
     * desplegables y cada grupo era un enlace cuyo destino dependía del rol: para el ADMIN,
     * Personal llevaba derecho a Docentes porque era su única pantalla del grupo; para
     * INSTITUCIÓN, a {@code /personal}, donde puede elegir. Eso se comprobaba buscando esos
     * dos href en el menú.
     *
     * <p>En la barra lateral los grupos ya no son enlaces: son títulos de sección con sus
     * destinos listados debajo, todos a la vista. Así que buscar {@code /personal} en el menú
     * ya no tiene sentido; el atajo al grupo no desapareció, se mudó a la miga de pan, que
     * sigue leyendo los mismos {@code data-*} del servidor.
     *
     * <p>Se comprueban entonces las dos cosas que sí siguen siendo ciertas y que son lo que el
     * test protegía: que el servidor calcula el destino según el rol, y que el menú no ofrece
     * pantallas que el rol no puede abrir.
     */
    @Test
    @DisplayName("El menú apunta a donde corresponde según el rol")
    void elMenuApuntaSegunElRol() throws Exception {
        String admin = mockMvc.perform(get("/").with(user(principal("ADMIN"))))
            .andReturn().getResponse().getContentAsString();
        String inst = mockMvc.perform(get("/").with(user(principal("INSTITUCION"))))
            .andReturn().getResponse().getContentAsString();

        // --- Destino del grupo, que la miga de pan usa para armar el atajo ---
        assertThat(admin)
            .as("para el ADMIN, el grupo Personal tiene que llevar directo a Docentes")
            .contains("data-personal=\"/docentes\"");
        assertThat(inst)
            .as("para INSTITUCION, al grupo, que es donde puede elegir")
            .contains("data-personal=\"/personal\"");

        // --- Lo que cada rol ve listado ---
        assertThat(admin)
            .as("Docentes es del ADMIN")
            .contains("href=\"/docentes\"");
        assertThat(admin)
            .as("las tres pantallas de INSTITUCION no se le ofrecen al ADMIN: el menú no puede "
                + "mostrar un destino que al abrirlo devuelve 403")
            .doesNotContain("href=\"/usuarios\"")
            .doesNotContain("href=\"/mi-institucion\"")
            .doesNotContain("href=\"/puestos\"");

        assertThat(inst)
            .as("INSTITUCION las ve todas, sin pasar por una pantalla intermedia")
            .contains("href=\"/docentes\"")
            .contains("href=\"/usuarios\"")
            .contains("href=\"/mi-institucion\"")
            .contains("href=\"/puestos\"");
    }

    /**
     * Las tablas que se adaptan a móvil arman una tarjeta por fila, y cada celda muestra la
     * etiqueta de su columna a partir de {@code data-label}: en pantalla angosta el
     * {@code <thead>} no está a la vista, así que sin la etiqueta el valor queda suelto sin
     * decir de qué es.
     *
     * <p>Se comprueba en el archivo y no en el HTML renderizado porque lo que se quiere fijar
     * es la convención: agregar una columna nueva y olvidarse del {@code data-label} no rompe
     * nada en escritorio, y en el teléfono aparece un dato sin nombre que nadie va a notar
     * hasta que alguien lo mire ahí.
     *
     * <p>La lista es explícita y no un barrido de todas las plantillas con tabla: los
     * listados académicos siguen siendo de escritorio por decisión (RNF-23 reinterpretado,
     * ver contexto/05-trazabilidad.md). Cuando alguno se adapte, se agrega acá.
     */
    @Test
    @DisplayName("Las tablas adaptadas a movil etiquetan todas sus celdas")
    void lasTablasDeMovilEtiquetanSusCeldas() throws Exception {
        java.util.List<String> adaptadas = java.util.List.of(
            "asistencia/list.html", "reporte/asistencias.html", "docente/list.html",
            // Nace adaptada: cada celda de datos lleva su data-label (RF-79).
            "asistencia/bloques-pendientes.html");

        java.util.List<String> problemas = new java.util.ArrayList<>();
        for (String rel : adaptadas) {
            String html = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/templates", rel));

            // El modo tarjeta es opt-in: sin la clase la tabla se sigue desplazando de
            // costado, que es el comportamiento de las que no se adaptaron.
            if (!html.contains("table--tarjetas")) {
                problemas.add(rel + " -> le falta la clase table--tarjetas");
            }

            java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("<td\\b[^>]*>").matcher(html);
            while (m.find()) {
                String td = m.group();
                // La celda de acciones y la del estado vacío no llevan etiqueta a propósito:
                // una son botones y la otra es un mensaje, no el valor de una columna.
                boolean exenta = td.contains("table__actions") || td.contains("table__empty");
                if (!exenta && !td.contains("data-label")) {
                    problemas.add(rel + " -> " + td);
                }
                // Cambiar el display de los elementos de una tabla hace que Chrome y Safari
                // le saquen la semántica de tabla al árbol de accesibilidad. El role la repone.
                if (!td.contains("role=")) {
                    problemas.add(rel + " (sin role) -> " + td);
                }
            }
        }
        assertThat(problemas)
            .as("en el teléfono estas celdas quedarían sin decir a qué columna pertenecen")
            .isEmpty();
    }

    /**
     * La contracara: ninguna tabla que no esté en la lista de arriba puede llevar
     * {@code table--tarjetas}.
     *
     * <p>La primera versión de este cambio aplicaba el modo tarjeta a {@code .table} a secas,
     * y se coló en los cinco listados que NO se adaptaron. Ahí no hay {@code data-label}, así
     * que en el teléfono aparecían valores sin nombre: un "2°" y un "— Sin asignar —" sueltos,
     * sin decir que uno era el año y el otro el titular. Pasar de largo era fácil, porque la
     * pantalla se veía prolija.
     */
    @Test
    @DisplayName("Ninguna tabla sin etiquetas entra en modo tarjeta")
    void soloLasTablasEtiquetadasSonTarjetas() throws Exception {
        java.util.List<String> adaptadas = java.util.List.of(
            "asistencia/list.html", "reporte/asistencias.html", "docente/list.html",
            // Nace adaptada: cada celda de datos lleva su data-label (RF-79).
            "asistencia/bloques-pendientes.html");

        java.nio.file.Path base = java.nio.file.Path.of("src/main/resources/templates");
        java.util.List<String> intrusas;
        try (java.util.stream.Stream<java.nio.file.Path> archivos = java.nio.file.Files.walk(base)) {
            intrusas = archivos
                .filter(f -> f.toString().endsWith(".html"))
                .filter(f -> {
                    try { return java.nio.file.Files.readString(f).contains("table--tarjetas"); }
                    catch (Exception e) { return false; }
                })
                // En Windows el separador es \ y la lista de arriba usa /.
                .map(f -> base.relativize(f).toString().replace(java.io.File.separatorChar, '/'))
                .filter(rel -> !adaptadas.contains(rel))
                .toList();
        }
        assertThat(intrusas)
            .as("esta tabla se veria como tarjetas pero sin etiquetas: valores sin nombre")
            .isEmpty();
    }

    /**
     * El alta pide dos nombres seguidos: el completo, que es el dato legal, y uno corto que
     * sirve para entrar. Para que el segundo no se lea como un tramite repetido, se propone
     * a partir del primero.
     *
     * <p>Se comprueba el cableado y no el resultado: la derivacion vive en JavaScript y corre
     * en el navegador. Lo que puede romperse en silencio desde acá es que el campo pierda el
     * atributo que lo conecta con el nombre, o que la pantalla deje de cargar el script; en
     * los dos casos el formulario sigue funcionando y la sugerencia simplemente no aparece,
     * que es la clase de falla que nadie reporta.
     */
    @Test
    @DisplayName("El alta de institucion propone el usuario a partir del nombre")
    void elAltaProponeElUsuario() throws Exception {
        String html = mockMvc.perform(get("/alta-institucion"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html)
            .as("el campo tiene que declarar de que otro campo sale la sugerencia")
            .contains("data-sugerir-desde=\"nombreInstitucion\"");
        assertThat(html)
            .as("sin el script no hay sugerencia, y el formulario no se queja")
            .contains("sugerir-usuario.js");
        assertThat(html)
            .as("el campo dejo de llamarse 'Usuario' a secas: se pide como la version corta del nombre")
            .contains("Nombre corto o siglas");
    }

    // ========================================================================
    //  Umbral de separacion de bloques (RF-76)
    // ========================================================================

    @Test
    @DisplayName("Mi institución deja configurar el umbral de separación y lo muestra")
    void miInstitucionMuestraElUmbral() throws Exception {
        MvcResult r = mockMvc.perform(get("/mi-institucion").with(user(principal("INSTITUCION"))))
            .andExpect(status().isOk())
            .andReturn();

        String html = r.getResponse().getContentAsString();
        assertThat(html)
            .as("hasta RF-76 el umbral solo se cambiaba por SQL")
            .contains("umbralSeparacionMin")
            .contains("Umbral de separación entre clases");
        assertThat(html)
            .as("tiene que venir precargado con el valor vigente, no vacío")
            .contains("value=\"60\"");
    }

    @Test
    @DisplayName("Guardar el umbral lo persiste en la institución")
    void guardarElUmbralLoPersiste() throws Exception {
        Institucion antes = institucionRepository.findById(tenantId).orElseThrow();

        mockMvc.perform(post("/mi-institucion")
                .with(user(principal("INSTITUCION"))).with(csrf())
                .param("nombre", antes.getNombre())
                .param("cuit", "")
                .param("umbralSeparacionMin", "30"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/mi-institucion"));

        assertThat(institucionRepository.findById(tenantId).orElseThrow().getUmbralSeparacionMin())
            .isEqualTo((short) 30);
    }

    @Test
    @DisplayName("Un umbral fuera de rango se rechaza en el formulario, no en la base")
    void umbralFueraDeRangoSeRechazaEnElFormulario() throws Exception {
        // El CHECK ck_instituciones_umbral_separacion de V019 no existe en H2, asi que este
        // test no lo prueba: prueba que la validacion del DTO frena el valor ANTES de llegar
        // a la base. Es justamente lo que evita que el limite se manifieste como un error de
        // integridad ilegible en vez de un mensaje al lado del campo.
        Institucion antes = institucionRepository.findById(tenantId).orElseThrow();

        mockMvc.perform(post("/mi-institucion")
                .with(user(principal("INSTITUCION"))).with(csrf())
                .param("nombre", antes.getNombre())
                .param("cuit", "")
                .param("umbralSeparacionMin", "300"))
            .andExpect(status().isOk())          // se queda en el formulario, no redirige
            .andExpect(content().string(containsString("240")));

        assertThat(institucionRepository.findById(tenantId).orElseThrow().getUmbralSeparacionMin())
            .as("el valor rechazado no se tiene que haber guardado")
            .isEqualTo(antes.getUmbralSeparacionMin());
    }

    // ------------------------------------------------------------------------

    private UsuarioAutenticado principal(String rol) {
        Rol r = new Rol();
        r.setId((short) 1);
        r.setCodigo(rol);
        r.setDescripcion(rol);

        Usuario u = Usuario.builder().persona(DatosDePrueba.persona("Test", "Pantallas")).id(99L).username("test.pantallas").passwordHash("no-se-usa").activo(true).rol(r).emailVerificadoEn(LocalDateTime.now()).build();
        u.setInstitucionId(tenantId);
        return new UsuarioAutenticado(u);
    }

    // ------------------------------------------------------------------------
    //  Ciclo lectivo para los datos de prueba (V023)
    // ------------------------------------------------------------------------
    //  La comision tiene periodo_id NOT NULL desde V023, asi que persistir una exige antes un
    //  ciclo con al menos un periodo. Se crea uno anual por tenant, reutilizado por todo el
    //  test: lo que estos casos prueban no es el calendario.
    //  Se busca antes de crear, y no se cachea en un campo: las filas sobreviven de un metodo
    //  al siguiente pero la instancia del test no, asi que un cache por instancia intentaba
    //  crear el mismo ciclo otra vez y chocaba contra uq_ciclos_inst_anio.
    private PeriodoLectivo periodoDe(Long tenantId) {
        return periodoLectivoRepository.seleccionablesDelTenant(tenantId).stream()
            .findFirst()
            .orElseGet(() -> cicloLectivoRepository
                .save(DatosDePrueba.cicloAnualDelTenant(
                    tenantId, java.time.LocalDate.now().getYear()))
                .getPeriodos().get(0));
    }
}
