package edu.cent35.asistencias.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLIntegrityConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que cada restricción de la base se traduzca a una explicación entendible, y que el
 * texto crudo del motor —que menciona tablas, índices y valores concretos— nunca llegue a la
 * pantalla.
 */
class ManejadorDeColisionesTest {

    @ParameterizedTest(name = "{0} se explica hablando de {1}")
    @CsvSource({
        "uq_docentes_inst_dni,             DNI",
        "uq_docentes_inst_legajo,          legajo",
        "uq_usuarios_inst_username,        nombre de usuario",
        "uq_usuarios_inst_email,           correo",
        "uq_instituciones_nombre,          nombre",
        "uq_instituciones_cuit,            CUIT",
        "uq_carreras_inst_codigo,          carrera",
        "uq_materias_inst_codigo,          materia",
        "uq_comisiones_materia_codigo,     comisión",
        "uq_asistencias_doc_horario_fecha, marca",
        "ck_docentes_baja_posterior_al_alta, fecha de baja"
    })
    @DisplayName("Cada restriccion se traduce a un mensaje que nombra el dato en conflicto")
    void traduceCadaRestriccion(String restriccion, String loQueTieneQueNombrar) {
        String mensaje = ManejadorDeColisiones.traducir(errorDeLaBase(restriccion));

        assertThat(mensaje).containsIgnoringCase(loQueTieneQueNombrar);
    }

    @Test
    @DisplayName("El texto crudo del motor no se filtra a la pantalla")
    void noFiltraElMensajeDelMotor() {
        String mensaje = ManejadorDeColisiones.traducir(errorDeLaBase("uq_docentes_inst_dni"));

        // El error real dice "Duplicate entry '1-30111222' for key 'uq_docentes_inst_dni'":
        // nombra el indice, la tabla implicita y el valor que se intento guardar.
        assertThat(mensaje)
            .doesNotContain("uq_docentes_inst_dni")
            .doesNotContain("Duplicate entry")
            .doesNotContain("30111222");
    }

    @Test
    @DisplayName("Una restriccion desconocida cae en un mensaje general, no en el del motor")
    void restriccionDesconocida() {
        String mensaje = ManejadorDeColisiones.traducir(errorDeLaBase("uq_algo_que_no_existe_todavia"));

        assertThat(mensaje)
            .doesNotContain("uq_algo_que_no_existe_todavia")
            .containsIgnoringCase("no se pueden repetir");
    }

    // Arma el error como llega de verdad: Spring envuelve al del driver, y el nombre del
    // indice aparece recien en la causa mas profunda.
    private DataIntegrityViolationException errorDeLaBase(String restriccion) {
        var raiz = new SQLIntegrityConstraintViolationException(
            "Duplicate entry '1-30111222' for key '" + restriccion + "'");
        return new DataIntegrityViolationException("could not execute statement", raiz);
    }
}
