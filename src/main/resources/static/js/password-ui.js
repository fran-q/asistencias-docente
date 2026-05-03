/* =============================================================================
 *  password-ui.js
 *  Mejoras UX para inputs type=password:
 *   - boton ojo (SVG) para mostrar / ocultar el texto
 *   - bloqueo de copy / paste / cut por seguridad
 *
 *  Se aplica automaticamente a todo .password-toggle dentro de un
 *  .password-input que envuelva un <input type="password">.
 *
 *  HTML esperado:
 *    <div class="password-input">
 *      <input type="password" id="..." name="...">
 *      <button type="button" class="password-toggle"
 *              aria-label="Mostrar contraseña"></button>
 *    </div>
 *
 *  El icono del boton lo inyecta este script (SVG inline). Si JS esta
 *  desactivado, el boton queda vacio pero el campo de password sigue
 *  funcionando normalmente.
 * ========================================================================== */

(function (window, document) {
    'use strict';

    var SVG_EYE_OPEN =
        '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" ' +
        'stroke="currentColor" stroke-width="2" stroke-linecap="round" ' +
        'stroke-linejoin="round" aria-hidden="true">' +
        '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>' +
        '<circle cx="12" cy="12" r="3"/>' +
        '</svg>';

    var SVG_EYE_CLOSED =
        '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" ' +
        'stroke="currentColor" stroke-width="2" stroke-linecap="round" ' +
        'stroke-linejoin="round" aria-hidden="true">' +
        '<path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 ' +
        '18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 ' +
        '18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>' +
        '<line x1="1" y1="1" x2="23" y2="23"/>' +
        '</svg>';

    function setupToggle(button) {
        var wrapper = button.closest('.password-input');
        if (!wrapper) return;
        var input = wrapper.querySelector('input');
        if (!input) return;

        // Estado inicial: contraseña oculta -> mostrar icono ojo abierto
        button.innerHTML = SVG_EYE_OPEN;

        button.addEventListener('click', function (ev) {
            ev.preventDefault();
            var visible = input.type === 'text';
            input.type = visible ? 'password' : 'text';
            button.innerHTML = visible ? SVG_EYE_OPEN : SVG_EYE_CLOSED;
            button.setAttribute(
                'aria-label',
                visible ? 'Mostrar contraseña' : 'Ocultar contraseña'
            );
        });
    }

    function blockCopyPaste(input) {
        ['copy', 'cut', 'paste'].forEach(function (ev) {
            input.addEventListener(ev, function (e) {
                e.preventDefault();
                if (window.Toast) {
                    window.Toast.show(
                        'Por seguridad, no se permite ' + (ev === 'paste' ? 'pegar' : 'copiar') +
                        ' en campos de contraseña.',
                        'warning',
                        3000
                    );
                }
            });
        });
        // Tambien deshabilita el menu contextual sobre el input
        input.addEventListener('contextmenu', function (e) { e.preventDefault(); });
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('.password-toggle').forEach(setupToggle);
        document.querySelectorAll('input[type="password"]').forEach(blockCopyPaste);
    });

})(window, document);
