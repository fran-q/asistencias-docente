package edu.cent35.asistencias.config;

import edu.cent35.asistencias.service.DocenteService;
import org.aspectj.weaver.tools.PointcutExpression;
import org.aspectj.weaver.tools.PointcutParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vigila que el pointcut de {@link TenantFilterAspect} siga alcanzando a
 * los services reales.
 * <p>
 * <b>Por que existe este test (TD-007).</b> El pointcut original apuntaba
 * a paquetes {@code ..application..}. Cuando el proyecto se reorganizo a
 * package-by-layer (ADR-0006) esos paquetes desaparecieron y el aspecto
 * quedo <b>silenciosamente inactivo</b>: la capa 1 de la defensa
 * multi-tenant murio sin que nada lo avisara, porque el resto de los
 * tests son unitarios con Mockito y no ejercitan Hibernate ni AOP.
 * <p>
 * Un aspecto que no matchea no falla: simplemente no hace nada. Por eso
 * hace falta un test que verifique explicitamente la coincidencia. Si
 * alguien vuelve a mover o renombrar los services, este test se pone rojo
 * en vez de dejar pasar una fuga entre instituciones (RF-04, RNF-10).
 */
class TenantFilterAspectTest {

    // El mismo pointcut declarado en el aspecto. Debe mantenerse sincronizado.
    private static final String POINTCUT = "@within(org.springframework.stereotype.Service)";

    @Test
    @DisplayName("el pointcut alcanza a los metodos de un @Service real")
    void pointcutAlcanzaLosServices() throws NoSuchMethodException {
        PointcutExpression expresion = PointcutParser
            .getPointcutParserSupportingAllPrimitivesAndUsingContextClassloaderForResolution()
            .parsePointcutExpression(POINTCUT);

        // DocenteService es un @Service representativo de la capa de negocio.
        Method metodo = DocenteService.class.getMethod("listar");

        assertThat(expresion.matchesMethodExecution(metodo).alwaysMatches())
            .as("El pointcut de TenantFilterAspect debe alcanzar a los @Service. "
                + "Si esto falla, el filtro multi-tenant de Hibernate NO se activa.")
            .isTrue();
    }

    @Test
    @DisplayName("el pointcut NO alcanza clases que no son @Service")
    void pointcutNoAlcanzaOtrasClases() throws NoSuchMethodException {
        PointcutExpression expresion = PointcutParser
            .getPointcutParserSupportingAllPrimitivesAndUsingContextClassloaderForResolution()
            .parsePointcutExpression(POINTCUT);

        // Una clase de utilidad sin @Service no debe activar el filtro.
        Method metodo = TenantContext.class.getMethod("get");

        assertThat(expresion.matchesMethodExecution(metodo).alwaysMatches()).isFalse();
    }
}
