/* =============================================================================
 *  confirm-modal.js
 *  Modal de confirmación uniforme para acciones destructivas / sensibles.
 *  Reemplaza al window.confirm() nativo del navegador.
 *
 *  ----------------------------------------------------------------------------
 *  Uso declarativo (lo más común): poner atributos data-* en un <form>.
 *
 *  <form action="/foo/{id}/baja" method="post"
 *        data-confirm="¿Dar de baja esta carrera?"
 *        data-confirm-detail="Si tiene materias activas no se va a poder."
 *        data-confirm-action="Dar de baja"
 *        data-confirm-style="danger">
 *    <button type="submit">Dar de baja</button>
 *  </form>
 *
 *  Atributos:
 *   data-confirm         (obligatorio)  título / pregunta
 *   data-confirm-detail  (opcional)     texto secundario más chico
 *   data-confirm-action  (opcional)     texto del botón OK (default "Confirmar")
 *   data-confirm-style   (opcional)     "danger" para tono rojo, default neutro
 *
 *  ----------------------------------------------------------------------------
 *  Campo de fecha opcional: cuando la acción necesita que la persona elija una
 *  fecha (la baja de un docente, por ejemplo), el modal la pide ahí mismo en
 *  vez de mandarla a otra pantalla.
 *
 *  data-confirm-date        nombre del parámetro que se manda al servidor
 *  data-confirm-date-label  etiqueta visible del campo
 *  data-confirm-date-value  valor inicial (formato aaaa-mm-dd)
 *  data-confirm-date-min    fecha mínima aceptada
 *  data-confirm-date-max    fecha máxima aceptada
 *
 *  El valor viaja como <input hidden> agregado al form antes de enviarlo, así
 *  que el servidor lo recibe como un parámetro más y no necesita saber que
 *  vino de un modal.
 *
 *  ----------------------------------------------------------------------------
 *  Uso programático:
 *    const ok = await Confirm.ask({
 *        title: '¿Eliminar usuario?',
 *        detail: 'Esta acción no se puede deshacer.',
 *        action: 'Eliminar',
 *        style: 'danger'
 *    });
 *    if (ok) { ... }
 *
 *  ----------------------------------------------------------------------------
 *  Accesibilidad:
 *   - role="dialog" + aria-modal="true"
 *   - Focus en el botón OK al abrir, restaurado al cerrar
 *   - Tab/Shift+Tab cicla entre Cancelar y Confirmar
 *   - Esc cierra (cancela)
 *   - Click fuera del cuadro cierra (cancela)
 * ========================================================================== */

