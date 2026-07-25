package edu.cent35.asistencias;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de arranque de la aplicación. Spring Boot escanea desde este paquete hacia abajo, así
 * que todo lo de controller, service, repository, model, dto y config se registra solo.
 */
@SpringBootApplication
public class AsistenciasApplication {

	// Levanta el contexto de Spring y el servidor embebido.
	public static void main(String[] args) {
		SpringApplication.run(AsistenciasApplication.class, args);
	}

}
