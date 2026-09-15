package edu.cent35.asistencias.model;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Escribe el nombre y el apellido de una persona con mayúscula inicial, se hayan tipeado en
 * minúscula o todo en mayúscula. Solo corrige lo que no tiene una forma elegida: una palabra
 * que ya mezcla mayúsculas y minúsculas, como McDonald o DiStefano, queda como se escribió.
 *
 * <p>El navegador aplica la misma regla al salir del campo ({@code js/comun/nombre-propio.js}),
 * pero solo para mostrar el resultado antes de guardar: lo que se guarda lo decide esta clase,
 * desde {@code DocenteService} y {@code UsuarioService}. Si se cambia una, se cambia la otra.
 */
public final class NombrePropio {

    // Las partículas de los nombres y apellidos compuestos: "María de los Ángeles", "González
    // de la Vega". Van en minúscula, salvo cuando abren el campo: un apellido que se escribe
    // solo empieza con mayúscula, "De la Fuente".
    private static final Set<String> PARTICULAS = Set.of(
        "de", "del", "la", "las", "los", "y", "e", "da", "das", "do", "dos", "di", "van", "von");

    // Con la clase Unicode, para que también cuente el espacio duro de un texto copiado.
    private static final Pattern ESPACIOS = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private NombrePropio() {
    }

    // Devuelve el texto con mayúscula inicial en cada palabra y sin espacios de más.
    public static String normalizar(String texto) {
        if (texto == null) return null;
        String limpio = ESPACIOS.matcher(texto).replaceAll(" ").strip();
        if (limpio.isEmpty()) return limpio;

        String[] palabras = limpio.split(" ");
        StringBuilder sb = new StringBuilder(limpio.length());
        for (int i = 0; i < palabras.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(palabra(palabras[i], i == 0));
        }
        return sb.toString();
    }

    // Corrige una palabra. Cada tramo entre guiones o apóstrofos lleva su propia mayúscula:
    // Pérez-Gómez, O'Connor.
    private static String palabra(String p, boolean primera) {
        String minuscula = p.toLowerCase(Locale.ROOT);
        if (!primera && !mezcla(p) && PARTICULAS.contains(minuscula)) return minuscula;

        StringBuilder sb = new StringBuilder(p.length());
        int desde = 0;
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c == '-' || c == '\'' || c == '’') {
                sb.append(tramo(p.substring(desde, i))).append(c);
                desde = i + 1;
            }
        }
        return sb.append(tramo(p.substring(desde))).toString();
    }

    // Un tramo que mezcla mayúsculas y minúsculas se respeta; si no, lleva mayúscula inicial y
    // el resto en minúscula.
    private static String tramo(String t) {
        if (t.isEmpty() || mezcla(t)) return t;
        String m = t.toLowerCase(Locale.ROOT);
        int inicial = m.codePointAt(0);
        return new StringBuilder(m.length())
            .appendCodePoint(Character.toUpperCase(inicial))
            .append(m, Character.charCount(inicial), m.length())
            .toString();
    }

    // Si el texto trae mayúsculas y minúsculas a la vez: alguien eligió cómo se escribe.
    private static boolean mezcla(String t) {
        return t.codePoints().anyMatch(Character::isUpperCase)
            && t.codePoints().anyMatch(Character::isLowerCase);
    }
}
