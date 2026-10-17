package edu.cent35.asistencias.integracion;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 * <p><b>Y que los CHECK rechacen lo que deben.</b> H2 tampoco tiene ninguno de los CHECK de las
 * migraciones, así que uno mal escrito —o uno que dejara de existir— no lo notaría ningún otro
 * test. Acá están los de los bloques de presencia y el umbral de la institución (V019, V020,
 * V029 y V031), más el índice único que impide dos bloques abiertos del mismo docente. Hasta el
 * 2026-10-17 se probaban a mano.
 *
 * <p><b>Por qué se saltea si no hay MariaDB.</b> Las migraciones están escritas en SQL de
 * MySQL/MariaDB —{@code MODIFY COLUMN}, {@code COMMENT}, tipos propios— así que no corren en
 * H2. Antes que no tener la comprobación, el test se saltea limpiamente donde no hay motor y
 * corre donde sí lo hay, que es la máquina de desarrollo y la de la demostración.
 *
 * <p>Trabaja sobre una base descartable propia: no toca la de la aplicación.
 */
class MigracionesIT {

    /**
     * El puerto, de {@code MARIADB_PORT} y si no el de siempre.
     *
     * <p>Existe para poder correr este mismo test contra la MariaDB que lleva el instalador, que
     * escucha en un puerto propio (ADR-0022). Sin esto, lo único verificado sería el motor del
     * equipo de desarrollo, y el que se le entrega a la institución es otro.
     */
    private static final String PUERTO =
        System.getenv("MARIADB_PORT") == null ? "3306" : System.getenv("MARIADB_PORT");

    private static final String URL_BASE = "jdbc:mariadb://localhost:" + PUERTO + "/";
    private static final String BASE = "asistencias_test_migraciones";

    private static final String USUARIO = credencial("spring.datasource.username", "MARIADB_USER");
    private static final String CLAVE   = credencial("spring.datasource.password", "MARIADB_PASSWORD");

    /**
     * En el CI el motor tiene que estar: el workflow levanta un mariadb:10.4 y fija
     * {@code MARIADB_OBLIGATORIA=si}. Ahí saltearse sería esconder justo lo que este test vino a
     * cubrir --el CI en verde sin haber aplicado una sola migración--, así que falla. En una
     * máquina sin MariaDB, sin la variable, se sigue salteando.
     */
    private static final boolean MOTOR_OBLIGATORIO =
        "si".equalsIgnoreCase(System.getenv("MARIADB_OBLIGATORIA"));

    private static boolean hayMotor;
    // Por qué no se pudo conectar, para que el fallo del CI lo diga sin abrir el log.
    private static String porQueNoHayMotor;

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
        // La variable de entorno PRIMERO: lo que se pasa explícitamente le gana al archivo del
        // entorno de desarrollo. Es lo que permite correr este test contra otra base --la que
        // lleva el instalador, por ejemplo-- sin tocar la configuración local de nadie.
        String delEntorno = System.getenv(variableDeEntorno);
        if (delEntorno != null && !delEntorno.isBlank()) {
            return delEntorno;
        }

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
            // por falta de infraestructura entrena a ignorar los tests rojos. Salvo en el CI,
            // ver MOTOR_OBLIGATORIO.
            hayMotor = false;
            porQueNoHayMotor = e.getMessage();
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

    /**
     * Lo primero de cada test. En el CI el motor tiene que estar (ver MOTOR_OBLIGATORIO); en
     * una máquina sin MariaDB, el test se saltea.
     */
    private static void exigirMotor() {
        if (MOTOR_OBLIGATORIO) {
            assertThat(hayMotor)
                .as("MARIADB_OBLIGATORIA=si y no hay MariaDB en localhost:%s: %s", PUERTO, porQueNoHayMotor)
                .isTrue();
        }
        assumeTrue(hayMotor,
            "Sin MariaDB en localhost:" + PUERTO + "; se saltea la verificación de migraciones");
    }

    // La base ya migrada, para que la usen todos los tests de la clase. Ver migrada().
    private static Flyway flywayMigrada;

