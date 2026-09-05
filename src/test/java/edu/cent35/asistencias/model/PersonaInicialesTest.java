package edu.cent35.asistencias.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre las iniciales del avatar de los listados.
 *
 * <p>Se prueban acá y no mirando la tabla porque el avatar es decorativo —lleva
 * {@code aria-hidden}— y por lo tanto es de las cosas que nadie mira hasta que están mal.
 * Los casos que importan no son el normal sino los bordes: una cuenta institucional no tiene
 * apellido, y con la implementación ingenua —recortar los dos primeros caracteres del nombre
 * completo— la inicial que sale es la segunda letra del apellido, no la del nombre.
 */
class PersonaInicialesTest {

    private Persona persona(String nombre, String apellido) {
        Persona p = new Persona();
        p.setNombre(nombre);
        p.setApellido(apellido);
        return p;
    }

    @Test
    @DisplayName("Son la del apellido y la del nombre, en ese orden")
    void apellidoYNombre() {
        // Y no "GA": el nombre completo es "García, María", así que recortarlo daría las dos
        // primeras del apellido, que es justo lo que este helper existe para evitar.
        assertThat(persona("María", "García").getIniciales()).isEqualTo("GM");
    }

    @Test
    @DisplayName("Siempre en mayúscula, venga como venga cargado")
    void siempreEnMayuscula() {
        assertThat(persona("maría", "garcía").getIniciales()).isEqualTo("GM");
    }

    @Test
    @DisplayName("Sin apellido usa las dos primeras del nombre")
    void cuentaInstitucionalSinApellido() {
        // Es el caso de las cuentas que representan al establecimiento: getNombreCompleto ya
        // tolera el apellido nulo por el mismo motivo.
        assertThat(persona("Secretaría", null).getIniciales()).isEqualTo("SE");
        assertThat(persona("Secretaría", "   ").getIniciales()).isEqualTo("SE");
    }

    @Test
    @DisplayName("Un nombre de una sola letra no rompe nada")
    void nombreDeUnaLetra() {
        assertThat(persona("A", null).getIniciales()).isEqualTo("A");
        assertThat(persona("A", "Pérez").getIniciales()).isEqualTo("PA");
    }

    @Test
    @DisplayName("Con los dos vacíos devuelve vacío, no una excepción")
    void ningunDato() {
        assertThat(persona(null, null).getIniciales()).isEmpty();
    }
}
