package edu.cent35.asistencias;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Properties;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La zona horaria del sistema. Un servidor en la nube corre en UTC, y como todo lo que decide según
 * la hora sale del reloj de la máquina, sin esto el sistema andaría tres horas adelantado.
 */
class AsistenciasApplicationTest {

    private TimeZone zonaOriginal;

    @BeforeEach
    void guardarZona() {
        zonaOriginal = TimeZone.getDefault();
    }

    @AfterEach
    void reponerZona() {
        TimeZone.setDefault(zonaOriginal);
    }

    @Test
    @DisplayName("En un servidor en UTC, el reloj del sistema queda en la hora de Ushuaia")
    void elRelojNoDependeDelServidor() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        AsistenciasApplication.fijarZonaHoraria();

        assertThat(Clock.systemDefaultZone().getZone().getId())
            .isEqualTo("America/Argentina/Ushuaia");
    }

    @Test
    @DisplayName("Hibernate usa la misma zona que el reloj del sistema")
    void hibernateUsaLaMismaZona() throws IOException {
        Properties base = new Properties();
        try (InputStream entrada = new ClassPathResource("application.properties").getInputStream()) {
            base.load(entrada);
        }

        // Si difieren, lo que se guarda y lo que se decide quedan corridos entre sí.
        assertThat(base.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone"))
            .isEqualTo(AsistenciasApplication.ZONA_HORARIA);
    }
}
