package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.model.academico.EstadoCiclo;
import edu.cent35.asistencias.model.academico.TipoDiaNoLaborable;
import edu.cent35.asistencias.model.asistencia.EstadoAsistencia;
import edu.cent35.asistencias.model.asistencia.EstadoCierre;
import edu.cent35.asistencias.model.asistencia.EstadoSalida;
import edu.cent35.asistencias.model.asistencia.MetodoAsistencia;
import edu.cent35.asistencias.model.asistencia.OrigenMarca;
import edu.cent35.asistencias.model.biometria.MetodoConsentimiento;
import edu.cent35.asistencias.model.institucion.PropositoCodigo;
import edu.cent35.asistencias.model.institucion.RolCodigo;
import edu.cent35.asistencias.model.persona.CampoIdentidad;
import edu.cent35.asistencias.model.persona.OrigenCambioIdentidad;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
 * V029 y V031), los que atan el método de una asistencia a su estado (V034), y el índice único
 * que impide dos bloques abiertos del mismo docente. Hasta el 2026-10-17 se probaban a mano.
 *
 * <p><b>Y que los enums digan lo mismo que la base.</b> Cada enum persistido se compara con el
 * CHECK de su columna, y uno nuevo que nadie sumara a esa comparación hace fallar el test.
 *
 * <p><b>Y que una migración que corrige datos toque solo lo que debe.</b> Sobre una base vacía
 * no corrige nada, así que pasaría en verde con cualquier criterio: V033 y V034 se prueban
 * aparte, con filas sembradas.
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
    //  Los enums
    // ========================================================================

    /**
     * Cada enum persistido tiene los mismos valores que el CHECK de su columna.
     *
     * <p>Un valor que está en el enum y no en el CHECK revienta recién en MariaDB, porque el
     * resto de la suite corre sobre H2, sin estos CHECK. Al revés queda un valor que la base
     * acepta y el sistema no conoce: así sobrevivió {@code DIGITAL} hasta V035. Se compara contra
     * lo que el motor tiene guardado, no contra el texto de las migraciones.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("enumsConSuCheck")
    @DisplayName("Cada enum persistido tiene los mismos valores que su CHECK")
    void cadaEnumCoincideConSuCheck(String constraint, Class<?> tipo, Set<String> valores)
            throws Exception {
        exigirMotor();
        migrada();

        Set<String> clausulas = leer("SELECT CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS "
            + "WHERE CONSTRAINT_SCHEMA = '" + BASE + "' AND CONSTRAINT_NAME = '" + constraint + "'");
        assertThat(clausulas).as("tiene que existir el CHECK %s", constraint).hasSize(1);

        Set<String> enLaBase = Pattern.compile("'([^']*)'").matcher(clausulas.iterator().next())
            .results().map(m -> m.group(1)).collect(Collectors.toSet());
        assertThat(enLaBase)
            .as("%s tiene que admitir lo mismo que %s", constraint, tipo.getSimpleName())
            .containsExactlyInAnyOrderElementsOf(valores);
    }

    static Stream<Arguments> enumsConSuCheck() {
        return Stream.of(
            comparacion("ck_asistencias_estado", EstadoAsistencia.class),
            comparacion("ck_asistencias_metodo", MetodoAsistencia.class),
            comparacion("ck_bloques_estado_cierre", EstadoCierre.class),
            comparacion("ck_bloques_estado_salida", EstadoSalida.class),
            comparacion("ck_bloques_origen_salida", OrigenMarca.class),
            // Sin PRESUNTO, a propósito: una entrada siempre la vio alguien.
            comparacion("ck_bloques_origen_entrada", OrigenMarca.class, OrigenMarca.PRESUNTO),
            comparacion("ck_ciclos_estado", EstadoCiclo.class),
            comparacion("ck_consentimientos_metodo", MetodoConsentimiento.class),
            comparacion("ck_dias_tipo", TipoDiaNoLaborable.class),
            comparacion("ck_cambios_campo", CampoIdentidad.class),
            comparacion("ck_cambios_origen", OrigenCambioIdentidad.class));
    }

    // Un CHECK, el enum de su columna y lo que tiene que admitir: todo el enum, menos lo excluido.
    private static Arguments comparacion(String constraint, Class<? extends Enum<?>> tipo,
                                         Enum<?>... excluidos) {
        Set<String> valores = nombres(tipo);
        for (Enum<?> excluido : excluidos) {
            valores.remove(excluido.name());
        }
        return Arguments.of(constraint, tipo, valores);
    }

    private static Set<String> nombres(Class<? extends Enum<?>> tipo) {
        Set<String> valores = new LinkedHashSet<>();
        for (Enum<?> valor : tipo.getEnumConstants()) {
            valores.add(valor.name());
        }
        return valores;
    }

    /** Los roles no tienen CHECK sino un catálogo sembrado, y tiene que decir lo mismo que el enum. */
    @Test
    @DisplayName("Ninguna foránea que MariaDB 10.6 rechazaba tiene ON DELETE SET NULL (TD-011, V038)")
    void lasColumnasConCheckNoTienenSetNull() throws Exception {
        exigirMotor();
        Set<String> foraneas = Set.of(
            "fk_asistencias_modelo_facial", "fk_bloques_modelo_entrada", "fk_bloques_modelo_salida",
            "fk_bloques_cerrado_por", "fk_bloques_abierto_por");
        try (Connection c = DriverManager.getConnection(URL_BASE, USUARIO, CLAVE);
             PreparedStatement ps = c.prepareStatement(
                 "SELECT CONSTRAINT_NAME, DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS "
                     + "WHERE CONSTRAINT_SCHEMA = ?")) {
            ps.setString(1, BASE);
            Map<String, String> reglas = new LinkedHashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (foraneas.contains(rs.getString(1))) {
                        reglas.put(rs.getString(1), rs.getString(2));
                    }
                }
            }
            assertThat(reglas).hasSize(foraneas.size());
            assertThat(reglas.values()).containsOnly("RESTRICT");
        }
    }

    @Test
    @DisplayName("Los roles sembrados son los que el sistema entiende")
    void losRolesSembradosCoincidenConSuEnum() throws Exception {
        exigirMotor();
        migrada();

        assertThat(leer("SELECT codigo FROM roles"))
            .containsExactlyInAnyOrderElementsOf(nombres(RolCodigo.class));
    }

    /**
     * Ningún enum de una entidad se queda afuera de esa comparación, y todos se guardan por nombre.
     *
     * <p>Sin esto, un enum nuevo simplemente no estaría en la lista y la comparación seguiría en
     * verde sin mirarlo. Y uno sin {@code EnumType.STRING} se guardaría por posición: agregar un
     * valor en el medio cambiaría lo que dicen las filas ya grabadas, sin que nada falle.
     *
     * <p>No necesita el motor: mira las clases.
     */
    @Test
    @DisplayName("Todo enum de una entidad se guarda por nombre y tiene con qué compararse")
    void ningunEnumSeQuedaAfuera() throws Exception {
        Set<Object> comparados = enumsConSuCheck().map(a -> a.get()[1])
            .collect(Collectors.toCollection(HashSet::new));
        // Sin CHECK, a propósito: los valores los fija el enum (ver 03-modelo-datos), y un código
        // ya emitido con un propósito que se retira tiene que poder vencer solo.
        comparados.add(PropositoCodigo.class);

        ClassPathScanningCandidateComponentProvider escaner =
            new ClassPathScanningCandidateComponentProvider(false);
        escaner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Set<BeanDefinition> entidades = escaner.findCandidateComponents("edu.cent35.asistencias.model");
        assertThat(entidades).as("el escaneo tiene que encontrar las entidades").isNotEmpty();

        for (BeanDefinition entidad : entidades) {
            for (Field campo : Class.forName(entidad.getBeanClassName()).getDeclaredFields()) {
                if (!campo.getType().isEnum()) {
                    continue;
                }
                String donde = entidad.getBeanClassName() + "." + campo.getName();
                Enumerated enumerado = campo.getAnnotation(Enumerated.class);
                assertThat(enumerado).as("%s tiene que llevar @Enumerated", donde).isNotNull();
                assertThat(enumerado.value())
                    .as("%s tiene que guardarse por nombre", donde)
                    .isEqualTo(EnumType.STRING);
                assertThat(comparados)
                    .as("%s tiene que estar en enumsConSuCheck", donde)
                    .contains(campo.getType());
            }
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
                bloques, con(abierto(), "puesto_salida_id", 1)),

            // V034: cada método dice una sola cosa, y queda atado al estado.
            Arguments.of("ck_asistencias_metodo", "un método que no existe",
                "asistencias", con(asistencia(1, null), "metodo", "CAMARA")),
            Arguments.of("ck_asistencias_metodo_modelo", "una ausencia del job con modelo facial",
                "asistencias", con(ausenciaDelJob(), "modelo_facial_id", 1)),
            Arguments.of("ck_asistencias_metodo_modelo", "una carga a mano con confianza del rostro",
                "asistencias", con(asistencia(1, null), "metodo", "MANUAL",
                                   "confianza", new BigDecimal("0.9"))),
            Arguments.of("ck_asistencias_sistema", "una presencia que dice haberla generado el job",
                "asistencias", con(ausenciaDelJob(), "estado", "PRESENTE")),
            Arguments.of("ck_asistencias_sistema", "una ausencia del job con jornada",
                "asistencias", con(ausenciaDelJob(), "bloque_id", 1)),
            Arguments.of("ck_asistencias_camara_presente", "una ausencia registrada por la cámara",
                "asistencias", con(asistencia(1, null), "estado", "AUSENTE")),

            // V037: lo que dicen varias columnas sobre un mismo estado tiene que coincidir.
            Arguments.of("ck_bloques_cierre_origen", "un cierre por rostro con la salida presunta",
                bloques, con(cerrado(), "origen_salida", "PRESUNTO")),
            Arguments.of("ck_bloques_cierre_origen", "un cierre por rostro sin marca de salida",
                bloques, con(cerrado(), "estado_salida", "SIN_MARCA")),
            Arguments.of("ck_bloques_cierre_origen", "un cierre del admin con la salida por rostro",
                bloques, con(cerrado(), "estado_cierre", "CERRADO_POR_ADMIN", "motivo_cierre_id", 1)),
            Arguments.of("ck_bloques_cierre_origen", "un bloque sin cierre con la salida a mano",
                bloques, con(cerrado(), "estado_cierre", "SIN_CIERRE", "origen_salida", "MANUAL",
                             "estado_salida", "SIN_MARCA")),
            Arguments.of("ck_bloques_cierre_origen", "un bloque sin cierre que se fue en hora",
                bloques, con(cerrado(), "estado_cierre", "SIN_CIERRE", "origen_salida", "PRESUNTO")),
            Arguments.of("ck_consentimientos_vigencia", "un consentimiento vigente y revocado",
                "consentimientos_biometricos", con(consentimiento(), "fecha_revocacion", "2026-09-01 10:00:00")),
            Arguments.of("ck_consentimientos_vigencia", "uno que dejó de estar vigente sin revocarse",
                "consentimientos_biometricos", con(consentimiento(), "vigente", 0)),
            Arguments.of("ck_carreras_baja_coherente", "una carrera activa con fecha de baja",
                "carreras", con(carrera(), "fecha_baja", "2026-09-01")),
            Arguments.of("ck_carreras_baja_coherente", "una carrera dada de baja sin fecha",
                "carreras", con(carrera(), "activo", 0)));
    }

    /**
     * Las formas válidas entran. Sin esto, un CHECK escrito de más —que rechazara también lo
     * correcto— pasaría por bueno: los casos de arriba solo miran que se rechace.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("filasValidas")
    @DisplayName("Las formas válidas de un bloque, una institución y una asistencia entran")
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
            Arguments.of("un umbral de cuatro horas justas", "instituciones", institucion(240)),
            Arguments.of("una ausencia del job", "asistencias", ausenciaDelJob()),
            Arguments.of("una marca de la cámara, con su modelo", "asistencias",
                con(asistencia(1, null), "modelo_facial_id", 1, "confianza", new BigDecimal("0.9100"))),
            Arguments.of("una ausencia cargada a mano", "asistencias",
                con(asistencia(1, null), "metodo", "MANUAL", "estado", "AUSENTE")),
            Arguments.of("una entrada sin cámara, con su jornada", "asistencias",
                con(asistencia(1, 1), "metodo", "MANUAL")),
            Arguments.of("un cierre del admin que se fue antes", bloques,
                con(cerrado(), "estado_cierre", "CERRADO_POR_ADMIN", "origen_salida", "MANUAL",
                    "estado_salida", "ANTICIPADA", "motivo_cierre_id", 1)),
            Arguments.of("un consentimiento vigente", "consentimientos_biometricos", consentimiento()),
            Arguments.of("un consentimiento revocado", "consentimientos_biometricos",
                con(consentimiento(), "vigente", 0, "fecha_revocacion", "2026-09-01 10:00:00")),
            Arguments.of("una carrera activa", "carreras", carrera()),
            Arguments.of("una carrera dada de baja, con su fecha", "carreras",
                con(carrera(), "activo", 0, "fecha_baja", "2026-09-01")),
            Arguments.of("un equipo operando como kiosco", "puestos_captura",
                con(puesto(), "kiosco_habilitado", 1, "kiosco_habilitado_en", "2026-09-01 10:00:00")),
            // Revocar no baja la bandera del kiosco, a propósito: dice que el equipo estaba operando
            // solo cuando se revocó. Lo que cierra la puerta es que se miren las dos columnas.
            Arguments.of("un equipo revocado que estaba operando como kiosco", "puestos_captura",
                con(puesto(), "activo", 0, "fecha_baja", "2026-09-01",
                    "kiosco_habilitado", 1, "kiosco_habilitado_en", "2026-09-01 10:00:00")));
    }

    /**
     * Toda tabla con baja lógica ata {@code activo} y {@code fecha_baja} con un CHECK (V037).
     *
     * <p>Mira el esquema y no una lista escrita acá: una tabla nueva con baja lógica que se
     * olvide del CHECK hace fallar el test sin que nadie tenga que acordarse de sumarla.
     */
    @Test
    @DisplayName("Toda tabla con baja lógica ata activo y fecha_baja con un CHECK")
    void todaBajaLogicaEstaAtada() throws Exception {
        exigirMotor();
        migrada();

        Set<String> conBaja = leer("SELECT b.TABLE_NAME FROM information_schema.COLUMNS b "
            + "JOIN information_schema.COLUMNS a ON a.TABLE_SCHEMA = b.TABLE_SCHEMA "
            + "  AND a.TABLE_NAME = b.TABLE_NAME AND a.COLUMN_NAME = 'activo' "
            + "WHERE b.TABLE_SCHEMA = '" + BASE + "' AND b.COLUMN_NAME = 'fecha_baja'");
        assertThat(conBaja).contains("usuarios", "docentes", "carreras", "puestos_captura");

        for (String tabla : conBaja) {
            Set<String> clausulas = leer("SELECT CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS "
                + "WHERE CONSTRAINT_SCHEMA = '" + BASE + "' AND TABLE_NAME = '" + tabla + "'");
            assertThat(clausulas)
                .as("%s tiene que atar activo y fecha_baja", tabla)
                .anyMatch(c -> c.contains("`activo`") && c.contains("`fecha_baja`"));
        }
    }

    /**
     * Lo que el sistema supone único mientras esté activo, la base lo garantiza (V037): un
     * consentimiento vigente y un modelo facial activo por docente, y un ciclo activo por
     * institución. Hasta acá lo cuidaba solo el servicio, que no frena dos pedidos simultáneos.
     *
     * <p>Mismo mecanismo que el bloque abierto de V019: un UNIQUE sobre una columna generada que
     * vale NULL cuando no aplica, porque un UNIQUE admite repetir NULL. Por eso también se prueba
     * que uno activo y uno que ya no lo está conviven: es lo que conserva el historial.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("unoSoloPorVez")
    @DisplayName("Lo que tiene que ser único mientras está activo, lo es")
    void unoSoloPorVez(String indice, String tabla, Map<String, Object> activo,
                       Map<String, Object> otroActivo, Map<String, Object> yaNoActivo) {
        exigirMotor();
        migrada();

        assertThatThrownBy(() -> insertar(tabla, List.of(activo, otroActivo)))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining(indice);

        assertThatCode(() -> insertar(tabla, List.of(activo, yaNoActivo)))
            .doesNotThrowAnyException();
    }

    static Stream<Arguments> unoSoloPorVez() {
        Map<String, Object> vigente = consentimiento();
        Map<String, Object> ciclo = new LinkedHashMap<>();
        ciclo.put("institucion_id", 1);
        ciclo.put("anio", 2026);
        ciclo.put("fecha_inicio", "2026-03-01");
        ciclo.put("fecha_fin", "2026-12-20");
        ciclo.put("estado", "ACTIVO");
        Map<String, Object> ciclo2027 = con(ciclo, "anio", 2027,
                                            "fecha_inicio", "2027-03-01", "fecha_fin", "2027-12-20");
        return Stream.of(
            Arguments.of("uq_consentimientos_un_vigente_por_docente", "consentimientos_biometricos",
                vigente, con(vigente, "version_terminos", "v2"),
                con(vigente, "vigente", 0, "fecha_revocacion", "2026-09-01 10:00:00")),
            Arguments.of("uq_modelos_un_activo_por_docente", "modelos_faciales",
                modelo(), modelo(), con(modelo(), "activo", 0, "fecha_baja", "2026-09-01 10:00:00")),
            Arguments.of("uq_ciclos_un_activo_por_institucion", "ciclos_lectivos",
                ciclo, ciclo2027, con(ciclo2027, "estado", "CERRADO")));
    }

    // Un consentimiento vigente: lo mínimo que la tabla acepta. Los ids no apuntan a nada.
    private static Map<String, Object> consentimiento() {
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("docente_id", 1);
        fila.put("version_terminos", "v1");
        fila.put("metodo", "ESCRITO");
        fila.put("registrado_por_usuario_id", 1);
        return fila;
    }

    // Un modelo facial activo. El contenido no importa: lo que se prueba es su estado.
    private static Map<String, Object> modelo() {
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("docente_id", 1);
        fila.put("embedding_cifrado", new byte[] {1});
        fila.put("algoritmo", "LBPH");
        fila.put("version_algoritmo", "1");
        fila.put("dimensiones", 1);
        fila.put("registrado_por_usuario_id", 1);
        return fila;
    }

    private static Map<String, Object> carrera() {
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("institucion_id", 1);
        fila.put("codigo", "TSDS");
        fila.put("nombre", "Tecnicatura");
        return fila;
    }

    private static Map<String, Object> puesto() {
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("institucion_id", 1);
        fila.put("nombre", "Entrada norte");
        fila.put("token_hash", "hash");
        return fila;
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

    /**
     * V033 pasa a MANUAL las asistencias que imputó una entrada sin cámara, y ninguna otra.
     *
     * <p>Sobre la base recién migrada la migración no encontró nada que corregir, así que acá se
     * vuelve a correr su UPDATE —el texto del archivo, no una copia— sobre filas sembradas para
     * el caso. Las cuatro que no tienen que cambiar son cada una un límite del criterio: la de
     * un bloque por rostro, la del job sin bloque, la que tiene evidencia biométrica y la que
     * apunta a un bloque de otra institución.
     */
    @Test
    @DisplayName("V033 pasa a MANUAL las asistencias de una entrada sin cámara, y ninguna otra")
    void v033CorrigeSoloLasDeEntradaSinCamara() throws Exception {
        exigirMotor();
        migrada();
        Map<Long, String> metodoDespues = new LinkedHashMap<>();

        try (Connection c = DriverManager.getConnection(URL_BASE + BASE, USUARIO, CLAVE)) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SET SESSION foreign_key_checks = 0");
            }
            try {
                // Una jornada abierta a mano, una por rostro y otra a mano de la institución 2.
                // Un docente por bloque: dos abiertos del mismo los rechaza el índice único.
                Map<String, Object> aMano = con(abierto(), "origen_entrada", "MANUAL",
                                                "motivo_entrada_id", 1, "abierto_por_usuario_id", 1);
                insertarEn(c, "bloques_presencia", con(aMano, "id", 101, "docente_id", 1));
                insertarEn(c, "bloques_presencia", con(abierto(), "id", 102, "docente_id", 2));
                insertarEn(c, "bloques_presencia",
                           con(aMano, "id", 103, "docente_id", 3, "institucion_id", 2));

                Object[] evidencia = {"modelo_facial_id", 7, "confianza", new BigDecimal("0.9100")};
                insertarEn(c, "asistencias", asistencia(1, 101));
                insertarEn(c, "asistencias", con(asistencia(2, 102), evidencia));
                insertarEn(c, "asistencias",
                           con(asistencia(3, null), "estado", "AUSENTE", "metodo", "SISTEMA"));
                insertarEn(c, "asistencias", con(asistencia(4, 101), evidencia));
                insertarEn(c, "asistencias", asistencia(5, 103));

                try (Statement st = c.createStatement()) {
                    st.executeUpdate(sentenciaDe("V033__entrada_sin_camara_como_manual.sql", "UPDATE"));
                }
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT id, metodo FROM asistencias ORDER BY id")) {
                    while (rs.next()) {
                        metodoDespues.put(rs.getLong(1), rs.getString(2));
                    }
                }
            } finally {
                c.rollback();
            }
        }

        assertThat(metodoDespues).containsExactly(
            Map.entry(1L, "MANUAL"),
            Map.entry(2L, "AUTOMATICO"),
            Map.entry(3L, "SISTEMA"),
            Map.entry(4L, "AUTOMATICO"),
            Map.entry(5L, "AUTOMATICO"));
    }

    /**
     * V034 pasa a SISTEMA las ausencias que había generado el job, y ninguna otra fila.
     *
     * <p>El esquema migrado ya no admite el estado de antes —una ausencia AUTOMATICO la rechaza
     * {@code ck_asistencias_camara_presente}—, así que las filas se siembran con los CHECK
     * apagados en la sesión, y se prenden de nuevo antes de correr el UPDATE del archivo. Eso
     * prueba dos cosas: qué filas toca, y que las que toca quedan cumpliendo los CHECK nuevos.
     */
    @Test
    @DisplayName("V034 pasa a SISTEMA las ausencias del job, y ninguna otra")
    void v034ReetiquetaSoloLasAusenciasDelJob() throws Exception {
        exigirMotor();
        migrada();
        Map<Long, String> metodoDespues = new LinkedHashMap<>();

        try (Connection c = DriverManager.getConnection(URL_BASE + BASE, USUARIO, CLAVE)) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SET SESSION foreign_key_checks = 0");
                st.execute("SET SESSION check_constraint_checks = 0");
            }
            try {
                // 1: una ausencia del job, como quedaba antes de V034.
                insertarEn(c, "asistencias", con(asistencia(1, null), "estado", "AUSENTE"));
                // 2: una marca de la cámara, con su evidencia.
                insertarEn(c, "asistencias", con(asistencia(2, null), "modelo_facial_id", 7,
                                                 "confianza", new BigDecimal("0.9100")));
                // 3: una marca de la cámara cuyo modelo se suprimió (ARCO): AUTOMATICO sin modelo.
                insertarEn(c, "asistencias", con(asistencia(3, null), "estado", "TARDE"));
                // 4: una ausencia cargada a mano.
                insertarEn(c, "asistencias",
                           con(asistencia(4, null), "metodo", "MANUAL", "estado", "AUSENTE"));

                try (Statement st = c.createStatement()) {
                    st.execute("SET SESSION check_constraint_checks = 1");
                    st.executeUpdate(sentenciaDe("V034__metodo_sistema_para_ausencias_del_job.sql", "UPDATE"));
                }
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT id, metodo FROM asistencias ORDER BY id")) {
                    while (rs.next()) {
                        metodoDespues.put(rs.getLong(1), rs.getString(2));
                    }
                }
            } finally {
                c.rollback();
            }
        }

        assertThat(metodoDespues).containsExactly(
            Map.entry(1L, "SISTEMA"),
            Map.entry(2L, "AUTOMATICO"),
            Map.entry(3L, "AUTOMATICO"),
            Map.entry(4L, "MANUAL"));
    }

    /**
     * V037 les pone fecha a las cuentas que el sistema dio de baja sin ella, y a ninguna otra.
     *
     * <p>{@code UsuarioService.darDeBaja} ponía {@code activo = 0} sin tocar {@code fecha_baja}, y
     * el CHECK nuevo rechaza esa combinación. Las filas se siembran con los CHECK apagados, como
     * en la prueba de V034: el esquema migrado ya no admite el estado de antes.
     */
    @Test
    @DisplayName("V037 completa la fecha de las cuentas dadas de baja sin ella, y de ninguna otra")
    void v037CompletaLaFechaDeLasCuentasDadasDeBaja() throws Exception {
        exigirMotor();
        migrada();
        Map<Long, String> fechaDespues = new LinkedHashMap<>();

        try (Connection c = DriverManager.getConnection(URL_BASE + BASE, USUARIO, CLAVE)) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SET SESSION foreign_key_checks = 0");
                st.execute("SET SESSION check_constraint_checks = 0");
            }
            try {
                // 1: dada de baja por el código viejo. Toma la fecha de su última modificación.
                insertarEn(c, "usuarios",
                           con(usuario(1), "activo", 0, "actualizado_en", "2026-05-04 10:00:00"));
                // 2: activa.
                insertarEn(c, "usuarios", usuario(2));
                // 3: dada de baja con su fecha, que no se pisa.
                insertarEn(c, "usuarios", con(usuario(3), "activo", 0, "fecha_baja", "2026-01-15"));

                try (Statement st = c.createStatement()) {
                    st.execute("SET SESSION check_constraint_checks = 1");
                    st.executeUpdate(sentenciaDe("V037__estados_atados_en_la_base.sql", "UPDATE"));
                }
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT id, fecha_baja FROM usuarios ORDER BY id")) {
                    while (rs.next()) {
                        fechaDespues.put(rs.getLong(1), String.valueOf(rs.getString(2)));
                    }
                }
            } finally {
                c.rollback();
            }
        }

        assertThat(fechaDespues).containsExactly(
            Map.entry(1L, "2026-05-04"),
            Map.entry(2L, "null"),
            Map.entry(3L, "2026-01-15"));
    }

    // Una cuenta activa: lo mínimo que la tabla acepta. Los ids no apuntan a nada.
    private static Map<String, Object> usuario(long id) {
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("id", id);
        fila.put("institucion_id", 1);
        fila.put("rol_id", 1);
        fila.put("username", "cuenta" + id);
        fila.put("email", "cuenta" + id + "@ejemplo.edu.ar");
        fila.put("password_hash", "no-se-usa");
        return fila;
    }

    // Una ausencia como la genera el job desde V034: SISTEMA, sin jornada ni evidencia.
    private static Map<String, Object> ausenciaDelJob() {
        return con(asistencia(1, null), "estado", "AUSENTE", "metodo", "SISTEMA");
    }

    // Una asistencia por rostro de la institución 1, con el bloque que se pida o ninguno. El
    // docente y el horario salen del id para no chocar con el UNIQUE de la clase y el día.
    private static Map<String, Object> asistencia(long id, Integer bloqueId) {
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("id", id);
        fila.put("institucion_id", 1);
        fila.put("docente_id", id);
        fila.put("comision_id", 1);
        fila.put("horario_id", id);
        fila.put("fecha", "2026-09-01");
        fila.put("hora_registrada", "08:00:00");
        fila.put("estado", "PRESENTE");
        fila.put("metodo", "AUTOMATICO");
        if (bloqueId != null) {
            fila.put("bloque_id", bloqueId);
        }
        return fila;
    }

    // La sentencia de una migración que empieza con lo que se pida, tal como está en el archivo:
    // sin los comentarios y con el esquema de esta base en lugar del placeholder. Tiene que haber
    // exactamente una, para que el test no corra otra sin darse cuenta.
    private static String sentenciaDe(String archivo, String comienzo) throws Exception {
        try (InputStream in = MigracionesIT.class.getResourceAsStream("/db/migration/" + archivo)) {
            assertThat(in).as("no está la migración %s", archivo).isNotNull();
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                .lines()
                .filter(linea -> !linea.strip().startsWith("--"))
                .collect(Collectors.joining("\n"))
                .replace("${esquema}", BASE);
            List<String> candidatas = Stream.of(sql.split(";"))
                .map(String::strip)
                .filter(s -> s.startsWith(comienzo))
                .toList();
            assertThat(candidatas)
                .as("%s tendría que tener una sola sentencia que empiece con %s", archivo, comienzo)
                .hasSize(1);
            return candidatas.get(0);
        }
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
                    insertarEn(c, tabla, fila);
                }
            } finally {
                c.rollback();
            }
        }
    }

    // Una fila, en la conexión y la transacción de quien llama.
    private static void insertarEn(Connection c, String tabla, Map<String, Object> fila)
            throws SQLException {
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
