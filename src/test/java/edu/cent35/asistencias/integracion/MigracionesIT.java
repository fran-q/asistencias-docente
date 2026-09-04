package edu.cent35.asistencias.integracion;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Aplica las migraciones desde cero contra MariaDB y comprueba que el esquema resultante es el
 * que las entidades esperan.
 *
 * <p><b>Por qué existe.</b> El resto de la suite corre sobre H2 con Flyway apagado y el esquema
 * generado por Hibernate a partir de las entidades. Eso valida la lógica, pero deja un hueco:
 * si una migración quedara desalineada de su entidad, <b>los tests pasarían igual</b> y el
 * desajuste aparecería recién al arrancar contra la base de verdad. Este test cierra ese hueco.
 *
 * <p><b>Qué comprueba, en orden.</b> Que todas las migraciones aplican de cero sin error sobre una
 * base vacía; que ninguna quedó pendiente ni fallida; y que el esquema que producen tiene
 * exactamente las tablas y columnas que el resto del sistema da por sentadas.
 *
 * <p><b>Por qué se saltea si no hay MariaDB.</b> Las migraciones están escritas en SQL de
 * MySQL/MariaDB —{@code MODIFY COLUMN}, {@code COMMENT}, tipos propios— así que no corren en
 * H2. Antes que no tener la comprobación, el test se saltea limpiamente donde no hay motor y
 * corre donde sí lo hay, que es la máquina de desarrollo y la de la demostración.
 *
 * <p>Trabaja sobre una base descartable propia: no toca la de la aplicación.
 */
class MigracionesIT {

    private static final String URL_BASE = "jdbc:mariadb://localhost:3306/";
    private static final String BASE = "asistencias_test_migraciones";

    private static final String USUARIO = credencial("spring.datasource.username", "MARIADB_USER");
    private static final String CLAVE   = credencial("spring.datasource.password", "MARIADB_PASSWORD");

    private static boolean hayMotor;

    /**
     * Busca una credencial en application-local.properties y, si no está, en el entorno.
     *
     * <p>Acá estuvo escrita la contraseña real de la base, en claro, en un archivo versionado.
     * El repositorio es público: el `.gitignore` protegía al properties pero no a este test, que
     * era la otra puerta. Una contraseña que entra al historial de git queda ahí para siempre,
     * así que la única forma de sacarla es no escribirla nunca.
     *
     * <p>Si no aparece por ninguna de las dos vías devuelve null, la conexión falla y el test se
     * saltea solo, que es el mismo camino que ya tomaba cuando no había MariaDB a mano.
     */
    private static String credencial(String propiedad, String variableDeEntorno) {
        try (InputStream in = MigracionesIT.class.getResourceAsStream("/application-local.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                String valor = p.getProperty(propiedad);
                if (valor != null && !valor.isBlank()) {
                    return valor.trim();
                }
            }
        } catch (Exception e) {
            // Sin el archivo se sigue con el entorno; no hay nada que reportar.
        }
        return System.getenv(variableDeEntorno);
    }

    @BeforeAll
    static void prepararBaseDescartable() {
        try (Connection c = DriverManager.getConnection(URL_BASE, USUARIO, CLAVE);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + BASE);
            st.execute("CREATE DATABASE " + BASE + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            hayMotor = true;
        } catch (Exception e) {
            // Sin MariaDB a mano el test no puede correr. Se saltea, no falla: un test rojo
            // por falta de infraestructura entrena a ignorar los tests rojos.
            hayMotor = false;
        }
    }

