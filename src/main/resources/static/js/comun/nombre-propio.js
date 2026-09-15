/* =============================================================================
 *  nombre-propio.js
 *
 *  Pone mayuscula inicial en los campos de nombre y apellido al salir del campo.
 *  Se activa con data-nombre-propio en el input.
 *
 *  Por que. Lo que se guarda lo decide el servidor (NombrePropio.java), que
 *  aplica esta misma regla venga el dato de donde venga. Esto solo la muestra
 *  antes: quien tipeo "juan pablo" ve "Juan Pablo" al pasar al campo siguiente,
 *  en vez de enterarse en el listado de que el sistema lo cambio.
 *
 *  Al salir del campo y no mientras se escribe: corregir en cada tecla le mueve
 *  el cursor a quien esta escribiendo.
 *
 *  La regla tiene que ser la misma que la de NombrePropio.java. Si difieren, el
 *  campo muestra una cosa y se guarda otra.
 * ========================================================================== */

(function (window, document) {
    'use strict';

    // Las mismas de NombrePropio: van en minuscula, salvo cuando abren el campo.
    var PARTICULAS = ['de', 'del', 'la', 'las', 'los', 'y', 'e', 'da', 'das', 'do', 'dos', 'di', 'van', 'von'];

    var MAYUSCULA = /\p{Lu}/u;
    var MINUSCULA = /\p{Ll}/u;

    // Si el texto trae mayusculas y minusculas a la vez: alguien eligio como se escribe.
    function mezcla(t) {
        return MAYUSCULA.test(t) && MINUSCULA.test(t);
    }

    // Un tramo mezclado se respeta; si no, mayuscula inicial y el resto en minuscula.
    function tramo(t) {
        if (!t || mezcla(t)) return t;
        var m = t.toLowerCase();
        var inicial = String.fromCodePoint(m.codePointAt(0));
        return inicial.toUpperCase() + m.slice(inicial.length);
    }

    // Cada tramo entre guiones o apostrofos lleva su propia mayuscula: Perez-Gomez, O'Connor.
    // El split con parentesis conserva los separadores en las posiciones impares.
    function palabra(p, primera) {
        var minuscula = p.toLowerCase();
        if (!primera && !mezcla(p) && PARTICULAS.indexOf(minuscula) !== -1) return minuscula;
        return p.split(/(['’-])/).map(function (parte, i) {
            return i % 2 === 1 ? parte : tramo(parte);
        }).join('');
    }

    function normalizar(texto) {
        var limpio = texto.replace(/\s+/g, ' ').trim();
        if (!limpio) return limpio;
        return limpio.split(' ').map(function (p, i) {
            return palabra(p, i === 0);
        }).join(' ');
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('input[data-nombre-propio]').forEach(function (campo) {
            // change y no blur: salta solo si el valor cambio, asi que pasar por el campo sin
            // tocarlo no reescribe el nombre que ya estaba guardado.
            campo.addEventListener('change', function () {
                var corregido = normalizar(campo.value);
                if (corregido !== campo.value) campo.value = corregido;
            });
        });
    });

    // Expuesto para poder probar la regla sin montar el formulario.
    window.NombrePropio = { normalizar: normalizar };

})(window, document);