    /**
     * Migra la base descartable la primera vez que un test la pide, y después la reusa: todos
     * los tests de esta clase trabajan sobre el mismo esquema recién migrado, en el orden que
     * JUnit decida.
     *
     * <p>Se guarda recién cuando la migración terminó bien. Si fallara, el test siguiente
     * vuelve a intentarlo y falla igual, en vez de correr sobre un esquema a medias.
     */
    private static synchronized Flyway migrada() {
        if (flywayMigrada == null) {
            Flyway nueva = Flyway.configure()
                .dataSource(URL_BASE + BASE, USUARIO, CLAVE)
                .locations("classpath:db/migration")
                // Las migraciones califican las tablas como ${esquema}.tabla porque en la
                // aplicación el historial vive en otra base y, sin calificar, el CREATE TABLE
                // terminaría ahí. Acá el esquema es esta base descartable: es justamente lo que
                // el placeholder permite, que la misma migración corra contra cualquier nombre.
                .placeholders(Map.of("esquema", BASE))
                .load();
            nueva.migrate();
            flywayMigrada = nueva;
        }
        return flywayMigrada;
    }

    @Test
    @DisplayName("Las migraciones aplican de cero y dejan el esquema que las entidades esperan")
    void migracionesDesdeCero() throws Exception {
        exigirMotor();
        Flyway flyway = migrada();

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
            "bloques_presencia",
            // V023. El año calendario de cursada y sus tramos.
            "ciclos_lectivos", "periodos_lectivos",
            // V024. Los días de adentro del ciclo en los que no se dicta clase.
            "dias_no_laborables");

        // 3) Y las columnas que el código da por sentadas, incluidas las que agregaron las
        //    migraciones tardias: si una migración se perdiera, esto lo dice.
        assertThat(columnasDe("materias")).contains("anio", "docente_titular_id", "fecha_baja");
        // V023. La comisión se ata a un período, y con eso a un año calendario.
        assertThat(columnasDe("comisiones")).contains("periodo_id");
        // V026. La cámara con la que captura cada puesto.
        assertThat(columnasDe("puestos_captura"))
            .contains("camara_dispositivo_id", "camara_etiqueta", "camara_elegida_en");
        // V027. Reabrir un ciclo cerrado: quien y cuando, sin borrar el cierre.
        assertThat(columnasDe("ciclos_lectivos")).contains("reabierto_en", "reabierto_por");
        // V030. Varios equipos por institución, con tope propio: la columna generada que
        // sostenía el tope de uno se fue con el índice que la usaba.
        assertThat(columnasDe("instituciones")).contains("max_puestos_habilitados");
        assertThat(columnasDe("puestos_captura")).doesNotContain("institucion_si_habilitado");
        // V028. El tipo de día sin clase, por el que se filtra el listado.
        assertThat(columnasDe("dias_no_laborables")).contains("tipo", "motivo");
        assertThat(columnasDe("ciclos_lectivos")).contains("anio", "fecha_inicio", "fecha_fin", "estado");
        assertThat(columnasDe("periodos_lectivos")).contains("ciclo_id", "fecha_inicio", "fecha_fin", "orden");
        assertThat(columnasDe("carreras")).contains("duracion_anios", "fecha_baja");
        assertThat(columnasDe("usuarios")).contains("ultimo_login", "email_verificado_en", "fecha_baja");
        assertThat(columnasDe("horarios")).contains("tolerancia_min", "creado_en", "actualizado_en");
        assertThat(columnasDe("roles")).contains("creado_en");
        assertThat(columnasDe("motivos_carga_manual")).contains("creado_en", "fecha_baja");
        // V019 y V020. El bloque de presencia y quien lo cerró a mano.
        assertThat(columnasDe("bloques_presencia")).contains(
            "hora_entrada", "hora_salida", "origen_entrada", "origen_salida",
            "estado_cierre", "estado_salida",
            "modelo_facial_entrada_id", "modelo_facial_salida_id",
            // Columna generada: vale docente_id mientras el bloque esté abierto, y su UNIQUE
            // es lo que impide que un docente tenga dos bloques abiertos a la vez.
            "bloque_abierto_de",
            // V020.
            "cerrado_por_usuario_id", "motivo_cierre_id", "detalle_cierre");
        assertThat(columnasDe("asistencias")).contains("bloque_id");
        // V025. Modo kiosco: de qué equipo salió cada marca, y cuál puede operar sin sesión.
        assertThat(columnasDe("asistencias")).contains("puesto_id");
        assertThat(columnasDe("bloques_presencia")).contains("puesto_id");
        // V031. Y por qué equipo se fue: con una cámara por entrada no tienen por qué coincidir.
        assertThat(columnasDe("bloques_presencia")).contains("puesto_salida_id");
        assertThat(columnasDe("puestos_captura")).contains(
            "kiosco_habilitado", "kiosco_habilitado_en", "kiosco_habilitado_por");
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
        //    institución tenga un solo nombre. Lo trajo V014 sobre usuarios y desde V016 la
        //    columna vive en personas, junto al resto de la identidad.
        Set<String> nullable = leer("SELECT COLUMN_NAME FROM information_schema.COLUMNS "
            + "WHERE TABLE_SCHEMA = '" + BASE + "' AND TABLE_NAME = 'personas' "
            + "AND IS_NULLABLE = 'YES'");
        assertThat(nullable).contains("apellido");