    @AfterAll
    static void limpiar() {
        if (!hayMotor) return;
        try (Connection c = DriverManager.getConnection(URL_BASE, USUARIO, CLAVE);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + BASE);
        } catch (Exception ignored) {
            // La base descartable queda; no es motivo para fallar el test.
        }
    }

    @Test
    @DisplayName("Las migraciones aplican de cero y dejan el esquema que las entidades esperan")
    void migracionesDesdeCero() throws Exception {
        assumeTrue(hayMotor, "Sin MariaDB en localhost:3306; se saltea la verificación de migraciones");

        Flyway flyway = Flyway.configure()
            .dataSource(URL_BASE + BASE, USUARIO, CLAVE)
            .locations("classpath:db/migration")
            // Las migraciones califican las tablas como ${esquema}.tabla porque en la
            // aplicacion el historial vive en otra base y, sin calificar, el CREATE TABLE
            // terminaria ahi. Aca el esquema es esta base descartable: es justamente lo que
            // el placeholder permite, que la misma migracion corra contra cualquier nombre.
            .placeholders(java.util.Map.of("esquema", BASE))
            .load();

        flyway.migrate();

        // 1) Ninguna quedó pendiente ni fallida.
        List<String> problemas = new ArrayList<>();
        for (MigrationInfo mi : flyway.info().all()) {
            if (!mi.getState().isApplied() || mi.getState().isFailed()) {
                problemas.add(mi.getVersion() + " (" + mi.getState() + ")");
            }
        }
        assertThat(problemas)
            .as("toda migración tiene que quedar aplicada y sin errores")
            .isEmpty();

        assertThat(flyway.info().applied())
            .as("la cantidad de migraciones aplicadas tiene que coincidir con los archivos")
            .hasSize(flyway.info().all().length);

        // 2) El esquema resultante tiene las tablas del modelo.
        Set<String> tablas = leer("SELECT TABLE_NAME FROM information_schema.TABLES "
            + "WHERE TABLE_SCHEMA = '" + BASE + "' AND TABLE_NAME <> 'flyway_schema_history'");

        assertThat(tablas).containsExactlyInAnyOrder(
            "instituciones", "usuarios", "roles", "codigos_verificacion",
            "carreras", "materias", "comisiones", "horarios",
            "docentes", "consentimientos_biometricos", "modelos_faciales",
            "asistencias", "asistencias_manuales", "motivos_carga_manual",
            "justificaciones_ausencia",
            // V015. Equipos habilitados para el pase y el registro del rostro (ADR-0015).
            "puestos_captura",
            // V016. La identidad sale de usuarios y docentes y pasa a vivir acá (ADR-0016).
            "personas",
            // V017. Historial de cambios sobre los datos de identidad (ADR-0016).
            "cambios_identidad",
            // V019. Permanencia del docente con marca de entrada y de salida (ADR-0017).
            "bloques_presencia");

        // 3) Y las columnas que el codigo da por sentadas, incluidas las que agregaron las
        //    migraciones tardias: si una migracion se perdiera, esto lo dice.
        assertThat(columnasDe("materias")).contains("anio", "docente_titular_id", "fecha_baja");
        assertThat(columnasDe("carreras")).contains("duracion_anios", "fecha_baja");
        assertThat(columnasDe("usuarios")).contains("ultimo_login", "email_verificado_en", "fecha_baja");
        assertThat(columnasDe("horarios")).contains("tolerancia_min", "creado_en", "actualizado_en");
        assertThat(columnasDe("roles")).contains("creado_en");
        assertThat(columnasDe("motivos_carga_manual")).contains("creado_en", "fecha_baja");
        // V019 y V020. El bloque de presencia y quien lo cerro a mano.
        assertThat(columnasDe("bloques_presencia")).contains(
            "hora_entrada", "hora_salida", "origen_entrada", "origen_salida",
            "estado_cierre", "estado_salida",
            "modelo_facial_entrada_id", "modelo_facial_salida_id",
            // Columna generada: vale docente_id mientras el bloque este abierto, y su UNIQUE
            // es lo que impide que un docente tenga dos bloques abiertos a la vez.
            "bloque_abierto_de",
            // V020.
            "cerrado_por_usuario_id", "motivo_cierre_id", "detalle_cierre");
        assertThat(columnasDe("asistencias")).contains("bloque_id");
        assertThat(columnasDe("instituciones")).contains("umbral_separacion_min");

        // Lo que las migraciones sacaron tiene que estar efectivamente afuera.
        assertThat(columnasDe("comisiones"))
            .as("el cupo se elimino en V010")
            .doesNotContain("cupo");
        assertThat(columnasDe("horarios"))
            .as("la vigencia se elimino en V012")
            .doesNotContain("vigente_desde", "vigente_hasta");
        assertThat(columnasDe("modelos_faciales"))
            .as("creado_en se elimino en V013 por duplicar fecha_registro")
            .doesNotContain("creado_en");

        // 4) El apellido tiene que admitir NULL: es lo que permite que una cuenta de
        //    institucion tenga un solo nombre. Lo trajo V014 sobre usuarios y desde V016 la
        //    columna vive en personas, junto al resto de la identidad.
        Set<String> nullable = leer("SELECT COLUMN_NAME FROM information_schema.COLUMNS "
            + "WHERE TABLE_SCHEMA = '" + BASE + "' AND TABLE_NAME = 'personas' "
            + "AND IS_NULLABLE = 'YES'");
        assertThat(nullable).contains("apellido");

        // 5) V016 saco la identidad de usuarios y docentes. Si alguna de estas columnas
        //    reapareciera, habria dos fuentes de verdad para el mismo dato.
        assertThat(columnasDe("usuarios"))
            .as("nombre y apellido viven en personas desde V016")
            .doesNotContain("nombre", "apellido");
        assertThat(columnasDe("usuarios"))
            .as("el email de acceso se queda: es el login y el destino de los codigos")
            .contains("email", "persona_id");

        // V018. persona_id admite NULL: la cuenta institucional representa al establecimiento y
        // no a una persona fisica. Si volviera a ser obligatorio, el alta de institucion tendria
        // que inventar una identidad para alguien que todavia no existe.
        Set<String> usuariosNullable = leer("SELECT COLUMN_NAME FROM information_schema.COLUMNS "
            + "WHERE TABLE_SCHEMA = '" + BASE + "' AND TABLE_NAME = 'usuarios' "
            + "AND IS_NULLABLE = 'YES'");
        assertThat(usuariosNullable).contains("persona_id");
        assertThat(columnasDe("docentes"))
            .as("docentes pasa a ser el vinculo laboral, no la identidad")
            .doesNotContain("dni", "nombre", "apellido", "email", "telefono");
        assertThat(columnasDe("docentes")).contains("persona_id", "legajo", "fecha_alta");

        // 6) La constancia del dato biometrico suprimido (ADR-0016): el embedding pasa a
        //    admitir NULL para poder borrar el dato conservando la fila como registro.
        Set<String> biometricoNullable = leer("SELECT COLUMN_NAME FROM information_schema.COLUMNS "
            + "WHERE TABLE_SCHEMA = '" + BASE + "' AND TABLE_NAME = 'modelos_faciales' "
            + "AND IS_NULLABLE = 'YES'");
        assertThat(biometricoNullable).contains("embedding_cifrado");
        assertThat(columnasDe("modelos_faciales")).contains("fecha_supresion", "motivo_supresion");

        // 7) V017. La trazabilidad: quien cambio la identidad y quien dio de baja.
        assertThat(columnasDe("cambios_identidad"))
            .as("una fila por campo modificado, con su valor anterior y quien lo cambio")
            .contains("persona_id", "usuario_id", "campo", "valor_anterior", "valor_nuevo",
                      "origen", "fecha");

        for (String tabla : new String[]{"carreras", "materias", "comisiones", "horarios",
                                         "docentes", "usuarios", "puestos_captura",
                                         "modelos_faciales"}) {
            assertThat(columnasDe(tabla))
                .as("la baja de %s tiene que dejar constancia de quien la hizo", tabla)
                .contains("dado_de_baja_por");
        }
    }

    private Set<String> columnasDe(String tabla) throws Exception {
        return leer("SELECT COLUMN_NAME FROM information_schema.COLUMNS "
            + "WHERE TABLE_SCHEMA = '" + BASE + "' AND TABLE_NAME = '" + tabla + "'");
    }

    private Set<String> leer(String sql) throws Exception {
        Set<String> valores = new LinkedHashSet<>();
        try (Connection c = DriverManager.getConnection(URL_BASE + BASE, USUARIO, CLAVE);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                valores.add(rs.getString(1));
            }
        }
        return valores;
    }
}
