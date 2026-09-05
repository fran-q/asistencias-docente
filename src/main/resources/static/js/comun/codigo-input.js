/* =============================================================================
 *  codigo-input.js — Las seis casillas del código de un solo uso
 *
 *  Las casillas son sólo la capa visible. El valor que viaja al servidor sigue
 *  siendo el input hidden [data-codigo-valor], así que el DTO y el controlador
 *  no cambian: reciben el mismo string de seis dígitos que recibían de un input
 *  común.
 *
 *  Qué resuelve, además de verse mejor:
 *
 *    · Pegar. El código llega por mail y se copia completo. Pegar "481920" en la
 *      primera casilla la llenaría con un solo carácter y descartaría el resto;
 *      acá se distribuye entre las seis.
 *    · Borrar. Backspace en una casilla vacía vuelve a la anterior, que es lo
 *      que hace cualquiera que se dio cuenta de que erró un dígito.
 *    · Sólo dígitos. El código es numérico; filtrar en la entrada evita el viaje
 *      al servidor para que responda que no es válido.
 *
 *  Si el JavaScript no corre, las casillas siguen siendo inputs de un carácter y
 *  el hidden queda vacío: por eso el formulario valida el código en el servidor
 *  igual que antes.
 * ========================================================================== */
(function () {
    'use strict';

    var form = document.querySelector('[data-codigo-form]');
    if (!form) return;

    var grupo  = form.querySelector('[data-codigo-casillas]');
    var oculto = form.querySelector('[data-codigo-valor]');
    if (!grupo || !oculto) return;

    var casillas = Array.prototype.slice.call(grupo.querySelectorAll('input'));

    function sincronizar() {
        oculto.value = casillas.map(function (c) { return c.value; }).join('');
    }

    function repartir(texto, desde) {
        var digitos = (texto || '').replace(/\D/g, '').split('');
        for (var i = desde; i < casillas.length && digitos.length; i++) {
            casillas[i].value = digitos.shift();
        }
        sincronizar();
        /* El foco va a la primera vacía, o a la última si se completó: dejarlo
           donde estaba obliga a buscar a mano dónde seguir. */
        var vacia = casillas.find(function (c) { return !c.value; });
        (vacia || casillas[casillas.length - 1]).focus();

        /* Completo el código, el botón de enviar es el próximo paso lógico. No se
           envía solo: el formulario también pide la contraseña nueva. */
    }

    casillas.forEach(function (casilla, i) {
        casilla.addEventListener('input', function () {
            /* Si el navegador metió más de un carácter (autocompletado del SMS,
               teclado predictivo), se reparte en vez de recortarse. */
            if (casilla.value.length > 1) { repartir(casilla.value, i); return; }

            casilla.value = casilla.value.replace(/\D/g, '');
            sincronizar();
            if (casilla.value && i < casillas.length - 1) casillas[i + 1].focus();
        });

        casilla.addEventListener('keydown', function (e) {
            if (e.key === 'Backspace' && !casilla.value && i > 0) {
                e.preventDefault();
                casillas[i - 1].value = '';
                sincronizar();
                casillas[i - 1].focus();
            }
            if (e.key === 'ArrowLeft'  && i > 0)                   casillas[i - 1].focus();
            if (e.key === 'ArrowRight' && i < casillas.length - 1) casillas[i + 1].focus();
        });

        casilla.addEventListener('paste', function (e) {
            e.preventDefault();
            repartir((e.clipboardData || window.clipboardData).getData('text'), i);
        });

        /* Al enfocar se selecciona lo que hay: escribir encima reemplaza en vez
           de quedar bloqueado por el maxlength. */
        casilla.addEventListener('focus', function () { casilla.select(); });
    });

    sincronizar();
})();