        // 5) V016 sacó la identidad de usuarios y docentes. Si alguna de estas columnas
        //    reapareciera, habría dos fuentes de verdad para el mismo dato.
        assertThat(columnasDe("usuarios"))
            .as("nombre y apellido viven en personas desde V016")
            .doesNotContain("nombre", "apellido");
        assertThat(columnasDe("usuarios"))
            .as("el email de acceso se queda: es el login y el destino de los codigos")
            .contains("email", "persona_id");

        // V018. persona_id admite NULL: la cuenta institucional representa al establecimiento y
        // no a una persona física. Si volviera a ser obligatorio, el alta de institución tendría
        // que inventar una identidad para alguien que todavía no existe.
        Set<String> usuariosNullable = leer("SELECT COLUMN_NAME FROM information_schema.COLUMNS "
            + "WHERE TABLE_SCHEMA = '" + BASE + "' AND TABLE_NAME = 'usuarios' "
            + "AND IS_NULLABLE = 'YES'");
        assertThat(usuariosNullable).contains("persona_id");
        assertThat(columnasDe("docentes"))
            .as("docentes pasa a ser el vinculo laboral, no la identidad")
            .doesNotContain("dni", "nombre", "apellido", "email", "telefono");
        assertThat(columnasDe("docentes")).contains("persona_id", "legajo", "fecha_alta");

        // 6) La constancia del dato biométrico suprimido (ADR-0016): el embedding pasa a
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

    // ========================================================================
    //  Los CHECK
    // ========================================================================

    /**
     * Cada CHECK rechaza lo que tiene que rechazar, y es él quien lo rechaza.
     *
     * <p>Cada caso parte de una fila que entra y le cambia una sola cosa. Lo que se comprueba es
     * que el motor la rechace <b>nombrando ese constraint</b>: rechazada por otro, el caso no
     * probaría lo que dice; y si el CHECK desapareciera, la fila entraría y el caso fallaría.
     */
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("filasQueUnCheckRechaza")
    @DisplayName("Cada CHECK rechaza lo suyo, y es él quien lo rechaza")
    void cadaCheckRechazaLoSuyo(String constraint, String caso, String tabla,
                                Map<String, Object> fila) {
        exigirMotor();
        migrada();

        assertThatThrownBy(() -> insertar(tabla, fila))
            .as("%s tendría que rechazar %s", constraint, caso)
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("`" + constraint + "`");
    }

