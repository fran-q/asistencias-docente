package edu.cent35.asistencias.integracion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El armado de la pantalla del pase: la cámara ocupa el alto de la ventana y el acceso al
 * kiosco vive en el encabezado.
 *
 * <p><b>Mira la plantilla y no la pantalla renderizada</b>, igual que los dos casos de
 * {@code AjustesPantallasIT} que revisan los scripts sueltos. Lo que hay que comprobar es
 * dónde está escrito cada bloque, y para eso no hace falta levantar una institución, un
 * puesto autorizado y una clase en curso: el HTML sale igual.
 *
 * <p><b>Qué cuida.</b> Las dos cosas se caen sin romper nada visible. Sin
 * {@code pantalla-camara} la pantalla vuelve a desplazarse entera y el video vuelve a su
 * proporción fija, o sea recortado por abajo —la imagen se corta a la altura del cuello—.
 * Y el acceso al kiosco de vuelta al pie es un botón que nadie encuentra. En los dos casos
 * la pantalla abre bien y no falla nada: se nota recién mirándola.
 */
class PasePantallaIT {

    private static final Path PASE =
        Path.of("src/main/resources/templates/asistencia/pase.html");

    @Test
    @DisplayName("La pantalla del pase pide el marco de alto de ventana")
    void elPaseOcupaLaVentana() throws Exception {
        String plantilla = Files.readString(PASE);

        assertThat(plantilla)
            .as("sin esta clase el video vuelve a su proporción fija y se recorta por abajo")
            .contains("class=\"pase pantalla-camara\"");
    }

    @Test
    @DisplayName("El acceso al kiosco está en el encabezado, no al pie")
    void elAccesoAlKioscoEstaArriba() throws Exception {
        String plantilla = Files.readString(PASE);

        int finDelEncabezado = plantilla.indexOf("</header>");
        int accesoAlKiosco = plantilla.indexOf("value=\"kiosco\"");

        assertThat(finDelEncabezado).as("la pantalla perdió su encabezado").isNotNegative();
        assertThat(accesoAlKiosco)
            .as("se busca el campo oculto y no el texto del botón, que ya cambió una vez")
            .isNotNegative();
        assertThat(accesoAlKiosco)
            .as("el acceso al kiosco volvió abajo, donde no se encuentra")
            .isLessThan(finDelEncabezado);
    }

    @Test
    @DisplayName("Sigue habiendo un solo acceso al kiosco")
    void hayUnSoloAccesoAlKiosco() throws Exception {
        String plantilla = Files.readString(PASE);

        // Duplicarlo pondria en dos lugares una accion que CIERRA LA SESION, y la que se
        // aprieta sin querer es siempre la que sobra. Paso al moverlo del encabezado al pie
        // la vez anterior: quedaron los dos un rato.
        assertThat(plantilla.split("value=\"kiosco\"", -1).length - 1).isEqualTo(1);
    }
}
