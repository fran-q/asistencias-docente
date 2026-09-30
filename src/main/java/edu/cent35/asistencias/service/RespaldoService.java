package edu.cent35.asistencias.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Una copia de la base, bajada desde el sistema y sin abrir una terminal (ADR-0022).
 *
 * <p><b>Por qué existe.</b> Una instalación autónoma guarda todo en una sola máquina: si esa
 * notebook se rompe o se la roban, se pierde el registro de asistencia de la institución entera.
 * Respaldar es la única defensa, y hasta acá la única forma de hacerlo era escribir un comando,
 * que es exactamente lo que este despliegue no puede pedirle a nadie.
 *
 * <p><b>Se lleva las dos bases, y eso no es un detalle.</b> El historial de Flyway vive en un
 * esquema aparte ({@code spring.flyway.default-schema}). Una copia que traiga solo los datos
 * parece completa y no lo es: al restaurarla, Flyway no encuentra su historial, con
 * {@code baseline-on-migrate} se planta en la versión 1 e intenta aplicar la V002 en adelante
 * sobre una base que ya las tiene. La migración falla y la aplicación no levanta.
 *
 * <p><b>Primero el archivo entero, después la descarga.</b> Si el volcado se mandara
 * directamente al navegador, un {@code mysqldump} que muere a la mitad dejaría un {@code .sql}
 * truncado que se abre igual y se lee como completo. Se escribe a un temporal, se mira el código
 * de salida, y recién entonces se entrega.
 */
@Service
@Slf4j
public class RespaldoService {

    private static final Pattern URL_JDBC =
        Pattern.compile("^jdbc:(?:mariadb|mysql)://([^:/?]+)(?::(\\d+))?/([^?;]+)");

    private static final DateTimeFormatter SELLO =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final String url;
    private final String usuario;
    private final String password;
    private final String esquemaFlyway;
    private final String mysqldump;
    private final int minutosDeEspera;

    public RespaldoService(
            @Value("${spring.datasource.url:}") String url,
            @Value("${spring.datasource.username:}") String usuario,
            @Value("${spring.datasource.password:}") String password,
            @Value("${spring.flyway.default-schema:}") String esquemaFlyway,
            // En la instalacion apunta al mysqldump que viene con la MariaDB empaquetada. En
            // desarrollo alcanza con que este en el PATH, o con la ruta del XAMPP.
            @Value("${app.instalacion.mysqldump:mysqldump}") String mysqldump,
            @Value("${app.instalacion.respaldo-minutos:10}") int minutosDeEspera) {
        this.url = url;
        this.usuario = usuario;
        this.password = password;
        this.esquemaFlyway = esquemaFlyway;
        this.mysqldump = mysqldump;
        this.minutosDeEspera = minutosDeEspera;
    }

    /**
     * Genera el volcado y devuelve el archivo temporal. Quien llama lo entrega y lo borra.
     *
     * @throws IllegalStateException si el volcado no se pudo completar, con el motivo adentro:
     *         es lo que se le muestra a la persona, así que dice qué pasó y no "error interno".
     */
    public Path generar() {
        Datos datos = leerDatosDeConexion();
        Path destino = archivoTemporal();

        List<String> comando = comando(datos, destino);
        log.info("Respaldo: volcando {} y {}", datos.esquema(), esquemaFlyway);

        try {
            ProcessBuilder pb = new ProcessBuilder(comando);
            // La contrasena por variable de entorno y no en la linea de comandos: los argumentos
            // de un proceso los ve cualquiera que liste los procesos de la maquina.
            pb.environment().put("MYSQL_PWD", password == null ? "" : password);
            pb.redirectErrorStream(false);
            Process proceso = pb.start();

            String errores = new String(proceso.getErrorStream().readAllBytes(),
                                        StandardCharsets.UTF_8);
            boolean termino = proceso.waitFor(minutosDeEspera, TimeUnit.MINUTES);
            if (!termino) {
                proceso.destroyForcibly();
                borrar(destino);
                throw new IllegalStateException(
                    "El respaldo tardó más de " + minutosDeEspera + " minutos y se canceló.");
            }
            if (proceso.exitValue() != 0) {
                log.error("Respaldo fallido (codigo {}): {}", proceso.exitValue(), errores.trim());
                borrar(destino);
                throw new IllegalStateException(
                    "No se pudo generar el respaldo. " + primeraLinea(errores));
            }
        } catch (IOException ex) {
            borrar(destino);
            // El caso mas comun de todos: la herramienta no esta donde dice la configuracion.
            throw new IllegalStateException(
                "No se encontró la herramienta de respaldo (" + mysqldump + "). "
                + "Revisá app.instalacion.mysqldump.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            borrar(destino);
            throw new IllegalStateException("El respaldo quedó interrumpido.", ex);
        }

        log.info("Respaldo generado: {} ({} bytes)", destino.getFileName(), tamanio(destino));
        return destino;
    }

    /** Cómo se va a llamar el archivo que baja. */
    public String nombreSugerido() {
        return "visum-respaldo-" + LocalDateTime.now().format(SELLO) + ".sql";
    }

    /**
     * El comando, armado aparte para poder comprobarlo sin una base ni un {@code mysqldump}
     * instalado. Lo que más se rompe acá no es la ejecución sino qué se le pide que vuelque.
     */
    List<String> comando(Datos datos, Path destino) {
        List<String> c = new ArrayList<>();
        c.add(mysqldump);
        c.add("--host=" + datos.host());
        c.add("--port=" + datos.puerto());
        c.add("--user=" + usuario);
        // Sin bloquear las tablas: el pase puede estar tomando asistencia mientras esto corre.
        c.add("--single-transaction");
        c.add("--default-character-set=utf8mb4");
        c.add("--result-file=" + destino.toAbsolutePath());
        c.add("--databases");
        c.add(datos.esquema());
        // El historial de Flyway. Sin esto la copia no se puede restaurar (ver el javadoc).
        if (esquemaFlyway != null && !esquemaFlyway.isBlank()
            && !esquemaFlyway.equals(datos.esquema())) {
            c.add(esquemaFlyway);
        }
        return c;
    }

    /** Host, puerto y esquema, sacados de la URL de conexión. */
    Datos leerDatosDeConexion() {
        Matcher m = URL_JDBC.matcher(url == null ? "" : url);
        if (!m.find()) {
            throw new IllegalStateException(
                "No se pudo leer la conexión a la base para armar el respaldo.");
        }
        String puerto = m.group(2) == null ? "3306" : m.group(2);
        return new Datos(m.group(1), puerto, m.group(3));
    }

    /** Los datos de conexión que necesita el volcado. */
    public record Datos(String host, String puerto, String esquema) {}

    private Path archivoTemporal() {
        try {
            return Files.createTempFile("visum-respaldo-", ".sql");
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo crear el archivo del respaldo.", ex);
        }
    }

    private void borrar(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ex) {
            log.warn("No se pudo borrar el temporal del respaldo: {}", p, ex);
        }
    }

    private long tamanio(Path p) {
        try {
            return Files.size(p);
        } catch (IOException ex) {
            return -1;
        }
    }

    // El primer renglon del error de la herramienta. El resto suele ser ruido, y esto va a una
    // pantalla que lee alguien de secretaria.
    private String primeraLinea(String texto) {
        if (texto == null || texto.isBlank()) return "";
        return texto.strip().lines().findFirst().orElse("").strip();
    }
}
