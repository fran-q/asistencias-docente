package edu.cent35.asistencias.service;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

/**
 * Texto legal del consentimiento informado para tratamiento de datos
 * biometricos del docente. Cumple con los requisitos de la Ley 25.326
 * (informacion previa al titular) y de la Resolucion AAIP 255/2022
 * (datos biometricos = sensibles, consentimiento expreso).
 * <p>
 * <b>Versionado</b>: cuando se modifique el texto, hay que incrementar
 * {@link #VERSION_ACTUAL} y dejar el texto anterior accesible si fuera
 * necesario reconstruir el historico. Los docentes que aceptaron versiones
 * previas siguen vigentes (su aceptacion fue legal en ese momento); el
 * sistema puede sugerirles re-aceptar la version nueva pero no es
 * obligatorio.
 * <p>
 * En la base de datos, cada registro guarda en {@code version_terminos}
 * el valor de {@link #VERSION_ACTUAL} que estaba al momento de aceptar.
 */
public final class TextoConsentimiento {

    /**
     * Version del texto vigente. Formato {@code aaaa-mm-vN}.
     * Incrementar cuando se modifique el cuerpo del texto.
     */
    public static final String VERSION_ACTUAL = "2026-05-v1";

    /**
     * Cuerpo del consentimiento. Se renderiza tal cual en la pantalla
     * de aceptacion (template Thymeleaf {@code consentimiento-otorgar.html}).
     * <p>
     * Si el texto cambia, hay que ajustar tambien {@link #VERSION_ACTUAL}.
     */
    public static final String CUERPO = """
        CONSENTIMIENTO INFORMADO PARA EL TRATAMIENTO DE DATOS BIOMETRICOS
        (Ley Nacional N° 25.326 de Proteccion de Datos Personales y
        Resolucion AAIP N° 255/2022)

        1. RESPONSABLE DEL TRATAMIENTO
           La institucion educativa identificada en este sistema es la
           responsable del tratamiento de los datos personales y biometricos
           del docente cuya firma figura al pie. Para cualquier consulta o
           ejercicio de los derechos ARCO (acceso, rectificacion, cancelacion
           y oposicion), el docente debe dirigirse a la administracion de su
           institucion.

        2. FINALIDAD DEL TRATAMIENTO
           Los datos biometricos faciales del docente seran utilizados
           exclusivamente para verificar su presencia en clase mediante un
           sistema automatizado de reconocimiento facial. No se utilizaran
           para ninguna otra finalidad, ni se compartiran con terceros.

        3. DATOS QUE SE TRATARAN
           El sistema procesa una representacion matematica (embedding) del
           rostro del docente, NO una fotografia. Los embeddings se almacenan
           cifrados y no permiten reconstruir la imagen original. Las
           imagenes captadas durante el proceso de registro inicial se
           descartan inmediatamente despues de generar el embedding.

        4. CONSERVACION
           Los datos biometricos se conservaran mientras dure la relacion
           laboral del docente con la institucion. Una vez finalizada, los
           datos seran eliminados de forma segura dentro de los treinta (30)
           dias corridos.

        5. DERECHOS DEL DOCENTE
           El docente puede en cualquier momento:
           - Acceder a los datos que el sistema tiene sobre el.
           - Pedir su rectificacion si fueran inexactos.
           - Solicitar la cancelacion de su modelo biometrico, lo que
             implica que dejara de poder usarse el reconocimiento facial
             para registrar su asistencia.
           - REVOCAR este consentimiento en cualquier momento, sin necesidad
             de justificar la decision y sin que ello le acarree perjuicios.

        6. CARACTER VOLUNTARIO
           El consentimiento es libre, expreso e informado. La negativa a
           otorgarlo no podra ser causa de discriminacion ni de sancion. En
           caso de no consentir, la institucion ofrecera un mecanismo
           alternativo de registro de asistencia.

        7. AUDITORIA
           El sistema registra, con fines de prueba forense, la fecha, la
           hora, la direccion IP y el navegador desde donde se otorgo o
           revoco el presente consentimiento.

        Habiendo leido y comprendido lo anterior, OTORGO mi consentimiento
        libre, expreso e informado para el tratamiento de mis datos
        biometricos con la finalidad indicada en el punto 2.
        """;

    private TextoConsentimiento() {
        // util class
    }
}
