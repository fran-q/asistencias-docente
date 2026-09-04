/* =============================================================================
 *  sugerir-usuario.js
 *
 *  Propone un nombre de usuario a partir del nombre de la institucion.
 *
 *  Por que. El alta pide dos nombres: el completo, que es el dato legal y va en
 *  los reportes y en las constancias, y uno corto que sirve para entrar. Escribir
 *  dos nombres seguidos se lee como un tramite repetido, y el segundo termina
 *  siendo cualquier cosa --justo el que despues hay que recordar todos los dias.
 *
 *  Es una SUGERENCIA, no una imposicion. Sacar buenas siglas de un nombre exige
 *  saber que parte es la sigla: de "Universidad Tecnologica Nacional - Facultad
 *  Regional Tierra del Fuego" una persona saca "utn-frtdf" y ningun algoritmo
 *  generico llega a eso. Lo que se propone es razonable; corregirlo cuesta dos
 *  segundos y el campo queda intacto apenas alguien lo toca.
 * ========================================================================== */

(function (window, document) {
    'use strict';

    // Palabras que no aportan a una sigla. Sin esto "Instituto de Formacion Docente"
    // daria "idfd" en vez de "ifd".
    var VACIAS = ['de', 'del', 'la', 'las', 'el', 'los', 'y', 'en', 'para', 'por', 'a'];

    // Quita tildes y la diaeresis. La n con virgulilla se conserva como n: el usuario
    // solo admite [a-zA-Z0-9._-], y "nino" es preferible a perder la letra.
    function sinAcentos(texto) {
        return texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
    }

    // A partir de aca se abrevia. El corte NO es por cantidad de palabras sino por
    // largo del resultado: "CENT 35" da "cent-35" y se entiende, pero "Universidad
    // Tecnologica Nacional" daria "universidad-tecnologica-nacional", treinta y dos
    // caracteres para tipear todos los dias. Lo que importa es cuanto se escribe.
    var LARGO_ACEPTABLE = 16;

    // Reduce una parte del nombre: entera si es corta, siglas si no. Con forzarSiglas
    // en true no consulta el largo y siempre devuelve las iniciales.
    function abreviar(parte, forzarSiglas) {
        var palabras = parte.split(/[^a-z0-9]+/).filter(function (p) { return p.length > 0; });
        if (palabras.length === 0) return '';

        var utiles = palabras.filter(function (p) { return VACIAS.indexOf(p) === -1; });
        if (utiles.length === 0) utiles = palabras;

        var entero = utiles.join('-');
        if (!forzarSiglas && entero.length <= LARGO_ACEPTABLE) return entero;

        // Siglas: la inicial de cada palabra, pero los numeros enteros. En "EPET N 2"
        // el 2 es lo que la distingue de las demas, y reducirlo a "2" ya esta bien,
        // pero en "CENT 35" recortar a "3" la arruinaria.
        return utiles.map(function (p) {
            return /^[0-9]+$/.test(p) ? p : p.charAt(0);
        }).join('');
    }

    /**
     * Arma una propuesta a partir del nombre completo.
     *
     * <p>Antes de abreviar se parte por el guion, porque los nombres institucionales
     * suelen venir en dos mitades: "Universidad Tecnologica Nacional - Facultad
     * Regional Tierra del Fuego". Abreviando cada mitad por separado sale "utn-frtf",
     * que se lee; tratandolo como un solo bloque salia "utnfrtf", que no.
     */
    function proponer(nombre) {
        var limpio = sinAcentos(nombre).toLowerCase().trim();
        if (!limpio) return '';

        var partes = limpio.split(/\s+[-–—]+\s+/);

        function unir(forzarSiglas) {
            return partes
                .map(function (p) { return abreviar(p, forzarSiglas); })
                .filter(function (p) { return p.length > 0; })
                .join('-');
        }

        // El largo se mide sobre el resultado ARMADO y no sobre cada mitad: en
        // "Instituto N 1 - Sede Central" ninguna de las dos pasaba el limite por su
        // cuenta, pero juntas daban veintiseis caracteres. Si el resultado sigue siendo
        // largo, se abrevian las dos a siglas.
        var suave = unir(false);
        if (suave.length <= LARGO_ACEPTABLE) return suave.slice(0, 60);

        var duro = unir(true);
        return (duro || suave).slice(0, 60);   // el campo admite 60
    }

    document.addEventListener('DOMContentLoaded', function () {
        var destino = document.querySelector('[data-sugerir-desde]');
        if (!destino) return;

        var origen = document.getElementById(destino.getAttribute('data-sugerir-desde'));
        if (!origen) return;

        // Si el campo ya trae algo --el formulario volvio con errores y hay que
        // conservar lo tipeado-- no se pisa nada.
        var tocadoAMano = destino.value.trim().length > 0;

        // Cualquier edicion manual corta la sugerencia para siempre. Seguir escribiendo
        // encima de lo que alguien acaba de decidir es lo peor que puede hacer esto.
        ['input', 'change'].forEach(function (evento) {
            destino.addEventListener(evento, function (e) {
                if (e.isTrusted) tocadoAMano = true;
            });
        });

        origen.addEventListener('input', function () {
            if (tocadoAMano) return;
            destino.value = proponer(origen.value);
        });
    });

    // Expuesto para poder probar la derivacion sin montar el formulario.
    window.SugerirUsuario = { proponer: proponer };

})(window, document);
