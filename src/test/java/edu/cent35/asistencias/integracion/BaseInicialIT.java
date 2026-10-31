package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.service.BloquePresenciaService;
import edu.cent35.asistencias.service.GeneradorAusenciasService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * El sistema entero sobre un MariaDB con soporte, arrancando de {@code despliegue/base-inicial.sql}.
 *
 * <p><b>Por qué existe esa base.</b> {@code V001} no aplica de 10.6 en adelante y una migración
 * aplicada no se edita (TD-011). Una base nueva arranca de ese archivo, que trae el esquema y el
 * historial de Flyway hasta {@code V038}: Flyway da por aplicado lo anterior y sigue desde ahí.
 *
 * <p><b>Qué prueba.</b> Que Flyway la acepta con sus huellas, que Hibernate valida las entidades
 * contra ella (el contexto no levanta si no), que los CHECK dejan pasar la supresión ARCO, y que las
 * pantallas responden: el resto de la suite corre sobre H2 y nunca le pregunta nada a este motor.
 * Una migración nueva que el motor rechace rompe acá, antes de llegar a producción.
 *
 * <p>Corre solo si está {@code MARIADB_NUEVA_PORT}, que el CI fija siempre. Si el motor no responde,
 * falla: no se saltea.
 */
@EnabledIfEnvironmentVariable(named = "MARIADB_NUEVA_PORT", matches = ".+")
@AutoConfigureMockMvc
class BaseInicialIT extends SupresionArcoIT {

    private static final String CLAVE_DE_PRUEBA = "ClaveDePrueba1";
    private static final String URL_BASE = "jdbc:mariadb://127.0.0.1:" + System.getenv("MARIADB_NUEVA_PORT") + "/";
    private static final String USUARIO = entornoO("MARIADB_NUEVA_USER", "root");
    private static final String CLAVE = entornoO("MARIADB_NUEVA_PASSWORD", "");

    private static final LocalDate LUNES_DEL_CICLO = LocalDate.of(2026, 3, 2);

    private static boolean cargada;

    @Autowired private MockMvc mvc;
    @Autowired private Flyway flyway;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private GeneradorAusenciasService generadorDeAusencias;
    @Autowired private BloquePresenciaService bloquePresencia;

    // Corre antes de que Spring arme el contexto: Flyway e Hibernate tienen que encontrar la base.
    @DynamicPropertySource
    static void apuntarAlMotorNuevo(DynamicPropertyRegistry registro) throws Exception {
        cargarBaseInicial();
        registro.add("spring.datasource.url", () -> URL_BASE + "asistenciautomatica");
        registro.add("spring.datasource.username", () -> USUARIO);
        registro.add("spring.datasource.password", () -> CLAVE);
        registro.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        registro.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registro.add("spring.jpa.properties.hibernate.dialect",
            () -> "org.hibernate.dialect.MariaDBDialect");
        registro.add("spring.flyway.enabled", () -> "true");
        registro.add("spring.flyway.baseline-on-migrate", () -> "false");
    }