    static Stream<Arguments> filasQueUnCheckRechaza() {
        String bloques = "bloques_presencia";
        return Stream.of(
            // V019: los diez de bloques_presencia.
            Arguments.of("ck_bloques_origen_entrada", "una entrada que no es por rostro ni a mano",
                bloques, con(abierto(), "origen_entrada", "CAMARA")),
            Arguments.of("ck_bloques_origen_salida", "una salida de origen desconocido",
                bloques, con(cerrado(), "origen_salida", "OTRO")),
            Arguments.of("ck_bloques_estado_cierre", "un estado de cierre que no existe",
                bloques, con(cerrado(), "estado_cierre", "CERRADO")),
            Arguments.of("ck_bloques_estado_salida", "un estado de salida que no existe",
                bloques, con(cerrado(), "estado_salida", "TARDE")),
            Arguments.of("ck_bloques_confianza_entrada", "una confianza de entrada mayor que 1",
                bloques, con(abierto(), "confianza_entrada", new BigDecimal("1.0001"))),
            Arguments.of("ck_bloques_confianza_salida", "una confianza de salida negativa",
                bloques, con(cerrado(), "confianza_salida", new BigDecimal("-0.0001"))),
            Arguments.of("ck_bloques_cierre_coherente", "un bloque abierto con hora de salida",
                bloques, con(abierto(), "hora_salida", "10:00:00")),
            Arguments.of("ck_bloques_cierre_coherente", "un bloque cerrado sin hora de salida",
                bloques, con(cerrado(), "hora_salida", null)),
            Arguments.of("ck_bloques_salida_posterior", "una salida a la misma hora que la entrada",
                bloques, con(cerrado(), "hora_salida", "08:00:00")),
            Arguments.of("ck_bloques_entrada_modelo", "una entrada a mano con confianza del rostro",
                bloques, con(abierto(), "origen_entrada", "MANUAL", "motivo_entrada_id", 1,
                             "confianza_entrada", new BigDecimal("0.9"))),
            Arguments.of("ck_bloques_entrada_modelo", "una entrada a mano con modelo facial",
                bloques, con(abierto(), "origen_entrada", "MANUAL", "motivo_entrada_id", 1,
                             "modelo_facial_entrada_id", 1)),
            Arguments.of("ck_bloques_salida_modelo", "una salida presunta con confianza del rostro",
                bloques, con(cerrado(), "estado_cierre", "SIN_CIERRE", "origen_salida", "PRESUNTO",
                             "estado_salida", "SIN_MARCA", "confianza_salida", new BigDecimal("0.9"))),

            // V019, sobre la institución: el hueco entre clases va de 0 a 4 horas.
            Arguments.of("ck_instituciones_umbral_separacion", "un umbral de más de cuatro horas",
                "instituciones", institucion(241)),
            Arguments.of("ck_instituciones_umbral_separacion", "un umbral negativo",
                "instituciones", institucion(-1)),

            // V020: el cierre del admin lleva motivo, y ningún otro cierre lleva motivo, autor ni
            // detalle.
            Arguments.of("ck_bloques_cierre_admin", "un cierre del admin sin motivo",
                bloques, con(cerrado(), "estado_cierre", "CERRADO_POR_ADMIN", "origen_salida", "MANUAL")),
            Arguments.of("ck_bloques_cierre_admin", "un cierre por rostro con motivo",
                bloques, con(cerrado(), "motivo_cierre_id", 1)),
            Arguments.of("ck_bloques_cierre_admin", "un cierre por rostro con autor",
                bloques, con(cerrado(), "cerrado_por_usuario_id", 1)),
            Arguments.of("ck_bloques_cierre_admin", "un cierre por rostro con detalle",
                bloques, con(cerrado(), "detalle_cierre", "Corregida a mano")),

            // V029: el espejo, del lado de la entrada.
            Arguments.of("ck_bloques_entrada_admin", "una entrada a mano sin motivo",
                bloques, con(abierto(), "origen_entrada", "MANUAL")),
            Arguments.of("ck_bloques_entrada_admin", "una entrada por rostro con motivo",
                bloques, con(abierto(), "motivo_entrada_id", 1)),
            Arguments.of("ck_bloques_entrada_admin", "una entrada por rostro con autor",
                bloques, con(abierto(), "abierto_por_usuario_id", 1)),
            Arguments.of("ck_bloques_entrada_admin", "una entrada por rostro con detalle",
                bloques, con(abierto(), "detalle_entrada", "Sin cámara")),

            // V031: el equipo de la salida solo existe si hubo salida.
            Arguments.of("ck_bloques_puesto_salida_con_salida", "un equipo de salida en un bloque abierto",
                bloques, con(abierto(), "puesto_salida_id", 1)));
    }

