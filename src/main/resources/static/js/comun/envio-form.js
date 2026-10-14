/* =============================================================================
 *  envio-form.js
 *
 *  Bloquea el botón mientras el formulario viaja al servidor.
 *
 *  Por qué. Entre el click en "Guardar" y la recarga de la página no había
 *  ninguna señal propia: lo único que se movía era el indicador del navegador,
 *  que en una pestaña maximizada es una franja de dos píxeles arriba de todo.
 *  En una red lenta eso se lee como que el botón no anduvo, y la reacción
 *  natural es volver a apretarlo. El servidor recibía el alta dos veces.
 *
 *  Cubre los DOS caminos por los que se envía un formulario en la app:
 *
 *   1. Envío normal. Se escucha 'submit' en document, en fase de BURBUJA: así
 *      corre después del handler que form-validacion.js pone sobre el <form>.
 *      Si ese handler frenó el envío, defaultPrevented ya viene en true y no
 *      se bloquea nada --si no, un formulario inválido quedaría con el botón
 *      muerto y sin forma de reintentar.
 *
 *   2. Envío tras confirmar. confirm-modal.js frena el submit en fase de
 *      CAPTURA y después llama a form.submit(), que por especificación NO
 *      vuelve a disparar el evento. Ahí el aviso no puede llegar solo, así que
 *      confirm-modal invoca EnvioForm.marcar() antes de enviar.
 * ========================================================================== */

(function (window, document) {
    'use strict';

    var MARCA = 'data-enviando';

    function botonesDe(form, disparador) {
        // El botón que se apretó es el que cambia de texto. Los demás submit del
        // mismo formulario igual se deshabilitan: si el formulario ya se fue, no
        // hay ninguno que siga siendo una acción válida.
        var todos = [].slice.call(form.querySelectorAll(
            'button[type="submit"], button:not([type]), input[type="submit"]'
        ));
        if (disparador && todos.indexOf(disparador) === -1) todos.push(disparador);
        return todos;
    }

    function marcarEnviando(form, disparador) {
        if (!form || form.hasAttribute(MARCA)) return;   // idempotente
        form.setAttribute(MARCA, '');

        botonesDe(form, disparador).forEach(function (btn) {
            // El ancho se congela ANTES de tocar el texto: sin esto el botón se
            // encoge al cambiar "Guardar cambios" por "Guardando...", y en una
            // fila de botones se corre todo lo que tiene al lado.
            var ancho = btn.getBoundingClientRect().width;
            if (ancho) btn.style.minWidth = Math.ceil(ancho) + 'px';

            if (btn === (disparador || botonesDe(form)[0])) {
                // El texto original se guarda para poder volver atrás: si la
                // página se restaura del cache del navegador (botón Atrás), el
                // botón tiene que estar otra vez como estaba.
                btn.setAttribute('data-texto-previo', btn.textContent);
                var espera = btn.getAttribute('data-texto-enviando');
                if (espera) btn.textContent = espera;
                btn.classList.add('btn--enviando');
            }

            // disabled y no aria-disabled: acá la intención es que el click no
            // llegue, no solo anunciarlo. Ningún submit de la app lleva name ni
            // value, así que deshabilitarlo no le saca nada al envío.
            btn.disabled = true;
        });
    }

    function restaurar(form) {
        form.removeAttribute(MARCA);
        botonesDe(form).forEach(function (btn) {
            btn.disabled = false;
            btn.classList.remove('btn--enviando');
            btn.style.minWidth = '';
            var previo = btn.getAttribute('data-texto-previo');
            if (previo !== null) {
                btn.textContent = previo;
                btn.removeAttribute('data-texto-previo');
            }
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.addEventListener('submit', function (ev) {
            // Lo freno validación (o cualquier otro handler): no hay envío que anunciar.
            if (ev.defaultPrevented) return;
            var form = ev.target;
            if (form instanceof HTMLFormElement) marcarEnviando(form, ev.submitter);
        }, false);
    });

    // Volver con el botón Atrás puede devolver la página tal como estaba, con el
    // botón todavía deshabilitado y diciendo "Guardando...". Es un formulario que
    // quedaría inutilizable sin recargar a mano. 'persisted' distingue esa
    // restauración de una carga normal.
    window.addEventListener('pageshow', function (ev) {
        if (!ev.persisted) return;
        [].forEach.call(document.querySelectorAll('form[' + MARCA + ']'), restaurar);
    });

    window.EnvioForm = { marcar: marcarEnviando, restaurar: restaurar };

})(window, document);
