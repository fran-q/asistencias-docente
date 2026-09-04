/* =============================================================================
 *  toast.js
 *  API minima de notificaciones toast.
 *
 *  Uso programatico:
 *    Toast.show('Guardado!', 'success');
 *    Toast.show('Algo fallo', 'error', 7000);
 *    Toast.show('Cuidado', 'warning');
 *
 *  Carga automatica desde flash attributes:
 *    En layout/base.html (y login.html) hay un <div id="flash-data"
 *    data-success="..." data-warning="..." data-error="..."> hidden.
 *    Este script lo lee al cargar el DOM y dispara los toasts
 *    correspondientes.
 * ========================================================================== */

(function (window, document) {
    'use strict';

    var DEFAULT_DURATION_MS = 5000;
    var TYPES = {
        success: { icon: '✓' },   // check
        warning: { icon: '⚠' },   // warning sign
        error:   { icon: '✕' },   // cross
        info:    { icon: 'ℹ' }    // info
    };

    var container = null;

    function ensureContainer() {
        if (container) return container;
        container = document.createElement('div');
        container.id = 'toast-container';
        container.setAttribute('role', 'status');
        container.setAttribute('aria-live', 'polite');
        document.body.appendChild(container);
        return container;
    }

    function show(message, type, durationMs) {
        if (!message) return;
        type = TYPES[type] ? type : 'info';
        durationMs = durationMs || DEFAULT_DURATION_MS;

        ensureContainer();

        var toast = document.createElement('div');
        toast.className = 'toast toast--' + type;

        var iconEl = document.createElement('span');
        iconEl.className = 'toast__icon';
        iconEl.textContent = TYPES[type].icon;
        toast.appendChild(iconEl);

        var msgEl = document.createElement('span');
        msgEl.className = 'toast__msg';
        msgEl.textContent = message;
        toast.appendChild(msgEl);

        var closeBtn = document.createElement('button');
        closeBtn.type = 'button';
        closeBtn.className = 'toast__close';
        closeBtn.setAttribute('aria-label', 'Cerrar');
        closeBtn.textContent = '✕';
        closeBtn.addEventListener('click', function () { dismiss(toast); });
        toast.appendChild(closeBtn);

        container.appendChild(toast);

        // forzar layout antes de animar la entrada
        // eslint-disable-next-line no-unused-expressions
        toast.offsetWidth;
        toast.classList.add('toast--in');

        var dismissTimer = setTimeout(function () { dismiss(toast); }, durationMs);
        toast.addEventListener('mouseenter', function () { clearTimeout(dismissTimer); });
        toast.addEventListener('mouseleave', function () {
            dismissTimer = setTimeout(function () { dismiss(toast); }, durationMs);
        });
    }

    function dismiss(toast) {
        if (!toast || toast.classList.contains('toast--out')) return;
        toast.classList.remove('toast--in');
        toast.classList.add('toast--out');
        setTimeout(function () {
            if (toast.parentNode) toast.parentNode.removeChild(toast);
        }, 250);
    }

    function bootstrapFromFlashData() {
        var data = document.getElementById('flash-data');
        if (!data) return;
        if (data.dataset.success) show(data.dataset.success, 'success');
        if (data.dataset.warning) show(data.dataset.warning, 'warning');
        if (data.dataset.error)   show(data.dataset.error,   'error');
    }

    document.addEventListener('DOMContentLoaded', bootstrapFromFlashData);

    /*
     *  Botones que solo avisan por que NO se puede hacer algo.
     *
     *      <button data-aviso-toast="No se puede dar de baja: es titular de 3 materias.">
     *
     *  Existen para las acciones que el servidor ya sabe que van a fallar. Antes se
     *  abria igual el cuadro de confirmacion, se pedia una fecha y el rechazo llegaba
     *  despues de enviar: tres pasos para llegar a un "no". Ahora el motivo aparece de
     *  una vez y no se envia nada.
     *
     *  Se escucha por delegacion en el documento y no boton por boton: los listados se
     *  filtran y se redibujan, y un listener atado a cada fila se pierde en el camino.
     */
    document.addEventListener('click', function (e) {
        var boton = e.target.closest('[data-aviso-toast]');
        if (!boton) return;
        e.preventDefault();
        show(boton.dataset.avisoToast, 'warning', 6000);
    });

    window.Toast = { show: show, dismiss: dismiss };

})(window, document);
