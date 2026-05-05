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
        cancelBtn = overlay.querySelector('.modal__cancel');
        okBtn     = overlay.querySelector('.modal__ok');

        cancelBtn.addEventListener('click', function () { close(false); });
        okBtn.addEventListener('click',     function () { close(true);  });
        overlay.addEventListener('click',   function (e) {
            if (e.target === overlay) close(false);
        });
        // Trap del foco entre los dos botones
        overlay.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') { e.preventDefault(); close(false); return; }
            if (e.key === 'Tab') {
                e.preventDefault();
                if (document.activeElement === okBtn) cancelBtn.focus();
                else okBtn.focus();
            }
        });
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

        lastFocused = document.activeElement;
        overlay.setAttribute('aria-hidden', 'false');
        overlay.classList.add('modal-overlay--in');
        // Focus inicial en OK (Enter por default confirma; Esc cancela)
        setTimeout(function () { okBtn.focus(); }, 50);

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
        ask({
            title:  msg,
            detail: form.dataset.confirmDetail,
            action: form.dataset.confirmAction,
            style:  form.dataset.confirmStyle
        }).then(function (ok) {
            if (ok) form.submit();   // submit() programatico NO dispara el event 'submit' otra vez
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.addEventListener('submit', handleSubmit, true);
    });

    window.Confirm = { ask: ask };

})(window, document);