    /**
     * Las formas válidas entran. Sin esto, un CHECK escrito de más —que rechazara también lo
     * correcto— pasaría por bueno: los casos de arriba solo miran que se rechace.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("filasValidas")
    @DisplayName("Las formas válidas de un bloque y de una institución entran")
    void lasFormasValidasEntran(String caso, String tabla, Map<String, Object> fila) {
        exigirMotor();
        migrada();

        assertThatCode(() -> insertar(tabla, fila))
            .as("%s tendría que entrar", caso)
            .doesNotThrowAnyException();
    }

    static Stream<Arguments> filasValidas() {
        String bloques = "bloques_presencia";
        return Stream.of(
            Arguments.of("un bloque abierto por rostro", bloques, abierto()),
            Arguments.of("un bloque cerrado por rostro, en hora", bloques, cerrado()),
            Arguments.of("la confianza en sus dos bordes, 1 y 0",
                bloques, con(cerrado(), "confianza_entrada", BigDecimal.ONE,
                             "confianza_salida", BigDecimal.ZERO)),
            Arguments.of("una entrada a mano, con motivo, autor y detalle",
                bloques, con(abierto(), "origen_entrada", "MANUAL", "motivo_entrada_id", 1,
                             "abierto_por_usuario_id", 1, "detalle_entrada", "Sin cámara")),
            Arguments.of("un cierre del admin, con motivo, autor y detalle",
                bloques, con(cerrado(), "estado_cierre", "CERRADO_POR_ADMIN", "origen_salida", "MANUAL",
                             "motivo_cierre_id", 1, "cerrado_por_usuario_id", 1,
                             "detalle_cierre", "Se fue sin marcar")),
            Arguments.of("un bloque sin cierre, con la salida presunta",
                bloques, con(cerrado(), "estado_cierre", "SIN_CIERRE", "origen_salida", "PRESUNTO",
                             "estado_salida", "SIN_MARCA")),
            Arguments.of("un bloque cerrado, con el equipo de la salida",
                bloques, con(cerrado(), "puesto_salida_id", 1)),
            Arguments.of("un umbral de cero minutos", "instituciones", institucion(0)),
            Arguments.of("un umbral de cuatro horas justas", "instituciones", institucion(240)));
    }

    /**
     * Un docente no puede tener dos bloques abiertos a la vez, y uno cerrado no cuenta. Lo
     * sostiene el índice único sobre una columna generada que vale el docente mientras el bloque
     * está abierto y NULL cuando no; era el punto en duda de V019, porque descansa en que un
     * UNIQUE admite repetir NULL.
     */
    @Test
    @DisplayName("Un docente no tiene dos bloques abiertos, pero sí uno abierto y otro cerrado")
    void unSoloBloqueAbiertoPorDocente() {
        exigirMotor();
        migrada();
        Map<String, Object> otroMasTarde = con(abierto(), "hora_entrada", "11:00:00");

        assertThatThrownBy(() -> insertar("bloques_presencia", List.of(abierto(), otroMasTarde)))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("uq_bloques_un_solo_abierto_por_docente");

        assertThatCode(() -> insertar("bloques_presencia", List.of(cerrado(), otroMasTarde)))
            .doesNotThrowAnyException();
    }

    // Un bloque abierto por rostro: lo mínimo que la tabla acepta. Los ids no apuntan a nada,
    // ver insertar().
    private static Map<String, Object> abierto() {
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("institucion_id", 1);
        fila.put("docente_id", 1);
        fila.put("fecha", "2026-09-01");
        fila.put("hora_entrada", "08:00:00");
        fila.put("origen_entrada", "AUTOMATICO");
        fila.put("estado_cierre", "ABIERTO");
        return fila;
    }

    // El mismo bloque, cerrado por rostro y en hora.
    private static Map<String, Object> cerrado() {
        return con(abierto(), "estado_cierre", "CERRADO_POR_ROSTRO", "hora_salida", "10:00:00",
                   "origen_salida", "AUTOMATICO", "estado_salida", "EN_HORA");
    }

    // Una institución con el umbral que se pida; lo demás, lo mínimo.
    private static Map<String, Object> institucion(int umbral) {
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("nombre", "Instituto del umbral");
        fila.put("umbral_separacion_min", umbral);
        return fila;
    }

    // Una copia de la fila con valores cambiados, de a pares: columna, valor, columna, valor.
    private static Map<String, Object> con(Map<String, Object> base, Object... pares) {
        Map<String, Object> fila = new LinkedHashMap<>(base);
        for (int i = 0; i < pares.length; i += 2) {
            fila.put((String) pares[i], pares[i + 1]);
        }
        return fila;
    }

    private static void insertar(String tabla, Map<String, Object> fila) throws SQLException {
        insertar(tabla, List.of(fila));
    }

    /**
     * Inserta las filas en una transacción y la deshace al final, entren o no: cada caso arranca
     * de las tablas vacías.
     *
     * <p>Con las claves foráneas apagadas en la sesión. Lo que se prueba son los CHECK, y así un
     * caso no necesita sembrar la institución, el docente, el usuario, el motivo y el modelo
     * facial a los que apunta. Los CHECK siguen activos: los gobierna otra variable,
     * {@code check_constraint_checks}.
     */
    private static void insertar(String tabla, List<Map<String, Object>> filas) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL_BASE + BASE, USUARIO, CLAVE)) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SET SESSION foreign_key_checks = 0");
            }
            try {
                for (Map<String, Object> fila : filas) {
                    String sql = "INSERT INTO " + tabla + " (" + String.join(", ", fila.keySet())
                        + ") VALUES (" + String.join(", ", Collections.nCopies(fila.size(), "?")) + ")";
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        int i = 1;
                        for (Object valor : fila.values()) {
                            ps.setObject(i++, valor);
                        }
                        ps.executeUpdate();
                    }
                }
            } finally {
                c.rollback();
            }
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
