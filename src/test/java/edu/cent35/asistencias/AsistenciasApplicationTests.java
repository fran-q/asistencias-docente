package edu.cent35.asistencias;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: verifica que el contexto de Spring arranca sin errores.
 * Usa el perfil "test" (H2 en memoria) para no requerir MariaDB durante el build.
 */
@SpringBootTest
@ActiveProfiles("test")
class AsistenciasApplicationTests {

	@Test
	void contextLoads() {
	}

}
