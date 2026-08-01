package edu.cent35.asistencias.model;

/**
 * Normaliza y valida un CUIT. La normalización existe porque el CUIT es único en todo el
 * sistema y compararlo como texto dejaría pasar el mismo número escrito de dos maneras; la
 * validación del dígito verificador existe porque un CUIT bien formado igual puede no existir.
 */
public final class Cuit {

    private static final int LARGO = 11;

    /**
     * Pesos del cálculo del dígito verificador, definidos por AFIP.
     *
     * <p>Cada uno de los diez primeros dígitos se multiplica por su peso; el verificador
     * sale de esa suma. Que estén en este orden no es arbitrario: es la especificación.
     */
    private static final int[] PESOS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private Cuit() {
        // Clase de utilidad: no se instancia.
    }

    /**
     * Devuelve el CUIT en la forma canónica con guiones.
     *
     * <p>Acepta las dos maneras en que la gente lo escribe —con guiones o de corrido— porque
     * exigir una sola era rechazar un dato correcto por un detalle de tipeo.
     *
     * @return el CUIT con guiones, o null si venía vacío
     */
    public static String normalizar(String crudo) {
        if (crudo == null) return null;
        String digitos = soloDigitos(crudo);
        if (digitos.isEmpty()) return null;

        // Si no son 11 digitos no se puede armar la forma canonica. Se devuelve lo que vino
        // recortado: la validacion del formulario ya lo rechazo, y aca inventar algo seria
        // peor que dejarlo pasar tal cual.
        if (digitos.length() != LARGO) return crudo.trim();

        return digitos.substring(0, 2) + "-" + digitos.substring(2, 10) + "-" + digitos.substring(10);
    }

    /**
     * Indica si el CUIT es válido, comprobando su dígito verificador.
     *
     * <p>Un CUIT con el formato correcto puede igualmente no existir: el último dígito se
     * calcula a partir de los diez anteriores, así que un error de tipeo en cualquiera de
     * ellos deja de cerrar. Es lo que separa "tiene forma de CUIT" de "es un CUIT".
     *
     * <p>Un valor vacío se considera válido: el campo es opcional, y quien decide si puede
     * faltar es la anotación de obligatoriedad, no esta comprobación.
     */
    public static boolean esValido(String crudo) {
        if (crudo == null) return true;
        String digitos = soloDigitos(crudo);
        if (digitos.isEmpty()) return true;
        if (digitos.length() != LARGO) return false;

        int declarado = digitos.charAt(LARGO - 1) - '0';
        return declarado == calcularVerificador(digitos);
    }

    // Aplica los pesos a los diez primeros digitos y deriva el verificador de esa suma.
    private static int calcularVerificador(String digitos) {
        int suma = 0;
        for (int i = 0; i < PESOS.length; i++) {
            suma += (digitos.charAt(i) - '0') * PESOS[i];
        }
        int verificador = 11 - (suma % 11);
        // Los dos casos de borde de la especificacion: 11 se colapsa a 0 y 10 a 9.
        if (verificador == 11) return 0;
        if (verificador == 10) return 9;
        return verificador;
    }

    private static String soloDigitos(String crudo) {
        return crudo.replaceAll("\\D", "");
    }
}