(function (window, document) {
    'use strict';

    var SVG_QUESTION =
        '<svg viewBox="0 0 24 24" width="32" height="32" fill="none" ' +
        'stroke="currentColor" stroke-width="2" stroke-linecap="round" ' +
        'stroke-linejoin="round" aria-hidden="true">' +
        '<circle cx="12" cy="12" r="10"/>' +
        '<path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>' +
        '<line x1="12" y1="17" x2="12.01" y2="17"/>' +
        '</svg>';

    var SVG_WARNING =
        '<svg viewBox="0 0 24 24" width="32" height="32" fill="none" ' +
        'stroke="currentColor" stroke-width="2" stroke-linecap="round" ' +
        'stroke-linejoin="round" aria-hidden="true">' +
        '<path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 ' +
        '3.86a2 2 0 0 0-3.42 0z"/>' +
        '<line x1="12" y1="9" x2="12" y2="13"/>' +
        '<line x1="12" y1="17" x2="12.01" y2="17"/>' +
        '</svg>';

    var overlay   = null;
    var panel     = null;
    var iconEl    = null;
    var titleEl   = null;
    var detailEl  = null;
    var campoEl   = null;
    var campoLbl  = null;
    var campoInp  = null;
    var cancelBtn = null;
    var okBtn     = null;
    var lastFocused = null;
    var resolvePromise = null;

    function build() {
        overlay = document.createElement('div');
        overlay.className = 'modal-overlay';
        overlay.setAttribute('role', 'dialog');
        overlay.setAttribute('aria-modal', 'true');
        overlay.setAttribute('aria-hidden', 'true');
        overlay.innerHTML =
            '<div class="modal" role="document">' +
                '<div class="modal__icon"></div>' +
                '<h2 class="modal__title"></h2>' +
                '<p class="modal__detail"></p>' +
                '<div class="modal__campo" hidden>' +
                    '<label class="modal__campo-label" for="modal-campo-fecha"></label>' +
                    '<input type="date" id="modal-campo-fecha" class="modal__campo-input">' +
                '</div>' +
                '<div class="modal__actions">' +
                    '<button type="button" class="btn btn--ghost modal__cancel">Cancelar</button>' +
                    '<button type="button" class="btn btn--primary modal__ok">Confirmar</button>' +
                '</div>' +
            '</div>';
        document.body.appendChild(overlay);

        panel     = overlay.querySelector('.modal');
        iconEl    = overlay.querySelector('.modal__icon');
        titleEl   = overlay.querySelector('.modal__title');
        detailEl  = overlay.querySelector('.modal__detail');
        campoEl   = overlay.querySelector('.modal__campo');
        campoLbl  = overlay.querySelector('.modal__campo-label');
        campoInp  = overlay.querySelector('.modal__campo-input');
        cancelBtn = overlay.querySelector('.modal__cancel');
        okBtn     = overlay.querySelector('.modal__ok');

        cancelBtn.addEventListener('click', function () { close(false); });
        okBtn.addEventListener('click',     function () { confirmar();  });
        overlay.addEventListener('click',   function (e) {
            if (e.target === overlay) close(false);
        });
        // Trap del foco entre los elementos visibles del modal
        overlay.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') { e.preventDefault(); close(false); return; }
            if (e.key === 'Tab') {
                e.preventDefault();
                var focusables = enfocables();
                var i = focusables.indexOf(document.activeElement);
                var paso = e.shiftKey ? -1 : 1;
                var siguiente = (i + paso + focusables.length) % focusables.length;
                focusables[siguiente].focus();
            }
        });
    }

    // Los elementos que reciben foco, en el orden en que aparecen. El campo entra solo
    // cuando esta visible, para que Tab no caiga en un control escondido.
    function enfocables() {
        var lista = [];
        if (!campoEl.hidden) lista.push(campoInp);
        lista.push(cancelBtn, okBtn);
        return lista;
    }

    // Un campo vacio o fuera de rango no puede confirmar: si dejaramos pasar, el error
    // recien aparecerian despues de recargar la pantalla.
    function confirmar() {
        if (!campoEl.hidden && !campoInp.checkValidity()) {
            campoInp.reportValidity();
            campoInp.focus();
            return;
        }
        close(true);
    }

    function ask(opts) {
        if (!overlay) build();
        opts = opts || {};

        var isDanger = opts.style === 'danger';
        panel.classList.toggle('modal--danger', isDanger);
        okBtn.classList.toggle('btn--primary',     !isDanger);
        okBtn.classList.toggle('btn--danger-solid', isDanger);

        iconEl.innerHTML = isDanger ? SVG_WARNING : SVG_QUESTION;
        titleEl.textContent = opts.title || '¿Confirmar?';
        if (opts.detail) {
            detailEl.textContent = opts.detail;
            detailEl.style.display = '';
        } else {
            detailEl.textContent = '';
            detailEl.style.display = 'none';
        }
        okBtn.textContent = opts.action || 'Confirmar';

        var pideFecha = !!opts.dateName;
        campoEl.hidden = !pideFecha;
        if (pideFecha) {
            campoLbl.textContent = opts.dateLabel || 'Fecha';
            campoInp.value = opts.dateValue || '';
            campoInp.min   = opts.dateMin   || '';
            campoInp.max   = opts.dateMax   || '';
            campoInp.required = true;
        }

        lastFocused = document.activeElement;
        overlay.setAttribute('aria-hidden', 'false');
        overlay.classList.add('modal-overlay--in');
        // Foco inicial: en el campo si hay que completarlo, si no en OK (Enter confirma).
        setTimeout(function () { (pideFecha ? campoInp : okBtn).focus(); }, 50);

        return new Promise(function (resolve) { resolvePromise = resolve; });
    }

    function close(result) {
        if (!overlay) return;
        overlay.classList.remove('modal-overlay--in');
        overlay.setAttribute('aria-hidden', 'true');
        if (lastFocused && typeof lastFocused.focus === 'function') {
            try { lastFocused.focus(); } catch (e) { /* ignore */ }
        }
        if (resolvePromise) {
            var r = resolvePromise;
            resolvePromise = null;
            r(result);
        }
    }

    // Interceptor de forms con data-confirm
    function handleSubmit(ev) {
        var form = ev.target;
        if (!(form instanceof HTMLFormElement)) return;
        var msg = form.dataset.confirm;
        if (!msg) return;

        ev.preventDefault();
        var nombreCampo = form.dataset.confirmDate;

        ask({
            title:  msg,
            detail: form.dataset.confirmDetail,
            action: form.dataset.confirmAction,
            style:  form.dataset.confirmStyle,
            dateName:  nombreCampo,
            dateLabel: form.dataset.confirmDateLabel,
            dateValue: form.dataset.confirmDateValue,
            dateMin:   form.dataset.confirmDateMin,
            dateMax:   form.dataset.confirmDateMax
        }).then(function (ok) {
            if (!ok) return;
            // El valor se lee recien aca porque solo importa si confirmo. Es seguro
            // leerlo con el modal ya cerrado: no se limpia hasta el proximo ask(), y
            // no puede haber dos abiertos a la vez.
            if (nombreCampo) agregarCampoOculto(form, nombreCampo, campoInp.value);
            form.submit();   // submit() programatico NO dispara el event 'submit' otra vez
        });
    }

    // Mete el valor elegido en el form como un input mas, reemplazando el de un intento
    // anterior para no mandar el parametro dos veces.
    function agregarCampoOculto(form, nombre, valor) {
        var previo = form.querySelector('input[type="hidden"][name="' + nombre + '"]');
        if (previo) previo.remove();
        var input = document.createElement('input');
        input.type  = 'hidden';
        input.name  = nombre;
        input.value = valor;
        form.appendChild(input);
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.addEventListener('submit', handleSubmit, true);
    });

    window.Confirm = { ask: ask };

})(window, document);
