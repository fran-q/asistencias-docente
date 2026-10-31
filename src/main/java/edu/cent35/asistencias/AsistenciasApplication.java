package edu.cent35.asistencias;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

/**
 * Punto de arranque de la aplicación. Spring Boot escanea desde este paquete hacia abajo, así
 * que todo lo de controller, service, repository, model, dto y config se registra solo.
 */
@SpringBootApplication
public class AsistenciasApplication {

    /** La hora con la que trabaja el sistema, sea cual sea la del servidor donde corra. */
    public static final String ZONA_HORARIA = "America/Argentina/Ushuaia";

    /**
     * Fija la zona de toda la JVM, antes de que exista ningún bean.
     *
     * <p>Qué clase está en curso, quién llegó tarde, de qué día son las ausencias: todo eso sale de
     * {@code Clock.systemDefaultZone()}, que usa la zona de la máquina. Con un servidor en UTC, el
     * sistema andaría tres horas adelantado sin ningún error a la vista. Va antes de arrancar
     * Spring porque cada servicio guarda su reloj al crearse, y ese reloj ya no cambia de zona.
     */
    static void fijarZonaHoraria() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONA_HORARIA));
    }

    // Fija la hora y levanta el contexto de Spring y el servidor embebido.
    public static void main(String[] args) {
        fijarZonaHoraria();
        SpringApplication.run(AsistenciasApplication.class, args);
    }

}