    /**
     * Carga el archivo una sola vez, igual que el motor al crear la base por primera vez.
     *
     * <p>Se niega a seguir si ya hay una base con datos: este test empieza de cero y termina
     * borrándola, y la variable de entorno podría estar apuntando a una que importa.
     */
    private static void cargarBaseInicial() throws Exception {
        if (cargada) {
            return;
        }
        try (Connection c = DriverManager.getConnection(URL_BASE + "?allowMultiQueries=true", USUARIO, CLAVE);
             Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.SCHEMATA "
                        + "WHERE SCHEMA_NAME = 'asistenciautomatica'")) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    throw new IllegalStateException("Ya hay una base asistenciautomatica en el puerto "
                        + "de MARIADB_NUEVA_PORT. Este test la borraria: apuntalo a un motor descartable.");
                }
            }
            st.execute(Files.readString(Path.of("despliegue/base-inicial.sql")));
        }
        cargada = true;
    }

    @AfterAll
    static void borrarBases() throws SQLException {
        try (Connection c = DriverManager.getConnection(URL_BASE, USUARIO, CLAVE);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS asistenciautomatica");
            st.execute("DROP DATABASE IF EXISTS asistenciautomatica_meta");
        }
    }

    @Test
    @DisplayName("Flyway acepta la base inicial con sus huellas y no queda nada pendiente")
    void flywayAceptaLaBaseInicial() {
        List<MigrationState> estados = new ArrayList<>();
        for (MigrationInfo info : flyway.info().all()) {
            estados.add(info.getState());
        }
        assertThat(estados).isNotEmpty();
        assertThat(estados).doesNotContain(MigrationState.PENDING, MigrationState.FAILED,
            MigrationState.MISSING_SUCCESS, MigrationState.IGNORED);

        // validate compara la huella de cada migración con la del historial que trajo el archivo.
        flyway.validate();
    }

    @Test
    @DisplayName("Los catálogos llegan con sus tildes bien")
    void losCatalogosLleganBien() throws Exception {
        try (Connection c = DriverManager.getConnection(URL_BASE + "asistenciautomatica", USUARIO, CLAVE);
             Statement st = c.createStatement()) {
            try (ResultSet roles = st.executeQuery("SELECT COUNT(*) FROM roles")) {
                roles.next();
                assertThat(roles.getInt(1)).isPositive();
            }
            List<String> motivos = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT descripcion FROM motivos_carga_manual")) {
                while (rs.next()) {
                    motivos.add(rs.getString(1));
                }
            }
            assertThat(motivos).isNotEmpty();
            assertThat(String.join(" ", motivos)).doesNotContain("Ã", "�", "?");
        }
    }

    @Test
    @DisplayName("Las pantallas de gestión responden sobre este motor")
    void lasPantallasResponden() throws Exception {
        cuenta.setPasswordHash(passwordEncoder.encode(CLAVE_DE_PRUEBA));
        usuarios.save(cuenta);

        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(post("/login").session(sesion).with(csrf())
                .param("username", cuenta.getUsername()).param("password", CLAVE_DE_PRUEBA))
            .andExpect(redirectedUrl("/"));

        // Cada una ejercita sus consultas, varias con JOIN y filtros: H2 no dice nada de ellas.
        List<String> conFalla = new ArrayList<>();
        for (String ruta : List.of("/", "/docentes", "/asistencias", "/asistencias/bloques/pendientes",
                "/reportes", "/reportes/graficos", "/horarios", "/comisiones", "/materias", "/carreras",
                "/ciclos", "/usuarios", "/mi-institucion", "/mi-cuenta", "/puestos")) {
            int estado = mvc.perform(get(ruta).session(sesion)).andReturn().getResponse().getStatus();
            if (estado >= 400) {
                conFalla.add(ruta + " -> " + estado);
            }
        }
        assertThat(conFalla).isEmpty();
    }

    @Test
    @DisplayName("El job de ausencias corre sobre este motor")
    void elJobCorre() {
        // Se llama a los dos pasos y no a ejecutar(), que atrapa las excepciones de cada institucion
        // y las deja en el log: una consulta que este motor rechazara pasaria en verde.
        TenantContext.set(tenantId);
        try {
            bloquePresencia.cerrarBloquesVencidos(LUNES_DEL_CICLO, LocalTime.of(23, 0));
            assertThat(generadorDeAusencias.generarParaInstitucion(
                    tenantId, LUNES_DEL_CICLO, LocalTime.of(23, 0)))
                .as("el horario de 18 a 22 termino sin marca").isEqualTo(1);
        } finally {
            TenantContext.clear();
        }
    }

    private static String entornoO(String variable, String porDefecto) {
        String valor = System.getenv(variable);
        return valor == null ? porDefecto : valor;
    }
}
