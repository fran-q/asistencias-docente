/* =============================================================================
 *  hora-picker.js
 *
 *  Reemplaza el selector de hora del navegador por dos listas: hora y minutos.
 *
 *  Por que. El <input type="time"> se ve y se comporta distinto en cada
 *  navegador, y el de Chrome abre una doble columna que hay que recorrer entera
 *  para llegar a las 14. Dos listas se ven igual en todos lados y son dos
 *  clicks siempre.
 *
 *  El input original NO se elimina: queda oculto y sigue siendo el que se envia
 *  y el que el servidor valida. Los selects solo escriben en el. Asi el formato
 *  que viaja (HH:mm) no cambia, y si este script no llegara a correr el campo
 *  sigue existiendo con su valor.
 * ========================================================================== */
(function (window, document) {
    'use strict';

    // De 5 en 5: los horarios de cursada caen siempre en multiplos de cinco, y
    // sesenta opciones de minutos convierten la lista en algo que hay que buscar.
    var PASO_MINUTOS = 5;

    function dosDigitos(n) {
        return (n < 10 ? '0' : '') + n;
    }

    function llenar(select, hasta, paso) {
        for (var n = 0; n < hasta; n += paso) {
            var o = document.createElement('option');
            o.value = dosDigitos(n);
            o.textContent = dosDigitos(n);
            select.appendChild(o);
        }
    }

    function conectar(contenedor) {
        var id      = contenedor.dataset.horaPicker;
        var oculto  = document.getElementById(id);
        var selHora = document.getElementById(id + '-h');
        var selMin  = document.getElementById(id + '-m');
        if (!oculto || !selHora || !selMin) return;

        llenar(selHora, 24, 1);
        llenar(selMin, 60, PASO_MINUTOS);

        // Estado inicial: lo que ya traiga el input, que en modo edicion es la hora
        // guardada y en alta puede venir de un intento anterior con errores.
        var partes = (oculto.value || '').split(':');
        if (partes.length >= 2) {
            selHora.value = dosDigitos(parseInt(partes[0], 10));
            var min = parseInt(partes[1], 10);
            // Un horario cargado antes de este cambio puede no caer en el paso de cinco.
            // En vez de moverlo en silencio se le agrega su propia opcion, asi el
            // formulario muestra la hora que de verdad esta guardada.
            if (min % PASO_MINUTOS !== 0) {
                var extra = document.createElement('option');
                extra.value = dosDigitos(min);
                extra.textContent = dosDigitos(min);
                selMin.appendChild(extra);
            }
            selMin.value = dosDigitos(min);
        }

        function volcar() {
            oculto.value = selHora.value + ':' + selMin.value;
            // Se avisa por si algo mas escucha el campo (validacion del navegador incluida).
            oculto.dispatchEvent(new Event('change', { bubbles: true }));
        }

        selHora.addEventListener('change', volcar);
        selMin.addEventListener('change', volcar);
        volcar();
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('[data-hora-picker]').forEach(conectar);
    });

})(window, document);
