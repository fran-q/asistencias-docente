package edu.cent35.asistencias.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El comando del respaldo, sin base ni {@code mysqldump} instalado.
 *
 * <p>Lo que se cubre acá es <b>qué se le pide que vuelque</b>, que es donde una copia se
 * convierte en un archivo inútil sin que nadie se entere: un volcado sin el esquema del
 * historial de Flyway se genera igual, pesa lo mismo y parece completo, pero al restaurarlo la
 * aplicación no levanta. Que el proceso corra de verdad no está cubierto por tests —depende de
 * una herramienta externa— y se prueba instalando.
 */
@DisplayName("Comando del respaldo")
class RespaldoServiceTest {

    private static final Path DESTINO = Path.of("respaldo.sql");

    private RespaldoService servicio(String url, String esquemaFlyway) {
        return new RespaldoService(url, "visum", "secreta", esquemaFlyway, "mysqldump", 10);
    }

    @Test
    @DisplayName("se lleva la base de datos y la del historial de Flyway")
    void incluyeLasDosBases() {
        RespaldoService s = servicio(
            "jdbc:mariadb://localhost:3306/asistenciautomatica?useUnicode=true",
            "asistenciautomatica_meta");

        List<String> comando = s.comando(s.leerDatosDeConexion(), DESTINO);

        assertThat(comando).containsSubsequence("--databases",
                                                "asistenciautomatica",
                                                "asistenciautomatica_meta");
    }

    @Test
    @DisplayName("lee host, puerto y esquema de la URL")
    void leeLaConexion() {
        RespaldoService s = servicio("jdbc:mariadb://10.0.0.5:3307/visumdb", "meta");

        RespaldoService.Datos datos = s.leerDatosDeConexion();

        assertThat(datos.host()).isEqualTo("10.0.0.5");
        assertThat(datos.puerto()).isEqualTo("3307");
        assertThat(datos.esquema()).isEqualTo("visumdb");
    }

    @Test
    @DisplayName("sin puerto en la URL asume el de siempre")
    void puertoPorDefecto() {
        RespaldoService s = servicio("jdbc:mariadb://localhost/asistenciautomatica", "meta");

        assertThat(s.leerDatosDeConexion().puerto()).isEqualTo("3306");
    }

    @Test
    @DisplayName("no nombra dos veces el mismo esquema")
    void noRepiteElEsquema() {
        RespaldoService s = servicio("jdbc:mariadb://localhost:3306/unasola", "unasola");

        List<String> comando = s.comando(s.leerDatosDeConexion(), DESTINO);

        assertThat(comando.stream().filter("unasola"::equals)).hasSize(1);
    }

    @Test
    @DisplayName("no bloquea las tablas: el pase puede estar tomando asistencia")
    void noBloqueaLasTablas() {
        RespaldoService s = servicio("jdbc:mariadb://localhost:3306/asistenciautomatica", "meta");

        assertThat(s.comando(s.leerDatosDeConexion(), DESTINO)).contains("--single-transaction");
    }

    @Test
    @DisplayName("una URL que no se entiende lo dice, en vez de armar un comando a medias")
    void urlIlegible() {
        RespaldoService s = servicio("jdbc:h2:mem:testdb", "meta");

        assertThatThrownBy(s::leerDatosDeConexion)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("conexión a la base");
    }
}
