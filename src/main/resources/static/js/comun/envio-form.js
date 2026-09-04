/* =============================================================================
 *  envio-form.js
 *
 *  Bloquea el boton mientras el formulario viaja al servidor.
 *
 *  Por que. Entre el click en "Guardar" y la recarga de la pagina no habia
 *  ninguna senal propia: lo unico que se movia era el indicador del navegador,
 *  que en una pestana maximizada es una franja de dos pixeles arriba de todo.
 *  En una red lenta eso se lee como que el boton no anduvo, y la reaccion
 *  natural es volver a apretarlo. El servidor recibia el alta dos veces.
 *
 *  Cubre los DOS caminos por los que se envia un formulario en la app:
 *
 *   1. Envio normal. Se escucha 'submit' en document, en fase de BURBUJA: asi
 *      corre despues del handler que form-validacion.js pone sobre el <form>.
 *      Si ese handler freno el envio, defaultPrevented ya viene en true y no
 *      se bloquea nada --si no, un formulario invalido quedaria con el boton
 *      muerto y sin forma de reintentar.
 *
 *   2. Envio tras confirmar. confirm-modal.js frena el submit en fase de
 *      CAPTURA y despues llama a form.submit(), que por especificacion NO
 *      vuelve a disparar el evento. Ahi el aviso no puede llegar solo, asi que
 *      confirm-modal invoca EnvioForm.marcar() antes de enviar.
 * ========================================================================== */

(function (window, document) {
    'use strict';

    var MARCA = 'data-enviando';

    function botonesDe(form, disparador) {
        // El boton que se apreto es el que cambia de texto. Los demas submit del
        // mismo formulario igual se deshabilitan: si el formulario ya se fue, no
        // hay ninguno que siga siendo una accion valida.
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
            // El ancho se congela ANTES de tocar el texto: sin esto el boton se
            // encoge al cambiar "Guardar cambios" por "Guardando...", y en una
            // fila de botones se corre todo lo que tiene al lado.
            var ancho = btn.getBoundingClientRect().width;
            if (ancho) btn.style.minWidth = Math.ceil(ancho) + 'px';

            if (btn === (disparador || botonesDe(form)[0])) {
                // El texto original se guarda para poder volver atras: si la
                // pagina se restaura del cache del navegador (boton Atras), el
                // boton tiene que estar otra vez como estaba.
                btn.setAttribute('data-texto-previo', btn.textContent);
                var espera = btn.getAttribute('data-texto-enviando');
                if (espera) btn.textContent = espera;
                btn.classList.add('btn--enviando');
            }

            // disabled y no aria-disabled: aca la intencion es que el click no
            // llegue, no solo anunciarlo. Ningun submit de la app lleva name ni
            // value, asi que deshabilitarlo no le saca nada al envio.
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
            // Lo freno validacion (o cualquier otro handler): no hay envio que anunciar.
            if (ev.defaultPrevented) return;
            var form = ev.target;
            if (form instanceof HTMLFormElement) marcarEnviando(form, ev.submitter);
        }, false);
    });

    // Volver con el boton Atras puede devolver la pagina tal como estaba, con el
    // boton todavia deshabilitado y diciendo "Guardando...". Es un formulario que
    // quedaria inutilizable sin recargar a mano. 'persisted' distingue esa
    // restauracion de una carga normal.
    window.addEventListener('pageshow', function (ev) {
        if (!ev.persisted) return;
        [].forEach.call(document.querySelectorAll('form[' + MARCA + ']'), restaurar);
    });

    window.EnvioForm = { marcar: marcarEnviando, restaurar: restaurar };

})(window, document);
