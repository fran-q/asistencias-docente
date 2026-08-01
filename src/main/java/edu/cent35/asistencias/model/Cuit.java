package edu.cent35.asistencias.model;

/**
 * Lleva el CUIT a una sola forma antes de guardarlo. Hace falta porque el CUIT es único en
 * todo el sistema: si una institución lo carga con guiones y otra sin ellos, son el mismo
 * número pero dos textos distintos, y la restricción de unicidad no los detectaría.
 */
public final class Cuit {

    /** Forma en la que se guarda siempre: 30-12345678-9. */
    private static final int LARGO = 11;

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
        String digitos = crudo.replaceAll("\\D", "");
        if (digitos.isEmpty()) return null;

        // Si no son 11 digitos no se puede armar la forma canonica. Se devuelve lo que vino
        // recortado: la validacion del formulario ya lo rechazo, y aca inventar algo seria
        // peor que dejarlo pasar tal cual.
        if (digitos.length() != LARGO) return crudo.trim();

        return digitos.substring(0, 2) + "-" + digitos.substring(2, 10) + "-" + digitos.substring(10);
    }
}
